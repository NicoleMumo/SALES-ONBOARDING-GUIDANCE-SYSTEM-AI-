package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.validation.FieldEntryStore
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.LiveValidationSession
import com.example.salesonboarding.validation.LiveValidationState
import com.example.salesonboarding.validation.RequiredIf
import com.example.salesonboarding.validation.ValidationErrorDisplay
import com.example.salesonboarding.validation.ValidationErrorDisplayMapper
import com.example.salesonboarding.validation.ValidationResult
import com.example.salesonboarding.risk.CompletionRiskEvent
import com.example.salesonboarding.risk.CompletionRiskGuidance
import com.example.salesonboarding.risk.CompletionRiskGuidanceMapper
import com.example.salesonboarding.risk.CompletionRiskResult
import com.example.salesonboarding.risk.CompletionRiskScorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NextFieldGuidance(
    val prediction: FieldPrediction? = null,
    val correction: ValidationErrorDisplay? = null,
    val nextFieldId: String? = null,
    val message: String = "",
    val isComplete: Boolean = false,
    val riskGuidance: CompletionRiskGuidance? = null,
    val modelUnavailable: Boolean = false
)

data class OnboardingReadiness(
    val requiredFieldCount: Int,
    val completedRequiredFieldCount: Int,
    val incompleteRequiredFields: List<String>,
    val invalidOptionalFields: List<String>
) {
    val scorePercent: Int
        get() = if (requiredFieldCount == 0) 100
        else completedRequiredFieldCount * 100 / requiredFieldCount

    val isReady: Boolean
        get() = incompleteRequiredFields.isEmpty() && invalidOptionalFields.isEmpty()
}

class OnboardingFormController(
    private val sessionId: String,
    val fields: List<FormField> = DEFAULT_FIELDS,
    scope: CoroutineScope,
    private val entryStore: FieldEntryStore,
    private val predictor: NextFieldPredictor? = null,
    private val industry: String = "telecom",
    private val context: String = "individual",
    private val agentId: String = "unknown-agent",
    playbook: OnboardingPlaybook? = null,
    initialModelUnavailable: Boolean = false,
    private val agentEmail: String = ""
) {
    val playbook: OnboardingPlaybook = playbook ?: OnboardingPlaybook.create(
        id = "$industry-standard-onboarding",
        title = "$industry onboarding",
        industry = industry,
        fields = fields
    )
    private val modelUnavailable = initialModelUnavailable || predictor == null

    private val session = LiveValidationSession(
        sessionId = sessionId,
        fields = fields,
        scope = scope
    )

    val stateFlow: StateFlow<LiveValidationState> = session.stateFlow
    private val guidance = MutableStateFlow(NextFieldGuidance())
    val guidanceFlow: StateFlow<NextFieldGuidance> = guidance.asStateFlow()
    private val risk = MutableStateFlow<CompletionRiskResult?>(null)
    val riskFlow: StateFlow<CompletionRiskResult?> = risk.asStateFlow()
    private var lastPredictorInput: PredictorInput? = null
    private var lastObservedResults: Map<String, ValidationResult> = emptyMap()
    private val processedRiskInteractions = mutableSetOf<Int>()

    init {
        (entryStore as? OnboardingSessionStore)?.startSession(
            sessionId,
            agentId,
            industry,
            context,
            this.playbook,
            agentEmail
        )
        scope.launch {
            stateFlow.collect { state ->
                val sessionStore = entryStore as? OnboardingSessionStore
                state.results.forEach { (fieldId, result) ->
                    if (lastObservedResults[fieldId] != result) {
                        sessionStore?.recordValidation(sessionId, fieldId, result.isValid, result.errorMessage)
                    }
                }
                lastObservedResults = state.results

                state.interactionHistory.forEachIndexed { index, interaction ->
                    val validatedAt = interaction.validatedAtMillis
                    val passed = interaction.validationPassed
                    if (validatedAt != null && passed != null && processedRiskInteractions.add(index)) {
                        val durationMinutes =
                            (validatedAt - interaction.startedAtMillis).coerceAtLeast(0) / 60_000.0
                        val wasRevisited = state.interactionHistory
                            .take(index)
                            .any { it.fieldId == interaction.fieldId }
                        recordRiskEvent(
                            CompletionRiskEvent(
                                durationMinutes = durationMinutes,
                                validationPassed = passed,
                                isRevisit = wasRevisited,
                                progressPercent = readiness(state).scorePercent.toDouble()
                            )
                        )
                        sessionStore?.appendEvent(
                            sessionId,
                            if (passed) "field_validation_passed" else "field_validation_failed",
                            interaction.fieldId
                        )
                    }
                }

                val lastValidatedInteraction = state.interactionHistory.lastOrNull {
                    it.validatedAtMillis != null && it.validationPassed != null
                }
                val hasRequiredIncompleteFields = fields.any { field ->
                    isRequired(field, state) && !isValid(field, state)
                }
                val predictionCandidates = if (hasRequiredIncompleteFields) {
                    fields.filterNot { isValid(it, state) }
                } else {
                    emptyList()
                }
                val predictorInput = PredictorInput(
                    fieldHistory = state.interactionHistory
                        .filter { it.validatedAtMillis != null }
                        .map { it.fieldId },
                    lastValidation = when (lastValidatedInteraction?.validationPassed) {
                        true -> "passed"
                        false -> "failed"
                        null -> "NONE"
                    },
                    candidateFieldIds = predictionCandidates.map(FormField::fieldId)
                )
                if (predictorInput != lastPredictorInput) {
                    lastPredictorInput = predictorInput
                    val prediction = if (predictor == null) {
                        null
                    } else {
                        predictor.predictAmongCandidates(
                            predictorInput.fieldHistory,
                            industry,
                            context,
                            predictorInput.lastValidation,
                            predictionCandidates,
                            fields
                        )
                    }
                    guidance.value = buildGuidance(state, prediction)
                } else {
                    guidance.value = buildGuidance(state, guidance.value.prediction)
                }
            }
        }
        scope.launch {
            risk.collect {
                guidance.value = buildGuidance(stateFlow.value, guidance.value.prediction)
            }
        }
    }

    fun updateField(fieldId: String, value: String) {
        session.updateField(fieldId, value)
    }

    fun recordRiskEvent(event: CompletionRiskEvent) {
        val events = currentRiskEvents + event
        risk.value = CompletionRiskScorer.score(events)
        currentRiskEvents = events
    }

    fun canContinue(state: LiveValidationState = stateFlow.value): Boolean =
        fields.all { field ->
            val required = isRequired(field, state)
            if (!required && state.values[field.fieldId].isNullOrBlank()) {
                true
            } else {
                state.touchedFieldIds.contains(field.fieldId) &&
                    state.results[field.fieldId]?.isValid == true
            }
        }

    fun readiness(state: LiveValidationState = stateFlow.value): OnboardingReadiness {
        val requiredFields = fields.filter { isRequired(it, state) }
        val incompleteRequired = requiredFields.filterNot { isValid(it, state) }
        val invalidOptional = fields.filter { field ->
            !isRequired(field, state) &&
                field.fieldId in state.touchedFieldIds &&
                !state.values[field.fieldId].isNullOrBlank() &&
                state.results[field.fieldId]?.isValid != true
        }
        return OnboardingReadiness(
            requiredFieldCount = requiredFields.size,
            completedRequiredFieldCount = requiredFields.size - incompleteRequired.size,
            incompleteRequiredFields = incompleteRequired.map(FormField::fieldName),
            invalidOptionalFields = invalidOptional.map(FormField::fieldName)
        )
    }

    fun submit(state: LiveValidationState = stateFlow.value): Boolean =
        submit(state) { }

    fun submit(
        state: LiveValidationState,
        onResult: (Result<Unit>) -> Unit
    ): Boolean {
        if (!canContinue(state)) return false
        val entries = fields.mapNotNull { field ->
            val value = state.values[field.fieldId]
            if (!value.isNullOrBlank() && state.results[field.fieldId]?.isValid == true) {
                FieldEntry(
                    entryId = "$sessionId:${field.fieldId}",
                    sessionId = sessionId,
                    fieldId = field.fieldId,
                    value = value,
                    isValid = true
                )
            } else null
        }
        val sessionStore = entryStore as? OnboardingSessionStore
        if (sessionStore != null) {
            sessionStore.completeSession(sessionId, entries, onResult)
        } else {
            runCatching { entries.forEach(entryStore::save) }
                .onSuccess { onResult(Result.success(Unit)) }
                .onFailure { onResult(Result.failure(it)) }
        }
        return true
    }

    private fun buildGuidance(
        state: LiveValidationState,
        prediction: FieldPrediction?
    ): NextFieldGuidance {
        val correction = ValidationErrorDisplayMapper.forState(state).firstOrNull()
        if (correction != null) {
            val fieldName = fields.firstOrNull { it.fieldId == correction.fieldId }?.fieldName
                ?: correction.fieldId
            return NextFieldGuidance(
                prediction = prediction,
                correction = correction,
                message = "Correct $fieldName: ${correction.message}",
                riskGuidance = risk.value?.let(CompletionRiskGuidanceMapper::from),
                modelUnavailable = modelUnavailable
            )
        }

        val nextField = fields.firstOrNull { field ->
            isRequired(field, state) && !isValid(field, state)
        }
        if (nextField == null) {
            return NextFieldGuidance(
                prediction = prediction,
                message = "All required information is complete. You can continue.",
                isComplete = true,
                riskGuidance = risk.value?.let(CompletionRiskGuidanceMapper::from),
                modelUnavailable = modelUnavailable
            )
        }

        val predictedField = prediction?.let { predicted ->
            fields.firstOrNull { it.fieldId == predicted.fieldId && !isValid(it, state) }
        }
        val recommendedField = predictedField ?: nextField
        return NextFieldGuidance(
            prediction = prediction,
            nextFieldId = recommendedField.fieldId,
            message = "Collect ${recommendedField.fieldName} next.",
            riskGuidance = risk.value?.let(CompletionRiskGuidanceMapper::from),
            modelUnavailable = modelUnavailable
        )
    }

    private fun isRequired(field: FormField, state: LiveValidationState): Boolean =
        field.isRequired || field.validationRule.any { rule ->
            rule is RequiredIf && state.values[rule.dependsOnFieldId]
                ?.equals(rule.expectedValue, ignoreCase = true) == true
        }

    private fun isValid(field: FormField, state: LiveValidationState): Boolean =
        !state.values[field.fieldId].isNullOrBlank() &&
            state.results[field.fieldId]?.isValid == true

    private var currentRiskEvents: List<CompletionRiskEvent> = emptyList()

    private data class PredictorInput(
        val fieldHistory: List<String>,
        val lastValidation: String,
        val candidateFieldIds: List<String>
    )

    companion object {
        val DEFAULT_FIELDS: List<FormField> = listOf(
            KnownFields.get("full_name"),
            KnownFields.get("email_address"),
            KnownFields.get("mobile_phone_number"),
            KnownFields.get("date_of_birth"),
            KnownFields.get("gender"),
            KnownFields.get("home_address")
        )
    }
}

class InMemoryFieldEntryStore : FieldEntryStore {
    private val entries = mutableListOf<FieldEntry>()

    val savedEntries: List<FieldEntry>
        get() = entries.toList()

    override fun save(entry: FieldEntry) {
        entries.removeAll { it.sessionId == entry.sessionId && it.fieldId == entry.fieldId }
        entries.add(entry)
    }
}
