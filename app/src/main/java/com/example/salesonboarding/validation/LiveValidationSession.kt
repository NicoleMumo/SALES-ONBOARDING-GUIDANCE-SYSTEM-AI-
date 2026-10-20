package com.example.salesonboarding.validation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveValidationState(
    val values: Map<String, String?> = emptyMap(),
    val results: Map<String, ValidationResult> = emptyMap(),
    val touchedFieldIds: Set<String> = emptySet(),
    val interactionHistory: List<FieldInteraction> = emptyList()
)

data class FieldInteraction(
    val fieldId: String,
    val startedAtMillis: Long,
    val validationPassed: Boolean? = null,
    val validatedAtMillis: Long? = null
)

class LiveValidationSession(
    private val sessionId: String,
    fields: List<FormField> = KnownFields.all,
    configuration: FieldRuleConfiguration = FieldRuleConfiguration(),
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS
) {
    private val fieldsById = configuration.allFields(fields).associateBy { it.fieldId }
    private val validationJobs = mutableMapOf<String, Job>()
    private val state = MutableStateFlow(LiveValidationState())

    val stateFlow: StateFlow<LiveValidationState> = state.asStateFlow()

    /**
     * Values are published immediately, while validation waits 300 ms after
     * the last update for that field to avoid validating every keystroke.
     */
    fun updateField(fieldId: String, value: String?): Job {
        require(fieldsById.containsKey(fieldId)) { "Unknown field: $fieldId" }
        val currentState = state.value
        val lastInteraction = currentState.interactionHistory.lastOrNull()
        val history = if (lastInteraction?.fieldId != fieldId || lastInteraction?.validationPassed != null) {
            currentState.interactionHistory + FieldInteraction(fieldId, System.currentTimeMillis())
        } else {
            currentState.interactionHistory
        }
        val affectedIds = buildSet {
            add(fieldId)
            fieldsById.values
                .filter { field -> field.validationRule.any { it.dependsOn(fieldId) } }
                .forEach { add(it.fieldId) }
        }
        state.value = currentState.copy(
            values = state.value.values + (fieldId to value),
            results = currentState.results - affectedIds,
            touchedFieldIds = currentState.touchedFieldIds + fieldId,
            interactionHistory = history
        )

        validationJobs[fieldId]?.cancel()
        val job = scope.launch {
            delay(debounceMillis)
            validateAffectedFields(fieldId)
        }
        validationJobs[fieldId] = job
        return job
    }

    private fun validateAffectedFields(changedFieldId: String) {
        val affectedIds = buildSet {
            add(changedFieldId)
            fieldsById.values
                .filter { field -> field.validationRule.any { it.dependsOn(changedFieldId) } }
                .forEach { add(it.fieldId) }
        }
        val currentValues = state.value.values
        val context = ValidationContext(currentValues)
        val updatedResults = state.value.results.toMutableMap()

        affectedIds.forEach { fieldId ->
            val field = fieldsById.getValue(fieldId)
            val entry = FieldEntry(
                entryId = "$sessionId:$fieldId",
                sessionId = sessionId,
                fieldId = fieldId,
                value = currentValues[fieldId]
            )
            updatedResults[fieldId] = field.validate(entry, context)
        }
        val validatedAt = System.currentTimeMillis()
        val changedFieldResult = updatedResults[changedFieldId]
        val history = state.value.interactionHistory.toMutableList()
        val interactionIndex = history.indexOfLast { it.fieldId == changedFieldId }
        if (interactionIndex >= 0 && changedFieldResult != null) {
            history[interactionIndex] = history[interactionIndex].copy(
                validationPassed = changedFieldResult.isValid,
                validatedAtMillis = validatedAt
            )
        }
        state.value = state.value.copy(results = updatedResults, interactionHistory = history)
    }

    private fun ValidationRule.dependsOn(fieldId: String): Boolean =
        (this is RequiredIf && dependsOnFieldId == fieldId) ||
            (this is PatternByFieldValue && dependsOnFieldId == fieldId)

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}
