package com.example.salesonboarding

import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import android.util.Base64

data class CompanyMembership(
    val companyId: String,
    val email: String,
    val displayName: String,
    val isActive: Boolean
)

data class SalesAgentAccount(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val isActive: Boolean = true,
    val isRegistered: Boolean = false
)

data class OnboardingAnalytics(
    val sessionCount: Int = 0,
    val completedCount: Int = 0,
    val averageCompletionMinutes: Double = 0.0,
    val averageReadinessPercent: Double = 0.0,
    val commonPausePoint: String? = null,
    val agentPerformance: List<AgentPerformance> = emptyList(),
    val consistency: ConsistencyReport = ConsistencyReport(),
    val fieldNames: Map<String, String> = emptyMap()
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
                    (finished.toDouble() - started.toDouble()) / 60_000.0
                } else {
                    null
                }
            }
            val readiness = sessions.mapNotNull { session ->
                val requiredFields = session.requiredFieldIds.ifEmpty { session.playbookFieldIds }
                if (requiredFields.isEmpty()) return@mapNotNull null
                requiredFields.count { session.validation[it] == true }.toDouble() /
                    requiredFields.size
            }
            val pausePoints = sessions.filterNot {
                it.status.equals("completed", ignoreCase = true)
            }.mapNotNull { session ->
                val requiredFields = session.requiredFieldIds.ifEmpty { session.playbookFieldIds }
                requiredFields.firstOrNull { session.validation[it] != true }
                    ?.let { fieldId -> session.fieldNames[fieldId] ?: fieldId }
            }
            return OnboardingAnalytics(
                sessionCount = sessions.size,
                completedCount = completed.size,
                averageCompletionMinutes = durations.averageOrZero(),
                averageReadinessPercent = readiness.averageOrZero() * 100,
                commonPausePoint = pausePoints.groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }?.key,
                agentPerformance = sessions.groupBy { it.agentId.ifBlank { "Unknown agent" } }
                    .map { (agentId, agentSessions) ->
                        val agentCompleted = agentSessions.count {
                            it.status.equals("completed", ignoreCase = true)
                        }
                        AgentPerformance(
                            agentId = agentId,
                            sessionCount = agentSessions.size,
                            completedCount = agentCompleted
                        )
                    }
                    .sortedByDescending(AgentPerformance::sessionCount),
                consistency = SessionConsistencyCalculator.report(
                    completed.map { session ->
                        ConsistencyInput(
                            agentId = session.agentId,
                            startedAt = session.startedAt,
                            standardOrder = session.playbookFieldIds,
                            steps = session.steps,
                            checksEachAttempt = session.checksEachAttempt
                        )
                    }
                ),
                fieldNames = buildMap { sessions.forEach { putAll(it.fieldNames) } }
            )
        }
    }
}

data class AgentPerformance(
    val agentId: String,
    val sessionCount: Int,
    val completedCount: Int
) {
    val completionRate: Double
        get() = if (sessionCount == 0) 0.0 else completedCount.toDouble() / sessionCount
}

data class OnboardingSessionMetrics(
    val status: String,
    val startedAt: Long?,
    val completedAt: Long?,
    val agentId: String = "",
    val validation: Map<String, Boolean> = emptyMap(),
    val playbookFieldIds: List<String> = emptyList(),
    val requiredFieldIds: List<String> = emptyList(),
    val fieldNames: Map<String, String> = emptyMap(),
    /** Answered steps in the order they happened, from the session's saved events. */
    val steps: List<RecordedStep> = emptyList(),
    /** False for sessions saved before answers were checked on leaving the box. */
    val checksEachAttempt: Boolean = true
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
    fun loadAgentMembership(
        agentId: String,
        email: String,
        onResult: (Result<CompanyMembership?>) -> Unit
    )
    fun enrollAgent(
        companyId: String,
        agentId: String,
        email: String,
        onResult: (Result<Unit>) -> Unit
    )
    fun observeAgents(onResult: (Result<List<SalesAgentAccount>>) -> Unit)
    fun addAgent(email: String, displayName: String, onResult: (Result<Unit>) -> Unit)
    fun setAgentActive(agentId: String, isActive: Boolean, onResult: (Result<Unit>) -> Unit)
    fun loadCompanyIndustry(companyId: String, onResult: (Result<String?>) -> Unit)
    fun saveCompanyIndustry(industry: String, onResult: (Result<Unit>) -> Unit)
    fun loadRuleConfiguration(onResult: (Result<FieldRuleConfiguration>) -> Unit)
    fun saveRequiredRule(fieldId: String, enabled: Boolean, onResult: (Result<Unit>) -> Unit)
    fun addCustomField(
        fieldId: String,
        name: String,
        description: String,
        fieldType: String,
        rules: Set<AdminRule>,
        choices: Set<String>,
        onResult: (Result<Unit>) -> Unit
    )
    /** Replaces the name, instructions, type, checks and choices of one of the company's own questions. */
    fun updateCustomField(
        fieldId: String,
        name: String,
        description: String,
        fieldType: String,
        rules: Set<AdminRule>,
        choices: Set<String>,
        onResult: (Result<Unit>) -> Unit
    )
    /** Removes one of the company's own questions from new sessions; saved sessions keep their copy. */
    fun deleteCustomField(fieldId: String, onResult: (Result<Unit>) -> Unit)
    fun loadAnalytics(onResult: (Result<OnboardingAnalytics>) -> Unit)
    fun loadSessions(onResult: (Result<List<StoredOnboardingSession>>) -> Unit)
}

class FirebaseAdminDataStore(
    private val database: DatabaseReference,
    private val companyId: String = ""
) : AdminDataStore {
    private val companyRoot: DatabaseReference
        get() = database.child("companies").child(companyId)
    private val agents: DatabaseReference
        get() = companyRoot.child("agentInvitations")
    private val rules: DatabaseReference
        get() = companyRoot.child("fieldRuleOverrides")
    private val fields: DatabaseReference
        get() = companyRoot.child("fieldDefinitions")
    private val sessions: DatabaseReference
        get() = companyRoot.child("onboardingSessions")

    override fun loadAgentMembership(
        agentId: String,
        email: String,
        onResult: (Result<CompanyMembership?>) -> Unit
    ) {
        if (agentId.isBlank() || email.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Sign in with your invited work email.")))
            return
        }
        database.child("companyMemberships").child(agentId).get()
            .addOnSuccessListener { snapshot ->
                val membershipEmail = snapshot.child("email").getValue(String::class.java)
                    ?.trim()?.lowercase(java.util.Locale.ROOT)
                val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
                val membership = if (membershipEmail == normalizedEmail) {
                    CompanyMembership(
                        companyId = snapshot.child("companyId").getValue(String::class.java).orEmpty(),
                        email = membershipEmail,
                        displayName = snapshot.child("displayName").getValue(String::class.java).orEmpty(),
                        isActive = snapshot.child("isActive").getValue(Boolean::class.java) == true
                    )
                } else {
                    null
                }
                onResult(Result.success(membership))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun enrollAgent(
        companyId: String,
        agentId: String,
        email: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val normalizedCompanyId = companyId.trim()
        val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
        if (!COMPANY_ID_PATTERN.matches(normalizedCompanyId)) {
            onResult(Result.failure(IllegalArgumentException("Enter the company ID provided by your administrator.")))
            return
        }
        if (agentId.isBlank() || normalizedEmail.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("The signed-in agent account is unavailable.")))
            return
        }
        val emailKey = emailKey(normalizedEmail)
        val invitation = database.child("companies").child(normalizedCompanyId)
            .child("agentInvitations").child(emailKey)
        invitation.get()
            .addOnSuccessListener { snapshot ->
                val invitedEmail = snapshot.child("email").getValue(String::class.java)
                    ?.trim()?.lowercase(java.util.Locale.ROOT)
                if (!snapshot.exists() || invitedEmail != normalizedEmail ||
                    snapshot.child("isActive").getValue(Boolean::class.java) != true
                ) {
                    onResult(
                        Result.failure(
                            IllegalArgumentException("That email is not actively invited to this company.")
                        )
                    )
                    return@addOnSuccessListener
                }
                database.child("companyMemberships").child(agentId).setValue(
                    mapOf(
                        "companyId" to normalizedCompanyId,
                        "email" to normalizedEmail,
                        "displayName" to snapshot.child("displayName").getValue(String::class.java).orEmpty(),
                        "isActive" to true,
                        "invitationKey" to emailKey
                    )
                ).addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun observeAgents(onResult: (Result<List<SalesAgentAccount>>) -> Unit) {
        if (!isConfigured(onResult)) return
        database.child("companyMemberships").orderByChild("companyId").equalTo(companyId).get()
            .addOnSuccessListener { memberships ->
                agents.get()
                    .addOnSuccessListener { invitations ->
                        val registered = memberships.children.mapNotNull { it.toMembershipAgent() }
                        val registeredEmails = registered.mapTo(mutableSetOf()) {
                            it.email.lowercase(java.util.Locale.ROOT)
                        }
                        val pending = invitations.children.mapNotNull { it.toInvitationAgent() }
                            .filterNot { it.email.lowercase(java.util.Locale.ROOT) in registeredEmails }
                        onResult(Result.success((registered + pending).sortedBy { it.email }))
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun loadCompanyIndustry(
        companyId: String,
        onResult: (Result<String?>) -> Unit
    ) {
        if (!COMPANY_ID_PATTERN.matches(companyId)) {
            onResult(Result.failure(IllegalArgumentException("A valid company ID is required.")))
            return
        }
        database.child("companies").child(companyId).child("settings").child("industry").get()
            .addOnSuccessListener { snapshot ->
                val industry = snapshot.getValue(String::class.java)
                if (industry == null || IndustrySelectionValidator.validate(industry) == null) {
                    onResult(Result.success(industry))
                } else {
                    onResult(Result.failure(IllegalStateException("The company has an unsupported industry setting.")))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun saveCompanyIndustry(
        industry: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!isConfigured(onResult)) return
        if (IndustrySelectionValidator.validate(industry) != null) {
            onResult(Result.failure(IllegalArgumentException("Choose a supported company industry.")))
            return
        }
        companyRoot.child("settings").child("industry").setValue(industry)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun addAgent(
        email: String,
        displayName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!isConfigured(onResult)) return
        val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
        if (normalizedEmail.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Enter the agent's email address.")))
            return
        }
        val key = emailKey(normalizedEmail)
        agents.child(key).runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.value != null) return Transaction.abort()
                    currentData.value = mapOf(
                        "email" to normalizedEmail,
                        "displayName" to displayName.trim(),
                        "isActive" to true
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
                            Result.failure(IllegalArgumentException("An invitation already exists for this email."))
                        )
                        else -> onResult(Result.success(Unit))
                    }
                }
            }
        )
    }

    override fun setAgentActive(
        agentId: String,
        isActive: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!isConfigured(onResult)) return
        if (agentId.startsWith(INVITATION_ID_PREFIX)) {
            agents.child(agentId.removePrefix(INVITATION_ID_PREFIX)).child("isActive")
                .setValue(isActive)
                .addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        } else {
            database.child("companyMemberships").child(agentId).get()
                .addOnSuccessListener { membership ->
                    if (membership.child("companyId").getValue(String::class.java) != companyId) {
                        onResult(Result.failure(IllegalArgumentException("This agent does not belong to your company.")))
                        return@addOnSuccessListener
                    }
                    val invitationKey = membership.child("invitationKey").getValue(String::class.java)
                    val updates = mutableMapOf<String, Any?>(
                        "/companyMemberships/$agentId/isActive" to isActive
                    )
                    if (!invitationKey.isNullOrBlank()) {
                        updates["/companies/$companyId/agentInvitations/$invitationKey/isActive"] = isActive
                    }
                    database.updateChildren(updates)
                        .addOnSuccessListener { onResult(Result.success(Unit)) }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
    }

    override fun loadRuleConfiguration(onResult: (Result<FieldRuleConfiguration>) -> Unit) {
        if (!isConfigured(onResult)) return
        rules.get().addOnSuccessListener { ruleSnapshot ->
            fields.get().addOnSuccessListener { fieldsSnapshot ->
                val overrides = ruleSnapshot.children.associate { field ->
                    val required = field.child("required").getValue(Boolean::class.java) == true
                    field.key.orEmpty() to FieldRuleOverride(
                        addRules = if (required) {
                            listOf(Required, NotBlank)
                        } else {
                            emptyList()
                        },
                        removeRules = if (required) {
                            emptyList()
                        } else {
                            listOf(Required, NotBlank)
                        }
                    )
                }
                val customFields = try {
                    fieldsSnapshot.children.mapNotNull { it.toFormField() }
                } catch (error: IllegalArgumentException) {
                    onResult(
                        Result.failure(
                            IllegalStateException("A saved custom field contains invalid configuration.", error)
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
        if (!isConfigured(onResult)) return
        if (!isValidFieldId(fieldId, onResult)) return
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
        choices: Set<String>,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!isConfigured(onResult)) return
        if (!isValidFieldId(fieldId, onResult)) return
        val ruleNames = rules.map(AdminRule::name)
        fields.child(fieldId).runTransaction(
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
                        "rules" to ruleNames,
                        "choices" to choices.toList()
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

    override fun updateCustomField(
        fieldId: String,
        name: String,
        description: String,
        fieldType: String,
        rules: Set<AdminRule>,
        choices: Set<String>,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!isConfigured(onResult)) return
        if (!isValidFieldId(fieldId, onResult)) return
        fields.child(fieldId).setValue(
            mapOf(
                "fieldId" to fieldId,
                "fieldName" to name,
                "description" to description,
                "fieldType" to fieldType,
                "rules" to rules.map(AdminRule::name),
                "choices" to choices.toList()
            )
        )
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun deleteCustomField(fieldId: String, onResult: (Result<Unit>) -> Unit) {
        if (!isConfigured(onResult)) return
        if (!isValidFieldId(fieldId, onResult)) return
        fields.child(fieldId).removeValue()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun loadAnalytics(onResult: (Result<OnboardingAnalytics>) -> Unit) {
        if (!isConfigured(onResult)) return
        sessions.orderByChild("startedAt").get()
            .addOnSuccessListener { snapshot ->
                val metrics = snapshot.children.map { session ->
                    val playbookFields = session.child("playbookSnapshot").children
                    val validation = session.child("validation").children.mapNotNull { field ->
                        field.child("isValid").getValue(Boolean::class.java)?.let { valid ->
                            field.key?.let { it to valid }
                        }
                    }.toMap()
                    OnboardingSessionMetrics(
                        status = session.child("status").getValue(String::class.java).orEmpty(),
                        startedAt = session.child("startedAt").value.asLongTimestamp(),
                        completedAt = session.child("completedAt").value.asLongTimestamp(),
                        agentId = session.child("agentEmail").getValue(String::class.java)
                            ?.takeIf(String::isNotBlank)
                            ?: session.child("agentId").getValue(String::class.java).orEmpty(),
                        validation = validation,
                        playbookFieldIds = playbookFields.mapNotNull {
                            it.child("fieldId").getValue(String::class.java)
                        },
                        requiredFieldIds = playbookFields.mapNotNull { field ->
                            if (field.child("isRequired").getValue(Boolean::class.java) == true) {
                                field.child("fieldId").getValue(String::class.java)
                            } else {
                                null
                            }
                        },
                        fieldNames = playbookFields.mapNotNull { field ->
                            val fieldId = field.child("fieldId").getValue(String::class.java)
                            val fieldName = field.child("fieldName").getValue(String::class.java)
                            if (fieldId != null && fieldName != null) fieldId to fieldName else null
                        }.toMap(),
                        steps = session.child("events").children.mapNotNull(DataSnapshot::toRecordedStep),
                        checksEachAttempt = session.child("answerChecks")
                            .getValue(String::class.java) == ANSWER_CHECKS_ON_LEAVE
                    )
                }
                onResult(Result.success(OnboardingAnalytics.fromSessions(metrics)))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun loadSessions(onResult: (Result<List<StoredOnboardingSession>>) -> Unit) {
        if (!isConfigured(onResult)) return
        sessions.orderByChild("startedAt").get()
            .addOnSuccessListener { snapshot ->
                onResult(
                    Result.success(
                        snapshot.children.mapNotNull(DataSnapshot::toStoredOnboardingSession)
                            .sortedByDescending { it.startedAt ?: 0L }
                    )
                )
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun DataSnapshot.toMembershipAgent(): SalesAgentAccount? {
        val id = key ?: return null
        return SalesAgentAccount(
            id = id,
            email = child("email").getValue(String::class.java).orEmpty(),
            displayName = child("displayName").getValue(String::class.java).orEmpty(),
            isActive = child("isActive").getValue(Boolean::class.java) == true,
            isRegistered = true
        )
    }

    private fun DataSnapshot.toInvitationAgent(): SalesAgentAccount? {
        val key = key ?: return null
        val email = child("email").getValue(String::class.java) ?: return null
        return SalesAgentAccount(
            id = "$INVITATION_ID_PREFIX$key",
            email = email,
            displayName = child("displayName").getValue(String::class.java).orEmpty(),
            isActive = child("isActive").getValue(Boolean::class.java) == true,
            isRegistered = false
        )
    }

    private fun <T> isConfigured(onResult: (Result<T>) -> Unit): Boolean {
        if (COMPANY_ID_PATTERN.matches(companyId)) return true
        onResult(Result.failure(IllegalStateException("A trusted company ID is required for this action.")))
        return false
    }

    private fun <T> isValidFieldId(
        fieldId: String,
        onResult: (Result<T>) -> Unit
    ): Boolean {
        if (FormField.isValidId(fieldId)) return true
        onResult(
            Result.failure(
                IllegalArgumentException("Field IDs must use lowercase letters, digits, and underscores.")
            )
        )
        return false
    }

    companion object {
        private const val INVITATION_ID_PREFIX = "invite_"

        private fun emailKey(email: String): String =
            Base64.encodeToString(email.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun DataSnapshot.toFormField(): FormField? {
        val fieldId = child("fieldId").getValue(String::class.java) ?: key ?: return null
        require(FormField.isValidId(fieldId)) { "A saved custom field has an invalid ID." }
        val rules = child("rules").children.map {
            val ruleName = it.getValue(String::class.java)
                ?: throw IllegalArgumentException("A saved custom-field rule is not a string.")
            AdminRule.valueOf(ruleName)
        }.toSet()
        val choices = child("choices").children.mapNotNull {
            it.getValue(String::class.java)?.takeIf(String::isNotBlank)
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
            validationRules = rules.toValidationRules() +
                if (choices.isEmpty()) emptyList() else listOf(OneOf(choices)),
            description = child("description").getValue(String::class.java).orEmpty()
        )
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

/**
 * One saved session event as an answered step. The app saves an event each time
 * an answer is checked (see `OnboardingFormController`); other event types and
 * incomplete events are ignored rather than guessed at.
 */
private fun DataSnapshot.toRecordedStep(): RecordedStep? {
    val passed = when (child("type").getValue(String::class.java)) {
        "field_validation_passed" -> true
        "field_validation_failed" -> false
        else -> return null
    }
    val fieldId = child("fieldId").getValue(String::class.java) ?: return null
    val timestamp = child("timestamp").value.asLongTimestamp() ?: return null
    return RecordedStep(fieldId, passed, timestamp)
}

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
