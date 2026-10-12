package com.example.salesonboarding.risk

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

        assertEquals(0.05, result.score, 0.0)
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
                progressPercent = 40.0
            )
        )

        assertEquals(0.75, result.score, 0.0)
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
                progressPercent = 10.0
            )
        )

        assertEquals(1.0, result.score, 0.0)
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
}
