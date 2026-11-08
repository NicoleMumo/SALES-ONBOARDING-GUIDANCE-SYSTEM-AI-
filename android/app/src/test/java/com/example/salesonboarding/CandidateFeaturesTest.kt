package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateFeaturesTest {
    private val form = OnboardingPlaybooks.forIndustry("banking", FieldRuleConfiguration(), "individual").fields

    @Test
    fun thePhoneWorksOutTheSameFactsAsTheTrainingScript() {
        // Expected rows printed by ml/next_question_v2.py candidate_features() for this case:
        // a banking individual client, ID document answered first, then the full name.
        val history = listOf("identification_document", "full_name")
        val candidates = form.filterNot { it.fieldId in history }
        val expected = mapOf(
            "email_address" to listOf(1, 13, 1, 1, 1),
            "mobile_phone_number" to listOf(2, 14, 0, 0, 1),
            "date_of_birth" to listOf(3, 15, 0, 0, 1),
            "home_address" to listOf(4, 16, 0, 0, 1),
            "identification_document_type" to listOf(6, 18, 0, 0, 1),
            "identification_document_number" to listOf(7, 19, 0, 0, 1),
            "profession" to listOf(8, 20, 0, 0, 1),
            "income" to listOf(9, 21, 0, 0, 1),
            "assets" to listOf(10, 22, 0, 0, 0),
            "us_person_status" to listOf(11, 23, 0, 0, 1),
            "politically_exposed_person_status" to listOf(12, 24, 0, 0, 1),
            "terms_and_conditions_acceptance" to listOf(13, 24, 0, 0, 1)
        )

        val rows = CandidateFeatures.of(history, candidates, form)

        assertEquals(expected.keys.toList(), candidates.map { it.fieldId })
        candidates.zip(rows).forEach { (field, row) ->
            assertEquals(field.fieldId, expected.getValue(field.fieldId), row.toList())
        }
    }

    @Test
    fun aCompanyQuestionAtTheEndOfTheFormGetsItsPlace() {
        val custom = FormField(
            fieldId = "loyalty_number", fieldName = "Loyalty number", fieldType = "text",
            isRequired = true, validationRules = listOf(Required, NotBlank), description = "The client's loyalty card number."
        )
        val withCustom = form + custom
        val history = form.map { it.fieldId } - "terms_and_conditions_acceptance"
        val candidates = withCustom.filterNot { it.fieldId in history }

        val rows = CandidateFeatures.of(history, candidates, withCustom)

        // Terms is open and comes first; the company question sits right after it, at position 14.
        assertEquals(listOf(13, 13, 1, 1, 1), rows[0].toList())
        assertEquals(listOf(14, 14, 0, 0, 1), rows[1].toList())
    }

    @Test
    fun nothingAnsweredYetCountsFromBeforeTheFirstQuestion() {
        val rows = CandidateFeatures.of(emptyList(), form, form)
        // The first question is one step on from "nothing answered" (gap +1).
        assertEquals(listOf(0, 13, 1, 1, 1), rows[0].toList())
    }

    @Test
    fun farPositionsAndLongGapsAreCappedAsInTraining() {
        val long = (0 until 60).map { i ->
            FormField(fieldId = "q$i", fieldName = "Q$i", fieldType = "text", isRequired = false,
                validationRules = emptyList(), description = "")
        }
        val rows = CandidateFeatures.of(listOf("q0"), long.drop(1), long)
        assertEquals(listOf(1, 13, 1, 0, 0), rows.first().toList())
        assertEquals(listOf(39, 24, 0, 0, 0), rows.last().toList())
    }

    @Test
    fun aCandidateMissingFromTheFormIsPlacedAfterIt() {
        val stray = FormField(fieldId = "stray", fieldName = "Stray", fieldType = "text", isRequired = true,
            validationRules = listOf(Required), description = "")
        val rows = CandidateFeatures.of(emptyList(), listOf(stray), form)
        assertEquals(form.size, rows.single()[0])
    }
}
