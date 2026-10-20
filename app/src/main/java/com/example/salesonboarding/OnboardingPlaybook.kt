package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KnownFields
import com.example.salesonboarding.validation.NotBlank
import com.example.salesonboarding.validation.Required
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
    private val bankingIndividualFields = listOf(
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

    private val bankingBusinessFields =
        listOf("full_name", "business_name") + bankingIndividualFields.drop(1) + listOf(
            "commercial_address",
            "beneficial_owner_information",
            "business_relationship_purpose",
            "business_relationship_nature",
            "ownership_and_control_information"
        )

    private val telecomBaseFields = listOf(
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

    private val fieldsByIndustry = mapOf(
        "telecom" to telecomBaseFields + listOf("subscriber_number"),
        "banking" to bankingIndividualFields
    )

    private val fieldsByContext = mapOf(
        "banking" to mapOf(
            "individual" to bankingIndividualFields,
            "business" to bankingBusinessFields
        ),
        "telecom" to mapOf(
            "mobile" to telecomFields("mobile"),
            "broadband" to telecomFields("broadband"),
            "tv" to telecomFields("tv"),
            "iot" to telecomFields("iot"),
            "landline" to telecomFields("landline")
        )
    )

    fun forIndustry(
        industry: String,
        configuration: FieldRuleConfiguration,
        context: String? = null
    ): OnboardingPlaybook {
        val baseFieldIds = requireNotNull(fieldsByIndustry[industry]) {
            "No onboarding playbook is defined for industry '$industry'."
        }
        val fieldIds = if (context == null) {
            baseFieldIds
        } else {
            requireNotNull(fieldsByContext[industry]?.get(context)) {
                "No onboarding playbook is defined for '$industry' context '$context'."
            }
        }
        val catalogue = KnownFields.all.associateBy(FormField::fieldId)
        val industryFields = fieldIds.map { fieldId ->
            val field = catalogue[fieldId] ?: if (fieldId == "business_name") {
                FormField(
                    fieldId = "business_name",
                    fieldName = "Registered business name",
                    fieldType = "text",
                    isRequired = true,
                    validationRule = listOf(Required, NotBlank),
                    description = "Enter the business name as shown on its registration documents."
                )
            } else {
                error("Playbook field '$fieldId' is missing from the known-field catalogue.")
            }
            val configuredField = configuration.fieldFor(field)
            when {
                industry == "banking" && context == "business" && fieldId in BANKING_BUSINESS_REQUIRED_FIELDS ->
                    configuredField.copy(
                        isRequired = true,
                        validationRule = configuredField.validationRule
                            .filterNot { it is com.example.salesonboarding.validation.RequiredIf } +
                            listOf(Required, NotBlank)
                    )
                industry == "banking" && context == "business" && fieldId in BANKING_BUSINESS_OPTIONAL_FIELDS ->
                    configuredField.copy(
                        validationRule = configuredField.validationRule
                            .filterNot { it is com.example.salesonboarding.validation.RequiredIf }
                    )
                context != null && fieldId in setOf("phone_service_selection", "internet_service_selection") ->
                    configuredField.copy(
                        isRequired = true,
                        validationRule = configuredField.validationRule
                            .filterNot { it is com.example.salesonboarding.validation.RequiredIf } +
                            listOf(Required, NotBlank)
                    )
                else -> configuredField
            }
        }
        val fields = (industryFields + configuration.customFields)
            .distinctBy(FormField::fieldId)
        val industryTitle = when (industry) {
            "telecom" -> "Telecommunications onboarding"
            "banking" -> "Banking onboarding"
            else -> industry.replaceFirstChar(Char::uppercase) + " onboarding"
        }
        val title = if (context == null) industryTitle else {
            "${industryTitle.removeSuffix(" onboarding")} ${context.replaceFirstChar(Char::uppercase)} onboarding"
        }
        return OnboardingPlaybook.create(
            id = if (context == null) "$industry-standard-onboarding" else "$industry-$context-onboarding",
            title = title,
            industry = industry,
            fields = fields
        )
    }

    private fun telecomFields(context: String): List<String> {
        val contextFields = when (context) {
            "mobile", "landline" -> listOf("phone_service_selection")
            "broadband", "tv" -> listOf("internet_service_selection")
            else -> emptyList()
        }
        val streamingFields = if (context == "tv" || context == "broadband") {
            listOf("streaming_addons_selection")
        } else {
            emptyList()
        }
        return telecomBaseFields + contextFields + streamingFields + listOf("subscriber_number")
    }

    private val BANKING_BUSINESS_REQUIRED_FIELDS = setOf(
        "commercial_address",
        "beneficial_owner_information",
        "business_relationship_nature",
        "business_relationship_purpose"
    )

    private val BANKING_BUSINESS_OPTIONAL_FIELDS = setOf(
        "ownership_and_control_information",
        "assets"
    )
}
