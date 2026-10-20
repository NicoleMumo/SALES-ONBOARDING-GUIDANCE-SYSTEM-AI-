package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginFormValidatorTest {
    @Test
    fun validSalesAgentCredentialsPassValidation() {
        assertNull(LoginFormValidator.validate("agent@example.com", "password123"))
    }

    @Test
    fun blankEmailIsRejected() {
        assertEquals(
            "Enter your email address.",
            LoginFormValidator.validate("", "password123")
        )
    }

    @Test
    fun malformedEmailIsRejected() {
        assertEquals(
            "Enter a valid email address.",
            LoginFormValidator.validate("agent", "password123")
        )
    }

    @Test
    fun existingShortPasswordCanBeUsedToSignIn() {
        assertNull(LoginFormValidator.validate("agent@example.com", "short"))
    }
}
