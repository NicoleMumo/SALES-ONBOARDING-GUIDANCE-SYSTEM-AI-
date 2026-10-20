package com.example.salesonboarding.validation

object KnownFields {
    private val required = listOf(Required, NotBlank)
    private val descriptions = mapOf(
        "assets" to "Enter the business's declared assets as a number greater than zero.",
        "beneficial_owner_information" to "List the people who ultimately own or control the business.",
        "business_relationship_nature" to "Describe the business and its relationship with the account.",
        "business_relationship_purpose" to "Record why the business wants to open or use this account.",
        "commercial_address" to "Enter the address where the business operates.",
        "contact_phone_number" to "Enter a phone number where the customer can be reached.",
        "contract_type" to "Choose the customer's prepaid or postpaid plan.",
        "date_of_birth" to "Enter the customer's date of birth. The customer must be at least 18.",
        "email_address" to "Enter an email address the customer can access.",
        "face_match_verification" to "Record the result of your organization's face-match check.",
        "full_name" to "Enter the customer's name as shown on their identification document.",
        "gender" to "Choose the gender stated by the customer.",
        "home_address" to "Enter the customer's home address, including the area or street where available.",
        "identification_document" to "Record the identification document provided by the customer.",
        "identification_document_number" to "Enter the number exactly as it appears on the selected document.",
        "identification_document_type" to "Choose the type of identification document provided.",
        "income" to "Enter the customer's verified income as a number greater than zero.",
        "internet_service_selection" to "Choose the internet or TV service the customer wants.",
        "mobile_phone_number" to "Enter the customer's mobile number in the format shown below.",
        "ownership_and_control_information" to "Describe who owns or controls the business.",
        "paperless_billing_preference" to "Record whether the customer wants paperless bills.",
        "payment_method" to "Choose how the customer plans to pay.",
        "phone_service_selection" to "Choose the phone service the customer wants.",
        "physical_address" to "Enter the customer's current residential or business address.",
        "politically_exposed_person_status" to "Record the answer given by the customer.",
        "profession" to "Enter the customer's job, trade, or occupation.",
        "selfie_liveness_check" to "Record the result of your organization's live-selfie check.",
        "service_type" to "Choose the telecom service the customer is signing up for.",
        "streaming_addons_selection" to "List any streaming add-ons the customer wants.",
        "subscriber_number" to "Enter the phone number that will be linked to the service.",
        "subscriber_type" to "Choose who will own or use the service.",
        "terms_and_conditions_acceptance" to "Confirm the customer reviewed and accepted the terms presented.",
        "us_person_status" to "Record the answer given by the customer."
    )
    private val businessOnly = { fieldId: String ->
        FormField(
            fieldId = fieldId,
            fieldName = fieldId.replace('_', ' ').replaceFirstChar { it.uppercase() },
            fieldType = "text",
            isRequired = false,
            validationRule = listOf(RequiredIf("customer_type", "business")),
            description = descriptions[fieldId].orEmpty()
        )
    }

    val all: List<FormField> = listOf(
        FormField("assets", "Assets", "number", false, listOf(PositiveNumber)),
        businessOnly("beneficial_owner_information"),
        businessOnly("business_relationship_nature"),
        businessOnly("business_relationship_purpose"),
        businessOnly("commercial_address"),
        FormField("contact_phone_number", "Contact phone number", "phone", true, listOf(*required.toTypedArray(), KenyanPhoneFormat)),
        FormField("contract_type", "Contract type", "choice", true, listOf(*required.toTypedArray(), OneOf(setOf("prepaid", "postpaid")))),
        FormField("date_of_birth", "Date of birth", "date", true, listOf(*required.toTypedArray(), DateFormatAndMinimumAge(18))),
        FormField("email_address", "Email address", "email", true, listOf(*required.toTypedArray(), EmailFormat)),
        FormField("face_match_verification", "Face match verification", "boolean", true, listOf(*required.toTypedArray(), MustBeTrue)),
        FormField("full_name", "Full name", "text", true, listOf(*required.toTypedArray(), RegexPattern("^[\\p{L}]+(?:[ '-][\\p{L}]+)*$", "Use letters, spaces, hyphens, or apostrophes only."))),
        FormField("gender", "Gender", "choice", true, listOf(*required.toTypedArray(), OneOf(setOf("male", "female", "other")))),
        FormField("home_address", "Home address", "text", true, required),
        FormField("identification_document", "Identification document", "file", true, required),
        FormField(
            "identification_document_number",
            "Identification document number",
            "text",
            true,
            listOf(
                *required.toTypedArray(),
                PatternByFieldValue(
                    "identification_document_type",
                    mapOf(
                        "national_id" to "^\\d{7,9}$",
                        "passport" to "^[A-Za-z]\\d{7}$",
                        "alien_card" to "^[A-Za-z0-9]{5,20}$",
                        "refugee_id" to "^[A-Za-z0-9]{5,20}$",
                        "military_id" to "^[A-Za-z0-9]{5,20}$",
                        "business_registration" to "^[A-Za-z0-9-]{5,30}$",
                        "tax_pin" to "^[A-Za-z0-9-]{5,30}$",
                        "other" to "^[A-Za-z0-9-]{5,30}$"
                    ),
                    "Enter a valid number for the selected document type."
                )
            )
        ),
        FormField("identification_document_type", "Identification document type", "choice", true, listOf(*required.toTypedArray(), OneOf(setOf("national_id", "passport", "alien_card", "refugee_id", "military_id")))),
        FormField("income", "Income", "number", true, listOf(*required.toTypedArray(), PositiveNumber)),
        FormField("internet_service_selection", "Internet service selection", "choice", false, listOf(RequiredIf("service_type", "broadband"), RequiredIf("service_type", "tv"))),
        FormField("mobile_phone_number", "Mobile phone number", "phone", true, listOf(*required.toTypedArray(), KenyanPhoneFormat)),
        businessOnly("ownership_and_control_information"),
        FormField("paperless_billing_preference", "Paperless billing preference", "boolean", true, listOf(*required.toTypedArray(), OneOf(setOf("yes", "no")))),
        FormField("payment_method", "Payment method", "choice", true, listOf(*required.toTypedArray(), OneOf(setOf("cash", "mobile_money", "card", "bank_transfer", "direct_debit")))),
        FormField("phone_service_selection", "Phone service selection", "choice", false, listOf(RequiredIf("service_type", "mobile"), RequiredIf("service_type", "landline"))),
        FormField("physical_address", "Physical address", "text", true, required),
        FormField("politically_exposed_person_status", "Politically exposed person status", "boolean", true, listOf(*required.toTypedArray(), OneOf(setOf("yes", "no")))),
        FormField("profession", "Profession", "text", true, required),
        FormField("selfie_liveness_check", "Selfie liveness check", "boolean", true, listOf(*required.toTypedArray(), MustBeTrue)),
        FormField("service_type", "Service type", "choice", true, listOf(*required.toTypedArray(), OneOf(setOf("mobile", "broadband", "tv", "iot", "landline")))),
        FormField("streaming_addons_selection", "Streaming add-ons selection", "text", false, emptyList()),
        FormField("subscriber_number", "Subscriber number", "phone", true, listOf(*required.toTypedArray(), KenyanPhoneFormat)),
        FormField("subscriber_type", "Subscriber type", "choice", true, listOf(*required.toTypedArray(), OneOf(setOf("individual", "business", "child", "foreign_national", "refugee", "stateless", "company")))),
        FormField("terms_and_conditions_acceptance", "Terms and conditions acceptance", "boolean", true, listOf(*required.toTypedArray(), MustBeTrue)),
        FormField("us_person_status", "US person status", "boolean", true, listOf(*required.toTypedArray(), OneOf(setOf("yes", "no"))))
    ).map { field ->
        field.copy(description = field.description.ifBlank { descriptions[field.fieldId].orEmpty() })
    }

    private val byId = all.associateBy { it.fieldId }

    fun get(fieldId: String, configuration: FieldRuleConfiguration = FieldRuleConfiguration()): FormField =
        requireNotNull(byId[fieldId]) { "Unknown field: $fieldId" }.let(configuration::fieldFor)
}
