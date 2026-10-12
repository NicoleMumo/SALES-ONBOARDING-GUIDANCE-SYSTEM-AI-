package com.example.salesonboarding.validation

data class ValidationErrorDisplay(
    val fieldId: String,
    val message: String
)

object ValidationErrorDisplayMapper {
    fun forResult(
        fieldId: String,
        result: ValidationResult?,
        touched: Boolean
    ): ValidationErrorDisplay? =
        if (touched && result != null && !result.isValid) {
            ValidationErrorDisplay(
                fieldId = fieldId,
                message = requireNotNull(result.errorMessage)
            )
        } else {
            null
        }

    fun forState(state: LiveValidationState): List<ValidationErrorDisplay> =
        state.results.keys
            .sorted()
            .mapNotNull { fieldId ->
                forResult(
                    fieldId = fieldId,
                    result = state.results[fieldId],
                    touched = fieldId in state.touchedFieldIds
                )
            }
}
