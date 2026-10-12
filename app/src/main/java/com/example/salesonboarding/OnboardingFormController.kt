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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NextFieldGuidance(
    val prediction: FieldPrediction? = null
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
    private var lastGuidanceKey: List<String> = emptyList()

    init {
        scope.launch {
            stateFlow.collect { state ->
                val history = fields
                    .filter { state.values[it.fieldId].isNullOrBlank().not() && state.results[it.fieldId]?.isValid == true }
                    .map { it.fieldId }
                if (history != lastGuidanceKey && predictor != null) {
                    lastGuidanceKey = history
                    guidance.value = NextFieldGuidance(
                        predictor.predict(history, industry, context, "passed")
                    )
                }
            }
        }
    }

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
