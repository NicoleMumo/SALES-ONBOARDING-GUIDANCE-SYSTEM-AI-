package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.NotBlank
import com.example.salesonboarding.validation.Required
import com.example.salesonboarding.validation.RequiredIf
import com.example.salesonboarding.validation.OneOf
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
    private val bankingBaseFields = listOf(
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
    )
    private val bankingBusinessFields = listOf(
        "commercial_address",
        "beneficial_owner_information",
        "business_relationship_purpose",
        "business_relationship_nature",
        "ownership_and_control_information"
    )

    fun forIndustry(
        industry: String,
        configuration: FieldRuleConfiguration,
        context: String? = null
    ): OnboardingPlaybook {
        val workflowContext = context ?: if (industry == "banking") "individual" else "mobile"
        val fieldIds = when (industry) {
            "banking" -> bankingBaseFields + if (workflowContext == "business") bankingBusinessFields else emptyList()
            "telecom" -> listOf(
                "full_name", "email_address", "contact_phone_number", "date_of_birth",
                "gender", "physical_address", "identification_document", "subscriber_type",
                "service_type", "contract_type", "payment_method", "paperless_billing_preference"
            ) + when (workflowContext) {
                "mobile", "landline" -> listOf("phone_service_selection")
                "broadband", "tv" -> listOf("internet_service_selection", "streaming_addons_selection")
                "iot" -> emptyList()
                else -> throw IllegalArgumentException("Unsupported telecom context '$workflowContext'.")
            } + "subscriber_number"
            else -> throw IllegalArgumentException("No onboarding playbook is defined for industry '$industry'.")
        }
        if (industry == "banking" && workflowContext !in setOf("individual", "business")) {
            throw IllegalArgumentException("Unsupported banking context '$workflowContext'.")
        }
        val catalogue = KnownFields.all.associateBy(FormField::fieldId)
        val businessOnly = if (industry == "banking" && workflowContext == "business") {
            bankingBusinessFields.toSet()
        } else {
            emptySet()
        }
        val industryFields = fieldIds.map { fieldId ->
            val field = requireNotNull(catalogue[fieldId]) {
                "Playbook field '$fieldId' is missing from the known-field catalogue."
            }
            val configured = configuration.fieldFor(field)
            when {
                industry == "telecom" && fieldId == "service_type" -> configured.copy(
                    isRequired = true,
                    validationRule = listOf(Required, NotBlank, OneOf(setOf(workflowContext)))
                )
                fieldId in businessOnly -> {
                    configured.copy(
                        isRequired = true,
                        validationRule = (configured.validationRule.filterNot { it is RequiredIf } + Required + NotBlank).distinct()
                    )
                }
                else -> configured
            }
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
            title = "$title (${workflowContext.replaceFirstChar(Char::uppercase)})",
            industry = industry,
            fields = fields
        )
    }
}
