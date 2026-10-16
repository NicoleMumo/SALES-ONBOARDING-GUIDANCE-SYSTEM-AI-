package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.validation.FieldEntryStore
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot

data class StoredOnboardingSession(
    val sessionId: String,
    val agentId: String,
    val industry: String,
    val context: String,
    val status: String,
    val startedAt: Long?,
    val completedAt: Long?,
    val entries: List<FieldEntry>
)

interface OnboardingSessionStore : FieldEntryStore {
    fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook
    )
    fun recordValidation(sessionId: String, fieldId: String, isValid: Boolean, message: String?)
    fun appendEvent(sessionId: String, type: String, fieldId: String?, value: String?)
    fun markCompleted(sessionId: String)
    fun getSession(sessionId: String, onResult: (Result<StoredOnboardingSession?>) -> Unit)
}

class FirebaseFieldEntryStore(
    database: DatabaseReference
) : OnboardingSessionStore {
    private val sessions = database.child("onboardingSessions")

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
    ) {
        sessions.child(sessionId).updateChildren(
            mapOf(
                "sessionId" to sessionId,
                "agentId" to agentId,
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

    override fun appendEvent(sessionId: String, type: String, fieldId: String?, value: String?) {
        sessions.child(sessionId).child("events").push().setValue(
            mapOf(
                "type" to type,
                "fieldId" to fieldId,
                "valueProvided" to !value.isNullOrBlank(),
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

    private fun DataSnapshot.toStoredSession(): StoredOnboardingSession? {
        if (!exists()) return null
        val entries = child("entries").children.mapNotNull { entry ->
            entry.toFieldEntry()
        }
        return StoredOnboardingSession(
            sessionId = child("sessionId").getValue(String::class.java) ?: key.orEmpty(),
            agentId = child("agentId").getValue(String::class.java).orEmpty(),
            industry = child("industry").getValue(String::class.java).orEmpty(),
            context = child("context").getValue(String::class.java).orEmpty(),
            status = child("status").getValue(String::class.java).orEmpty(),
            startedAt = child("startedAt").getValue(Long::class.java),
            completedAt = child("completedAt").getValue(Long::class.java),
            entries = entries
        )
    }

    private fun DataSnapshot.toFieldEntry(): FieldEntry? {
        val fieldId = child("fieldId").getValue(String::class.java) ?: key ?: return null
        return FieldEntry(
            entryId = child("entryId").getValue(String::class.java).orEmpty(),
            sessionId = child("sessionId").getValue(String::class.java).orEmpty(),
            fieldId = fieldId,
            value = child("value").getValue(String::class.java),
            timestamp = child("timestamp").getValue(Long::class.java) ?: 0L,
            isValid = child("isValid").getValue(Boolean::class.java)
        )
    }

    private fun com.google.android.gms.tasks.Task<*>.reportFailure(message: String) =
        addOnFailureListener { error ->
            android.util.Log.e("FirebaseFieldEntryStore", message, error)
        }
}
