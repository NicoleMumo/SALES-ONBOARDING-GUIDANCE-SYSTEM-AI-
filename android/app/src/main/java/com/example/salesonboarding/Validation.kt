package com.example.salesonboarding

import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale

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

/**
 * Whether the question must be answered, given the current [values]: always-required
 * fields, and fields a [RequiredIf] rule switches on (compared like the rule itself:
 * trimmed, ignoring case).
 */
fun FormField.isRequiredFor(values: Map<String, String?>): Boolean =
    isRequired || validationRules.any { rule ->
        rule is RequiredIf && values[rule.dependsOnFieldId]
            ?.trim()
            ?.equals(rule.expectedValue.trim(), ignoreCase = true) == true
    }

// endregion

// region Helpers

internal fun String.normalize(): String = trim().lowercase(Locale.ROOT)

internal fun String?.normalized(): String? = this?.normalize()

// endregion

// region Rule base types

sealed interface ValidationRule {
    /**
     * The id of another field this rule reads, or null if it only looks at its own value.
     * When that other field changes, the field owning this rule is checked again.
     */
    val dependsOnFieldId: String? get() = null

    /** Returns an error message, or null when the value is acceptable. */
    fun validate(value: String?, context: ValidationContext): String?
}

/** Base for rules that only apply when a value is present (blank is handled by [Required]). */
sealed class OptionalValueRule : ValidationRule {
    final override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank()) null else validateNonBlank(value, context)

    protected abstract fun validateNonBlank(value: String, context: ValidationContext): String?
}

/** Base for rules that accept a value only if it fully matches a regular expression. */
sealed class RegexRule(
    private val compiled: Regex,
    private val errorMessage: String
) : OptionalValueRule() {
    final override fun validateNonBlank(value: String, context: ValidationContext): String? =
        if (compiled.matches(value)) null else errorMessage
}

// endregion

// region Presence rules

data object Required : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank()) "Enter a value for this field." else null
}

/** Rejects missing or whitespace-only answers when this rule is explicitly applied. */
data object NotBlank : ValidationRule {
    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.isNullOrBlank()) "Enter a value for this field." else null
}

data class RequiredIf(
    override val dependsOnFieldId: String,
    val expectedValue: String,
    val message: String = "Enter a value because the selected option requires it."
) : ValidationRule {
    private val expected = expectedValue.normalize()

    override fun validate(value: String?, context: ValidationContext): String? =
        if (context.normalizedValue(dependsOnFieldId) == expected && value.isNullOrBlank()) message else null
}

data object MustBeTrue : ValidationRule {
    private val truthy = setOf("true", "yes", "pass", "passed")

    override fun validate(value: String?, context: ValidationContext): String? =
        if (value.normalized() in truthy) null else "Confirm this field to continue."
}

// endregion

// region Format rules

data class RegexPattern(
    val pattern: String,
    val message: String = "Enter a value in the required format."
) : RegexRule(Regex(pattern), message) {
    companion object {
        /** True if [pattern] compiles. Check admin-supplied patterns with this before saving them. */
        fun isValid(pattern: String): Boolean = try {
            Regex(pattern)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

private val EMAIL_REGEX = Regex(
    "^(?!.*\\.\\.)[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@" +
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
)

data object EmailFormat : RegexRule(EMAIL_REGEX, "Enter a valid email address.")

data object KenyanPhoneFormat : RegexRule(
    Regex("^(?:0[17]\\d{8}|\\+254[17]\\d{8})$"),
    "Enter a valid Kenyan phone number (07XXXXXXXX, 01XXXXXXXX, +2547XXXXXXXX or +2541XXXXXXXX)."
)

data class PatternByFieldValue(
    override val dependsOnFieldId: String,
    val patternsByValue: Map<String, String>,
    val message: String = "Enter a value matching the selected option's format."
) : OptionalValueRule() {
    // Compiled once, with keys normalized the same way as the looked-up value.
    private val regexByValue: Map<String, Regex> = buildMap {
        patternsByValue.forEach { (key, pattern) ->
            val normalizedKey = key.normalize()
            require(normalizedKey.isNotEmpty()) { "Pattern selector must not be blank." }
            require(!containsKey(normalizedKey)) {
                "Duplicate patterns for selector '$normalizedKey'."
            }
            val regex = try {
                Regex(pattern)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid pattern for selector '$normalizedKey'.", error)
            }
            put(normalizedKey, regex)
        }
    }

    override fun validateNonBlank(value: String, context: ValidationContext): String? {
        val selected = context.normalizedValue(dependsOnFieldId)?.takeIf { it.isNotEmpty() } ?: return null
        val regex = regexByValue[selected] ?: return null
        return if (regex.matches(value)) null else message
    }
}

// endregion

// region Value rules

data class OneOf(
    val choices: Set<String>,
    val message: String = "Choose one of the available options."
) : OptionalValueRule() {
    private val normalizedChoices = choices.mapTo(HashSet()) { it.normalize() }

    override fun validateNonBlank(value: String, context: ValidationContext): String? =
        if (value.normalize() in normalizedChoices) null else message
}

data object PositiveNumber : OptionalValueRule() {
    // Plain decimals only: no exponents ("1e999999999"), signs or thousands separators.
    private val plainNumber = Regex("^\\d{1,15}(\\.\\d{1,2})?$")

    override fun validateNonBlank(value: String, context: ValidationContext): String? =
        if (plainNumber.matches(value) && value.toBigDecimal() > BigDecimal.ZERO) null
        else "Enter a number greater than zero."
}

// endregion

// region Date of birth

data class DateFormatAndMinimumAge(
    val minimumAge: Int,
    val maximumAge: Int = 120
) : OptionalValueRule() {
    init {
        require(minimumAge >= 0) { "minimumAge must not be negative" }
        require(maximumAge >= minimumAge) { "maximumAge must not be below minimumAge" }
    }

    override fun validateNonBlank(value: String, context: ValidationContext): String? =
        errorFor(value, today = Calendar.getInstance())

    /**
     * The check itself, with "today" passed in so tests can pin the date.
     * Uses java.util.Calendar, which works on every API level.
     */
    internal fun errorFor(value: String, today: Calendar): String? {
        if (!ISO_DATE_SHAPE.matches(value)) return "Use the date format YYYY-MM-DD."

        val birth = parseStrictDate(value) ?: return "Enter a real calendar date."
        if (birth.after(today)) return "The date cannot be in the future."
        val age = completedYears(birth, today)
        return when {
            age < minimumAge -> "The customer must be at least $minimumAge years old."
            age > maximumAge -> "Enter a realistic date of birth."
            else -> null
        }
    }
}

private val ISO_DATE_SHAPE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** Parses `YYYY-MM-DD` (already shape-checked), or returns null if it is not a real date. */
private fun parseStrictDate(isoDate: String): Calendar? {
    val (year, month, day) = isoDate.split('-').map(String::toInt)
    return try {
        Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }.also { it.timeInMillis } // a non-lenient calendar validates when the time is computed
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Whole years from [birth] to [today], counting the birthday only once it has been reached. */
private fun completedYears(birth: Calendar, today: Calendar): Int {
    val birthdayPending = today.monthAndDay() < birth.monthAndDay()
    return today[Calendar.YEAR] - birth[Calendar.YEAR] - if (birthdayPending) 1 else 0
}

private fun Calendar.monthAndDay(): Int = this[Calendar.MONTH] * 100 + this[Calendar.DAY_OF_MONTH]

// endregion

// region Error display

data class ValidationErrorDisplay(
    val fieldId: String,
    val message: String
)

object ValidationErrorDisplayMapper {

    /** Returns an error to show only for a touched field whose result is invalid. */
    fun forResult(
        fieldId: String,
        result: ValidationResult?,
        touched: Boolean
    ): ValidationErrorDisplay? {
        if (!touched) return null
        val message = result?.takeUnless { it.isValid }?.errorMessage ?: return null
        return ValidationErrorDisplay(fieldId, message)
    }

    /** Errors for all touched, invalid fields, ordered by field id. */
    fun forState(state: LiveValidationState): List<ValidationErrorDisplay> =
        state.results.entries
            .sortedBy { it.key }
            .mapNotNull { (fieldId, result) ->
                forResult(
                    fieldId = fieldId,
                    result = result,
                    touched = fieldId in state.touchedFieldIds
                )
            }
}

// endregion
