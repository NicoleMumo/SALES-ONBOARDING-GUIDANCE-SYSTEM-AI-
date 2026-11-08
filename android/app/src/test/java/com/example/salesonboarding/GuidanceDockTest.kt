package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceDockTest {
    @Test
    fun aWrongAnswerIsPointedAtFirst() {
        val guidance = NextFieldGuidance(
            prediction = FieldPrediction("date_of_birth", 0.9f),
            correction = ValidationErrorDisplay("contact_phone_number", "Enter a valid Kenyan phone number."),
            nextFieldId = null
        )

        assertEquals(GuidancePointer(GuidanceStep.FIX, "contact_phone_number"), guidance.pointer())
    }

    @Test
    fun theModelsPickIsShownAsTheSuggestion() {
        val guidance = NextFieldGuidance(prediction = FieldPrediction("date_of_birth", 0.9f), nextFieldId = "date_of_birth")

        assertEquals(GuidancePointer(GuidanceStep.SUGGESTED, "date_of_birth"), guidance.pointer())
    }

    @Test
    fun withoutTheModelTheNextRequiredQuestionIsShown() {
        val guidance = NextFieldGuidance(nextFieldId = "full_name", modelUnavailable = true)

        assertEquals(GuidancePointer(GuidanceStep.NEXT_REQUIRED, "full_name"), guidance.pointer())
    }

    @Test
    fun aCompleteFormPointsToTheReview() {
        assertEquals(GuidancePointer(GuidanceStep.DONE, null), NextFieldGuidance(isComplete = true).pointer())
    }

    @Test
    fun nothingIsShownBeforeTheGuidanceIsKnown() {
        assertNull(NextFieldGuidance().pointer())
    }
}
