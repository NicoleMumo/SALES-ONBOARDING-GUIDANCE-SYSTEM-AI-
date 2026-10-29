package com.example.salesonboarding.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin scorer must agree with the Python that trained it. The expected
 * scores below were computed by `ml/consistency.py` with the shipped
 * weights; if either side changes, these fail.
 */
class TrainedCompletionRiskModelTest {

    private fun event(
        duration: Double,
        passed: Boolean,
        revisit: Boolean,
        progress: Double
    ) = CompletionRiskEvent(duration, passed, revisit, progress)

    /**
     * The weights the app ships, as exported by the training script into
     * `ml/models/risk_model.json`. Built through [TrainedCompletionRiskModel.fromParts]
     * rather than the JSON reader, because Android's `org.json` is only a stub on
     * the unit-test classpath; the reader itself is a thin wrapper around this.
     */
    private val shippedWeights = listOf(
        0.029819, 0.31253, 0.190293, -0.028253, 0.015226, 0.024499,
        0.056975, -0.033293, -1.070033, 0.127716, -0.020178, -0.02873,
        -0.49848, 1.319212
    )

    private val model = requireNotNull(
        TrainedCompletionRiskModel.fromParts(
            featureNames = TrainedCompletionRiskModel.FEATURE_NAMES,
            weights = shippedWeights,
            intercept = 1.120239,
            mediumRiskThreshold = 0.6086,
            highRiskThreshold = 0.7112
        )
    )

    @Test
    fun scoresMatchThePythonModelThatProducedTheWeights() {
        val cases = listOf(
            Triple(listOf(event(1.0, true, false, 10.0)), true to false, 0.5127210042),
            Triple(
                listOf(
                    event(2.0, true, false, 10.0),
                    event(3.0, false, false, 10.0),
                    event(1.5, true, false, 20.0)
                ),
                true to false,
                0.5103873386
            ),
            Triple(
                listOf(event(20.0, false, false, 5.0), event(18.0, false, true, 5.0)),
                false to false,
                0.8556951047
            ),
            Triple(
                (1..10).map { event(2.0, true, false, it * 5.0) },
                true to true,
                0.6933542757
            ),
            Triple(
                listOf(
                    event(1.0, true, false, 25.0),
                    event(4.0, false, true, 25.0),
                    event(2.0, false, false, 25.0),
                    event(1.0, true, false, 40.0)
                ),
                false to false,
                0.5093270429
            )
        )

        cases.forEach { (events, form, expected) ->
            val result = model.score(events, isBanking = form.first, isBusiness = form.second)
            assertNotNull(result)
            assertEquals(expected, result!!.score, 1e-9)
        }
    }

    @Test
    fun featureValuesMatchTheTrainingDefinitions() {
        val events = listOf(
            event(2.0, true, false, 10.0),
            event(20.0, false, true, 10.0),
            event(3.0, false, false, 20.0)
        )

        val values = model.featureValues(events, isBanking = true, isBusiness = false)
            .toList()
        val named = TrainedCompletionRiskModel.FEATURE_NAMES.zip(values).toMap()

        assertEquals(3.0, named.getValue("events"), 0.0)
        assertEquals(2.0, named.getValue("failures"), 0.0)
        assertEquals(2.0, named.getValue("consecutive_failures"), 0.0)
        assertEquals(1.0, named.getValue("revisits"), 0.0)
        assertEquals(25.0, named.getValue("elapsed_minutes"), 0.0)
        assertEquals(20.0, named.getValue("longest_event_minutes"), 0.0)
        assertEquals(1.0, named.getValue("long_events"), 0.0)
        assertEquals(20.0, named.getValue("progress_percent"), 0.0)
        assertEquals(2.0 / 3.0, named.getValue("failure_rate"), 1e-12)
        assertEquals(1.0 / 3.0, named.getValue("revisit_rate"), 1e-12)
        assertEquals(25.0 / 3.0, named.getValue("minutes_per_event"), 1e-12)
        assertEquals(20.0 / 25.0, named.getValue("progress_per_minute"), 1e-12)
        assertEquals(1.0, named.getValue("is_banking"), 0.0)
        assertEquals(0.0, named.getValue("is_business"), 0.0)
    }

    @Test
    fun consecutiveFailuresCountOnlyTheMostRecentRun() {
        val events = listOf(
            event(1.0, false, false, 10.0),
            event(1.0, false, false, 10.0),
            event(1.0, true, false, 20.0)
        )

        val values = model.featureValues(events, isBanking = false, isBusiness = false)

        assertEquals(2.0, values[TrainedCompletionRiskModel.FEATURE_NAMES.indexOf("failures")], 0.0)
        assertEquals(
            0.0,
            values[TrainedCompletionRiskModel.FEATURE_NAMES.indexOf("consecutive_failures")],
            0.0
        )
    }

    @Test
    fun aSessionWithNoCompletedStepsHasNoScore() {
        assertNull(model.score(emptyList(), isBanking = true, isBusiness = false))
    }

    @Test
    fun zeroElapsedTimeDoesNotProduceAnInvalidScore() {
        val result = model.score(
            listOf(event(0.0, true, false, 0.0)),
            isBanking = false,
            isBusiness = false
        )

        assertNotNull(result)
        assertTrue(result!!.score in 0.0..1.0)
    }

    @Test
    fun levelsFollowTheExportedThresholds() {
        val low = model.score(listOf(event(1.0, true, false, 90.0)), false, false)!!
        val high = model.score(
            listOf(event(25.0, false, false, 1.0), event(25.0, false, true, 1.0)),
            false,
            true
        )!!

        assertEquals(CompletionRiskLevel.LOW, low.level)
        assertEquals(CompletionRiskLevel.HIGH, high.level)
        assertTrue(high.score > low.score)
    }

    @Test
    fun repeatedFailuresAreExplainedToTheAgent() {
        val result = model.score(
            listOf(event(1.0, false, false, 10.0), event(1.0, false, false, 10.0)),
            isBanking = true,
            isBusiness = false
        )!!

        assertTrue(result.reasons.any { it.contains("did not pass") })
    }

    @Test
    fun aModelBuiltForDifferentFeaturesIsRefusedNotGuessed() {
        fun build(
            names: List<String> = TrainedCompletionRiskModel.FEATURE_NAMES,
            weights: List<Double> = shippedWeights,
            intercept: Double = 1.120239,
            medium: Double = 0.6086,
            high: Double = 0.7112
        ) = TrainedCompletionRiskModel.fromParts(names, weights, intercept, medium, high)

        assertNotNull("the shipped values must still load", build())
        assertNull("swapped feature order", build(names = TrainedCompletionRiskModel.FEATURE_NAMES.reversed()))
        assertNull("a renamed feature", build(names = TrainedCompletionRiskModel.FEATURE_NAMES.dropLast(1) + "something_else"))
        assertNull("too few weights", build(weights = shippedWeights.dropLast(1)))
        assertNull("thresholds the wrong way round", build(medium = 0.9, high = 0.1))
        assertNull("a weight that is not a number", build(weights = shippedWeights.dropLast(1) + Double.NaN))
    }

    @Test
    fun theTrainedModelSeparatesSessionsThatTheHandSetRuleCannot() {
        // Both of these score identically under the shipped hand-set rule (one
        // failure, no long step, no revisit, progress under 50), yet one is a
        // first stumble and the other is a session in trouble.
        val earlyStumble = listOf(event(1.0, false, false, 10.0))
        val inTrouble = listOf(
            event(5.0, false, false, 10.0),
            event(6.0, false, false, 10.0),
            event(7.0, false, true, 10.0)
        )

        val ruleEarly = CompletionRiskScorer.score(earlyStumble).score
        val ruleTrouble = CompletionRiskScorer.score(inTrouble).score
        val modelEarly = model.score(earlyStumble, false, false)!!.score
        val modelTrouble = model.score(inTrouble, false, false)!!.score

        assertTrue("the hand-set rule should rank these close together",
            kotlin.math.abs(ruleTrouble - ruleEarly) < 0.2)
        assertTrue("the trained model should rank the session in trouble higher",
            modelTrouble > modelEarly)
    }
}
