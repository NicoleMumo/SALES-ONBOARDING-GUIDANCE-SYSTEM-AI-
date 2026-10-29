package com.example.salesonboarding

import com.example.salesonboarding.risk.CompletionRiskEvent
import com.example.salesonboarding.risk.CompletionRiskScorer
import com.example.salesonboarding.risk.TrainedCompletionRiskModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingFormControllerRiskTest {

    private val trainedModel = requireNotNull(
        TrainedCompletionRiskModel.fromParts(
            featureNames = TrainedCompletionRiskModel.FEATURE_NAMES,
            weights = listOf(
                0.029819, 0.31253, 0.190293, -0.028253, 0.015226, 0.024499,
                0.056975, -0.033293, -1.070033, 0.127716, -0.020178, -0.02873,
                -0.49848, 1.319212
            ),
            intercept = 1.120239,
            mediumRiskThreshold = 0.6086,
            highRiskThreshold = 0.7112
        )
    )

    private val events = listOf(
        CompletionRiskEvent(5.0, false, false, 10.0),
        CompletionRiskEvent(6.0, false, true, 10.0)
    )

    @Test
    fun usesTheTrainedModelWhenItIsAvailable() = runTest {
        val controller = OnboardingFormController(
            "session",
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore(),
            industry = "banking",
            context = "business",
            riskModel = trainedModel
        )

        events.forEach(controller::recordRiskEvent)

        val expected = trainedModel.score(events, isBanking = true, isBusiness = true)!!.score
        assertEquals(expected, controller.riskFlow.value!!.score, 1e-12)
    }

    @Test
    fun fallsBackToTheHandSetRuleWithoutAModel() = runTest {
        val controller = OnboardingFormController(
            "session",
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        events.forEach(controller::recordRiskEvent)

        assertEquals(CompletionRiskScorer.score(events).score, controller.riskFlow.value!!.score, 1e-12)
    }
}
