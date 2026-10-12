package com.example.salesonboarding.validation

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

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

data class FieldEntry(
    val entryId: String,
    val sessionId: String,
    val fieldId: String,
    val value: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean? = null
) {
    fun save(store: FieldEntryStore): FieldEntry {
        store.save(this)
        return this
    }
}

fun interface FieldEntryStore {
    fun save(entry: FieldEntry)
}

data class ValidationContext(
    val values: Map<String, String?> = emptyMap()
) {
    operator fun get(fieldId: String): String? = values[fieldId]
}

data class FormField(
    val fieldId: String,
    val fieldName: String,
    val fieldType: String,
    val isRequired: Boolean,
    val validationRule: List<ValidationRule>
) {
    fun validate(entry: FieldEntry, context: ValidationContext = ValidationContext()): ValidationResult {
        require(entry.fieldId == fieldId) {
            "Entry field '${entry.fieldId}' does not match '$fieldId'"
        }

        for (rule in validationRule) {
            val error = rule.validate(entry.value, context)
            if (error != null) return ValidationResult.invalid(error)
        }
        return ValidationResult.valid
    }

    fun getNextField(fields: List<FormField>): FormField? {
        val index = fields.indexOfFirst { it.fieldId == fieldId }
        return fields.getOrNull(index + 1)
    }
}

sealed interface ValidationRule {
    fun validate(value: String?, context: ValidationContext): String?
}

data object Required : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank()) "This field is required." else null
}

data object NotBlank : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank()) "This field cannot be blank." else null
}

data class RegexPattern(
    val pattern: String,
    val message: String = "The value has an invalid format."
) : ValidationRule {
    private val compiled = Pattern.compile(pattern)

    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank() || compiled.matcher(value).matches()) null else message
}

data class PatternByFieldValue(
    val dependsOnFieldId: String,
    val patternsByValue: Map<String, String>,
    val message: String = "The value has an invalid format."
) : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? {
        if (value.isNullOrBlank()) return null
        val selectedValue = context[dependsOnFieldId]?.trim()?.lowercase(Locale.ROOT)
        val pattern = patternsByValue[selectedValue] ?: return message
        return if (Regex(pattern).matches(value)) null else message
    }
}

data object EmailFormat : ValidationRule {
    private val pattern = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")

    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank() || pattern.matches(value)) null else "Enter a valid email address."
}

data object KenyanPhoneFormat : ValidationRule {
    private val pattern = Regex("^(?:07\\d{8}|\\+2547\\d{8})$")

    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank() || pattern.matches(value)) null
        else "Enter a valid Kenyan phone number (07XXXXXXXX or +2547XXXXXXXX)."
}

data class DateFormatAndMinimumAge(
    val minimumAge: Int
) : ValidationRule {
    private val pattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    override fun validate(value: String?, context: ValidationContext): String? {
        if (value.isNullOrBlank()) return null
        if (!pattern.matches(value)) return "Use the date format YYYY-MM-DD."
        val parts = value.split('-').map(String::toInt)
        val date = Calendar.getInstance().apply {
            isLenient = false
            set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return try {
            date.time
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - parts[0]
            if (today.get(Calendar.DAY_OF_YEAR) < date.get(Calendar.DAY_OF_YEAR)) age--
            if (age < minimumAge) "The customer must be at least $minimumAge years old." else null
        } catch (_: IllegalArgumentException) {
            "Enter a real calendar date."
        }
    }
}

data class OneOf(
    val choices: Set<String>,
    val message: String = "Choose one of the available options."
) : ValidationRule {
    private val normalizedChoices = choices.map { it.trim().lowercase(Locale.ROOT) }.toSet()

    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank() || value.trim().lowercase(Locale.ROOT) in normalizedChoices) null else message
}

data object PositiveNumber : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? {
        if (value.isNullOrBlank()) return null
        val number = value.toBigDecimalOrNull()
        return if (number != null && number > java.math.BigDecimal.ZERO) null
        else "Enter a number greater than zero."
    }
}

data object MustBeTrue : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? =
        if (value?.trim()?.lowercase(Locale.ROOT) in setOf("true", "yes", "pass", "passed")) null
        else "This field must be confirmed."
}

data class RequiredIf(
    val dependsOnFieldId: String,
    val expectedValue: String,
    val message: String = "This field is required for the selected option."
) : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? {
        val actual = context[dependsOnFieldId]?.trim()?.lowercase(Locale.ROOT)
        if (actual == expectedValue.trim().lowercase(Locale.ROOT) && value.isNullOrBlank()) return message
        return null
    }
}
