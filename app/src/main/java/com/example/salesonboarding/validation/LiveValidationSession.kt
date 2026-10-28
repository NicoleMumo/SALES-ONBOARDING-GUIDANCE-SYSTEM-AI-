package com.example.salesonboarding.validation

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Tracks the answers in one onboarding form and checks them as the agent types.
 *
 * Values are published immediately; validation waits [debounceMillis] after the last
 * change to a field, so it does not run on every keystroke. All state changes go through
 * [MutableStateFlow.update], so concurrent calls cannot overwrite each other.
 */
class LiveValidationSession(
    private val sessionId: String,
    fields: List<FormField> = KnownFields.all,
    configuration: FieldRuleConfiguration = FieldRuleConfiguration(),
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
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

    private val validationJobs = ConcurrentHashMap<String, Job>()
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
     * Records a new value for [fieldId] and schedules its check. The returned job
     * completes once that check has run, or is cancelled if the field changes again first.
     */
    fun updateField(fieldId: String, value: String?): Job {
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

        validationJobs[fieldId]?.cancel()
        val job = scope.launch {
            delay(debounceMillis)
            revalidate(fieldId)
        }
        validationJobs[fieldId] = job
        job.invokeOnCompletion { validationJobs.remove(fieldId, job) }
        return job
    }

    /** Cancels checks that are still waiting out the debounce delay. */
    fun cancelPendingValidation() {
        validationJobs.values.forEach(Job::cancel)
        validationJobs.clear()
    }

    private fun revalidate(changedFieldId: String) {
        val validatedAt = nowMillis()
        state.update { current ->
            val results = validate(affectedBy(changedFieldId), current.values)
            val changedPassed = results.getValue(changedFieldId).isValid
            current.copy(
                results = current.results + results,
                interactionHistory =
                    current.interactionHistory.withValidation(changedFieldId, changedPassed, validatedAt)
            )
        }
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
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
        private const val CHECK_FAILED_MESSAGE = "This field could not be checked. Try again."
    }
}

/** Adds a new interaction unless the agent is still on the same, not yet validated, field. */
private fun List<FieldInteraction>.withInteractionFor(
    fieldId: String,
    startedAtMillis: Long
): List<FieldInteraction> {
    val last = lastOrNull()
    val stillOpen = last != null && last.fieldId == fieldId && last.validationPassed == null
    return if (stillOpen) this else this + FieldInteraction(fieldId, startedAtMillis)
}

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