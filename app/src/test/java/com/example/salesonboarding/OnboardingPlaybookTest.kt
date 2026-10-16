package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FieldRuleOverride
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.Required
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPlaybookTest {
    @Test
    fun industriesGetDifferentOrderedStandardPlaybooks() {
        val configuration = FieldRuleConfiguration()
        val telecom = OnboardingPlaybooks.forIndustry("telecom", configuration)
        val banking = OnboardingPlaybooks.forIndustry("banking", configuration)

        assertEquals("telecom-standard-onboarding", telecom.id)
        assertEquals("banking-standard-onboarding", banking.id)
        assertNotEquals(telecom.fields.map { it.fieldId }, banking.fields.map { it.fieldId })
        assertTrue(telecom.fields.indexOfFirst { it.fieldId == "service_type" } >
            telecom.fields.indexOfFirst { it.fieldId == "full_name" })
        assertTrue(banking.fields.any { it.fieldId == "identification_document_number" })
    }

    @Test
    fun playbookRevisionChangesWhenConfiguredRulesChange() {
        val initial = OnboardingPlaybooks.forIndustry("telecom", FieldRuleConfiguration())
        val changed = OnboardingPlaybooks.forIndustry(
            "telecom",
            FieldRuleConfiguration(
                overrides = mapOf(
                    "full_name" to FieldRuleOverride(removeRules = listOf(Required))
                )
            )
        )

        assertNotEquals(initial.version, changed.version)
    }

    @Test
    fun customFieldsAreAppendedAfterStandardSteps() {
        val customField = KnownFields.get("profession")
        val playbook = OnboardingPlaybooks.forIndustry(
            "telecom",
            FieldRuleConfiguration(customFields = listOf(customField))
        )

        assertEquals("profession", playbook.fields.last().fieldId)
    }
}
