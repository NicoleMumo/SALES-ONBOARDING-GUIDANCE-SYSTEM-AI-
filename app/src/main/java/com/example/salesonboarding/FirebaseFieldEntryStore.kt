package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.validation.FieldEntryStore
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

data class StoredOnboardingSession(
    val sessionId: String,
    val agentId: String,
    val industry: String,
    val context: String,
    val status: String,
    val startedAt: Long?,
    val completedAt: Long?,
    val entries: List<FieldEntry>,
    val agentEmail: String = "",
    val playbookTitle: String = "",
    val fieldNames: Map<String, String> = emptyMap(),
    val validatedFieldIds: Set<String> = emptySet(),
    val resumeFieldId: String? = null
)

interface SessionHistoryStore {
    fun loadAgentSessions(agentUid: String, onResult: (Result<List<StoredOnboardingSession>>) -> Unit)
}

interface OnboardingSessionStore : FieldEntryStore {
    fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook
    )
    fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook,
        agentEmail: String
    ) {
        startSession(sessionId, agentId, industry, context, playbook)
    }
    fun recordValidation(sessionId: String, fieldId: String, isValid: Boolean, message: String?)
    fun appendEvent(sessionId: String, type: String, fieldId: String?)
    fun markCompleted(sessionId: String)
    fun completeSession(
        sessionId: String,
        entries: List<FieldEntry>,
        onResult: (Result<Unit>) -> Unit
    ) {
        try {
            entries.forEach(::save)
            markCompleted(sessionId)
            onResult(Result.success(Unit))
        } catch (error: Exception) {
            onResult(Result.failure(error))
        }
    }
    fun completeSession(
        sessionId: String,
        entries: List<FieldEntry>,
        fieldIds: List<String>,
        onResult: (Result<Unit>) -> Unit
    ) = completeSession(sessionId, entries, onResult)
    fun saveDraft(
        sessionId: String,
        entries: List<FieldEntry>,
        resumeFieldId: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        try {
            entries.filter { !it.value.isNullOrBlank() }.forEach(::save)
            onResult(Result.success(Unit))
        } catch (error: Exception) {
            onResult(Result.failure(error))
        }
    }
    fun getSession(sessionId: String, onResult: (Result<StoredOnboardingSession?>) -> Unit)
}

class FirebaseFieldEntryStore(
    database: DatabaseReference,
    private val companyId: String
) : OnboardingSessionStore {
    init {
        require(companyId.matches(Regex("^[A-Za-z0-9_-]{3,64}$"))) {
            "A valid company ID is required to store onboarding sessions."
        }
    }
    private val sessions = database.child("companies").child(companyId).child("onboardingSessions")

    override fun save(entry: FieldEntry) {
        val entryData = mapOf(
            "entryId" to entry.entryId,
            "sessionId" to entry.sessionId,
            "fieldId" to entry.fieldId,
            "value" to entry.value,
            "timestamp" to entry.timestamp,
            "isValid" to entry.isValid
        )

        sessions
            .child(entry.sessionId)
            .child("entries")
            .child(entry.fieldId)
            .setValue(entryData)
            .reportFailure(
                "Could not save field '${entry.fieldId}' for session '${entry.sessionId}'."
            )
    }

    override fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook
    ) = startSession(sessionId, agentId, industry, context, playbook, "")

    override fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook,
        agentEmail: String
    ) {
        sessions.child(sessionId).updateChildren(
            mapOf(
                "sessionId" to sessionId,
                "companyId" to companyId,
                "agentId" to agentId,
                "agentEmail" to agentEmail.trim(),
                "industry" to industry,
                "context" to context,
                "status" to "in_progress",
                "startedAt" to System.currentTimeMillis(),
                "playbookId" to playbook.id,
                "playbookTitle" to playbook.title,
                "playbookVersion" to playbook.version,
                "playbookSnapshot" to playbook.fields.map { field ->
                    mapOf(
                        "fieldId" to field.fieldId,
                        "fieldName" to field.fieldName,
                        "fieldType" to field.fieldType,
                        "description" to field.description,
                        "isRequired" to field.isRequired,
                        "rules" to field.validationRule.map { it.toString() }
                    )
                }
            )
        ).reportFailure("Could not start onboarding session '$sessionId'.")
    }

    override fun recordValidation(
        sessionId: String,
        fieldId: String,
        isValid: Boolean,
        message: String?
    ) {
        sessions.child(sessionId).child("validation").child(fieldId).setValue(
            mapOf(
                "fieldId" to fieldId,
                "isValid" to isValid,
                "errorMessage" to message,
                "updatedAt" to System.currentTimeMillis()
            )
        ).reportFailure("Could not save validation for field '$fieldId'.")
    }

    override fun appendEvent(sessionId: String, type: String, fieldId: String?) {
        sessions.child(sessionId).child("events").push().setValue(
            mapOf(
                "type" to type,
                "fieldId" to fieldId,
                "timestamp" to System.currentTimeMillis()
            )
        ).reportFailure("Could not save onboarding event '$type'.")
    }

    override fun markCompleted(sessionId: String) {
        sessions.child(sessionId).updateChildren(
            mapOf(
                "status" to "completed",
                "completedAt" to System.currentTimeMillis()
            )
        ).reportFailure("Could not mark onboarding session '$sessionId' as completed.")
    }

    override fun completeSession(
        sessionId: String,
        entries: List<FieldEntry>,
        onResult: (Result<Unit>) -> Unit
    ) = completeSession(sessionId, entries, entries.map(FieldEntry::fieldId), onResult)

    override fun completeSession(
        sessionId: String,
        entries: List<FieldEntry>,
        fieldIds: List<String>,
        onResult: (Result<Unit>) -> Unit
    ) {
        val updates = mutableMapOf<String, Any?>(
            "status" to "completed",
            "completedAt" to System.currentTimeMillis(),
            "resumeFieldId" to null
        )
        fieldIds.forEach { fieldId -> updates["entries/$fieldId"] = null }
        entries.forEach { entry ->
            updates["entries/${entry.fieldId}"] = mapOf(
                "entryId" to entry.entryId,
                "sessionId" to entry.sessionId,
                "fieldId" to entry.fieldId,
                "value" to entry.value,
                "timestamp" to entry.timestamp,
                "isValid" to entry.isValid
            )
        }
        sessions.child(sessionId).updateChildren(updates)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun saveDraft(
        sessionId: String,
        entries: List<FieldEntry>,
        resumeFieldId: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        val now = System.currentTimeMillis()
        sessions.child(sessionId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.child("status").getValue(String::class.java) != "in_progress") {
                    return Transaction.abort()
                }
                currentData.child("updatedAt").value = now
                currentData.child("lastSavedAt").value = now
                currentData.child("resumeFieldId").value = resumeFieldId
                entries.forEach { entry ->
                    currentData.child("entries").child(entry.fieldId).value =
                        if (entry.value.isNullOrBlank()) {
                            null
                        } else {
                            mapOf(
                                "entryId" to entry.entryId,
                                "sessionId" to entry.sessionId,
                                "fieldId" to entry.fieldId,
                                "value" to entry.value,
                                "timestamp" to entry.timestamp,
                                "isValid" to entry.isValid
                            )
                        }
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                when {
                    error != null -> onResult(Result.failure(error.toException()))
                    committed -> onResult(Result.success(Unit))
                    else -> onResult(
                        Result.failure(
                            IllegalStateException(
                                "This session is no longer in progress. Refresh your sessions before continuing."
                            )
                        )
                    )
                }
            }
        }, false)
    }

    override fun getSession(
        sessionId: String,
        onResult: (Result<StoredOnboardingSession?>) -> Unit
    ) {
        sessions.child(sessionId).get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.toStoredSession()))
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    private fun DataSnapshot.toStoredSession(): StoredOnboardingSession? = toStoredOnboardingSession()

    private fun com.google.android.gms.tasks.Task<*>.reportFailure(message: String) =
        addOnFailureListener { error ->
            android.util.Log.e("FirebaseFieldEntryStore", message, error)
        }
}

class FirebaseSessionHistoryStore(
    database: DatabaseReference,
    companyId: String
) : SessionHistoryStore {
    private val sessions = database.child("companies").child(companyId).child("onboardingSessions")

    override fun loadAgentSessions(
        agentUid: String,
        onResult: (Result<List<StoredOnboardingSession>>) -> Unit
    ) {
        if (agentUid.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Sign in again to view your sessions.")))
            return
        }
        sessions.orderByChild("agentId").equalTo(agentUid).get()
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
}

internal fun DataSnapshot.toStoredOnboardingSession(): StoredOnboardingSession? {
    if (!exists()) return null
    val fieldNames = child("playbookSnapshot").children.mapNotNull { field ->
        val fieldId = field.child("fieldId").getValue(String::class.java)
        val fieldName = field.child("fieldName").getValue(String::class.java)
        if (fieldId != null && fieldName != null) fieldId to fieldName else null
    }.toMap()
    val entries = child("entries").children.mapNotNull { entry ->
        val fieldId = entry.child("fieldId").getValue(String::class.java) ?: entry.key ?: return@mapNotNull null
        FieldEntry(
            entryId = entry.child("entryId").getValue(String::class.java).orEmpty(),
            sessionId = entry.child("sessionId").getValue(String::class.java).orEmpty(),
            fieldId = fieldId,
            value = entry.child("value").getValue(String::class.java),
            timestamp = entry.child("timestamp").getValue(Long::class.java) ?: 0L,
            isValid = entry.child("isValid").getValue(Boolean::class.java)
        )
    }
    val validatedFieldIds = child("validation").children.mapNotNull { field ->
        if (field.child("isValid").getValue(Boolean::class.java) == true) field.key else null
    }.toSet()
    return StoredOnboardingSession(
        sessionId = child("sessionId").getValue(String::class.java) ?: key.orEmpty(),
        agentId = child("agentId").getValue(String::class.java).orEmpty(),
        industry = child("industry").getValue(String::class.java).orEmpty(),
        context = child("context").getValue(String::class.java).orEmpty(),
        status = child("status").getValue(String::class.java).orEmpty(),
        startedAt = child("startedAt").getValue(Long::class.java),
        completedAt = child("completedAt").getValue(Long::class.java),
        entries = entries,
        agentEmail = child("agentEmail").getValue(String::class.java).orEmpty(),
        playbookTitle = child("playbookTitle").getValue(String::class.java).orEmpty(),
        fieldNames = fieldNames,
        validatedFieldIds = validatedFieldIds,
        resumeFieldId = child("resumeFieldId").getValue(String::class.java)
    )
}
