package com.example.salesonboarding.validation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveValidationSessionTest {
    @Test
    fun fieldIsValidatedAfterItsValueChanges() = runTest {
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            debounceMillis = 300
        )

        validation.updateField("email_address", "invalid")
        assertTrue(validation.stateFlow.value.results.isEmpty())

        advanceTimeBy(300)
        runCurrent()
        assertFalse(validation.stateFlow.value.results.getValue("email_address").isValid)
    }

    @Test
    fun changingADependencyRevalidatesOnlyDependentFields() = runTest {
        val fields = listOf(
            KnownFields.get("service_type"),
            KnownFields.get("phone_service_selection"),
            KnownFields.get("email_address")
        )
        val validation = LiveValidationSession("session", fields, scope = backgroundScope)

        validation.updateField("phone_service_selection", "")
        validation.updateField("email_address", "valid@example.com")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(validation.stateFlow.value.results.getValue("email_address").isValid)

        validation.updateField("service_type", "mobile")
        advanceTimeBy(300)
        runCurrent()

        assertFalse(validation.stateFlow.value.results.getValue("phone_service_selection").isValid)
        assertEquals(true, validation.stateFlow.value.results.getValue("email_address").isValid)
    }

    @Test
    fun unrelatedFieldDoesNotChangeWhenAnotherFieldIsEdited() = runTest {
        val fields = listOf(
            KnownFields.get("email_address"),
            KnownFields.get("profession")
        )
        val validation = LiveValidationSession("session", fields, scope = backgroundScope)

        validation.updateField("email_address", "invalid")
        advanceTimeBy(300)
        runCurrent()
        val emailResult = validation.stateFlow.value.results.getValue("email_address")

        validation.updateField("profession", "Engineer")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(emailResult, validation.stateFlow.value.results.getValue("email_address"))
    }

    @Test
    fun adminRuleOverridesAreUsedByLiveValidation() = runTest {
        val configuration = FieldRuleConfiguration(
            mapOf(
                "profession" to FieldRuleOverride(
                    addRules = listOf(RegexPattern("^[A-Z].*")),
                    removeRules = listOf(NotBlank)
                )
            )
        )
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("profession")),
            configuration = configuration,
            scope = backgroundScope
        )

        validation.updateField("profession", "engineer")
        advanceTimeBy(300)
        runCurrent()
        assertFalse(validation.stateFlow.value.results.getValue("profession").isValid)
    }
}
