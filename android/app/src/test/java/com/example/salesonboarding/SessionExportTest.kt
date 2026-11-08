package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExportTest {
    private val minute = 60_000L
    private val start = 1_767_254_400_000L // 2026-01-01T08:00:00Z

    private fun session(
        status: String = "completed",
        steps: List<RecordedStep>,
        sessionId: String = "session-1",
        agentUid: String = "uid-mary",
        checksEachAttempt: Boolean = true
    ) = OnboardingSessionMetrics(
        status = status,
        startedAt = start,
        completedAt = if (status == "completed") start + 10 * minute else null,
        agentId = "mary@example.com",
        playbookFieldIds = listOf("full_name", "date_of_birth", "contact_phone_number", "email_address"),
        steps = steps,
        checksEachAttempt = checksEachAttempt,
        sessionId = sessionId,
        agentUid = agentUid,
        industry = "banking",
        context = "individual",
        consentAt = start + minute
    )

    private val steps = listOf(
        RecordedStep("full_name", passed = true, timestampMillis = start + 2 * minute),
        RecordedStep("contact_phone_number", passed = false, timestampMillis = start + 3 * minute),
        RecordedStep("contact_phone_number", passed = true, timestampMillis = start + 5 * minute)
    )

    private fun rows(csv: String) = csv.trimEnd().lines().map { it.split(",") }

    @Test
    fun trainingFileHasEveryColumnTheTrainingDataNeeds() {
        // REQUIRED_COLUMNS in ml/dataset.py
        val required = setOf(
            "session_id", "sales_agent_id", "context_value", "onboarding_field", "event_timestamp",
            "event_duration_minutes", "agent_response_time_minutes", "previous_onboarding_field",
            "next_onboarding_field", "validation_status", "failure_reason", "is_revisit",
            "session_status", "split"
        )
        assertTrue(required.all { it in SessionCsvExport.TRAINING_COLUMNS })
    }

    @Test
    fun trainingFileHasOneRowPerCheckedAnswer() {
        val table = rows(SessionCsvExport.trainingCsv(listOf(session(steps = steps)), nowMillis = start))
        val header = table.first()
        fun cell(row: Int, column: String) = table[row][header.indexOf(column)]

        assertEquals(4, table.size)
        assertEquals("full_name", cell(1, "onboarding_field"))
        assertEquals("1.00", cell(1, "event_duration_minutes")) // from consent
        assertEquals("2.00", cell(3, "event_duration_minutes")) // from the previous check
        assertEquals("failed", cell(2, "validation_status"))
        assertEquals("True", cell(3, "is_revisit"))
        assertEquals("contact_phone_number", cell(1, "next_onboarding_field"))
        assertEquals("full_name", cell(2, "previous_onboarding_field"))
        assertEquals("25.00", cell(2, "onboarding_progress"))
        assertEquals("50.00", cell(3, "onboarding_progress"))
        assertEquals("in_progress", cell(2, "session_status"))
        assertEquals("completed", cell(3, "session_status"))
        assertEquals("2026-01-01T08:02:00Z", cell(1, "event_timestamp"))
    }

    @Test
    fun unfinishedSessionIsLeftOutUntilItCountsAsAbandoned() {
        val unfinished = session(status = "in_progress", steps = steps)
        val lastCheck = steps.last().timestampMillis

        val recent = rows(SessionCsvExport.trainingCsv(listOf(unfinished), lastCheck + minute))
        val old = rows(SessionCsvExport.trainingCsv(listOf(unfinished), lastCheck + SessionCsvExport.ABANDONED_AFTER_MILLIS))
        val column = old.first().indexOf("session_status")

        assertEquals(1, recent.size) // header only: the outcome is not known yet
        assertEquals(4, old.size)
        assertEquals("abandoned", old.last()[column])
    }

    @Test
    fun trainingFileHidesTheSalesAgentAndLeavesOutOldSessions() {
        val csv = SessionCsvExport.trainingCsv(
            listOf(
                session(steps = steps),
                session(steps = steps, sessionId = "old-session", checksEachAttempt = false)
            ),
            nowMillis = start
        )

        assertFalse(csv.contains("uid-mary"))
        assertFalse(csv.contains("mary@example.com"))
        assertFalse(csv.contains("old-session"))
        assertTrue(csv.contains(SessionCsvExport.agentPseudonym("uid-mary")))
    }

    @Test
    fun pseudonymIsStableAndDiffersBetweenSalesAgents() {
        val mary = SessionCsvExport.agentPseudonym("uid-mary")

        assertEquals(mary, SessionCsvExport.agentPseudonym("uid-mary"))
        assertTrue(mary.matches(Regex("AGENT-[0-9A-F]{12}")))
        assertTrue(mary != SessionCsvExport.agentPseudonym("uid-otieno"))
    }

    @Test
    fun sessionsFileHasOneRowPerSessionWithItsChecks() {
        val table = rows(SessionCsvExport.sessionsCsv(listOf(session(steps = steps))))
        val header = table.first()
        fun cell(column: String) = table[1][header.indexOf(column)]

        assertEquals(SessionCsvExport.SESSION_COLUMNS, header)
        assertEquals("mary@example.com", cell("sales_agent"))
        assertEquals("10.00", cell("duration_minutes"))
        assertEquals("3", cell("answers_checked"))
        assertEquals("1", cell("failed_checks"))
        assertEquals("yes", cell("followed_standard"))
    }

    @Test
    fun valuesWithCommasQuotesOrFormulasAreMadeSafe() {
        assertEquals(
            "\"a,b\",\"say \"\"hi\"\"\",'=SUM(A1)",
            SessionCsvExport.csvLine(listOf("a,b", "say \"hi\"", "=SUM(A1)"))
        )
    }
}
