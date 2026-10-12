package com.example.salesonboarding.risk

data class CompletionRiskFeatures(
    val elapsedMinutes: Double,
    val validationErrors: Int,
    val fieldRevisits: Int,
    val progressPercent: Double
) {
    init {
        require(elapsedMinutes >= 0) { "Elapsed minutes cannot be negative." }
        require(validationErrors >= 0) { "Validation errors cannot be negative." }
        require(fieldRevisits >= 0) { "Field revisits cannot be negative." }
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
)

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
        if (features.elapsedMinutes > LONG_EVENT_THRESHOLD_MINUTES) {
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
