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
    val touchedFieldIds: Set<String> = emptySet()
)

class LiveValidationSession(
    private val sessionId: String,
    fields: List<FormField> = KnownFields.all,
    configuration: FieldRuleConfiguration = FieldRuleConfiguration(),
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val onValidationSettled: (fieldId: String, result: ValidationResult) -> Unit = { _, _ -> }
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
        state.value = state.value.copy(
            values = state.value.values + (fieldId to value),
            touchedFieldIds = state.value.touchedFieldIds + fieldId
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
        state.value = state.value.copy(results = updatedResults)
        onValidationSettled(changedFieldId, updatedResults.getValue(changedFieldId))
    }

    private fun ValidationRule.dependsOn(fieldId: String): Boolean =
        (this is RequiredIf && dependsOnFieldId == fieldId) ||
            (this is PatternByFieldValue && dependsOnFieldId == fieldId)

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}
