package com.example.salesonboarding

import kotlin.math.exp
import org.json.JSONObject

// region Risk inputs, results and the hand-set rule

data class CompletionRiskFeatures(
    /**
     * Minutes of the longest single step ([CompletionRiskScorer] passes the longest step, not
     * the total). Not used in the score: the calculator counts long steps in [longEventCount].
     */
    val elapsedMinutes: Double,
    val validationErrors: Int,
    val fieldRevisits: Int,
    val progressPercent: Double,
    val longEventCount: Int = 0
) {
    init {
        require(elapsedMinutes.isFinite()) { "Elapsed minutes must be finite." }
        require(elapsedMinutes >= 0) { "Elapsed minutes cannot be negative." }
        require(validationErrors >= 0) { "Validation errors cannot be negative." }
        require(fieldRevisits >= 0) { "Field revisits cannot be negative." }
        require(longEventCount >= 0) { "Long event count cannot be negative." }
        require(progressPercent in 0.0..100.0) {
            "Progress percent must be between 0 and 100."
        }
    }
}

enum class CompletionRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class CompletionRiskResult(
    val score: Double,
    val level: CompletionRiskLevel,
    val reasons: List<String>
) {
    init {
        require(score.isFinite() && score in 0.0..1.0) {
            "Risk score must be finite and between 0 and 1."
        }
    }
}

object CompletionRiskCalculator {
    const val LONG_EVENT_THRESHOLD_MINUTES = 15.0
    const val LOW_PROGRESS_THRESHOLD_PERCENT = 50.0
    const val MEDIUM_RISK_THRESHOLD = 0.30
    const val HIGH_RISK_THRESHOLD = 0.70

    fun calculate(features: CompletionRiskFeatures): CompletionRiskResult {
        var score = BASE_SCORE
        val reasons = mutableListOf<String>()

        if (features.validationErrors > 0) {
            score += VALIDATION_ERROR_WEIGHT
            reasons += "Validation errors occurred."
        }
        if (features.longEventCount > 0) {
            score += LONG_EVENT_WEIGHT
            reasons += "The onboarding session is taking longer than expected."
        }
        if (features.fieldRevisits > 0) {
            score += REVISIT_WEIGHT * features.fieldRevisits
            reasons += "Fields were revisited."
        }
        if (features.progressPercent < LOW_PROGRESS_THRESHOLD_PERCENT) {
            score += LOW_PROGRESS_WEIGHT
            reasons += "Less than half of the required information is complete."
        }

        val boundedScore = score.coerceIn(0.0, 1.0)
        return CompletionRiskResult(
            score = boundedScore,
            level = when {
                boundedScore < MEDIUM_RISK_THRESHOLD -> CompletionRiskLevel.LOW
                boundedScore < HIGH_RISK_THRESHOLD -> CompletionRiskLevel.MEDIUM
                else -> CompletionRiskLevel.HIGH
            },
            reasons = reasons
        )
    }

    private const val BASE_SCORE = 0.05
    private const val VALIDATION_ERROR_WEIGHT = 0.25
    private const val LONG_EVENT_WEIGHT = 0.15
    private const val REVISIT_WEIGHT = 0.10
    private const val LOW_PROGRESS_WEIGHT = 0.10
}

// endregion

// region Risk from recorded steps

data class CompletionRiskEvent(
    val durationMinutes: Double,
    val validationPassed: Boolean,
    val isRevisit: Boolean,
    val progressPercent: Double
) {
    init {
        require(durationMinutes >= 0) { "Event duration cannot be negative." }
        require(progressPercent in 0.0..100.0) {
            "Progress percent must be between 0 and 100."
        }
    }
}

object CompletionRiskScorer {
    fun score(events: List<CompletionRiskEvent>): CompletionRiskResult {
        val features = CompletionRiskFeatures(
            elapsedMinutes = events.maxOfOrNull { it.durationMinutes } ?: 0.0,
            validationErrors = events.count { !it.validationPassed },
            fieldRevisits = events.count { it.isRevisit },
            progressPercent = events.lastOrNull()?.progressPercent ?: 0.0,
            longEventCount = events.count {
                it.durationMinutes > CompletionRiskCalculator.LONG_EVENT_THRESHOLD_MINUTES
            }
        )
        return CompletionRiskCalculator.calculate(features)
    }
}

// endregion

// region Trained risk model

/**
 * The trained replacement for [CompletionRiskCalculator]'s hand-set weights.
 *
 * It answers the same question -- is this session heading for the customer giving
 * up? -- but its weights were learned from simulated sessions instead of chosen by
 * hand, and it was tested on agents it had never seen. On that test it scored 0.71
 * to 0.73 (ROC-AUC) against 0.55 to 0.59 for the hand-set rule, which is close to
 * guessing. See docs/04 for the full numbers and limits.
 *
 * The arithmetic here must stay identical to `ml/consistency.py`, which
 * produced the weights; `TrainedCompletionRiskModelTest` checks it against scores
 * computed in Python.
 *
 * Only counts and times are used. No customer answer ever reaches this model.
 */
class TrainedCompletionRiskModel internal constructor(
    private val weights: DoubleArray,
    private val intercept: Double,
    private val mediumRiskThreshold: Double,
    private val highRiskThreshold: Double
) {

    /**
     * The chance-of-abandonment score for a session so far, or null if there are
     * no completed steps yet. The caller should fall back to [CompletionRiskScorer].
     */
    fun score(
        events: List<CompletionRiskEvent>,
        isBanking: Boolean,
        isBusiness: Boolean
    ): CompletionRiskResult? {
        if (events.isEmpty()) return null

        val values = featureValues(events, isBanking, isBusiness)
        var total = intercept
        for (index in values.indices) {
            total += weights[index] * values[index]
        }
        val score = (1.0 / (1.0 + exp(-total))).coerceIn(0.0, 1.0)

        return CompletionRiskResult(
            score = score,
            level = when {
                score < mediumRiskThreshold -> CompletionRiskLevel.LOW
                score < highRiskThreshold -> CompletionRiskLevel.MEDIUM
                else -> CompletionRiskLevel.HIGH
            },
            reasons = reasonsFor(events)
        )
    }

    /** Feature values in [FEATURE_NAMES] order. Mirrors `features()` in ml/consistency.py. */
    internal fun featureValues(
        events: List<CompletionRiskEvent>,
        isBanking: Boolean,
        isBusiness: Boolean
    ): DoubleArray {
        val count = events.size
        val failures = events.count { !it.validationPassed }
        val revisits = events.count { it.isRevisit }
        val elapsed = events.sumOf { it.durationMinutes }
        val longest = events.maxOf { it.durationMinutes }
        val longEvents = events.count { it.durationMinutes > LONG_EVENT_THRESHOLD_MINUTES }
        val progress = events.last().progressPercent

        var consecutiveFailures = 0
        for (event in events.asReversed()) {
            if (event.validationPassed) break
            consecutiveFailures++
        }

        return doubleArrayOf(
            count.toDouble(),
            failures.toDouble(),
            consecutiveFailures.toDouble(),
            revisits.toDouble(),
            elapsed,
            longest,
            longEvents.toDouble(),
            progress,
            failures.toDouble() / count,
            revisits.toDouble() / count,
            elapsed / count,
            if (elapsed > 0) progress / elapsed else 0.0,
            if (isBanking) 1.0 else 0.0,
            if (isBusiness) 1.0 else 0.0
        )
    }

    /**
     * Plain-English reasons, from what actually happened in the session.
     *
     * The score comes from all features together, so these explain the situation
     * rather than decompose the arithmetic. An empty list is normal for a session
     * that simply looks slow overall.
     */
    private fun reasonsFor(events: List<CompletionRiskEvent>): List<String> {
        val reasons = mutableListOf<String>()
        var consecutiveFailures = 0
        for (event in events.asReversed()) {
            if (event.validationPassed) break
            consecutiveFailures++
        }
        if (consecutiveFailures >= 2) {
            reasons += "The last $consecutiveFailures answers did not pass their checks."
        } else if (events.any { !it.validationPassed }) {
            reasons += "Some answers did not pass their checks."
        }
        if (events.any { it.durationMinutes > LONG_EVENT_THRESHOLD_MINUTES }) {
            reasons += "At least one question took much longer than usual."
        }
        if (events.count { it.isRevisit } >= 2) {
            reasons += "Several questions were answered more than once."
        }
        if (events.last().progressPercent < LOW_PROGRESS_PERCENT && events.size >= MANY_STEPS) {
            reasons += "Progress is still low after several questions."
        }
        return reasons
    }

    companion object {
        const val ASSET_NAME = "risk_model.json"

        private const val LONG_EVENT_THRESHOLD_MINUTES = 15.0
        private const val LOW_PROGRESS_PERCENT = 50.0
        private const val MANY_STEPS = 5

        /**
         * Feature order the exported weights were trained in. The export names its
         * features, so a model built from a different order is refused rather than
         * scored wrongly.
         */
        internal val FEATURE_NAMES = listOf(
            "events", "failures", "consecutive_failures", "revisits",
            "elapsed_minutes", "longest_event_minutes", "long_events",
            "progress_percent", "failure_rate", "revisit_rate",
            "minutes_per_event", "progress_per_minute", "is_banking", "is_business"
        )

        /**
         * Builds a model from already-read values, or returns null if they were
         * produced for a different set of features. Kept separate from JSON parsing
         * so the checks and the arithmetic can be tested without Android's `org.json`,
         * which is only a stub on the unit-test classpath.
         */
        internal fun fromParts(
            featureNames: List<String>,
            weights: List<Double>,
            intercept: Double,
            mediumRiskThreshold: Double,
            highRiskThreshold: Double
        ): TrainedCompletionRiskModel? {
            if (featureNames != FEATURE_NAMES || weights.size != FEATURE_NAMES.size) return null
            if (mediumRiskThreshold > highRiskThreshold) return null
            if (!intercept.isFinite() || weights.any { !it.isFinite() }) return null
            return TrainedCompletionRiskModel(
                weights = weights.toDoubleArray(),
                intercept = intercept,
                mediumRiskThreshold = mediumRiskThreshold,
                highRiskThreshold = highRiskThreshold
            )
        }

        /**
         * Reads an exported model, or returns null if it is malformed or built for
         * different features. A null model means the app keeps using the older
         * hand-set rule, which is why nothing here throws.
         */
        fun fromJsonOrNull(json: String): TrainedCompletionRiskModel? = try {
            val root = JSONObject(json)
            val names = root.getJSONArray("feature_names")
            val weights = root.getJSONArray("weights")
            fromParts(
                featureNames = List(names.length()) { names.getString(it) },
                weights = List(weights.length()) { weights.getDouble(it) },
                intercept = root.getDouble("intercept"),
                mediumRiskThreshold = root.getDouble("medium_risk_threshold"),
                highRiskThreshold = root.getDouble("high_risk_threshold")
            )
        } catch (_: Exception) {
            null
        }
    }
}

// endregion

// region Risk warning shown to the agent

data class CompletionRiskGuidance(
    val level: CompletionRiskLevel,
    val message: String,
    val reasons: List<String>
)

object CompletionRiskGuidanceMapper {
    fun from(result: CompletionRiskResult): CompletionRiskGuidance? =
        when (result.level) {
            CompletionRiskLevel.LOW -> null
            CompletionRiskLevel.MEDIUM -> CompletionRiskGuidance(
                level = result.level,
                message = "Offer extra help and review the information with the customer.",
                reasons = result.reasons.toList()
            )
            CompletionRiskLevel.HIGH -> CompletionRiskGuidance(
                level = result.level,
                message = "Pause and help the customer complete the remaining information.",
                reasons = result.reasons.toList()
            )
        }
}

// endregion

// region Process consistency

/**
 * How consistently onboarding sessions follow the standard process.
 *
 * This is deliberately not a model. Two plain counts per completed session:
 *  - steps out of place: the fewest steps that would have to move to put the
 *    session in the playbook's order (the same measure as `out_of_order_steps`
 *    in ml/consistency.py, tested on simulated sessions);
 *  - failed checks: how many answers had to be corrected.
 *
 * A session "followed the standard" when both are small. The limits come from
 * the simulated sessions, not from guesswork: there, the typical completed
 * session has 0 steps out of place and 0 failed checks, 90% have at most 3 and 2,
 * and "at most 2 and at most 2" holds for about 81% of completed sessions. They
 * should be checked again once real sessions exist.
 *
 * In this app the agent cannot choose the order: one question is shown at a
 * time and Next goes to the question the app picks (a correction, the AI's
 * suggestion, then the playbook order). So steps out of place are usually 0,
 * and when they are not they mostly reflect the AI's suggestion, not an agent
 * drifting. Failed checks are the measure that shows how agents differ. The
 * simulated thresholds above assumed agents could pick their own order, as on a
 * paper form, so the order limit is generous for this app.
 */
data class RecordedStep(
    val fieldId: String,
    val passed: Boolean,
    val timestampMillis: Long
)

data class SessionConsistency(
    val stepsOutOfPlace: Int,
    val misplacedFieldIds: List<String>,
    val failedChecks: Int,
    val failedFieldIds: List<String>
) {
    val followedStandard: Boolean
        get() = stepsOutOfPlace <= SessionConsistencyCalculator.MAX_STEPS_OUT_OF_PLACE &&
            failedChecks <= SessionConsistencyCalculator.MAX_FAILED_CHECKS
}

/** One completed session, reduced to what the consistency report needs. */
data class ConsistencyInput(
    val agentId: String,
    val startedAt: Long?,
    val standardOrder: List<String>,
    val steps: List<RecordedStep>
)

data class FieldCount(val fieldId: String, val count: Int)

data class AgentConsistency(
    val agentId: String,
    val sessionCount: Int,
    val followedStandardCount: Int,
    val averageStepsOutOfPlace: Double,
    val averageFailedChecks: Double
) {
    val followedStandardShare: Double
        get() = if (sessionCount == 0) 0.0 else followedStandardCount.toDouble() / sessionCount
}

/** Sessions started [weeksAgo] weeks before the most recent session. */
data class WeeklyConsistency(
    val weeksAgo: Int,
    val sessionCount: Int,
    val followedStandardCount: Int
) {
    val followedStandardShare: Double
        get() = if (sessionCount == 0) 0.0 else followedStandardCount.toDouble() / sessionCount
}

data class ConsistencyReport(
    val sessionCount: Int = 0,
    val followedStandardCount: Int = 0,
    val agents: List<AgentConsistency> = emptyList(),
    val mostMisplacedFields: List<FieldCount> = emptyList(),
    val mostFailedFields: List<FieldCount> = emptyList(),
    val weekly: List<WeeklyConsistency> = emptyList()
) {
    val followedStandardShare: Double
        get() = if (sessionCount == 0) 0.0 else followedStandardCount.toDouble() / sessionCount
}

object SessionConsistencyCalculator {
    const val MAX_STEPS_OUT_OF_PLACE = 2
    const val MAX_FAILED_CHECKS = 2
    const val WEEKS_SHOWN = 8
    const val FIELDS_SHOWN = 3

    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    /**
     * Fields that would have to move to put [fields] in [standardOrder].
     *
     * Keeps the longest run of steps already in standard order (not necessarily
     * next to each other) and returns the rest, so `a, d, b, c` gives `[d]`, not
     * `[b, c]`. When several runs are equally long, one of them is used; the count
     * is the same either way. Repeats are ignored (counted as failed checks or
     * revisits elsewhere), and fields outside the standard order are skipped.
     */
    fun misplacedFields(fields: List<String>, standardOrder: List<String>): List<String> {
        val rank = standardOrder.withIndex().associate { (index, fieldId) -> fieldId to index }
        val ordered = fields.distinct().filter { it in rank }
        if (ordered.isEmpty()) return emptyList()
        val ranks = ordered.map { rank.getValue(it) }

        val longest = IntArray(ranks.size) { 1 }
        val previous = IntArray(ranks.size) { -1 }
        for (position in ranks.indices) {
            for (earlier in 0 until position) {
                if (ranks[earlier] < ranks[position] && longest[earlier] + 1 > longest[position]) {
                    longest[position] = longest[earlier] + 1
                    previous[position] = earlier
                }
            }
        }
        val kept = mutableSetOf<Int>()
        var position = longest.indices.maxByOrNull { longest[it] } ?: return emptyList()
        while (position >= 0) {
            kept += position
            position = previous[position]
        }
        return ordered.filterIndexed { index, _ -> index !in kept }
    }

    fun forSession(steps: List<RecordedStep>, standardOrder: List<String>): SessionConsistency {
        val inTimeOrder = steps.sortedBy { it.timestampMillis }
        val misplaced = misplacedFields(inTimeOrder.map { it.fieldId }, standardOrder)
        val failed = inTimeOrder.filterNot { it.passed }
        return SessionConsistency(
            stepsOutOfPlace = misplaced.size,
            misplacedFieldIds = misplaced,
            failedChecks = failed.size,
            failedFieldIds = failed.map { it.fieldId }
        )
    }

    /** Summary for the admin dashboard. Pass completed sessions only. */
    fun report(sessions: List<ConsistencyInput>): ConsistencyReport {
        val scored = sessions
            .filter { it.steps.isNotEmpty() && it.standardOrder.isNotEmpty() }
            .map { it to forSession(it.steps, it.standardOrder) }
        if (scored.isEmpty()) return ConsistencyReport()

        val agents = scored.groupBy { (input, _) -> input.agentId.ifBlank { "Unknown agent" } }
            .map { (agentId, agentSessions) ->
                val results = agentSessions.map { it.second }
                AgentConsistency(
                    agentId = agentId,
                    sessionCount = results.size,
                    followedStandardCount = results.count { it.followedStandard },
                    averageStepsOutOfPlace = results.map { it.stepsOutOfPlace.toDouble() }.average(),
                    averageFailedChecks = results.map { it.failedChecks.toDouble() }.average()
                )
            }
            // Least consistent first: that is where an administrator looks.
            .sortedWith(
                compareBy<AgentConsistency> { it.followedStandardShare }
                    .thenByDescending { it.sessionCount }
            )

        val latest = scored.mapNotNull { it.first.startedAt }.maxOrNull()
        val weekly = if (latest == null) {
            emptyList()
        } else {
            scored.mapNotNull { (input, result) ->
                input.startedAt?.let { ((latest - it) / WEEK_MILLIS).toInt() to result }
            }
                .filter { (weeksAgo, _) -> weeksAgo in 0 until WEEKS_SHOWN }
                .groupBy({ it.first }, { it.second })
                .map { (weeksAgo, results) ->
                    WeeklyConsistency(
                        weeksAgo = weeksAgo,
                        sessionCount = results.size,
                        followedStandardCount = results.count { it.followedStandard }
                    )
                }
                .sortedByDescending { it.weeksAgo }
        }

        return ConsistencyReport(
            sessionCount = scored.size,
            followedStandardCount = scored.count { it.second.followedStandard },
            agents = agents,
            mostMisplacedFields = topFields(scored.flatMap { it.second.misplacedFieldIds }),
            mostFailedFields = topFields(scored.flatMap { it.second.failedFieldIds }),
            weekly = weekly
        )
    }

    private fun topFields(fieldIds: List<String>): List<FieldCount> =
        fieldIds.groupingBy { it }.eachCount()
            .map { (fieldId, count) -> FieldCount(fieldId, count) }
            .sortedWith(compareByDescending<FieldCount> { it.count }.thenBy { it.fieldId })
            .take(FIELDS_SHOWN)
}

// endregion
