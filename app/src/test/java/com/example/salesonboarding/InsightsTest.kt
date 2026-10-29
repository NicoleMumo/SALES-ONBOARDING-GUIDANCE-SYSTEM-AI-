package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        // A NaN threshold would make every step HIGH, since `score < NaN` is never true.
        assertNull("a medium threshold that is not a number", build(medium = Double.NaN))
        assertNull("a high threshold that is not a number", build(high = Double.NaN))
        assertNull("a threshold at or below 0", build(medium = 0.0))
        assertNull("a threshold at or above 1", build(high = 1.5))
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
            CompletionRiskFeatures(16.0, 0, 1, 40.0, longEventCount = 1)
        )

        assertEquals(
            "Offer extra help and review the information with the customer.",
            CompletionRiskGuidanceMapper.from(result)?.message
        )
    }

    @Test
    fun highRiskRecommendsHelpingWithRemainingInformation() {
        val result = CompletionRiskCalculator.calculate(
            CompletionRiskFeatures(16.0, 1, 2, 40.0, longEventCount = 1)
        )

        assertEquals(
            "Pause and help the customer complete the remaining information.",
            CompletionRiskGuidanceMapper.from(result)?.message
        )
    }
}

class SessionConsistencyTest {

    private val order = listOf("a", "b", "c", "d")

    private fun steps(vararg fields: String, failed: Set<Int> = emptySet()) =
        fields.mapIndexed { index, field ->
            RecordedStep(field, passed = index !in failed, timestampMillis = 1_000L * index)
        }

    // The same cases as OutOfOrderTests in tests/test_consistency.py, so Kotlin and Python
    // agree on what "steps out of place" means.
    @Test
    fun countsMatchThePythonDefinition() {
        val cases = mapOf(
            listOf("a", "b", "c", "d") to 0,
            listOf("a", "d", "b", "c") to 1,
            listOf("b", "c", "d", "a") to 1,
            listOf("c", "d", "a", "b") to 2,
            listOf("d", "c", "b", "a") to 3,
            listOf("a", "b", "a", "c") to 0,
            listOf("a", "custom_field", "b") to 0
        )

        cases.forEach { (fields, expected) ->
            assertEquals(fields.toString(), expected, SessionConsistencyCalculator.misplacedFields(fields, order).size)
        }
    }

    @Test
    fun theStepThatJumpedTheQueueIsTheOneBlamed() {
        assertEquals(listOf("d"), SessionConsistencyCalculator.misplacedFields(listOf("a", "d", "b", "c"), order))
    }

    @Test
    fun stepsAreReadInTimeOrderNotStorageOrder() {
        val shuffled = listOf(
            RecordedStep("c", true, 3_000),
            RecordedStep("a", true, 1_000),
            RecordedStep("b", true, 2_000)
        )

        assertEquals(0, SessionConsistencyCalculator.forSession(shuffled, order).stepsOutOfPlace)
    }

    @Test
    fun followedStandardUsesBothLimits() {
        val calm = SessionConsistencyCalculator.forSession(steps("a", "b", "c", "d"), order)
        val manyFailures = SessionConsistencyCalculator.forSession(
            steps("a", "a", "a", "b", "c", "d", failed = setOf(0, 1, 3)),
            order
        )
        val scrambled = SessionConsistencyCalculator.forSession(steps("d", "c", "b", "a"), order)

        assertTrue(calm.followedStandard)
        assertEquals(3, manyFailures.failedChecks)
        assertFalse(manyFailures.followedStandard)
        assertEquals(3, scrambled.stepsOutOfPlace)
        assertFalse(scrambled.followedStandard)
    }

    @Test
    fun theLimitsAreInclusive() {
        val atTheLimit = SessionConsistencyCalculator.forSession(
            steps("c", "d", "a", "b", "a", "b", failed = setOf(4, 5)),
            order
        )

        assertEquals(2, atTheLimit.stepsOutOfPlace)
        assertEquals(2, atTheLimit.failedChecks)
        assertTrue(atTheLimit.followedStandard)
    }

    @Test
    fun reportListsTheLeastConsistentAgentFirst() {
        val week = 7L * 24 * 60 * 60 * 1000
        val sessions = listOf(
            ConsistencyInput("steady", week * 10, order, steps("a", "b", "c", "d")),
            ConsistencyInput("steady", week * 10, order, steps("a", "b", "c", "d")),
            ConsistencyInput("drifting", week * 10, order, steps("d", "c", "b", "a")),
            ConsistencyInput("drifting", week * 9, order, steps("a", "b", "c", "d"))
        )

        val report = SessionConsistencyCalculator.report(sessions)

        assertEquals(4, report.sessionCount)
        assertEquals(3, report.followedStandardCount)
        assertEquals(listOf("drifting", "steady"), report.agents.map { it.agentId })
        assertEquals(0.5, report.agents.first().followedStandardShare, 1e-9)
        assertEquals(listOf(1, 0), report.weekly.map { it.weeksAgo })
        assertEquals(3, report.mostMisplacedFields.sumOf { it.count })
    }

    @Test
    fun theMostCorrectedFieldsAreCounted() {
        val sessions = listOf(
            ConsistencyInput("x", 0, order, steps("a", "b", "b", "c", failed = setOf(1))),
            ConsistencyInput("y", 0, order, steps("a", "b", "b", "c", failed = setOf(1))),
            ConsistencyInput("z", 0, order, steps("a", "a", "b", failed = setOf(0)))
        )

        val report = SessionConsistencyCalculator.report(sessions)

        assertEquals(FieldCount("b", 2), report.mostFailedFields.first())
    }

    @Test
    fun analyticsBuildsTheReportFromCompletedSessionsOnly() {
        val completed = com.example.salesonboarding.OnboardingSessionMetrics(
            status = "completed",
            startedAt = 0,
            completedAt = 60_000,
            agentId = "agent",
            playbookFieldIds = order,
            fieldNames = mapOf("a" to "Full name"),
            steps = steps("d", "c", "b", "a")
        )
        val stillOpen = completed.copy(status = "in_progress", steps = steps("a", "b"))

        val analytics = com.example.salesonboarding.OnboardingAnalytics.fromSessions(
            listOf(completed, stillOpen)
        )

        assertEquals(1, analytics.consistency.sessionCount)
        assertEquals(0, analytics.consistency.followedStandardCount)
        assertEquals("Full name", analytics.fieldNames["a"])
    }

    @Test
    fun sessionsWithoutStepsOrOrderAreSkippedNotCountedAsPerfect() {
        val report = SessionConsistencyCalculator.report(
            listOf(
                ConsistencyInput("x", 0, order, emptyList()),
                ConsistencyInput("y", 0, emptyList(), steps("a"))
            )
        )

        assertEquals(0, report.sessionCount)
        assertEquals(0.0, report.followedStandardShare, 0.0)
        assertEquals("both are reported as not counted, not silently dropped", 2, report.sessionsWithoutChecks)
    }

    @Test
    fun sessionsCheckedWhileTypingAreLeftOutAndCounted() {
        // An old session: typing a date with pauses saved three "failed" checks.
        val typedWithPauses = steps("a", "b", "b", "b", "b", "c", "d", failed = setOf(1, 2, 3))
        val report = SessionConsistencyCalculator.report(
            listOf(
                ConsistencyInput("old", 0, order, typedWithPauses, checksEachAttempt = false),
                ConsistencyInput("new", 0, order, steps("a", "b", "c", "d"))
            )
        )

        assertEquals(1, report.sessionCount)
        assertEquals(1, report.followedStandardCount)
        assertEquals(1, report.earlierSessionsLeftOut)
        assertEquals(listOf("new"), report.agents.map { it.agentId })
    }

    @Test
    fun onlyEarlierSessionsGiveAnEmptyReportThatStillSaysHowManyWereLeftOut() {
        val earlier = com.example.salesonboarding.OnboardingSessionMetrics(
            status = "completed",
            startedAt = 0,
            completedAt = 60_000,
            agentId = "agent",
            playbookFieldIds = order,
            steps = steps("a", "b", "c", "d"),
            checksEachAttempt = false
        )

        val analytics = com.example.salesonboarding.OnboardingAnalytics.fromSessions(listOf(earlier))

        assertEquals(0, analytics.consistency.sessionCount)
        assertEquals(1, analytics.consistency.earlierSessionsLeftOut)
        assertEquals(1, analytics.completedCount)
    }
}
