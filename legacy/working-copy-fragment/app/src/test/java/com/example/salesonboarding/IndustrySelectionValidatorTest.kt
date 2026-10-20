package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndustrySelectionValidatorTest {
    @Test
    fun supportedIndustriesAreAccepted() {
        assertNull(IndustrySelectionValidator.validate("telecom"))
        assertNull(IndustrySelectionValidator.validate("banking"))
    }

    @Test
    fun missingIndustryIsRejected() {
        assertEquals(
            "Select an onboarding industry.",
            IndustrySelectionValidator.validate(null)
        )
    }

    @Test
    fun unsupportedIndustryIsRejected() {
        assertEquals(
            "Select a valid onboarding industry.",
            IndustrySelectionValidator.validate("insurance")
        )
    }
}
