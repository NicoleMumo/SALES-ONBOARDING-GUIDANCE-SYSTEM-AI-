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

class PasswordResetTest {
    @Test
    fun sentEmailShowsTheNeutralMessage() {
        assertNull(PasswordReset.failureMessage(null))
    }

    @Test
    fun otherErrorsAskThePersonToTryAgain() {
        assertEquals(PasswordReset.FAILED_MESSAGE, PasswordReset.failureMessage(IllegalStateException("offline")))
    }
}

class InvitationIndexTest {
    @Test
    fun anInvitationIsFiledUnderTheEmailTheRulesCanWorkOut() {
        // The rules compute auth.token.email.toLowerCase().replace('.', ','): the same key.
        assertEquals("mary.jane@acme.co.ke".replace('.', ','), InvitationIndex.key(" Mary.Jane@Acme.co.ke "))
    }

    @Test
    fun anAddressTheDatabaseCannotUseAsAKeyIsNotFiled() {
        assertNull(InvitationIndex.key(""))
        assertNull(InvitationIndex.key("odd#name@acme.com"))
        assertNull(InvitationIndex.key("a/b@acme.com"))
    }
}
