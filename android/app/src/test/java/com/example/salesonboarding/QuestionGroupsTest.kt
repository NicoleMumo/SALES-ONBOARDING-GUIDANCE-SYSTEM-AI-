package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionGroupsTest {
    @Test
    fun everyStandardQuestionLandsInAKnownGroup() {
        KnownFields.all.forEach { field ->
            assertTrue(field.fieldId, QuestionGroups.of(field.fieldId) in QuestionGroups.order)
        }
    }

    @Test
    fun commonQuestionsAreGroupedSensibly() {
        assertEquals("Identity", QuestionGroups.of("full_name"))
        assertEquals("Identity", QuestionGroups.of("identification_document_number"))
        assertEquals("Contact", QuestionGroups.of("mobile_phone_number"))
        assertEquals("Contact", QuestionGroups.of("home_address"))
        assertEquals("Business", QuestionGroups.of("business_name"))
        assertEquals("Money", QuestionGroups.of("income"))
        assertEquals("Service", QuestionGroups.of("phone_service_selection"))
    }

    @Test
    fun anIndustryOnlyListsItsOwnQuestions() {
        val banking = OnboardingPlaybooks.fieldIdsFor("banking")
        val telecom = OnboardingPlaybooks.fieldIdsFor("telecom")
        assertTrue("income" in banking)
        assertFalse("income" in telecom)
        assertTrue("subscriber_type" in telecom)
        assertFalse("subscriber_type" in banking)
        assertTrue(OnboardingPlaybooks.fieldIdsFor("unknown").isEmpty())
    }

    @Test
    fun deletingACustomQuestionRemovesOnlyThatOne() {
        fun custom(id: String) = FormField(id, id, "text", false, emptyList(), "Help.")
        val configuration = FieldRuleConfiguration(customFields = listOf(custom("a"), custom("b")))
        assertEquals(listOf("b"), configuration.withoutCustomField("a").customFields.map { it.fieldId })
        assertEquals(2, configuration.withoutCustomField("missing").customFields.size)
    }
}
