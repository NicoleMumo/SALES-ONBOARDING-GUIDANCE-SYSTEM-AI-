package com.example.salesonboarding

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
