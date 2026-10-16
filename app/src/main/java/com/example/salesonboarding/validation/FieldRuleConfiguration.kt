package com.example.salesonboarding.validation

/**
 * Admin changes are deltas over the catalogue defaults. Removing Required
 * from a field intentionally makes that field optional for the configured
 * form; the model vocabulary and field IDs are unchanged.
 */
data class FieldRuleOverride(
    val addRules: List<ValidationRule> = emptyList(),
    val removeRules: List<ValidationRule> = emptyList()
) {
    fun applyTo(defaultRules: List<ValidationRule>): List<ValidationRule> =
        (defaultRules - removeRules.toSet() + addRules).distinct()
}

class FieldRuleConfiguration(
    private val overrides: Map<String, FieldRuleOverride> = emptyMap(),
    val customFields: List<FormField> = emptyList()
) {
    fun rulesFor(field: FormField): List<ValidationRule> =
        overrides[field.fieldId]?.applyTo(field.validationRule) ?: field.validationRule

    fun fieldFor(field: FormField): FormField =
        field.copy(validationRule = rulesFor(field))

    fun allFields(fields: List<FormField>): List<FormField> =
        (fields.map(::fieldFor) + customFields).distinctBy(FormField::fieldId)
}