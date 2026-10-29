package com.example.salesonboarding

import org.json.JSONObject
import kotlin.math.exp

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
