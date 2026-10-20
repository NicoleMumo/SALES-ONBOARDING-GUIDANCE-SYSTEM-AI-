package com.example.salesonboarding

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.risk.CompletionRiskEvent
import com.example.salesonboarding.risk.CompletionRiskLevel
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.EmailFormat
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.Required
import com.example.salesonboarding.validation.ValidationErrorDisplayMapper

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFormControllerTest {
    @Test
    fun continueIsDisabledForInvalidAndEmptyRequiredFields() = runTest {
        val store = InMemoryFieldEntryStore()
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("email_address"),
                KnownFields.get("full_name")
            ),
            scope = backgroundScope,
            entryStore = store
        )

        controller.updateField("email_address", "bad")
        advanceTimeBy(300)
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals(
            "Enter a valid email address.",
            ValidationErrorDisplayMapper.forState(controller.stateFlow.value).single().message
        )
    }

    @Test
    fun fixingAllRequiredFieldsEnablesContinueAndSavesEntries() = runTest {
        val store = InMemoryFieldEntryStore()
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("email_address"),
                KnownFields.get("full_name")
            ),
            scope = backgroundScope,
            entryStore = store
        )

        controller.updateField("email_address", "agent@example.com")
        controller.updateField("full_name", "Jane Wanjiku")
        advanceTimeBy(300)
        runCurrent()

        assertTrue(ValidationErrorDisplayMapper.forState(controller.stateFlow.value).isEmpty())
        assertTrue(controller.canContinue())
        assertTrue(controller.submit())
        assertEquals(setOf("email_address", "full_name"), store.savedEntries.map { it.fieldId }.toSet())
    }

    @Test
    fun successfulOnboardingRecordsTheCompleteSessionLifecycle() = runTest {
        val store = RecordingOnboardingSessionStore()
        val fields = listOf(
            KnownFields.get("full_name"),
            KnownFields.get("email_address")
        )
        val controller = OnboardingFormController(
            sessionId = "successful-session",
            fields = fields,
            scope = backgroundScope,
            entryStore = store,
            industry = "telecom",
            context = "individual",
            agentId = "agent@example.com"
        )

        controller.updateField("full_name", "Jane Wanjiku")
        controller.updateField("email_address", "jane@example.com")
        advanceTimeBy(300)
        runCurrent()

        assertTrue(controller.canContinue())
        assertTrue(controller.submit())
        assertEquals(
            SessionStart(
                "successful-session",
                "agent@example.com",
                "telecom",
                "individual",
                "telecom-standard-onboarding",
                controller.playbook.version,
                fields.map { it.fieldId }
            ),
            store.startedSession
        )
        assertEquals(setOf("full_name", "email_address"), store.validatedFields)
        assertEquals(setOf("full_name", "email_address"), store.savedEntries.map { it.fieldId }.toSet())
        assertTrue(store.events.any { it.type == "field_validation_passed" && it.fieldId == "full_name" })
        assertTrue(store.events.any { it.type == "field_validation_passed" && it.fieldId == "email_address" })
        assertEquals("successful-session", store.completedSessionId)
    }

    @Test
    fun customFieldValidationWorksAndPreservesHistoryForDynamicModelScoring() = runTest {
        val customField = FormField(
            fieldId = "loyalty_email",
            fieldName = "Loyalty email",
            fieldType = "email",
            isRequired = true,
            validationRule = listOf(Required, EmailFormat),
            description = "Email used for loyalty updates."
        )
        val modelHistories = mutableListOf<List<String>>()
        val controller = OnboardingFormController(
            sessionId = "custom-field-session",
            fields = listOf(customField, KnownFields.get("full_name")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore(),
            predictor = NextFieldPredictor { history, _, _, _ ->
                modelHistories += history
                null
            }
        )

        controller.updateField("loyalty_email", "customer@example.com")
        advanceTimeBy(300)
        runCurrent()
        controller.updateField("full_name", "Jane Wanjiku")
        advanceTimeBy(300)
        runCurrent()

        assertTrue(controller.canContinue())
        assertEquals(listOf(emptyList(), listOf("loyalty_email")), modelHistories)
    }

    @Test
    fun readinessNamesMissingFieldsAndCalculatesRequiredCompletion() = runTest {
        val fields = listOf(
            KnownFields.get("full_name"),
            KnownFields.get("email_address")
        )
        val controller = OnboardingFormController(
            sessionId = "readiness-session",
            fields = fields,
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.updateField("full_name", "Jane Wanjiku")
        advanceTimeBy(300)
        runCurrent()

        val readiness = controller.readiness()
        assertEquals(2, readiness.requiredFieldCount)
        assertEquals(1, readiness.completedRequiredFieldCount)
        assertEquals(listOf("Email address"), readiness.incompleteRequiredFields)
        assertEquals(50, readiness.scorePercent)
        assertFalse(readiness.isReady)
    }

    @Test
    fun guidanceTellsAgentToCorrectAnInvalidField() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.updateField("email_address", "bad")
        advanceTimeBy(300)
        runCurrent()

        assertEquals("email_address", controller.guidanceFlow.value.correction?.fieldId)
        assertEquals(
            "Correct Email address: Enter a valid email address.",
            controller.guidanceFlow.value.message
        )
    }

    private data class SessionStart(
        val sessionId: String,
        val agentId: String,
        val industry: String,
        val context: String,
        val playbookId: String,
        val playbookVersion: String,
        val fieldIds: List<String>
    )

    private data class SessionEvent(
        val type: String,
        val fieldId: String?
    )

    private class RecordingOnboardingSessionStore : OnboardingSessionStore {
        var startedSession: SessionStart? = null
        var completedSessionId: String? = null
        val validatedFields = mutableSetOf<String>()
        val events = mutableListOf<SessionEvent>()
        private val entries = mutableListOf<FieldEntry>()

        val savedEntries: List<FieldEntry>
            get() = entries.toList()

        override fun save(entry: FieldEntry) {
            entries.removeAll { it.fieldId == entry.fieldId }
            entries += entry
        }

        override fun startSession(
            sessionId: String,
            agentId: String,
            industry: String,
            context: String,
            playbook: OnboardingPlaybook
        ) {
            startedSession = SessionStart(
                sessionId,
                agentId,
                industry,
                context,
                playbook.id,
                playbook.version,
                playbook.fields.map { it.fieldId }
            )
        }

        override fun recordValidation(sessionId: String, fieldId: String, isValid: Boolean, message: String?) {
            if (isValid) validatedFields += fieldId
        }

        override fun appendEvent(sessionId: String, type: String, fieldId: String?) {
            events += SessionEvent(type, fieldId)
        }

        override fun markCompleted(sessionId: String) {
            completedSessionId = sessionId
        }

        override fun getSession(
            sessionId: String,
            onResult: (Result<StoredOnboardingSession?>) -> Unit
        ) = error("Retrieval is not part of this controller test")
    }

    @Test
    fun missingRequiredFieldProducesGuidance() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.updateField("email_address", "")
        advanceTimeBy(300)
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals("email_address", controller.guidanceFlow.value.correction?.fieldId)
        assertEquals(
            "Correct Email address: Enter a value for this field.",
            controller.guidanceFlow.value.message
        )
    }

    @Test
    fun malformedPhoneNumberProducesFormatGuidance() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("mobile_phone_number")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.updateField("mobile_phone_number", "071234567")
        advanceTimeBy(300)
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals(
            "Enter a valid Kenyan phone number (07XXXXXXXX or +2547XXXXXXXX).",
            controller.guidanceFlow.value.correction?.message
        )
    }

    @Test
    fun dependentRequiredFieldProducesGuidanceWhenServiceTypeNeedsIt() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("service_type"),
                KnownFields.get("phone_service_selection")
            ),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.updateField("service_type", "mobile")
        controller.updateField("phone_service_selection", "")
        advanceTimeBy(300)
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals(
            "phone_service_selection",
            controller.guidanceFlow.value.correction?.fieldId
        )
        assertEquals(
            "Correct Phone service selection: Enter a value because the selected option requires it.",
            controller.guidanceFlow.value.message
        )
    }

    @Test
    fun difficultSessionPublishesHighRiskGuidanceAfterRepeatedErrors() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.recordRiskEvent(
            CompletionRiskEvent(16.0, validationPassed = false, isRevisit = true, progressPercent = 25.0)
        )
        controller.recordRiskEvent(
            CompletionRiskEvent(2.0, validationPassed = false, isRevisit = true, progressPercent = 25.0)
        )
        runCurrent()

        assertEquals(CompletionRiskLevel.HIGH, controller.riskFlow.value?.level)
        assertEquals(
            "Pause and help the customer complete the remaining information.",
            controller.guidanceFlow.value.riskGuidance?.message
        )
    }

    @Test
    fun guidanceTellsAgentWhichRequiredFieldToCollectNext() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("full_name"),
                KnownFields.get("email_address")
            ),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.updateField("full_name", "Jane Wanjiku")
        advanceTimeBy(300)
        runCurrent()

        assertEquals("email_address", controller.guidanceFlow.value.nextFieldId)
        assertEquals("Collect Email address next.", controller.guidanceFlow.value.message)
    }

    @Test
    fun guidanceConfirmsWhenAllRequiredFieldsAreComplete() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.updateField("email_address", "agent@example.com")
        advanceTimeBy(300)
        runCurrent()

        assertTrue(controller.guidanceFlow.value.isComplete)
        assertEquals(
            "All required information is complete. You can continue.",
            controller.guidanceFlow.value.message
        )
    }
}
