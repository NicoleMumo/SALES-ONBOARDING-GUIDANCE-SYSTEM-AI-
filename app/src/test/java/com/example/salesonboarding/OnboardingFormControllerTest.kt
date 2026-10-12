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
import com.example.salesonboarding.validation.KnownFields
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
                "individual"
            ),
            store.startedSession
        )
        assertEquals(setOf("full_name", "email_address"), store.validatedFields)
        assertEquals(setOf("full_name", "email_address"), store.savedEntries.map { it.fieldId }.toSet())
        assertTrue(store.events.any { it.type == "field_updated" && it.fieldId == "full_name" })
        assertTrue(store.events.any { it.type == "field_updated" && it.fieldId == "email_address" })
        assertEquals("successful-session", store.completedSessionId)
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
        val context: String
    )

    private data class SessionEvent(
        val type: String,
        val fieldId: String?,
        val value: String?
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

        override fun startSession(sessionId: String, agentId: String, industry: String, context: String) {
            startedSession = SessionStart(sessionId, agentId, industry, context)
        }

        override fun recordValidation(sessionId: String, fieldId: String, isValid: Boolean, message: String?) {
            if (isValid) validatedFields += fieldId
        }

        override fun appendEvent(sessionId: String, type: String, fieldId: String?, value: String?) {
            events += SessionEvent(type, fieldId, value)
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
