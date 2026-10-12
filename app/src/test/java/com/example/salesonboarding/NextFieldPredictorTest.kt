package com.example.salesonboarding

import com.example.salesonboarding.validation.KnownFields
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NextFieldPredictorTest {
    @Test
    fun validAnswersAreSentToThePredictorAndRecommendationIsPublished() = runTest {
        var receivedHistory: List<String>? = null
        val predictor = NextFieldPredictor { history, _, _, validation ->
            receivedHistory = history
            assertEquals("passed", validation)
            FieldPrediction("mobile_phone_number", 0.8f)
        }
        val controller = OnboardingFormController(
            sessionId = "session",
            fields = listOf(
                KnownFields.get("full_name"),
                KnownFields.get("email_address"),
                KnownFields.get("mobile_phone_number")
            ),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore(),
            predictor = predictor
        )

        controller.updateField("full_name", "Jane Wanjiku")
        advanceTimeBy(300)
        runCurrent()
        controller.updateField("email_address", "agent@example.com")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("full_name", "email_address"), receivedHistory)
        assertEquals("mobile_phone_number", controller.guidanceFlow.value.prediction?.fieldId)
        assertTrue(controller.guidanceFlow.value.prediction?.confidence == 0.8f)
    }

    @Test
    fun telecomAndBankingWorkflowsPassTheirSelectedIndustryToGuidance() = runTest {
        val receivedIndustries = mutableListOf<String>()
        val predictor = NextFieldPredictor { _, industry, context, validation ->
            receivedIndustries += industry
            assertEquals("individual", context)
            assertEquals("passed", validation)
            FieldPrediction("email_address", 0.7f)
        }

        listOf("telecom", "banking").forEachIndexed { index, industry ->
            val controller = OnboardingFormController(
                sessionId = "session-$industry",
                fields = listOf(KnownFields.get("full_name"), KnownFields.get("email_address")),
                scope = backgroundScope,
                entryStore = InMemoryFieldEntryStore(),
                predictor = predictor,
                industry = industry,
                context = "individual"
            )

            controller.updateField("full_name", "Jane Wanjiku")
            advanceTimeBy(300)
            runCurrent()

            assertEquals("email_address", controller.guidanceFlow.value.prediction?.fieldId)
            assertEquals(0.7f, controller.guidanceFlow.value.prediction?.confidence)
            assertEquals(index + 1, receivedIndustries.size)
        }

        assertEquals(listOf("telecom", "banking"), receivedIndustries)
    }
}
