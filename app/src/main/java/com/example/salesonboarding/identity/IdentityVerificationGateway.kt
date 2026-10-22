package com.example.salesonboarding.identity

data class IdentityVerificationRequest(
    val sessionId: String,
    val countryCode: String,
    val documentType: String,
    val documentObjectRef: String,
    val selfieObjectRef: String
)

enum class IdentityCheckStatus {
    NOT_STARTED,
    PENDING,
    PROVIDER_CONFIRMED,
    PROVIDER_NOT_CONFIRMED,
    NEEDS_HUMAN_REVIEW
}

data class IdentityVerificationResult(
    val verificationId: String,
    val liveness: IdentityCheckStatus,
    val faceMatch: IdentityCheckStatus,
    val overallStatus: IdentityCheckStatus
)

interface IdentityVerificationGateway {
    suspend fun startVerification(
        request: IdentityVerificationRequest
    ): Result<IdentityVerificationResult>
}
