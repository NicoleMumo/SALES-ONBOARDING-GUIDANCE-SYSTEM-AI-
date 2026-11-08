package com.example.salesonboarding

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The administrator's two CSV exports of the company's sessions.
 *
 * [sessionsCsv] has one row per session, for reporting. [trainingCsv] has one row per checked
 * answer, in the columns of the training data written by ml/dataset.py, so real sessions can
 * be used to retrain the models. Neither file holds a client's answers or name: sessions only
 * save which question was checked, whether it passed and when. The training file also
 * replaces each sales agent with a pseudonym.
 */
object SessionCsvExport {
    /** An unfinished session with no check for this long is labelled abandoned for training. */
    const val ABANDONED_AFTER_MILLIS = 24L * 60 * 60 * 1000

    val SESSION_COLUMNS = listOf(
        "session_id", "sales_agent", "industry", "client_type", "status",
        "started_at", "completed_at", "duration_minutes", "answers_checked",
        "failed_checks", "steps_out_of_place", "followed_standard"
    )

    val TRAINING_COLUMNS = listOf(
        "session_id", "sales_agent_id", "industry", "context_value", "onboarding_field",
        "event_name", "event_timestamp", "event_status", "event_duration_minutes",
        "agent_response_time_minutes", "is_revisit", "previous_onboarding_field",
        "next_onboarding_field", "validation_status", "failure_reason",
        "onboarding_progress", "session_status", "split"
    )

    fun sessionsCsv(sessions: List<OnboardingSessionMetrics>): String = buildString {
        appendLine(csvLine(SESSION_COLUMNS))
        sessions.sortedBy { it.startedAt ?: 0L }.forEach { session ->
            val completed = session.status.equals("completed", ignoreCase = true)
            val consistency = SessionConsistencyCalculator.forSession(session.steps, session.playbookFieldIds)
            val counted = completed && session.checksEachAttempt && session.steps.isNotEmpty()
            val started = session.startedAt
            val finished = session.completedAt
            appendLine(
                csvLine(
                    listOf(
                        session.sessionId,
                        session.agentId,
                        session.industry,
                        session.context,
                        session.status,
                        started?.let(::isoUtc).orEmpty(),
                        finished?.let(::isoUtc).orEmpty(),
                        if (started != null && finished != null && finished >= started) {
                            minutes(finished - started)
                        } else "",
                        session.steps.size.toString(),
                        consistency.failedChecks.toString(),
                        if (counted) consistency.stepsOutOfPlace.toString() else "",
                        if (counted) (if (consistency.followedStandard) "yes" else "no") else ""
                    )
                )
            )
        }
    }

    /**
     * One row per checked answer. Step times are measured as the app measures them for the
     * risk model: from the previous checked answer, or from consent for the first one, so the
     * sales agent's response time is already inside `event_duration_minutes`. Only sessions
     * with a known outcome are written: completed, or abandoned (unfinished with no check for
     * [ABANDONED_AFTER_MILLIS]). Sessions saved before answers were checked on leaving the box
     * are left out, because their checks include half-typed answers. The split column is left
     * empty, to be filled by sales agent before training.
     */
    fun trainingCsv(sessions: List<OnboardingSessionMetrics>, nowMillis: Long): String = buildString {
        appendLine(csvLine(TRAINING_COLUMNS))
        trainingSessions(sessions, nowMillis)
            .forEach { (session, outcome) -> trainingRows(session, outcome).forEach { appendLine(csvLine(it)) } }
    }

    /** The sessions [trainingCsv] writes, with their outcome, oldest first. */
    fun trainingSessions(
        sessions: List<OnboardingSessionMetrics>,
        nowMillis: Long
    ): List<Pair<OnboardingSessionMetrics, String>> = sessions
        .filter { it.checksEachAttempt && it.steps.isNotEmpty() }
        .sortedBy { it.startedAt ?: 0L }
        .mapNotNull { session ->
            val lastCheck = session.steps.maxOf { it.timestampMillis }
            when {
                session.status.equals("completed", ignoreCase = true) -> session to "completed"
                nowMillis - lastCheck >= ABANDONED_AFTER_MILLIS -> session to "abandoned"
                else -> null
            }
        }

    private fun trainingRows(session: OnboardingSessionMetrics, finalStatus: String): List<List<String>> {
        val steps = session.steps.sortedBy { it.timestampMillis }
        val totalFields = session.playbookFieldIds.size
        val passedSoFar = mutableMapOf<String, Boolean>()
        var previousTime = session.consentAt ?: session.startedAt ?: steps.first().timestampMillis
        return steps.mapIndexed { index, step ->
            val isRevisit = steps.take(index).any { it.fieldId == step.fieldId }
            passedSoFar[step.fieldId] = step.passed
            val progress = String.format(
                Locale.US, "%.2f",
                if (totalFields == 0) 0.0 else passedSoFar.count { it.value } * 100.0 / totalFields
            )
            val row = listOf(
                session.sessionId,
                agentPseudonym(session.agentUid.ifBlank { session.agentId }),
                session.industry,
                session.context,
                step.fieldId,
                if (isRevisit) "revisited" else "entered",
                isoUtc(step.timestampMillis),
                if (step.passed) "completed" else "failed",
                minutes((step.timestampMillis - previousTime).coerceAtLeast(0)),
                "0.00",
                if (isRevisit) "True" else "False",
                steps.getOrNull(index - 1)?.fieldId.orEmpty(),
                steps.getOrNull(index + 1)?.fieldId.orEmpty(),
                if (step.passed) "passed" else "failed",
                "",
                progress,
                if (index == steps.lastIndex) finalStatus else "in_progress",
                ""
            )
            previousTime = step.timestampMillis
            row
        }
    }

    /** The same sales agent always gets the same pseudonym, which cannot be read back to them. */
    internal fun agentPseudonym(agentId: String): String {
        if (agentId.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest("salesmesh-agent:$agentId".toByteArray())
        return "AGENT-" + digest.take(6).joinToString("") { "%02X".format(it) }
    }

    internal fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private fun minutes(millis: Long): String = String.format(Locale.US, "%.2f", millis / 60_000.0)

    /** Quotes a value that holds a comma, quote or line break; a spreadsheet formula is made plain text. */
    internal fun csvLine(values: List<String>): String = values.joinToString(",") { raw ->
        val value = if (raw.firstOrNull() in FORMULA_STARTS) "'$raw" else raw
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    private val FORMULA_STARTS = setOf('=', '+', '@')
}
