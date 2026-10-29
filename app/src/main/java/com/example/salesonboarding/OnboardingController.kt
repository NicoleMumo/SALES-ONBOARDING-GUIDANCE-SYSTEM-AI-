package com.example.salesonboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// region Answer checking

data class LiveValidationState(
    val values: Map<String, String?> = emptyMap(),
    val results: Map<String, ValidationResult> = emptyMap(),
    val touchedFieldIds: Set<String> = emptySet(),
    val interactionHistory: List<FieldInteraction> = emptyList()
) {
    // [values] holds customer answers, so keep them out of logs and crash reports.
    override fun toString() =
        "LiveValidationState(fields=${values.keys}, touched=$touchedFieldIds, " +
                "valid=${results.mapValues { it.value.isValid }})"
}

data class FieldInteraction(
    val fieldId: String,
    val startedAtMillis: Long,
    val validationPassed: Boolean? = null,
    val validatedAtMillis: Long? = null
)

/**
 * Tracks the answers in one onboarding form and checks each one when the agent has
 * finished with it.
 *
 * [updateField] only records what was typed, so a half-typed answer is never marked
 * wrong. [checkField] checks it, when the agent leaves the box, picks a choice, or moves
 * on. Each check of a changed answer is one attempt in [LiveValidationState.interactionHistory];
 * checking an answer that has not changed records nothing. All state changes go through
 * [MutableStateFlow.update], so concurrent calls cannot overwrite each other.
 */
class LiveValidationSession(
    private val sessionId: String,
    fields: List<FormField> = KnownFields.all,
    configuration: FieldRuleConfiguration = FieldRuleConfiguration(),
    initialValues: Map<String, String?> = emptyMap(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val fieldsById = configuration.allFields(fields).associateBy { it.fieldId }

    /** For each field id, the fields whose rules read it and must be re-checked when it changes. */
    private val dependentsByFieldId: Map<String, Set<String>> =
        buildMap<String, MutableSet<String>> {
            fieldsById.values.forEach { field ->
                field.validationRules
                    .mapNotNull { it.dependsOnFieldId }
                    .forEach { source -> getOrPut(source) { mutableSetOf() }.add(field.fieldId) }
            }
        }

    private val state = MutableStateFlow(LiveValidationState())

    val stateFlow: StateFlow<LiveValidationState> = state.asStateFlow()

    init {
        // Saved drafts are untrusted: drop unknown ids and blanks, then re-check everything.
        val restored = initialValues
            .filterKeys(fieldsById::containsKey)
            .filterValues { !it.isNullOrBlank() }
        if (restored.isNotEmpty()) {
            state.value = LiveValidationState(
                values = restored,
                results = validate(restored.keys, restored),
                touchedFieldIds = restored.keys
            )
        }
    }

    /**
     * Records a new value for [fieldId] without checking it. Any earlier result for it, and
     * for fields whose rules read it, is cleared until [checkField] runs.
     */
    fun updateField(fieldId: String, value: String?) {
        require(fieldsById.containsKey(fieldId)) { "Unknown field: $fieldId" }
        val affectedIds = affectedBy(fieldId)
        val startedAt = nowMillis()

        state.update { current ->
            current.copy(
                values = current.values + (fieldId to value),
                results = current.results - affectedIds,
                touchedFieldIds = current.touchedFieldIds + fieldId,
                interactionHistory = current.interactionHistory.withInteractionFor(fieldId, startedAt)
            )
        }
    }

    /**
     * Checks [fieldId] (and the fields whose rules read it) if it changed since its last
     * check, and records the attempt. Does nothing for an unchanged answer.
     */
    fun checkField(fieldId: String) {
        require(fieldsById.containsKey(fieldId)) { "Unknown field: $fieldId" }
        val validatedAt = nowMillis()
        state.update { current ->
            if (!current.interactionHistory.hasOpenInteraction(fieldId)) return@update current
            val results = validate(affectedBy(fieldId), current.values)
            current.copy(
                results = current.results + results,
                interactionHistory = current.interactionHistory
                    .withValidation(fieldId, results.getValue(fieldId).isValid, validatedAt)
            )
        }
    }

    /** Checks every answer that changed since it was last checked, in the order they were started. */
    fun checkPendingFields() {
        stateFlow.value.interactionHistory
            .filter { it.validationPassed == null }
            .map(FieldInteraction::fieldId)
            .distinct()
            .forEach(::checkField)
    }

    /** The changed field plus every field whose rules depend on it. */
    private fun affectedBy(fieldId: String): Set<String> =
        dependentsByFieldId[fieldId].orEmpty() + fieldId

    /**
     * Checks each field in [fieldIds] against [values]. A rule that throws is treated as a
     * failed check, so a broken rule can never let an answer through or crash the form.
     */
    private fun validate(
        fieldIds: Collection<String>,
        values: Map<String, String?>
    ): Map<String, ValidationResult> {
        val context = ValidationContext(values)
        return fieldIds.associateWith { fieldId ->
            val entry = FieldEntry(
                entryId = "$sessionId:$fieldId",
                sessionId = sessionId,
                fieldId = fieldId,
                value = values[fieldId]
            )
            try {
                fieldsById.getValue(fieldId).validate(entry, context)
            } catch (_: Exception) {
                ValidationResult.invalid(CHECK_FAILED_MESSAGE)
            }
        }
    }

    companion object {
        private const val CHECK_FAILED_MESSAGE = "This field could not be checked. Try again."
    }
}

/** True while [fieldId] has a change that has not been checked yet. */
private fun List<FieldInteraction>.hasOpenInteraction(fieldId: String): Boolean =
    any { it.fieldId == fieldId && it.validationPassed == null }

/** Starts a new attempt at [fieldId] unless one is already open, so one attempt is one check. */
private fun List<FieldInteraction>.withInteractionFor(
    fieldId: String,
    startedAtMillis: Long
): List<FieldInteraction> =
    if (hasOpenInteraction(fieldId)) this else this + FieldInteraction(fieldId, startedAtMillis)

/** Records the check result on the most recent interaction with [fieldId]. */
private fun List<FieldInteraction>.withValidation(
    fieldId: String,
    passed: Boolean,
    validatedAtMillis: Long
): List<FieldInteraction> {
    val index = indexOfLast { it.fieldId == fieldId }
    if (index < 0) return this
    return toMutableList().also {
        it[index] = it[index].copy(validationPassed = passed, validatedAtMillis = validatedAtMillis)
    }
}

// endregion

// region Form controller

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

data class DraftSaveStatus(
    val isSaving: Boolean = false,
    val savedAtMillis: Long? = null,
    val errorMessage: String? = null
)

class OnboardingFormController(
    private val sessionId: String,
    val fields: List<FormField> = DEFAULT_FIELDS,
    private val scope: CoroutineScope,
    private val entryStore: FieldEntryStore,
    private val predictor: NextFieldPredictor? = null,
    private val industry: String = "telecom",
    private val context: String = "individual",
    private val agentId: String = "unknown-agent",
    playbook: OnboardingPlaybook? = null,
    initialModelUnavailable: Boolean = false,
    private val agentEmail: String = "",
    initialValues: Map<String, String?> = emptyMap(),
    private val initialResumeFieldId: String? = null,
    private val isResumingSession: Boolean = false,
    /**
     * The trained abandonment-risk model. When null (file missing or refused),
     * the older hand-set rule in [CompletionRiskScorer] is used instead.
     */
    private val riskModel: TrainedCompletionRiskModel? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    /** When the form opened: the first answer's time is counted from here. */
    private val openedAtMillis = nowMillis()

    val playbook: OnboardingPlaybook = playbook ?: OnboardingPlaybook.create(
        id = "$industry-standard-onboarding",
        title = "$industry onboarding",
        industry = industry,
        fields = fields
    )
    val supportsDraftSaving: Boolean = entryStore is OnboardingSessionStore
    private val modelUnavailable = initialModelUnavailable || predictor == null

    private val session = LiveValidationSession(
        sessionId = sessionId,
        fields = fields,
        initialValues = initialValues,
        nowMillis = nowMillis
    )

    val stateFlow: StateFlow<LiveValidationState> = session.stateFlow
    private val guidance = MutableStateFlow(NextFieldGuidance())
    val guidanceFlow: StateFlow<NextFieldGuidance> = guidance.asStateFlow()
    private val draftSaveStatus = MutableStateFlow(DraftSaveStatus())
    val draftSaveStatusFlow: StateFlow<DraftSaveStatus> = draftSaveStatus.asStateFlow()
    private val risk = MutableStateFlow<CompletionRiskResult?>(null)
    val riskFlow: StateFlow<CompletionRiskResult?> = risk.asStateFlow()
    private var lastPredictorInput: PredictorInput? = null
    private var lastObservedResults: Map<String, ValidationResult> = emptyMap()
    private val processedRiskInteractions = mutableSetOf<Int>()
    private var currentResumeFieldId = initialResumeFieldId ?: fields.firstOrNull()?.fieldId
    val resumeFieldId: String?
        get() = currentResumeFieldId
    private var hasDraftChanges = initialValues.values.any { !it.isNullOrBlank() }
    private var draftSaveJob: Job? = null
    private var draftSaveRequestId = 0

    init {
        if (!isResumingSession) {
            (entryStore as? OnboardingSessionStore)?.startSession(
                sessionId,
                agentId,
                industry,
                context,
                this.playbook,
                agentEmail
            )
        }
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
                        val wasRevisited = state.interactionHistory
                            .take(index)
                            .any { it.fieldId == interaction.fieldId }
                        // The risk model was trained on simulated steps whose time is the whole
                        // step (asking, answering, typing) and whose progress is answers passed
                        // out of all the form's questions, so the app measures both the same way.
                        recordRiskEvent(
                            CompletionRiskEvent(
                                durationMinutes = stepMinutes(state, validatedAt),
                                validationPassed = passed,
                                isRevisit = wasRevisited,
                                progressPercent = answeredPercent(state)
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

    /** Records what the agent typed or chose. It is checked by [checkField], not here. */
    fun updateField(fieldId: String, value: String) {
        session.updateField(fieldId, value)
        hasDraftChanges = true
        scheduleDraftSave()
    }

    /** Checks [fieldId] once the agent has finished with it (left the box, picked a choice). */
    fun checkField(fieldId: String) = session.checkField(fieldId)

    /** Checks every changed answer not yet checked, e.g. before changing section or reviewing. */
    fun checkPendingFields() = session.checkPendingFields()

    fun setResumeField(fieldId: String) {
        if (fields.none { it.fieldId == fieldId }) return
        currentResumeFieldId = fieldId
        scheduleDraftSave()
    }

    fun recordRiskEvent(event: CompletionRiskEvent) {
        val events = currentRiskEvents + event
        risk.value = riskModel?.score(
            events,
            isBanking = industry == "banking",
            isBusiness = context == "business"
        ) ?: CompletionRiskScorer.score(events)
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
            draftSaveJob?.cancel()
            draftSaveRequestId++
            draftSaveStatus.value = DraftSaveStatus()
            sessionStore.completeSession(sessionId, entries, fields.map(FormField::fieldId), onResult)
        } else {
            val saveResult = try {
                entries.forEach(entryStore::save)
                Result.success(Unit)
            } catch (error: Exception) {
                Result.failure(error)
            }
            onResult(saveResult)
        }
        return true
    }

    private fun scheduleDraftSave() {
        val sessionStore = entryStore as? OnboardingSessionStore ?: return
        if (!hasDraftChanges) return
        draftSaveJob?.cancel()
        val requestId = ++draftSaveRequestId
        draftSaveStatus.value = DraftSaveStatus(isSaving = true)
        draftSaveJob = scope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MILLIS)
            val currentState = stateFlow.value
            val entries = fields.map { field ->
                FieldEntry(
                    entryId = "$sessionId:${field.fieldId}",
                    sessionId = sessionId,
                    fieldId = field.fieldId,
                    value = currentState.values[field.fieldId],
                    isValid = currentState.results[field.fieldId]?.isValid
                )
            }
            sessionStore.saveDraft(sessionId, entries, currentResumeFieldId) { result ->
                if (requestId == draftSaveRequestId) {
                    draftSaveStatus.value = result.fold(
                        onSuccess = { DraftSaveStatus(savedAtMillis = System.currentTimeMillis()) },
                        onFailure = {
                            DraftSaveStatus(
                                errorMessage = it.message
                                    ?: "Could not save this draft. Check your connection and try again."
                            )
                        }
                    )
                }
            }
        }
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
        field.isRequiredFor(state.values)

    /** Minutes since the previous checked answer (or since the form opened), up to this check. */
    private fun stepMinutes(state: LiveValidationState, validatedAt: Long): Double {
        val previousCheck = state.interactionHistory
            .mapNotNull { it.validatedAtMillis }
            .filter { it < validatedAt }
            .maxOrNull() ?: openedAtMillis
        return (validatedAt - previousCheck).coerceAtLeast(0) / 60_000.0
    }

    /** Questions answered correctly, out of all the form's questions, as a percentage. */
    private fun answeredPercent(state: LiveValidationState): Double =
        if (fields.isEmpty()) 0.0 else fields.count { isValid(it, state) } * 100.0 / fields.size

    /** The risk inputs recorded so far, one per checked answer. */
    val riskEvents: List<CompletionRiskEvent>
        get() = currentRiskEvents

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
        private const val DRAFT_SAVE_DEBOUNCE_MILLIS = 700L

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

// endregion
