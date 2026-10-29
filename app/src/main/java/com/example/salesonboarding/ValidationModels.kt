package com.example.salesonboarding

// region Results

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
) {
    init {
        require(isValid || !errorMessage.isNullOrBlank()) {
            "Invalid results must include an error message"
        }
    }

    companion object {
        val valid = ValidationResult(true)

        fun invalid(message: String) = ValidationResult(false, message)
    }
}

// endregion

// region Entries & context

data class FieldEntry(
    val entryId: String,
    val sessionId: String,
    val fieldId: String,
    val value: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean? = null
) {
    // The answer is customer data, so keep it out of logs and crash reports.
    override fun toString() = "FieldEntry(entryId=$entryId, fieldId=$fieldId, value=<redacted>)"

    /** Copy with leading and trailing whitespace removed from the value. */
    fun trimmed(): FieldEntry = copy(value = value?.trim())

    /** Saves the trimmed entry, so stored values are clean, and returns it. */
    fun save(store: FieldEntryStore): FieldEntry = trimmed().also(store::save)
}

fun interface FieldEntryStore {
    fun save(entry: FieldEntry)
}

/** The current answers in the form, so a rule can look at other fields. */
data class ValidationContext(
    val values: Map<String, String?> = emptyMap()
) {
    operator fun get(fieldId: String): String? = values[fieldId]

    // Answers are customer data, so only the field ids are printed.
    override fun toString() = "ValidationContext(fields=${values.keys})"

    /** Another field's answer, trimmed and lower-cased for case-insensitive comparison. */
    fun normalizedValue(fieldId: String): String? = values[fieldId].normalized()
}

// endregion

// region Form field

data class FormField(
    val fieldId: String,
    val fieldName: String,
    val fieldType: String,
    val isRequired: Boolean,
    val validationRules: List<ValidationRule>,
    val description: String = ""
) {
    // Required is applied automatically when isRequired is true.
    private val effectiveRules: List<ValidationRule> =
        if (isRequired) listOf(Required) + validationRules else validationRules

    fun validate(
        entry: FieldEntry,
        context: ValidationContext = ValidationContext()
    ): ValidationResult {
        require(entry.fieldId == fieldId) {
            "Entry field '${entry.fieldId}' does not match '$fieldId'"
        }

        // Stray spaces (e.g. from keyboard autocomplete) should not fail a format check.
        val value = entry.value?.trim()
        val error = validateAnswer(value, context)
        return error?.let(ValidationResult::invalid) ?: ValidationResult.valid
    }

    private fun validateAnswer(
        value: String?,
        context: ValidationContext
    ): String? {
        if (value != null && value.length > MAX_ANSWER_LENGTH) {
            return "Answer is too long (maximum $MAX_ANSWER_LENGTH characters)."
        }
        return effectiveRules.firstNotNullOfOrNull { it.validate(value, context) }
    }

    companion object {
        /** Upper limit for any single answer, to keep oversized input out of the database. */
        const val MAX_ANSWER_LENGTH = 2_000

        private val ID_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")

        /** Field ids become database keys, so only lower-case letters, digits and `_` are allowed. */
        fun isValidId(fieldId: String): Boolean = ID_PATTERN.matches(fieldId)
    }
}

// endregion