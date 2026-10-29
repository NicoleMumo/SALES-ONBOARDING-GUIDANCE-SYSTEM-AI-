package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionRiskTest {
    @Test
    fun baselineSessionHasLowRisk() {
        val result = CompletionRiskCalculator.calculate(
            CompletionRiskFeatures(
                elapsedMinutes = 5.0,
                validationErrors = 0,
                fieldRevisits = 0,
                progressPercent = 80.0
            )
        )

        assertEquals(0.05, result.score, 0.0001)
        assertEquals(CompletionRiskLevel.LOW, result.level)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun riskIncludesValidationDurationRevisitAndProgressSignals() {
        val result = CompletionRiskCalculator.calculate(
            CompletionRiskFeatures(
                elapsedMinutes = 16.0,
                validationErrors = 1,
                fieldRevisits = 2,
                progressPercent = 40.0,
                longEventCount = 1
            )
        )

        assertEquals(0.75, result.score, 0.0001)
        assertEquals(CompletionRiskLevel.HIGH, result.level)
        assertEquals(4, result.reasons.size)
    }

    @Test
    fun revisitWeightAccumulatesAndScoreIsCapped() {
        val result = CompletionRiskCalculator.calculate(
            CompletionRiskFeatures(
                elapsedMinutes = 20.0,
                validationErrors = 3,
                fieldRevisits = 20,
                progressPercent = 10.0,
                longEventCount = 1
            )
        )

        assertEquals(1.0, result.score, 0.0001)
        assertEquals(CompletionRiskLevel.HIGH, result.level)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeValidationErrorsAreRejected() {
        CompletionRiskFeatures(
            elapsedMinutes = 1.0,
            validationErrors = -1,
            fieldRevisits = 0,
            progressPercent = 10.0
        )
    }

    @Test
    fun scorerDerivesRiskFeaturesFromOnboardingEvents() {
        val result = CompletionRiskScorer.score(
            listOf(
                CompletionRiskEvent(
                    durationMinutes = 8.0,
                    validationPassed = false,
                    isRevisit = false,
                    progressPercent = 25.0
                ),
                CompletionRiskEvent(
                    durationMinutes = 8.0,
                    validationPassed = true,
                    isRevisit = true,
                    progressPercent = 40.0
                )
            )
        )

        assertEquals(0.50, result.score, 0.0001)
        assertEquals(CompletionRiskLevel.MEDIUM, result.level)
        assertEquals(3, result.reasons.size)
    }

    @Test
    fun emptySessionStartsWithLowProgressRisk() {
        val result = CompletionRiskScorer.score(emptyList())

        assertEquals(0.15, result.score, 0.0001)
        assertEquals(CompletionRiskLevel.LOW, result.level)
        assertTrue(result.reasons.single().contains("half"))
    }

    @Test
    fun difficultSessionCombinesLongDurationErrorsAndRevisits() {
        val result = CompletionRiskScorer.score(
            listOf(
                CompletionRiskEvent(16.0, validationPassed = false, isRevisit = false, progressPercent = 20.0),
                CompletionRiskEvent(8.0, validationPassed = false, isRevisit = true, progressPercent = 25.0),
                CompletionRiskEvent(8.0, validationPassed = true, isRevisit = true, progressPercent = 30.0),
                CompletionRiskEvent(8.0, validationPassed = true, isRevisit = true, progressPercent = 35.0)
            )
        )

        assertEquals(0.85, result.score, 0.0001)
        assertEquals(CompletionRiskLevel.HIGH, result.level)
        assertEquals(4, result.reasons.size)
        assertTrue(result.reasons.any { it.contains("longer") })
        assertTrue(result.reasons.any { it.contains("revisited") })
    }

    @Test
    fun severalShortEventsDoNotCountAsOneLongEvent() {
        val result = CompletionRiskScorer.score(
            listOf(
                CompletionRiskEvent(8.0, true, false, 80.0),
                CompletionRiskEvent(8.0, true, false, 90.0)
            )
        )

        assertEquals(0.05, result.score, 0.0001)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun individualLongEventRaisesDurationRisk() {
        val result = CompletionRiskScorer.score(
            listOf(CompletionRiskEvent(16.0, true, false, 80.0))
        )

        assertEquals(0.20, result.score, 0.0001)
        assertTrue(result.reasons.single().contains("longer"))
    }
}
