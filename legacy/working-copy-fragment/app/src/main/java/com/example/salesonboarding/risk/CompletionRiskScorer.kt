package com.example.salesonboarding.risk

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
            elapsedMinutes = events.sumOf { it.durationMinutes },
            validationErrors = events.count { !it.validationPassed },
            fieldRevisits = events.count { it.isRevisit },
            progressPercent = events.lastOrNull()?.progressPercent ?: 0.0
        )
        return CompletionRiskCalculator.calculate(features)
    }
}
