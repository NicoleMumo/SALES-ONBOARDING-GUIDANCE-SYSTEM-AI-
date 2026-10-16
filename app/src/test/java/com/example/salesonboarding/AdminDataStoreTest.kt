package com.example.salesonboarding

import com.example.salesonboarding.validation.DateFormatAndMinimumAge
import com.example.salesonboarding.validation.EmailFormat
import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KenyanPhoneFormat
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.MustBeTrue
import com.example.salesonboarding.validation.NotBlank
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
            validationRule = emptyList(),
            description = "Optional customer loyalty number."
        )
        val fields = FieldRuleConfiguration(customFields = listOf(customField))
            .allFields(listOf(KnownFields.get("full_name")))

        assertEquals(listOf("full_name", "loyalty_number"), fields.map { it.fieldId })
        assertEquals("Optional customer loyalty number.", fields.last().description)
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
}
