package com.example.salesonboarding

import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import android.util.Base64

/** One company a sales agent works for. An agent can work for several. */
data class CompanyMembership(
    val companyId: String,
    val email: String,
    val displayName: String,
    val isActive: Boolean,
    /** The name the company's admin gave it; its ID until one is set. */
    val companyName: String = companyId
)

/** What a company tells its sales agents when they choose it: its name and the admin's message. */
data class CompanyBrief(val name: String = "", val message: String = "")

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
    val checksEachAttempt: Boolean = true,
    val sessionId: String = "",
    /** The sales agent's account ID; [agentId] holds their email when it was saved. */
    val agentUid: String = "",
    val industry: String = "",
    val context: String = "",
    val consentAt: Long? = null
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
    /** Every company the agent has joined, switched-off ones included, by company name. */
    fun loadAgentCompanies(
        agentId: String,
        email: String,
        onResult: (Result<List<CompanyMembership>>) -> Unit
    )
    fun enrollAgent(
        companyId: String,
        agentId: String,
        email: String,
        onResult: (Result<Unit>) -> Unit
    )
    /** The companies that invited [email] and have not switched the invitation off. */
    fun loadInvitedCompanies(email: String, onResult: (Result<List<String>>) -> Unit) {
        onResult(Result.success(emptyList()))
    }

    /** Whether [email] has an active invitation to [companyId], without joining the company. */
    fun isInvited(companyId: String, email: String, onResult: (Result<Boolean>) -> Unit) {
        onResult(Result.failure(UnsupportedOperationException("Invitations cannot be checked here.")))
    }
    fun observeAgents(onResult: (Result<List<SalesAgentAccount>>) -> Unit)
    fun addAgent(email: String, displayName: String, onResult: (Result<Unit>) -> Unit)
    fun setAgentActive(agentId: String, isActive: Boolean, onResult: (Result<Unit>) -> Unit)
    fun loadCompanyIndustry(companyId: String, onResult: (Result<String?>) -> Unit)
    fun saveCompanyIndustry(industry: String, onResult: (Result<Unit>) -> Unit)

    /** Whether the company allows anonymised steps to improve the system; off unless set. */
    fun loadSharesForTraining(companyId: String, onResult: (Result<Boolean>) -> Unit) {
        onResult(Result.success(false))
    }

    fun saveSharesForTraining(enabled: Boolean, onResult: (Result<Unit>) -> Unit) {
        onResult(Result.failure(UnsupportedOperationException("This setting cannot be changed here.")))
    }

    /** The company's name and its message to agents; empty when the admin has not set them. */
    fun loadCompanyBrief(companyId: String, onResult: (Result<CompanyBrief>) -> Unit) {
        onResult(Result.success(CompanyBrief()))
    }

    fun saveCompanyBrief(brief: CompanyBrief, onResult: (Result<Unit>) -> Unit) {
        onResult(Result.failure(UnsupportedOperationException("This setting cannot be changed here.")))
    }
    fun loadRuleConfiguration(onResult: (Result<FieldRuleConfiguration>) -> Unit)
    fun saveRequiredRule(fieldId: String, enabled: Boolean, onResult: (Result<Unit>) -> Unit)

    /** Puts a standard question in the form, or takes it out. Compliance questions always stay. */
    fun saveFieldShown(fieldId: String, shown: Boolean, onResult: (Result<Unit>) -> Unit) {
        onResult(Result.failure(UnsupportedOperationException("This setting cannot be changed here.")))
    }
    /** Adds one of the company's own questions, asked right after [placeAfter] (null: at the end). */
    fun addCustomField(
        fieldId: String,
        name: String,
        description: String,
        fieldType: String,
        rules: Set<AdminRule>,
        choices: Set<String>,
        placeAfter: String?,
        onResult: (Result<Unit>) -> Unit
    )
    /** Replaces the name, instructions, type, checks, choices and place of one of the company's own questions. */
    fun updateCustomField(
        fieldId: String,
        name: String,
        description: String,
        fieldType: String,
        rules: Set<AdminRule>,
        choices: Set<String>,
        placeAfter: String?,
        onResult: (Result<Unit>) -> Unit
    )
    /** Removes one of the company's own questions from new sessions; saved sessions keep their copy. */
    fun deleteCustomField(fieldId: String, onResult: (Result<Unit>) -> Unit)
    fun loadAnalytics(onResult: (Result<OnboardingAnalytics>) -> Unit)
    fun loadSessions(onResult: (Result<List<StoredOnboardingSession>>) -> Unit)

    /** Every session of the company, reduced to its checks and times, for the CSV exports. */
    fun loadSessionMetrics(onResult: (Result<List<OnboardingSessionMetrics>>) -> Unit) {
        onResult(Result.failure(UnsupportedOperationException("Exporting sessions is not available here.")))
    }
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

    override fun loadAgentCompanies(
        agentId: String,
        email: String,
        onResult: (Result<List<CompanyMembership>>) -> Unit
    ) {
        val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
        if (agentId.isBlank() || normalizedEmail.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Sign in with your invited work email.")))
            return
        }
        database.child("agentCompanies").child(agentId).get()
            .addOnSuccessListener { list ->
                val joined = list.children.mapNotNull { it.key }.filter(COMPANY_ID_PATTERN::matches)
                // Before agents could work for several companies, their one company was kept in
                // companyMemberships. An agent still listed only there is moved over once.
                database.child("companyMemberships").child(agentId).get()
                    .addOnSuccessListener { old ->
                        val oldCompany = old.child("companyId").getValue(String::class.java)
                        val oldEmail = old.child("email").getValue(String::class.java)?.trim()?.lowercase(java.util.Locale.ROOT)
                        val moveOver = oldCompany != null && COMPANY_ID_PATTERN.matches(oldCompany) &&
                            oldCompany !in joined && oldEmail == normalizedEmail &&
                            old.child("isActive").getValue(Boolean::class.java) == true
                        if (moveOver) {
                            enrollAgent(oldCompany!!, agentId, normalizedEmail) { moved ->
                                loadMemberships(agentId, normalizedEmail, joined + listOfNotNull(oldCompany.takeIf { moved.isSuccess }), onResult)
                            }
                        } else {
                            loadMemberships(agentId, normalizedEmail, joined, onResult)
                        }
                    }
                    .addOnFailureListener { loadMemberships(agentId, normalizedEmail, joined, onResult) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Reads the agent's membership in each company, and the name of each company they are active in. */
    private fun loadMemberships(
        agentId: String,
        email: String,
        companyIds: List<String>,
        onResult: (Result<List<CompanyMembership>>) -> Unit
    ) {
        if (companyIds.isEmpty()) {
            onResult(Result.success(emptyList()))
            return
        }
        val found = mutableListOf<CompanyMembership>()
        var waiting = companyIds.size
        var failure: Exception? = null
        fun done() {
            waiting--
            if (waiting > 0) return
            val error = failure
            if (found.isEmpty() && error != null) onResult(Result.failure(error))
            else onResult(Result.success(found.sortedBy { it.companyName.lowercase(java.util.Locale.ROOT) }))
        }
        companyIds.distinct().also { waiting = it.size }.forEach { companyId ->
            val company = database.child("companies").child(companyId)
            company.child("members").child(agentId).get()
                .addOnSuccessListener { member ->
                    val memberEmail = member.child("email").getValue(String::class.java)?.trim()?.lowercase(java.util.Locale.ROOT)
                    if (!member.exists() || memberEmail != email) {
                        done()
                        return@addOnSuccessListener
                    }
                    val membership = CompanyMembership(
                        companyId = companyId,
                        email = memberEmail,
                        displayName = member.child("displayName").getValue(String::class.java).orEmpty(),
                        isActive = member.child("isActive").getValue(Boolean::class.java) == true
                    )
                    if (!membership.isActive) {
                        found += membership
                        done()
                        return@addOnSuccessListener
                    }
                    company.child("settings").child("displayName").get()
                        .addOnSuccessListener { name ->
                            found += membership.copy(
                                companyName = name.getValue(String::class.java)?.trim()?.takeIf(String::isNotEmpty) ?: companyId
                            )
                            done()
                        }
                        .addOnFailureListener {
                            found += membership
                            done()
                        }
                }
                .addOnFailureListener {
                    failure = it
                    done()
                }
        }
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
                // The membership and the agent's own list of companies are written together.
                database.updateChildren(
                    mapOf(
                        "/companies/$normalizedCompanyId/members/$agentId" to mapOf(
                            "email" to normalizedEmail,
                            "displayName" to snapshot.child("displayName").getValue(String::class.java).orEmpty(),
                            "isActive" to true,
                            "invitationKey" to emailKey
                        ),
                        "/agentCompanies/$agentId/$normalizedCompanyId" to true
                    )
                ).addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun loadInvitedCompanies(email: String, onResult: (Result<List<String>>) -> Unit) {
        val key = InvitationIndex.key(email)
        if (key == null) {
            onResult(Result.success(emptyList()))
            return
        }
        database.child("invitationsByEmail").child(key).get()
            .addOnSuccessListener { invited ->
                onResult(
                    Result.success(
                        invited.children
                            .filter { it.getValue(Boolean::class.java) == true }
                            .mapNotNull { it.key }
                            .filter(COMPANY_ID_PATTERN::matches)
                    )
                )
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun isInvited(companyId: String, email: String, onResult: (Result<Boolean>) -> Unit) {
        val normalizedCompanyId = companyId.trim()
        val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
        if (!COMPANY_ID_PATTERN.matches(normalizedCompanyId) || normalizedEmail.isBlank()) {
            onResult(Result.success(false))
            return
        }
        database.child("companies").child(normalizedCompanyId)
            .child("agentInvitations").child(emailKey(normalizedEmail)).get()
            .addOnSuccessListener { snapshot ->
                val invitedEmail = snapshot.child("email").getValue(String::class.java)
                    ?.trim()?.lowercase(java.util.Locale.ROOT)
                onResult(
                    Result.success(
                        snapshot.exists() && invitedEmail == normalizedEmail &&
                            snapshot.child("isActive").getValue(Boolean::class.java) == true
                    )
                )
            }
            // The rules refuse to show an invitation that is not this person's: not invited.
            .addOnFailureListener { onResult(Result.success(false)) }
    }

    override fun observeAgents(onResult: (Result<List<SalesAgentAccount>>) -> Unit) {
        if (!isConfigured(onResult)) return
        companyRoot.child("members").get()
            .addOnSuccessListener { memberships ->
                agents.get()
                    .addOnSuccessListener { invitations ->
                        val registered = memberships.children.mapNotNull { it.toMembershipAgent() }
                        val registeredEmails = registered.mapTo(mutableSetOf()) {
                            it.email.lowercase(java.util.Locale.ROOT)
                        }
                        val pending = invitations.children.mapNotNull { it.toInvitationAgent() }
                            .filterNot { it.email.lowercase(java.util.Locale.ROOT) in registeredEmails }
                        fileInvitationsByEmail(invitations)
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

    override fun loadSharesForTraining(companyId: String, onResult: (Result<Boolean>) -> Unit) {
        if (!COMPANY_ID_PATTERN.matches(companyId)) {
            onResult(Result.failure(IllegalArgumentException("A valid company ID is required.")))
            return
        }
        database.child("companies").child(companyId).child("settings").child("shareForTraining").get()
            .addOnSuccessListener { onResult(Result.success(it.getValue(Boolean::class.java) == true)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun saveSharesForTraining(enabled: Boolean, onResult: (Result<Unit>) -> Unit) {
        if (!isConfigured(onResult)) return
        companyRoot.child("settings").child("shareForTraining").setValue(enabled)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun loadCompanyBrief(companyId: String, onResult: (Result<CompanyBrief>) -> Unit) {
        if (!COMPANY_ID_PATTERN.matches(companyId)) {
            onResult(Result.failure(IllegalArgumentException("A valid company ID is required.")))
            return
        }
        database.child("companies").child(companyId).child("settings").get()
            .addOnSuccessListener { settings ->
                onResult(
                    Result.success(
                        CompanyBrief(
                            name = settings.child("displayName").getValue(String::class.java).orEmpty().trim(),
                            message = settings.child("agentBrief").getValue(String::class.java).orEmpty().trim()
                        )
                    )
                )
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun saveCompanyBrief(brief: CompanyBrief, onResult: (Result<Unit>) -> Unit) {
        if (!isConfigured(onResult)) return
        val name = brief.name.trim()
        val message = brief.message.trim()
        if (name.length > CompanyBriefLimits.NAME || message.length > CompanyBriefLimits.MESSAGE) {
            onResult(Result.failure(IllegalArgumentException("The name or the message is too long.")))
            return
        }
        companyRoot.child("settings").updateChildren(mapOf("displayName" to name, "agentBrief" to message))
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
                        else -> fileInvitation(normalizedEmail, true) { onResult(it) }
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
            val invitation = agents.child(agentId.removePrefix(INVITATION_ID_PREFIX))
            invitation.child("isActive").setValue(isActive)
                .addOnSuccessListener {
                    invitation.child("email").get()
                        .addOnSuccessListener { email ->
                            val address = email.getValue(String::class.java)
                            if (address == null) onResult(Result.success(Unit)) else fileInvitation(address, isActive, onResult)
                        }
                        .addOnFailureListener { onResult(Result.success(Unit)) }
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
        } else {
            companyRoot.child("members").child(agentId).get()
                .addOnSuccessListener { membership ->
                    if (!membership.exists()) {
                        onResult(Result.failure(IllegalArgumentException("This agent does not belong to your company.")))
                        return@addOnSuccessListener
                    }
                    val invitationKey = membership.child("invitationKey").getValue(String::class.java)
                    // Only this company's access changes; the agent keeps any other company.
                    val updates = mutableMapOf<String, Any?>(
                        "/companies/$companyId/members/$agentId/isActive" to isActive
                    )
                    if (!invitationKey.isNullOrBlank()) {
                        updates["/companies/$companyId/agentInvitations/$invitationKey/isActive"] = isActive
                    }
                    membership.child("email").getValue(String::class.java)?.let(InvitationIndex::key)?.let { key ->
                        updates["/invitationsByEmail/$key/$companyId"] = isActive
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
                // Only a saved required setting changes a question; "hidden" alone does not.
                val overrides = ruleSnapshot.children.filter { it.hasChild("required") }.associate { field ->
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
                val hidden = ruleSnapshot.children
                    .filter { it.child("hidden").getValue(Boolean::class.java) == true }
                    .mapNotNull { it.key }
                    .toSet()
                onResult(Result.success(FieldRuleConfiguration(overrides, customFields, hiddenFieldIds = hidden)))
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

    override fun saveFieldShown(fieldId: String, shown: Boolean, onResult: (Result<Unit>) -> Unit) {
        if (!isConfigured(onResult)) return
        if (!isValidFieldId(fieldId, onResult)) return
        if (fieldId in FieldRuleConfiguration.DEFAULT_LOCKED_FIELD_IDS) {
            onResult(Result.failure(IllegalArgumentException("Compliance questions are always asked.")))
            return
        }
        // Shown is the default, so turning a question back on removes the setting.
        rules.child(fieldId).child("hidden").setValue(if (shown) null else true)
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
        placeAfter: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!isConfigured(onResult)) return
        if (!isValidFieldId(fieldId, onResult)) return
        if (placeAfter != null && !isValidFieldId(placeAfter, onResult)) return
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
                        "choices" to choices.toList(),
                        "placeAfter" to placeAfter
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
        placeAfter: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!isConfigured(onResult)) return
        if (!isValidFieldId(fieldId, onResult)) return
        if (placeAfter != null && !isValidFieldId(placeAfter, onResult)) return
        fields.child(fieldId).setValue(
            mapOf(
                "fieldId" to fieldId,
                "fieldName" to name,
                "description" to description,
                "fieldType" to fieldType,
                "rules" to rules.map(AdminRule::name),
                "choices" to choices.toList(),
                // Null removes the place, so the question goes back to the end.
                "placeAfter" to placeAfter
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
        loadSessionMetrics { result ->
            onResult(result.map(OnboardingAnalytics::fromSessions))
        }
    }

    override fun loadSessionMetrics(onResult: (Result<List<OnboardingSessionMetrics>>) -> Unit) {
        if (!isConfigured(onResult)) return
        sessions.orderByChild("startedAt").get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.children.map(DataSnapshot::toSessionMetrics)))
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

    /** Files this company's invitation under the email, so the agent finds it with no company ID. */
    private fun fileInvitation(email: String, isActive: Boolean, onResult: (Result<Unit>) -> Unit) {
        val key = InvitationIndex.key(email)
        if (key == null) {
            onResult(Result.success(Unit))
            return
        }
        database.child("invitationsByEmail").child(key).child(companyId).setValue(isActive)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun fileInvitationsByEmail(invitations: DataSnapshot) {
        val updates = invitations.children.mapNotNull { invitation ->
            val key = invitation.child("email").getValue(String::class.java)?.let(InvitationIndex::key) ?: return@mapNotNull null
            "/invitationsByEmail/$key/$companyId" to (invitation.child("isActive").getValue(Boolean::class.java) == true)
        }.toMap()
        if (updates.isNotEmpty()) database.updateChildren(updates)
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
            description = child("description").getValue(String::class.java).orEmpty(),
            // An unreadable place is ignored, so the question is still asked, at the end.
            placeAfter = child("placeAfter").getValue(String::class.java)?.takeIf(FormField::isValidId)
        )
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

/** One saved session, reduced to what the Overview and the CSV exports need. */
private fun DataSnapshot.toSessionMetrics(): OnboardingSessionMetrics {
    val playbookFields = child("playbookSnapshot").children
    val validation = child("validation").children.mapNotNull { field ->
        field.child("isValid").getValue(Boolean::class.java)?.let { valid ->
            field.key?.let { it to valid }
        }
    }.toMap()
    val agentUid = child("agentId").getValue(String::class.java).orEmpty()
    return OnboardingSessionMetrics(
        status = child("status").getValue(String::class.java).orEmpty(),
        startedAt = child("startedAt").value.asLongTimestamp(),
        completedAt = child("completedAt").value.asLongTimestamp(),
        agentId = child("agentEmail").getValue(String::class.java)
            ?.takeIf(String::isNotBlank)
            ?: agentUid,
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
        steps = child("events").children.mapNotNull(DataSnapshot::toRecordedStep),
        checksEachAttempt = child("answerChecks")
            .getValue(String::class.java) == ANSWER_CHECKS_ON_LEAVE,
        sessionId = child("sessionId").getValue(String::class.java) ?: key.orEmpty(),
        agentUid = agentUid,
        industry = child("industry").getValue(String::class.java).orEmpty(),
        context = child("context").getValue(String::class.java).orEmpty(),
        consentAt = child("consentAt").value.asLongTimestamp()
    )
}

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

/** The longest company name and message to agents the database accepts. */
object CompanyBriefLimits {
    const val NAME = 80
    const val MESSAGE = 1500
}

/**
 * Where an invitation is filed by email: the address in lower case with dots as commas,
 * which the database rules can work out from the signed-in person's email to let only them
 * read it. Null for an address the database cannot use as a key.
 */
object InvitationIndex {
    fun key(email: String): String? {
        val address = email.trim().lowercase(java.util.Locale.ROOT)
        if (address.isEmpty() || address.any { it in "#$[]/" }) return null
        return address.replace('.', ',')
    }
}
