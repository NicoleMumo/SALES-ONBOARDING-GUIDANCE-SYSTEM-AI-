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

    private fun risk(level: CompletionRiskLevel) = CompletionRiskGuidance(level, "Offer help.", emptyList())

    @Test
    fun theGuideCelebratesAFinishedFormAboveEverythingElse() {
        val guidance = NextFieldGuidance(isComplete = true, riskGuidance = risk(CompletionRiskLevel.HIGH))
        assertEquals(GuideMood.CELEBRATING, guideMood(guidance, justPassed = false))
    }

    @Test
    fun highRiskWorriesTheGuideEvenAfterAnAcceptedAnswer() {
        val guidance = NextFieldGuidance(nextFieldId = "full_name", riskGuidance = risk(CompletionRiskLevel.HIGH))
        assertEquals(GuideMood.WORRIED, guideMood(guidance, justPassed = true))
    }

    @Test
    fun anAnswerToFixOrMediumRiskConcernsTheGuide() {
        val toFix = NextFieldGuidance(correction = ValidationErrorDisplay("full_name", "Enter the full name."))
        val medium = NextFieldGuidance(nextFieldId = "full_name", riskGuidance = risk(CompletionRiskLevel.MEDIUM))
        assertEquals(GuideMood.CONCERNED, guideMood(toFix, justPassed = false))
        assertEquals(GuideMood.CONCERNED, guideMood(medium, justPassed = true))
    }

    @Test
    fun anAcceptedAnswerMakesTheGuideHappyOtherwiseItIsCalm() {
        val guidance = NextFieldGuidance(nextFieldId = "date_of_birth")
        assertEquals(GuideMood.HAPPY, guideMood(guidance, justPassed = true))
        assertEquals(GuideMood.CALM, guideMood(guidance, justPassed = false))
    }
}
