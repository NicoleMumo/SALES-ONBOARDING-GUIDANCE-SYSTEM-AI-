package com.example.salesonboarding

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