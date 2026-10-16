package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KnownFields
import java.security.MessageDigest

data class OnboardingPlaybook(
    val id: String,
    val title: String,
    val industry: String,
    val version: String,
    val fields: List<FormField>
) {
    companion object {
        fun create(
            id: String,
            title: String,
            industry: String,
            fields: List<FormField>
        ): OnboardingPlaybook {
            val manifest = fields.joinToString("\n") { field ->
                listOf(
                    field.fieldId,
                    field.fieldName,
                    field.fieldType,
                    field.isRequired.toString(),
                    field.description,
                    field.validationRule.joinToString("|")
                ).joinToString("\u001f")
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(manifest.toByteArray(Charsets.UTF_8))
                .take(6)
                .joinToString("") { "%02x".format(it) }
            return OnboardingPlaybook(id, title, industry, "v1-$digest", fields)
        }
    }
}

object OnboardingPlaybooks {
    private val fieldsByIndustry = mapOf(
        "telecom" to listOf(
            "full_name",
            "email_address",
            "mobile_phone_number",
            "date_of_birth",
            "gender",
            "home_address",
            "service_type",
            "subscriber_type",
            "contract_type",
            "subscriber_number",
            "payment_method",
            "paperless_billing_preference",
            "terms_and_conditions_acceptance"
        ),
        "banking" to listOf(
            "full_name",
            "date_of_birth",
            "identification_document_type",
            "identification_document_number",
            "email_address",
            "contact_phone_number",
            "physical_address",
            "profession",
            "income",
            "politically_exposed_person_status",
            "terms_and_conditions_acceptance"
        )
    )

    fun forIndustry(
        industry: String,
        configuration: FieldRuleConfiguration
    ): OnboardingPlaybook {
        val fieldIds = requireNotNull(fieldsByIndustry[industry]) {
            "No onboarding playbook is defined for industry '$industry'."
        }
        val catalogue = KnownFields.all.associateBy(FormField::fieldId)
        val industryFields = fieldIds.map { fieldId ->
            val field = requireNotNull(catalogue[fieldId]) {
                "Playbook field '$fieldId' is missing from the known-field catalogue."
            }
            configuration.fieldFor(field)
        }
        val fields = (industryFields + configuration.customFields)
            .distinctBy(FormField::fieldId)
        val title = when (industry) {
            "telecom" -> "Telecommunications onboarding"
            "banking" -> "Banking onboarding"
            else -> industry.replaceFirstChar(Char::uppercase) + " onboarding"
        }
        return OnboardingPlaybook.create(
            id = "$industry-standard-onboarding",
            title = title,
            industry = industry,
            fields = fields
        )
    }
}
