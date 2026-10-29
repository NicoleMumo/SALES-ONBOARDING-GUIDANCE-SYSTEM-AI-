package com.example.salesonboarding.consistency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }
}
