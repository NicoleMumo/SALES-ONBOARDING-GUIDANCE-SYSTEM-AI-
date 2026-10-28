package com.example.salesonboarding.validation

object KnownFields {

    private val validFieldIdPattern = Regex("^[a-z][a-z0-9_]{0,63}$")

    private fun validateCatalog(catalog: List<FormField>) {
        val duplicates = catalog
            .groupingBy { it.fieldId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "KnownFields contains duplicate field ids: ${duplicates.sorted().joinToString()}"
        }
        catalog.forEach { field ->
            require(field.fieldId.matches(validFieldIdPattern)) {
                "Invalid field id '${field.fieldId}' in KnownFields. Use lower-case letters, digits, and underscores only."
            }
            require(field.fieldName.isNotBlank()) { "Field '${field.fieldId}' is missing a display name." }
            require(field.description.isNotBlank()) { "Field '${field.fieldId}' is missing agent-facing guidance." }
            require(field.validationRules.none { it == Required && field.isRequired.not() }) {
                "Field '${field.fieldId}' should not include Required when it is optional."
            }
        }
    }

    // region Constants

    private const val MINIMUM_CUSTOMER_AGE = 18
    // Letters (with combining accents) separated by a space, hyphen, or straight or curly apostrophe.
    private const val NAME_PATTERN = "^[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*$"

    private const val NATIONAL_ID_PATTERN = "^\\d{7,9}$"
    private const val PASSPORT_PATTERN = "^[A-Za-z]\\d{7}$"
    private const val ALPHANUMERIC_ID_PATTERN = "^[A-Za-z0-9]{5,20}$"
    private const val REFERENCE_NUMBER_PATTERN = "^[A-Za-z0-9-]{5,30}$"

    private val YES_NO = setOf("yes", "no")

    /** Field types understood by the UI layer. */
    private object Type {
        const val TEXT = "text"
        const val NUMBER = "number"
        const val DATE = "date"
        const val EMAIL = "email"
        const val PHONE = "phone"
        const val CHOICE = "choice"
        const val BOOLEAN = "boolean"
        const val FILE = "file"
    }

    /** Fields that other fields' rules depend on. */
    private object Id {
        const val SERVICE_TYPE = "service_type"
        const val IDENTIFICATION_DOCUMENT_TYPE = "identification_document_type"
    }

    private val documentNumberPatterns = mapOf(
        "national_id" to NATIONAL_ID_PATTERN,
        "passport" to PASSPORT_PATTERN,
        "alien_card" to ALPHANUMERIC_ID_PATTERN,
        "refugee_id" to ALPHANUMERIC_ID_PATTERN,
        "military_id" to ALPHANUMERIC_ID_PATTERN,
        "business_registration" to REFERENCE_NUMBER_PATTERN,
        "tax_pin" to REFERENCE_NUMBER_PATTERN,
        "other" to REFERENCE_NUMBER_PATTERN
    )

    // endregion

    // region Builders
    //
    // Fields with isRequired = true get the Required rule automatically (see FormField),
    // so the rule lists below only contain the extra checks.

    private fun displayName(id: String): String =
        id.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun field(
        id: String,
        type: String,
        description: String,
        rules: List<ValidationRule> = emptyList(),
        isRequired: Boolean = true,
        name: String = displayName(id)
    ) = FormField(
        fieldId = id,
        fieldName = name,
        fieldType = type,
        isRequired = isRequired,
        validationRules = rules,
        description = description
    )

    private fun choice(id: String, description: String, vararg options: String) =
        field(id, Type.CHOICE, description, listOf(OneOf(options.toSet())))

    private fun phone(id: String, description: String) =
        field(id, Type.PHONE, description, listOf(KenyanPhoneFormat))

    private fun yesNo(id: String, description: String, name: String = displayName(id)) =
        field(id, Type.BOOLEAN, description, listOf(OneOf(YES_NO)), name = name)

    private fun mustBeTrue(id: String, description: String) =
        field(id, Type.BOOLEAN, description, listOf(MustBeTrue))

    /**
     * Question for business accounts. Only the business playbooks include these fields,
     * so no customer-type check is needed here. [isRequired] mirrors the model's playbooks.
     */
    private fun businessField(id: String, description: String, isRequired: Boolean = true) =
        field(id, Type.TEXT, description, isRequired = isRequired)

    private fun serviceChoice(id: String, description: String, vararg requiredFor: String) =
        field(
            id, Type.CHOICE, description,
            rules = requiredFor.map { RequiredIf(Id.SERVICE_TYPE, it) },
            isRequired = false
        )

    // endregion

    // region Fields (alphabetical)

    val all: List<FormField> = listOf(
        field(
            "assets", Type.NUMBER,
            "Enter the business's declared assets as a number greater than zero.",
            rules = listOf(PositiveNumber), isRequired = false
        ),
        businessField(
            "beneficial_owner_information",
            "List the people who ultimately own or control the business.",
            isRequired = false
        ),
        businessField(
            "business_name",
            "Enter the registered legal name of the business."
        ),
        businessField(
            "business_relationship_nature",
            "Describe the business and its relationship with the account."
        ),
        businessField(
            "business_relationship_purpose",
            "Record why the business wants to open or use this account."
        ),
        businessField("commercial_address", "Enter the address where the business operates."),
        phone("contact_phone_number", "Enter a phone number where the customer can be reached."),
        choice(
            "contract_type", "Choose the customer's prepaid or postpaid plan.",
            "prepaid", "postpaid"
        ),
        field(
            "date_of_birth", Type.DATE,
            "Enter the customer's date of birth. The customer must be at least $MINIMUM_CUSTOMER_AGE.",
            rules = listOf(DateFormatAndMinimumAge(MINIMUM_CUSTOMER_AGE))
        ),
        field(
            "email_address", Type.EMAIL,
            "Enter an email address the customer can access.",
            rules = listOf(EmailFormat)
        ),
        mustBeTrue(
            "face_match_verification",
            "Record the result of your organization's face-match check."
        ),
        field(
            "full_name", Type.TEXT,
            "Enter the customer's name as shown on their identification document.",
            rules = listOf(
                RegexPattern(NAME_PATTERN, "Use letters, spaces, hyphens, or apostrophes only.")
            )
        ),
        choice("gender", "Choose the gender stated by the customer.", "male", "female", "other"),
        field(
            "home_address", Type.TEXT,
            "Enter the customer's home address, including the area or street where available."
        ),
        field(
            "identification_document", Type.FILE,
            "Record the identification document provided by the customer."
        ),
        field(
            "identification_document_number", Type.TEXT,
            "Enter the number exactly as it appears on the selected document.",
            rules = listOf(
                PatternByFieldValue(
                    dependsOnFieldId = Id.IDENTIFICATION_DOCUMENT_TYPE,
                    patternsByValue = documentNumberPatterns,
                    message = "Enter a valid number for the selected document type."
                )
            )
        ),
        choice(
            Id.IDENTIFICATION_DOCUMENT_TYPE, "Choose the type of identification document provided.",
            "national_id", "passport", "alien_card", "refugee_id", "military_id"
        ),
        field(
            "income", Type.NUMBER,
            "Enter the customer's verified income as a number greater than zero.",
            rules = listOf(PositiveNumber)
        ),
        serviceChoice(
            "internet_service_selection",
            "Choose the internet or TV service the customer wants.",
            "broadband", "tv"
        ),
        phone("mobile_phone_number", "Enter the customer's mobile number in the format shown below."),
        businessField(
            "ownership_and_control_information",
            "Describe who owns or controls the business.",
            isRequired = false
        ),
        yesNo("paperless_billing_preference", "Record whether the customer wants paperless bills."),
        choice(
            "payment_method", "Choose how the customer plans to pay.",
            "cash", "mobile_money", "card", "bank_transfer", "direct_debit"
        ),
        serviceChoice(
            "phone_service_selection",
            "Choose the phone service the customer wants.",
            "mobile", "landline"
        ),
        field(
            "physical_address", Type.TEXT,
            "Enter the customer's current residential or business address."
        ),
        yesNo("politically_exposed_person_status", "Record the answer given by the customer."),
        field("profession", Type.TEXT, "Enter the customer's job, trade, or occupation."),
        mustBeTrue(
            "selfie_liveness_check",
            "Record the result of your organization's live-selfie check."
        ),
        choice(
            Id.SERVICE_TYPE, "Choose the telecom service the customer is signing up for.",
            "mobile", "broadband", "tv", "iot", "landline"
        ),
        field(
            "streaming_addons_selection", Type.TEXT,
            "List any streaming add-ons the customer wants.",
            isRequired = false, name = "Streaming add-ons selection"
        ),
        phone("subscriber_number", "Enter the phone number that will be linked to the service."),
        choice(
            "subscriber_type", "Choose who will own or use the service.",
            "individual", "business", "child", "foreign_national", "refugee", "stateless", "company"
        ),
        mustBeTrue(
            "terms_and_conditions_acceptance",
            "Confirm the customer reviewed and accepted the terms presented."
        ),
        yesNo(
            "us_person_status", "Record the answer given by the customer.",
            name = "US person status"
        )
    ).also(::validateCatalog)

    // endregion

    private val byId: Map<String, FormField> = all.associateBy { it.fieldId }

    init {
        check(byId.size == all.size) { "KnownFields contains duplicate field ids" }
    }

    /**
     * Field ids the app can ask for. These must stay identical to `field_vocab`
     * in `preprocessing_meta.json` (the 34 fields the next-field model was
     * trained on). The model scores candidates by their description, so a field
     * missing here can be recommended but never shown or validated.
     */
    val fieldIds: Set<String> get() = byId.keys

    /** The configured field, or null if [fieldId] is not in the catalogue. */
    fun find(
        fieldId: String,
        configuration: FieldRuleConfiguration = FieldRuleConfiguration()
    ): FormField? = byId[fieldId]?.let(configuration::fieldFor)

    /** Like [find], but throws for an unknown id. */
    fun get(
        fieldId: String,
        configuration: FieldRuleConfiguration = FieldRuleConfiguration()
    ): FormField = requireNotNull(find(fieldId, configuration)) { "Unknown field: $fieldId" }
}