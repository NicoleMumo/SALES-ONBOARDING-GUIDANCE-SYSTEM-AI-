package com.example.salesonboarding.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionRiskGuidanceTest {
    @Test
    fun lowRiskDoesNotAddExtraAssistance() {
        val result = CompletionRiskCalculator.calculate(
            CompletionRiskFeatures(5.0, 0, 0, 80.0)
        )

        assertNull(CompletionRiskGuidanceMapper.from(result))
    }

    @Test
    fun mediumRiskRecommendsReviewingInformation() {
        val result = CompletionRiskCalculator.calculate(
            CompletionRiskFeatures(16.0, 0, 1, 40.0)
        )

        assertEquals(
            "Offer extra help and review the information with the customer.",
            CompletionRiskGuidanceMapper.from(result)?.message
        )
    }

    @Test
    fun highRiskRecommendsHelpingWithRemainingInformation() {
        val result = CompletionRiskCalculator.calculate(
            CompletionRiskFeatures(16.0, 1, 2, 40.0)
        )

        assertEquals(
            "Pause and help the customer complete the remaining information.",
            CompletionRiskGuidanceMapper.from(result)?.message
        )
    }
}
