package com.example.salesonboarding

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationRulesTest {
    private fun rule(rule: ValidationRule, value: String?, context: ValidationContext = ValidationContext()) =
        rule.validate(value, context)

    @Test fun requiredAndNotBlankRulesRejectMissingValues() {
        assertTrue(rule(Required, "ok").isNullOrBlank())
        assertFalse(rule(Required, " ").isNullOrBlank())
        assertFalse(rule(NotBlank, "").isNullOrBlank())
    }

    @Test fun regexRuleAcceptsAndRejectsPattern() {
        assertTrue(rule(RegexPattern("^\\d{3}$"), "123").isNullOrBlank())
        assertFalse(rule(RegexPattern("^\\d{3}$"), "12").isNullOrBlank())
    }

    @Test fun emailRuleAcceptsAndRejectsEmail() {
        assertTrue(rule(EmailFormat, "agent@example.com").isNullOrBlank())
        assertFalse(rule(EmailFormat, "agent@example").isNullOrBlank())
    }

    @Test fun phoneRuleAcceptsKenyanFormats() {
        assertTrue(rule(KenyanPhoneFormat, "0712345678").isNullOrBlank())
        assertTrue(rule(KenyanPhoneFormat, "+254712345678").isNullOrBlank())
        assertFalse(rule(KenyanPhoneFormat, "071234567").isNullOrBlank())
    }

    @Test fun dateRuleChecksDateAndAge() {
        assertTrue(rule(DateFormatAndMinimumAge(18), "1990-01-01").isNullOrBlank())
        assertFalse(rule(DateFormatAndMinimumAge(18), "not-a-date").isNullOrBlank())
        assertFalse(rule(DateFormatAndMinimumAge(18), "2020-01-01").isNullOrBlank())
    }

    @Test fun choicesNumberBooleanAndConditionalRules() {
        assertTrue(rule(OneOf(setOf("yes", "no")), "YES").isNullOrBlank())
        assertFalse(rule(OneOf(setOf("yes", "no")), "maybe").isNullOrBlank())
        assertTrue(rule(PositiveNumber, "10.50").isNullOrBlank())
        assertFalse(rule(PositiveNumber, "0").isNullOrBlank())
        assertTrue(rule(MustBeTrue, "passed").isNullOrBlank())
        assertFalse(rule(MustBeTrue, "false").isNullOrBlank())
        val context = ValidationContext(mapOf("service_type" to "mobile"))
        assertFalse(rule(RequiredIf("service_type", "mobile"), "", context).isNullOrBlank())
        assertTrue(rule(RequiredIf("service_type", "tv"), "", context).isNullOrBlank())
    }

    @Test fun dependentPatternRuleUsesTheSelectedDocumentType() {
        val context = ValidationContext(mapOf("identification_document_type" to "national_id"))
        val documentNumber = PatternByFieldValue(
            "identification_document_type",
            mapOf("national_id" to "^\\d{7,9}$", "passport" to "^[A-Za-z]\\d{7}$")
        )
        assertTrue(rule(documentNumber, "12345678", context).isNullOrBlank())
        assertFalse(rule(documentNumber, "AB1234567", context).isNullOrBlank())
    }

    @Test fun catalogueHoldsExactlyTheFieldsTheModelWasTrainedOn() {
        // Unit tests run from the app module, so the model's metadata is one folder up.
        val metadata = File("../ml/models/preprocessing_meta.json").readText()
        val vocabulary = Regex("\"field_vocab\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            .find(metadata)?.groupValues?.get(1)
            ?: error("field_vocab not found in preprocessing_meta.json")
        val modelFields = Regex("\"([a-z_]+)\"").findAll(vocabulary).map { it.groupValues[1] }.toSet()

        assertEquals(32, modelFields.size)
        assertEquals(modelFields, KnownFields.all.map { it.fieldId }.toSet())
    }

    @Test fun everyKnownFieldHasAValidAndInvalidCase() {
        KnownFields.all.forEach { field ->
            assertTrue(
                "Expected ${field.fieldId} to have agent-facing instructions",
                field.description.isNotBlank()
            )
        }
        KnownFields.all.forEach { field ->
            val validValue = when (field.fieldId) {
                "assets" -> ""
                "date_of_birth" -> "1990-01-01"
                "email_address" -> "agent@example.com"
                "full_name" -> "Jane Wanjiku"
                "gender" -> "female"
                "income" -> "1000"
                "identification_document_type" -> "passport"
                "identification_document_number" -> "A1234567"
                "mobile_phone_number", "contact_phone_number", "subscriber_number" -> "0712345678"
                "service_type" -> "mobile"
                "subscriber_type" -> "individual"
                "contract_type" -> "prepaid"
                "payment_method" -> "mobile_money"
                "terms_and_conditions_acceptance" -> "yes"
                "paperless_billing_preference", "politically_exposed_person_status", "us_person_status" -> "no"
                else -> "Provided value"
            }
            val context = ValidationContext(
                mapOf(
                    "customer_type" to "business",
                    "service_type" to "mobile",
                    "identification_document_type" to "passport"
                )
            )
            val entry = FieldEntry("entry", "session", field.fieldId, validValue)
            assertTrue("Expected ${field.fieldId} to accept its valid value", field.validate(entry, context).isValid)
            val isApplicable = field.isRequired ||
                field.validationRules.any { it is RequiredIf && context[it.dependsOnFieldId]?.equals(it.expectedValue, ignoreCase = true) == true }
            if (isApplicable) {
                assertFalse("Expected ${field.fieldId} to reject a blank value", field.validate(entry.copy(value = ""), context).isValid)
            } else {
                assertTrue("Expected ${field.fieldId} to allow a blank value", field.validate(entry.copy(value = ""), context).isValid)
            }
        }
    }

    @Test fun adminAddedFieldCanComposeRulesWithoutFieldSpecificCode() {
        val field = FormField(
            fieldId = "admin_reference",
            fieldName = "Reference",
            fieldType = "text",
            isRequired = true,
            validationRules = listOf(Required, RegexPattern("^REF-\\d{4}$"))
        )
        val entry = FieldEntry("entry", "session", "admin_reference", "REF-1234")
        assertTrue(field.validate(entry).isValid)
        assertFalse(field.validate(entry.copy(value = "bad")).isValid)
    }

    @Test fun adminCanAddAndRemoveRulesFromAnExistingField() {
        val defaultField = KnownFields.get("profession")
        val configuredField = KnownFields.get(
            "profession",
            FieldRuleConfiguration(
                mapOf(
                    "profession" to FieldRuleOverride(
                        addRules = listOf(RegexPattern("^[A-Z].*")),
                        removeRules = listOf(NotBlank)
                    )
                )
            )
        )

        assertFalse(configuredField.validate(
            FieldEntry("entry", "session", "profession", "nurse")
        ).isValid)
        assertTrue(configuredField.validate(
            FieldEntry("entry", "session", "profession", "Nurse")
        ).isValid)
        assertTrue(defaultField.validate(
            FieldEntry("entry", "session", "profession", "nurse")
        ).isValid)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ValidationErrorDisplayTest {
    @Test
    fun failingEmailRuleProducesAnActionableMessage() = runTest {
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("email_address"))
        )

        validation.answer("email_address", "not-an-email")
        runCurrent()

        val error = ValidationErrorDisplayMapper.forState(validation.stateFlow.value).single()
        assertEquals("email_address", error.fieldId)
        assertEquals("Enter a valid email address.", error.message)
    }

    @Test
    fun untouchedRequiredFieldDoesNotShowAnError() {
        val state = LiveValidationState(
            results = mapOf("email_address" to ValidationResult.invalid("Enter a value for this field."))
        )

        assertTrue(ValidationErrorDisplayMapper.forState(state).isEmpty())
        assertNull(
            ValidationErrorDisplayMapper.forResult(
                "email_address",
                state.results.getValue("email_address"),
                touched = false
            )
        )
    }

    @Test
    fun touchedAndClearedRequiredFieldShowsAnError() = runTest {
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("email_address"))
        )

        validation.answer("email_address", "agent@example.com")
        runCurrent()
        validation.answer("email_address", "")
        runCurrent()

        val error = ValidationErrorDisplayMapper.forState(validation.stateFlow.value).single()
        assertEquals("Enter a value for this field.", error.message)
    }

    @Test
    fun mapperReportsMultipleTouchedFieldsWithErrors() {
        val state = LiveValidationState(
            results = mapOf(
                "email_address" to ValidationResult.invalid("Enter a valid email address."),
                "mobile_phone_number" to ValidationResult.invalid(
                    "Enter a valid Kenyan phone number (07XXXXXXXX or +2547XXXXXXXX)."
                ),
                "profession" to ValidationResult.valid
            ),
            touchedFieldIds = setOf("email_address", "mobile_phone_number", "profession")
        )

        val errors = ValidationErrorDisplayMapper.forState(state)

        assertEquals(
            listOf("email_address", "mobile_phone_number"),
            errors.map(ValidationErrorDisplay::fieldId)
        )
        assertFalse(errors.any { it.fieldId == "profession" })
    }
}
