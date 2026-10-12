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
    val riskGuidance: CompletionRiskGuidance? = null
)

class OnboardingFormController(
    private val sessionId: String,
    val fields: List<FormField> = DEFAULT_FIELDS,
    scope: CoroutineScope,
    private val entryStore: FieldEntryStore,
    private val predictor: NextFieldPredictor? = null,
    private val industry: String = "telecom",
    private val context: String = "individual"
) {
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
    private var lastGuidanceKey: List<String> = emptyList()

    init {
        scope.launch {
            stateFlow.collect { state ->
                val history = fields
                    .filter { state.values[it.fieldId].isNullOrBlank().not() && state.results[it.fieldId]?.isValid == true }
                    .map { it.fieldId }
                if (history != lastGuidanceKey) {
                    lastGuidanceKey = history
                    val prediction = if (predictor == null) {
                        guidance.value.prediction
                    } else {
                        predictor.predict(history, industry, context, "passed")
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

    fun submit(state: LiveValidationState = stateFlow.value): Boolean {
        if (!canContinue(state)) return false
        fields.forEach { field ->
            val value = state.values[field.fieldId]
            if (!value.isNullOrBlank() && state.results[field.fieldId]?.isValid == true) {
                entryStore.save(
                    FieldEntry(
                        entryId = "$sessionId:${field.fieldId}",
                        sessionId = sessionId,
                        fieldId = field.fieldId,
                        value = value,
                        isValid = true
                    )
                )
            }
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
                riskGuidance = risk.value?.let(CompletionRiskGuidanceMapper::from)
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
                riskGuidance = risk.value?.let(CompletionRiskGuidanceMapper::from)
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
            riskGuidance = risk.value?.let(CompletionRiskGuidanceMapper::from)
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
