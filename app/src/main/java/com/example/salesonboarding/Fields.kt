package com.example.salesonboarding

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

// region Question catalogue

object KnownFields {

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
            require(FormField.isValidId(field.fieldId)) {
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

    /**
     * Every field the app can ask for. These must stay identical to `field_to_id` in
     * `preprocessing_meta.json` (the fields the next-field model was trained on);
     * `ValidationRulesTest` checks this. The model scores candidates by their description,
     * so a field missing here could be recommended but never shown or validated.
     */
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

    // validateCatalog has already rejected duplicate ids.
    private val byId: Map<String, FormField> = all.associateBy { it.fieldId }

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

// endregion

// region Admin rule changes

/**
 * An admin's changes to one field, as a delta over the catalogue defaults.
 *
 * [Required] is not kept in a field's rule list; it is the field's `isRequired` flag.
 * Adding [Required] here makes the field required, and removing it makes the field
 * optional for the configured form. If both are listed, adding wins. The model
 * vocabulary and field ids are unchanged.
 */
data class FieldRuleOverride(
    val addRules: List<ValidationRule> = emptyList(),
    val removeRules: List<ValidationRule> = emptyList()
) {
    fun applyTo(field: FormField): FormField {
        val isRequired = when {
            Required in addRules -> true
            Required in removeRules -> false
            else -> field.isRequired
        }
        val rules = (field.validationRules - removeRules.toSet() + addRules)
            .filterNot { it == Required }
            .distinct()
        return field.copy(isRequired = isRequired, validationRules = rules)
    }
}

class FieldRuleConfiguration(
    private val overrides: Map<String, FieldRuleOverride> = emptyMap(),
    val customFields: List<FormField> = emptyList(),
    private val lockedFieldIds: Set<String> = DEFAULT_LOCKED_FIELD_IDS
) {
    /**
     * The field with this configuration's overrides applied (unchanged if it has none).
     * Locked fields can gain rules but never lose them, so an admin cannot make a
     * compliance field optional or drop its checks.
     */
    fun fieldFor(field: FormField): FormField {
        val override = overrides[field.fieldId] ?: return field
        val allowed =
            if (field.fieldId in lockedFieldIds) override.copy(removeRules = emptyList()) else override
        return allowed.applyTo(field)
    }

    /** The configured extra rules for [field]. [Required] is not included; see `isRequired`. */
    fun rulesFor(field: FormField): List<ValidationRule> = fieldFor(field).validationRules

    /** Catalogue fields with overrides applied, followed by custom fields. Catalogue ids win. */
    fun allFields(fields: List<FormField>): List<FormField> =
        (fields.map(::fieldFor) + customFields).distinctBy(FormField::fieldId)

    /** Adds or replaces a custom field. Its id must be a safe database key. */
    fun withCustomField(field: FormField): FieldRuleConfiguration {
        require(FormField.isValidId(field.fieldId)) {
            "Invalid field id '${field.fieldId}': use lower-case letters, digits and '_', starting with a letter."
        }
        return FieldRuleConfiguration(
            overrides = overrides,
            customFields = customFields.filterNot { it.fieldId == field.fieldId } + field,
            lockedFieldIds = lockedFieldIds
        )
    }

    companion object {
        /** Compliance fields whose checks admins may add to but not remove. A policy choice: adjust as needed. */
        val DEFAULT_LOCKED_FIELD_IDS: Set<String> = setOf(
            "full_name",
            "date_of_birth",
            "identification_document_type",
            "identification_document_number",
            "politically_exposed_person_status",
            "us_person_status",
            "terms_and_conditions_acceptance"
        )
    }
}

// endregion

// region Playbooks

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
            val fieldsSnapshot = fields.toList()
            val fieldIds = fieldsSnapshot.map(FormField::fieldId)
            require(fieldIds.all { FormField.isValidId(it) }) {
                "Playbook field IDs must be valid database keys."
            }
            require(fieldIds.distinct().size == fieldIds.size) {
                "Playbook field IDs must be unique."
            }

            val manifest = ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(fieldsSnapshot.size)
                    fieldsSnapshot.forEach { field ->
                        val values = listOf(
                            field.fieldId,
                            field.fieldName,
                            field.fieldType,
                            field.isRequired.toString(),
                            field.description
                        ) + field.validationRules.map { it.toString() }
                        output.writeInt(values.size)
                        values.forEach { value ->
                            val bytes = value.toByteArray(Charsets.UTF_8)
                            output.writeInt(bytes.size)
                            output.write(bytes)
                        }
                    }
                }
                buffer.toByteArray()
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(manifest)
                .take(12)
                .joinToString("") { "%02x".format(it) }
            return OnboardingPlaybook(id, title, industry, "v1-$digest", fieldsSnapshot)
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
        "identification_document",
        "identification_document_type",
        "identification_document_number",
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
                    validationRules = listOf(Required, NotBlank),
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
                        validationRules = configuredField.validationRules
                            .filterNot { it is RequiredIf } +
                            listOf(Required, NotBlank)
                    )
                industry == "banking" && context == "business" && fieldId in BANKING_BUSINESS_OPTIONAL_FIELDS ->
                    configuredField.copy(
                        validationRules = configuredField.validationRules
                            .filterNot { it is RequiredIf }
                    )
                context != null && fieldId in setOf("phone_service_selection", "internet_service_selection") ->
                    configuredField.copy(
                        isRequired = true,
                        validationRules = configuredField.validationRules
                            .filterNot { it is RequiredIf } +
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

// endregion

// region Form sections

/** One step of the agent's form: a few related questions shown together. */
data class FormSection(val title: String, val fields: List<FormField>)

/**
 * The playbook split into sections. Sections only group neighbouring questions, so the
 * questions keep the playbook's order, which the consistency measure compares sessions
 * against. Questions a company added come last, under [COMPANY_SECTION].
 */
fun OnboardingPlaybook.sections(): List<FormSection> {
    val sectionOf = SECTION_BY_FIELD[industry].orEmpty()
    val sections = mutableListOf<FormSection>()
    fields.forEach { field ->
        val title = sectionOf[field.fieldId] ?: COMPANY_SECTION
        val last = sections.lastOrNull()
        if (last?.title == title) {
            sections[sections.lastIndex] = last.copy(fields = last.fields + field)
        } else {
            sections += FormSection(title, listOf(field))
        }
    }
    return sections
}

const val COMPANY_SECTION = "Company questions"

private fun section(title: String, vararg fieldIds: String) = fieldIds.associateWith { title }

private val SECTION_BY_FIELD: Map<String, Map<String, String>> = mapOf(
    "banking" to section(
        "Customer", "full_name", "business_name", "email_address", "mobile_phone_number",
        "date_of_birth", "home_address"
    ) + section(
        "Identity", "identification_document", "identification_document_type",
        "identification_document_number"
    ) + section(
        "Financial", "profession", "income", "assets"
    ) + section(
        "Declarations", "us_person_status", "politically_exposed_person_status"
    ) + section(
        "Account", "terms_and_conditions_acceptance", "commercial_address",
        "beneficial_owner_information", "business_relationship_purpose",
        "business_relationship_nature", "ownership_and_control_information"
    ),
    "telecom" to section(
        "Customer", "full_name", "email_address", "contact_phone_number", "date_of_birth",
        "gender", "physical_address"
    ) + section(
        "Identity", "identification_document", "subscriber_type"
    ) + section(
        "Service", "service_type", "contract_type", "payment_method",
        "paperless_billing_preference", "phone_service_selection",
        "internet_service_selection", "streaming_addons_selection"
    ) + section(
        "Activation", "subscriber_number"
    )
)

// endregion
