package com.example.salesonboarding.validation

import org.junit.Assert.assertFalse
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

    @Test fun everyKnownFieldHasAValidAndInvalidCase() {
        assertTrue(KnownFields.all.size == 33)
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
                "terms_and_conditions_acceptance", "selfie_liveness_check", "face_match_verification" -> "yes"
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
                field.validationRule.any { it is RequiredIf && context[it.dependsOnFieldId]?.equals(it.expectedValue, ignoreCase = true) == true }
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
