package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FieldRuleOverride
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.Required
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPlaybookTest {
    @Test
    fun playbooksFollowModelVocabAndContextSpecificSequences() {
        val bankingIndividual = OnboardingPlaybooks.forIndustry(
            "banking", FieldRuleConfiguration(), "individual"
        )
        val bankingBusiness = OnboardingPlaybooks.forIndustry(
            "banking", FieldRuleConfiguration(), "business"
        )
        val telecomBroadband = OnboardingPlaybooks.forIndustry(
            "telecom", FieldRuleConfiguration(), "broadband"
        )

        assertEquals("mobile_phone_number", bankingIndividual.fields[2].fieldId)
        assertFalse(bankingIndividual.fields.any { it.fieldId == "gender" })
        assertTrue(bankingBusiness.fields.any { it.fieldId == "beneficial_owner_information" && it.isRequired })
        assertTrue(telecomBroadband.fields.any { it.fieldId == "internet_service_selection" })
        assertTrue(telecomBroadband.fields.any { it.fieldId == "streaming_addons_selection" })
        assertFalse(telecomBroadband.fields.any { it.fieldId == "phone_service_selection" })
    }

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
    fun bankingContextPlaybooksMatchTheSyntheticSessionFields() {
        val configuration = FieldRuleConfiguration()
        val individual = OnboardingPlaybooks.forIndustry("banking", configuration, "individual")
        val business = OnboardingPlaybooks.forIndustry("banking", configuration, "business")

        assertEquals(
            listOf(
                "full_name",
                "email_address",
                "mobile_phone_number",
                "date_of_birth",
                "home_address",
                "identification_document_type",
                "identification_document_number",
                "selfie_liveness_check",
                "face_match_verification",
                "profession",
                "income",
                "assets",
                "us_person_status",
                "politically_exposed_person_status",
                "terms_and_conditions_acceptance"
            ),
            individual.fields.map { it.fieldId }
        )
        assertEquals(
            listOf(
                "full_name",
                "business_name",
                "email_address",
                "mobile_phone_number",
                "date_of_birth",
                "home_address",
                "identification_document_type",
                "identification_document_number",
                "selfie_liveness_check",
                "face_match_verification",
                "profession",
                "income",
                "assets",
                "us_person_status",
                "politically_exposed_person_status",
                "terms_and_conditions_acceptance",
                "commercial_address",
                "beneficial_owner_information",
                "business_relationship_purpose",
                "business_relationship_nature",
                "ownership_and_control_information"
            ),
            business.fields.map { it.fieldId }
        )
    }

    @Test
    fun telecomContextPlaybooksMatchTheSyntheticSessionFields() {
        val baseFields = listOf(
            "full_name",
            "email_address",
            "contact_phone_number",
            "date_of_birth",
            "gender",
            "physical_address",
            "identification_document",
            "subscriber_type",
            "service_type",
            "contract_type",
            "payment_method",
            "paperless_billing_preference"
        )
        val configuration = FieldRuleConfiguration()
        fun fieldIds(context: String) =
            OnboardingPlaybooks.forIndustry("telecom", configuration, context)
                .fields
                .map { it.fieldId }

        assertEquals(
            baseFields + "phone_service_selection" + "subscriber_number",
            fieldIds("mobile")
        )
        assertEquals(
            baseFields + "internet_service_selection" + "streaming_addons_selection" + "subscriber_number",
            fieldIds("broadband")
        )
        assertEquals(
            baseFields + "internet_service_selection" + "streaming_addons_selection" + "subscriber_number",
            fieldIds("tv")
        )
        assertEquals(baseFields + "subscriber_number", fieldIds("iot"))
        assertEquals(
            baseFields + "phone_service_selection" + "subscriber_number",
            fieldIds("landline")
        )
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
