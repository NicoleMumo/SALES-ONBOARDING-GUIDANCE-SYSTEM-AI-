package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.validation.FieldEntryStore
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.LiveValidationSession
import com.example.salesonboarding.validation.LiveValidationState
import com.example.salesonboarding.validation.RequiredIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class OnboardingFormController(
    private val sessionId: String,
    val fields: List<FormField> = DEFAULT_FIELDS,
    scope: CoroutineScope,
    private val entryStore: FieldEntryStore
) {
    private val session = LiveValidationSession(
        sessionId = sessionId,
        fields = fields,
        scope = scope
    )

    val stateFlow: StateFlow<LiveValidationState> = session.stateFlow

    fun updateField(fieldId: String, value: String) {
        session.updateField(fieldId, value)
    }

    fun canContinue(state: LiveValidationState = stateFlow.value): Boolean =
        fields.all { field ->
            val required = field.isRequired || field.validationRule.any { rule ->
                rule is RequiredIf && state.values[rule.dependsOnFieldId]
                    ?.equals(rule.expectedValue, ignoreCase = true) == true
            }
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
