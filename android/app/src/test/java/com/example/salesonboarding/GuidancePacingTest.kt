package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskWarningPacerTest {
    private val low = CompletionRiskLevel.LOW
    private val medium = CompletionRiskLevel.MEDIUM
    private val high = CompletionRiskLevel.HIGH

    private fun shown(vararg model: CompletionRiskLevel): List<CompletionRiskLevel> {
        val pacer = RiskWarningPacer()
        return model.map(pacer::next)
    }

    @Test
    fun theFirstFewAnswersNeverShowAWarning() {
        assertEquals(List(4) { low }, shown(high, high, high, high))
    }

    @Test
    fun redComesOnlyAfterAmberAndASustainedHighScore() {
        // Checks 5 and 6 are high but not yet three in a row: amber. Red from the third.
        assertEquals(
            listOf(low, low, low, low, medium, medium, high),
            shown(low, low, low, low, high, high, high)
        )
    }

    @Test
    fun aSingleHighScoreShowsOnlyAmber() {
        assertEquals(listOf(low, low, low, low, medium, low), shown(low, low, low, low, high, low))
    }

    @Test
    fun theWarningDropsAsSoonAsTheScoreDrops() {
        assertEquals(medium, shown(low, low, low, low, high, high, high, medium).last())
        assertEquals(low, shown(low, low, low, low, high, high, high, low).last())
    }
}

class SessionExpiryTest {
    private val hour = 60L * 60 * 1000

    private fun session(status: String = "in_progress", updatedAt: Long? = null, startedAt: Long? = 0L) =
        StoredOnboardingSession(
            sessionId = "s", agentId = "a", industry = "banking", context = "individual", status = status,
            startedAt = startedAt, completedAt = null, entries = emptyList(), updatedAt = updatedAt
        )

    @Test
    fun anUnfinishedSessionUntouchedFor48HoursIsStale() {
        assertFalse(SessionExpiry.isStale(session(updatedAt = 0L), 47 * hour))
        assertTrue(SessionExpiry.isStale(session(updatedAt = 0L), 48 * hour))
    }

    @Test
    fun theLastSaveCountsNotTheStart() {
        assertFalse(SessionExpiry.isStale(session(startedAt = 0L, updatedAt = 40 * hour), 60 * hour))
    }

    @Test
    fun finishedSessionsAreNeverStale() {
        assertFalse(SessionExpiry.isStale(session(status = "completed", updatedAt = 0L), 100 * hour))
        assertFalse(SessionExpiry.isStale(session(status = "abandoned", updatedAt = 0L), 100 * hour))
    }
}

class GuidanceListsTest {
    private val fields = listOf(
        KnownFields.get("full_name"),
        KnownFields.get("email_address"),
        KnownFields.get("date_of_birth")
    )

    @Test
    fun everyAnswerToFixIsListedInTheFormsOrder() {
        val session = LiveValidationSession("s", fields)
        session.updateField("date_of_birth", "not a date")
        session.checkField("date_of_birth")
        session.updateField("email_address", "bad")
        session.checkField("email_address")

        assertEquals(listOf("email_address", "date_of_birth"), answersToFix(fields, session.stateFlow.value))
    }

    @Test
    fun beforeAnyGuidanceThePanelPointsToTheFirstRequiredQuestion() {
        val state = LiveValidationSession("s", fields).stateFlow.value
        assertEquals(
            GuidancePointer(GuidanceStep.NEXT_REQUIRED, "full_name"),
            guidancePointer(NextFieldGuidance(), fields, state)
        )
    }
}

class EnterFollowTest {
    private val fields = listOf(
        KnownFields.get("full_name"),
        KnownFields.get("email_address"),
        KnownFields.get("date_of_birth")
    )

    @Test
    fun aGoodAnswerMovesOnEvenIfTheGuidanceStillPointsAtIt() {
        val session = LiveValidationSession("s", fields)
        session.updateField("email_address", "mary@example.com")
        session.checkField("email_address")
        val stale = GuidancePointer(GuidanceStep.NEXT_REQUIRED, "email_address")

        assertEquals("date_of_birth", followTarget("email_address", stale, fields, session.stateFlow.value))
    }

    @Test
    fun aWrongAnswerIsOpenedAgainToBeFixed() {
        val session = LiveValidationSession("s", fields)
        session.updateField("email_address", "not an email")
        session.checkField("email_address")
        val fix = GuidancePointer(GuidanceStep.FIX, "email_address")

        assertEquals("email_address", followTarget("email_address", fix, fields, session.stateFlow.value))
    }

    @Test
    fun otherwiseEnterGoesWhereTheGuidancePoints() {
        val state = LiveValidationSession("s", fields).stateFlow.value
        val next = GuidancePointer(GuidanceStep.NEXT_REQUIRED, "full_name")
        assertEquals("full_name", followTarget("email_address", next, fields, state))
        assertEquals("full_name", followTarget(null, next, fields, state))
    }

    // Auto-follow off: Enter goes to the next question in order, the date of birth here.

    @Test
    fun withoutAutoFollowAWrongAnswerStaysAndTheCalendarDoesNotOpen() {
        val session = LiveValidationSession("s", fields)
        session.updateField("email_address", "not an email")
        session.checkField("email_address")
        val fix = GuidancePointer(GuidanceStep.FIX, "email_address")

        assertEquals(
            "email_address",
            enterTarget("email_address", "date_of_birth", fix, fields, session.stateFlow.value)
        )
    }

    @Test
    fun withoutAutoFollowAGoodAnswerMovesToTheNextQuestionInOrder() {
        val session = LiveValidationSession("s", fields)
        session.updateField("email_address", "mary@example.com")
        session.checkField("email_address")
        val elsewhere = GuidancePointer(GuidanceStep.NEXT_REQUIRED, "full_name")

        assertEquals(
            "date_of_birth",
            enterTarget("email_address", "date_of_birth", elsewhere, fields, session.stateFlow.value)
        )
    }

    @Test
    fun withAutoFollowEnterFollowsTheGuidanceAsBefore() {
        val state = LiveValidationSession("s", fields).stateFlow.value
        val next = GuidancePointer(GuidanceStep.NEXT_REQUIRED, "full_name")
        assertEquals("full_name", enterTarget("email_address", null, next, fields, state))
    }
}
