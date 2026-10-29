package com.example.salesonboarding

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ValidationErrorDisplayTest {
    @Test
    fun failingEmailRuleProducesAnActionableMessage() = runTest {
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope
        )

        validation.updateField("email_address", "not-an-email")
        advanceTimeBy(300)
        runCurrent()

        val error = ValidationErrorDisplayMapper.forState(validation.stateFlow.value).single()
        assertEquals("email_address", error.fieldId)
        assertEquals("Enter a valid email address.", error.message)
    }

    @Test
    fun untouchedRequiredFieldDoesNotShowAnError() {
        val state = LiveValidationState(
            results = mapOf("email_address" to ValidationResult.invalid("Enter a value for this field."))
        )

        assertTrue(ValidationErrorDisplayMapper.forState(state).isEmpty())
        assertNull(
            ValidationErrorDisplayMapper.forResult(
                "email_address",
                state.results.getValue("email_address"),
                touched = false
            )
        )
    }

    @Test
    fun touchedAndClearedRequiredFieldShowsAnError() = runTest {
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope
        )

        validation.updateField("email_address", "agent@example.com")
        advanceTimeBy(300)
        runCurrent()
        validation.updateField("email_address", "")
        advanceTimeBy(300)
        runCurrent()

        val error = ValidationErrorDisplayMapper.forState(validation.stateFlow.value).single()
        assertEquals("Enter a value for this field.", error.message)
    }

    @Test
    fun mapperReportsMultipleTouchedFieldsWithErrors() {
        val state = LiveValidationState(
            results = mapOf(
                "email_address" to ValidationResult.invalid("Enter a valid email address."),
                "mobile_phone_number" to ValidationResult.invalid(
                    "Enter a valid Kenyan phone number (07XXXXXXXX or +2547XXXXXXXX)."
                ),
                "profession" to ValidationResult.valid
            ),
            touchedFieldIds = setOf("email_address", "mobile_phone_number", "profession")
        )

        val errors = ValidationErrorDisplayMapper.forState(state)

        assertEquals(
            listOf("email_address", "mobile_phone_number"),
            errors.map(ValidationErrorDisplay::fieldId)
        )
        assertFalse(errors.any { it.fieldId == "profession" })
    }
}
