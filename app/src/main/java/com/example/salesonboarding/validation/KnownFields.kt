package com.example.salesonboarding.validation

object KnownFields {
    private val required = listOf(Required, NotBlank)
    private val businessOnly = { fieldId: String ->
        FormField(
            fieldId, fieldId, "text", false,
            listOf(RequiredIf("customer_type", "business"))
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
                        "military_id" to "^[A-Za-z0-9]{5,20}$"
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
    )

    private val byId = all.associateBy { it.fieldId }

    fun get(fieldId: String): FormField =
        requireNotNull(byId[fieldId]) { "Unknown field: $fieldId" }
}
