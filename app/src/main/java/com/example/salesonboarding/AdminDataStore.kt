package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FieldRuleOverride
import com.example.salesonboarding.validation.Required
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import java.util.UUID

data class SalesAgentAccount(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val isActive: Boolean = true
)

data class OnboardingAnalytics(
    val sessionCount: Int = 0,
    val completedCount: Int = 0,
    val averageCompletionMinutes: Double = 0.0
) {
    val completionRate: Double
        get() = if (sessionCount == 0) 0.0 else completedCount.toDouble() / sessionCount
}

interface AdminDataStore {
    fun observeAgents(onResult: (Result<List<SalesAgentAccount>>) -> Unit)
    fun addAgent(email: String, displayName: String, onResult: (Result<Unit>) -> Unit)
    fun setAgentActive(agentId: String, isActive: Boolean, onResult: (Result<Unit>) -> Unit)
    fun loadRuleConfiguration(onResult: (Result<FieldRuleConfiguration>) -> Unit)
    fun saveRequiredRule(fieldId: String, enabled: Boolean, onResult: (Result<Unit>) -> Unit)
    fun loadAnalytics(onResult: (Result<OnboardingAnalytics>) -> Unit)
}

class FirebaseAdminDataStore(
    database: DatabaseReference
) : AdminDataStore {
    private val agents = database.child("salesAgents")
    private val rules = database.child("fieldRuleOverrides")
    private val sessions = database.child("onboardingSessions")

    override fun observeAgents(onResult: (Result<List<SalesAgentAccount>>) -> Unit) {
        agents.get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.children.mapNotNull { it.toAgent() }))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun addAgent(
        email: String,
        displayName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val id = UUID.randomUUID().toString()
        agents.child(id).setValue(
            mapOf(
                "id" to id,
                "email" to email,
                "displayName" to displayName,
                "isActive" to true
            )
        ).addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun setAgentActive(
        agentId: String,
        isActive: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        agents.child(agentId).child("isActive").setValue(isActive)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun loadRuleConfiguration(onResult: (Result<FieldRuleConfiguration>) -> Unit) {
        rules.get()
            .addOnSuccessListener { snapshot ->
                val overrides = snapshot.children.associate { field ->
                    val required = field.child("required").getValue(Boolean::class.java) == true
                    field.key.orEmpty() to FieldRuleOverride(
                        addRules = if (required) {
                            listOf(Required)
                        } else {
                            emptyList()
                        },
                        removeRules = if (required) {
                            emptyList()
                        } else {
                            listOf(Required)
                        }
                    )
                }
                onResult(Result.success(FieldRuleConfiguration(overrides)))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun saveRequiredRule(
        fieldId: String,
        enabled: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        rules.child(fieldId).child("required").setValue(enabled)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun loadAnalytics(onResult: (Result<OnboardingAnalytics>) -> Unit) {
        sessions.get()
            .addOnSuccessListener { snapshot ->
                val sessionSnapshots = snapshot.children.toList()
                val completed = sessionSnapshots.count {
                    it.child("status").getValue(String::class.java) == "completed"
                }
                val durations = sessionSnapshots.mapNotNull { session ->
                    val started = session.child("startedAt").getValue(Long::class.java)
                    val finished = session.child("completedAt").getValue(Long::class.java)
                    if (started != null && finished != null && finished >= started) {
                        (finished - started) / 60_000.0
                    } else {
                        null
                    }
                }
                onResult(
                    Result.success(
                        OnboardingAnalytics(
                            sessionCount = sessionSnapshots.size,
                            completedCount = completed,
                            averageCompletionMinutes = durations.averageOrZero()
                        )
                    )
                )
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun DataSnapshot.toAgent(): SalesAgentAccount? {
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        return SalesAgentAccount(
            id = id,
            email = child("email").getValue(String::class.java).orEmpty(),
            displayName = child("displayName").getValue(String::class.java).orEmpty(),
            isActive = child("isActive").getValue(Boolean::class.java) ?: true
        )
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
