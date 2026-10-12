package com.example.salesonboarding

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
