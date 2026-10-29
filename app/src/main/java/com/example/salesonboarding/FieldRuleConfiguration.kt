package com.example.salesonboarding

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