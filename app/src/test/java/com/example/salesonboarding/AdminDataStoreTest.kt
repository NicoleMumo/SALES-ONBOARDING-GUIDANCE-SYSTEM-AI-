package com.example.salesonboarding

import com.example.salesonboarding.validation.DateFormatAndMinimumAge
import com.example.salesonboarding.validation.EmailFormat
import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KenyanPhoneFormat
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.MustBeTrue
import com.example.salesonboarding.validation.NotBlank
import com.example.salesonboarding.validation.OneOf
import com.example.salesonboarding.validation.PositiveNumber
import com.example.salesonboarding.validation.Required
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminDataStoreTest {
    @Test
    fun configuredCustomFieldsAreAppendedToTheOnboardingForm() {
        val customField = FormField(
            fieldId = "loyalty_number",
            fieldName = "Loyalty number",
            fieldType = "number",
            isRequired = false,
            validationRules = emptyList(),
            description = "Optional customer loyalty number."
        )
        val fields = FieldRuleConfiguration(customFields = listOf(customField))
            .allFields(listOf(KnownFields.get("full_name")))

        assertEquals(listOf("full_name", "loyalty_number"), fields.map { it.fieldId })
        assertEquals("Optional customer loyalty number.", fields.last().description)
    }

    @Test
    fun savedFieldCanBeAppliedLocallyWithoutReloadingAndKeepsRuleOverrides() {
        val fullName = KnownFields.get("full_name")
        val initialConfiguration = FieldRuleConfiguration(
            overrides = mapOf(
                fullName.fieldId to com.example.salesonboarding.validation.FieldRuleOverride(
                    removeRules = listOf(Required, NotBlank)
                )
            )
        )
        val custom = FormField(
            fieldId = "business_type",
            fieldName = "Business type",
            description = "Select the customer type.",
            fieldType = "choice",
            isRequired = true,
            validationRules = listOf(
                Required,
                NotBlank,
                OneOf(setOf("Retail", "Wholesale"))
            )
        )

        val updated = initialConfiguration.withCustomField(custom)
        val configuredFields = updated.allFields(listOf(fullName))

        assertEquals(listOf("full_name", "business_type"), configuredFields.map { it.fieldId })
        assertTrue(configuredFields.first().validationRules.none { it == Required || it == NotBlank })
        assertEquals("Select the customer type.", configuredFields.last().description)
        assertTrue(configuredFields.last().validationRules.contains(OneOf(setOf("Retail", "Wholesale"))))
    }

    @Test
    fun selectedCustomFieldRulesAreAllConverted() {
        val rules = setOf(
            AdminRule.REQUIRED,
            AdminRule.NOT_BLANK,
            AdminRule.EMAIL,
            AdminRule.PHONE,
            AdminRule.POSITIVE_NUMBER,
            AdminRule.MUST_BE_TRUE,
            AdminRule.DATE
        ).toValidationRules()

        assertEquals(
            listOf(
                Required,
                NotBlank,
                EmailFormat,
                KenyanPhoneFormat,
                PositiveNumber,
                MustBeTrue,
                DateFormatAndMinimumAge(18)
            ),
            rules
        )
    }

    @Test
    fun choiceOptionsBecomeControlledValidationRules() {
        val field = FormField(
            fieldId = "business_type",
            fieldName = "Business type",
            fieldType = "choice",
            isRequired = true,
            validationRules = listOf(Required, OneOf(setOf("Retail", "Wholesale")))
        )

        assertTrue(field.validationRules.any { it == OneOf(setOf("Retail", "Wholesale")) })
    }

    @Test
    fun disablingRequirednessAlsoRemovesNotBlankFromCatalogueField() {
        // full_name is a locked compliance field, so use an unlocked one.
        val field = KnownFields.get("email_address")
        val configuredField = FieldRuleConfiguration(
            overrides = mapOf(
                field.fieldId to com.example.salesonboarding.validation.FieldRuleOverride(
                    removeRules = listOf(Required, NotBlank)
                )
            )
        ).fieldFor(field)

        assertTrue(!configuredField.isRequired)
        assertTrue(configuredField.validationRules.none { it == Required || it == NotBlank })
    }

    @Test
    fun lockedComplianceFieldStaysRequiredWhenRemovalIsRequested() {
        val field = KnownFields.get("full_name")
        val configuredField = FieldRuleConfiguration(
            overrides = mapOf(
                field.fieldId to com.example.salesonboarding.validation.FieldRuleOverride(
                    removeRules = listOf(Required, NotBlank)
                )
            )
        ).fieldFor(field)

        assertTrue(configuredField.isRequired)
    }

    @Test
    fun analyticsCountsSessionsAndUsesValidCompletedDurations() {
        val analytics = OnboardingAnalytics.fromSessions(
            listOf(
                OnboardingSessionMetrics("completed", 0L, 120_000L),
                OnboardingSessionMetrics("completed", 60_000L, 240_000L),
                OnboardingSessionMetrics("in_progress", 0L, null),
                OnboardingSessionMetrics("completed", 300_000L, 200_000L)
            )
        )

        assertEquals(4, analytics.sessionCount)
        assertEquals(3, analytics.completedCount)
        assertEquals(0.75, analytics.completionRate, 0.0)
        assertEquals(2.5, analytics.averageCompletionMinutes, 0.0)
    }

    @Test
    fun emptyAnalyticsHasZeroRateAndDuration() {
        val analytics = OnboardingAnalytics.fromSessions(emptyList())

        assertEquals(0, analytics.sessionCount)
        assertEquals(0, analytics.completedCount)
        assertEquals(0.0, analytics.completionRate, 0.0)
        assertTrue(analytics.averageCompletionMinutes.isNaN().not())
        assertEquals(0.0, analytics.averageCompletionMinutes, 0.0)
    }

    @Test
    fun analyticsReportsTeamProgressAndTheMostCommonUnfinishedStep() {
        val analytics = OnboardingAnalytics.fromSessions(
            listOf(
                OnboardingSessionMetrics(
                    status = "completed",
                    startedAt = 0L,
                    completedAt = 60_000L,
                    agentId = "agent-a",
                    validation = mapOf("name" to true, "email" to true),
                    playbookFieldIds = listOf("name", "email"),
                    fieldNames = mapOf("name" to "Name", "email" to "Email")
                ),
                OnboardingSessionMetrics(
                    status = "in_progress",
                    startedAt = 0L,
                    completedAt = null,
                    agentId = "agent-a",
                    validation = mapOf("name" to true),
                    playbookFieldIds = listOf("name", "email"),
                    fieldNames = mapOf("name" to "Name", "email" to "Email")
                ),
                OnboardingSessionMetrics(
                    status = "in_progress",
                    startedAt = 0L,
                    completedAt = null,
                    agentId = "agent-b",
                    validation = mapOf("name" to true),
                    playbookFieldIds = listOf("name", "email"),
                    fieldNames = mapOf("name" to "Name", "email" to "Email")
                )
            )
        )

        assertEquals(200.0 / 3, analytics.averageReadinessPercent, 0.01)
        assertEquals("Email", analytics.commonPausePoint)
        assertEquals(2, analytics.agentPerformance.size)
        assertEquals("agent-a", analytics.agentPerformance.first().agentId)
        assertEquals(0.5, analytics.agentPerformance.first().completionRate, 0.0)
    }
}
