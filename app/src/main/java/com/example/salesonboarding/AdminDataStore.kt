package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.FieldRuleOverride
import com.example.salesonboarding.validation.EmailFormat
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KenyanPhoneFormat
import com.example.salesonboarding.validation.MustBeTrue
import com.example.salesonboarding.validation.NotBlank
import com.example.salesonboarding.validation.PositiveNumber
import com.example.salesonboarding.validation.Required
import com.example.salesonboarding.validation.DateFormatAndMinimumAge
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
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

    companion object {
        fun fromSessions(sessions: Collection<OnboardingSessionMetrics>): OnboardingAnalytics {
            val completed = sessions.filter {
                it.status.equals("completed", ignoreCase = true)
            }
            val durations = completed.mapNotNull { session ->
                val started = session.startedAt
                val finished = session.completedAt
                if (started != null && finished != null && finished >= started) {
                    (finished - started) / 60_000.0
                } else {
                    null
                }
            }
            return OnboardingAnalytics(
                sessionCount = sessions.size,
                completedCount = completed.size,
                averageCompletionMinutes = durations.averageOrZero()
            )
        }
    }
}

data class OnboardingSessionMetrics(
    val status: String,
    val startedAt: Long?,
    val completedAt: Long?
)

enum class AdminRule(val label: String) {
    REQUIRED("Required"),
    NOT_BLANK("Not blank"),
    EMAIL("Email format"),
    PHONE("Kenyan phone"),
    POSITIVE_NUMBER("Positive number"),
    MUST_BE_TRUE("Must be confirmed"),
    DATE("Date, age 18+")
}

interface AdminDataStore {
    fun observeAgents(onResult: (Result<List<SalesAgentAccount>>) -> Unit)
    fun addAgent(email: String, displayName: String, onResult: (Result<Unit>) -> Unit)
    fun setAgentActive(agentId: String, isActive: Boolean, onResult: (Result<Unit>) -> Unit)
    fun loadRuleConfiguration(onResult: (Result<FieldRuleConfiguration>) -> Unit)
    fun saveRequiredRule(fieldId: String, enabled: Boolean, onResult: (Result<Unit>) -> Unit)
    fun addCustomField(
        fieldId: String,
        name: String,
        description: String,
        fieldType: String,
        rules: Set<AdminRule>,
        onResult: (Result<Unit>) -> Unit
    )
    fun loadAnalytics(onResult: (Result<OnboardingAnalytics>) -> Unit)
}

class FirebaseAdminDataStore(
    private val database: DatabaseReference
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
        rules.get().addOnSuccessListener { ruleSnapshot ->
            database.child("fieldDefinitions").get().addOnSuccessListener { fieldsSnapshot ->
                val overrides = ruleSnapshot.children.associate { field ->
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
                val customFields = try {
                    fieldsSnapshot.children.mapNotNull { it.toFormField() }
                } catch (error: IllegalArgumentException) {
                    onResult(
                        Result.failure(
                            IllegalStateException("A saved custom field contains an unknown validation rule.", error)
                        )
                    )
                    return@addOnSuccessListener
                }
                onResult(Result.success(FieldRuleConfiguration(overrides, customFields)))
            }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
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

    override fun addCustomField(
        fieldId: String,
        name: String,
        description: String,
        fieldType: String,
        rules: Set<AdminRule>,
        onResult: (Result<Unit>) -> Unit
    ) {
        val ruleNames = rules.map(AdminRule::name)
        database.child("fieldDefinitions").child(fieldId).runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.value != null) {
                        return Transaction.abort()
                    }
                    currentData.value = mapOf(
                        "fieldId" to fieldId,
                        "fieldName" to name,
                        "description" to description,
                        "fieldType" to fieldType,
                        "rules" to ruleNames
                    )
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    when {
                        error != null -> onResult(Result.failure(error.toException()))
                        !committed -> onResult(
                            Result.failure(IllegalArgumentException("A field with this ID already exists."))
                        )
                        else -> onResult(Result.success(Unit))
                    }
                }
            }
        )
    }

    override fun loadAnalytics(onResult: (Result<OnboardingAnalytics>) -> Unit) {
        sessions.get()
            .addOnSuccessListener { snapshot ->
                val metrics = snapshot.children.map { session ->
                    OnboardingSessionMetrics(
                        status = session.child("status").getValue(String::class.java).orEmpty(),
                        startedAt = session.child("startedAt").value.asLongTimestamp(),
                        completedAt = session.child("completedAt").value.asLongTimestamp()
                    )
                }
                onResult(Result.success(OnboardingAnalytics.fromSessions(metrics)))
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

    private fun DataSnapshot.toFormField(): FormField? {
        val fieldId = child("fieldId").getValue(String::class.java) ?: key ?: return null
        val rules = child("rules").children.map {
            val ruleName = it.getValue(String::class.java)
                ?: throw IllegalArgumentException("A saved custom-field rule is not a string.")
            AdminRule.valueOf(ruleName)
        }.toSet()
        return FormField(
            fieldId = fieldId,
            fieldName = child("fieldName").getValue(String::class.java).orEmpty(),
            fieldType = child("fieldType").getValue(String::class.java).orEmpty(),
            isRequired = rules.any {
                it == AdminRule.REQUIRED ||
                    it == AdminRule.NOT_BLANK ||
                    it == AdminRule.MUST_BE_TRUE
            },
            validationRule = rules.toValidationRules(),
            description = child("description").getValue(String::class.java).orEmpty()
        )
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

internal fun Set<AdminRule>.toValidationRules() = buildList {
    if (AdminRule.REQUIRED in this@toValidationRules) add(Required)
    if (AdminRule.NOT_BLANK in this@toValidationRules) add(NotBlank)
    if (AdminRule.EMAIL in this@toValidationRules) add(EmailFormat)
    if (AdminRule.PHONE in this@toValidationRules) add(KenyanPhoneFormat)
    if (AdminRule.POSITIVE_NUMBER in this@toValidationRules) add(PositiveNumber)
    if (AdminRule.MUST_BE_TRUE in this@toValidationRules) add(MustBeTrue)
    if (AdminRule.DATE in this@toValidationRules) add(DateFormatAndMinimumAge(18))
}

private fun Any?.asLongTimestamp(): Long? = (this as? Number)?.toLong()
