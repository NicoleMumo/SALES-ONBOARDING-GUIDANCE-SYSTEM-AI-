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
}
