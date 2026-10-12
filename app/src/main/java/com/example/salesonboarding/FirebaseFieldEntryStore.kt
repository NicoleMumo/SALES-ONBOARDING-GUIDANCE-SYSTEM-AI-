package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.validation.FieldEntryStore
import com.google.firebase.database.DatabaseReference

interface OnboardingSessionStore : FieldEntryStore {
    fun startSession(sessionId: String, agentId: String, industry: String, context: String)
    fun recordValidation(sessionId: String, fieldId: String, isValid: Boolean, message: String?)
    fun appendEvent(sessionId: String, type: String, fieldId: String?, value: String?)
    fun markCompleted(sessionId: String)
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
            .addOnFailureListener { error ->
                throw IllegalStateException(
                    "Could not save field '${entry.fieldId}' for session '${entry.sessionId}'.",
                    error
                )
            }
    }

    override fun startSession(sessionId: String, agentId: String, industry: String, context: String) {
        sessions.child(sessionId).updateChildren(
            mapOf(
                "sessionId" to sessionId,
                "agentId" to agentId,
                "industry" to industry,
                "context" to context,
                "status" to "in_progress",
                "startedAt" to System.currentTimeMillis()
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
                "value" to value,
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

    private fun com.google.android.gms.tasks.Task<*>.reportFailure(message: String) =
        addOnFailureListener { error ->
            android.util.Log.e("FirebaseFieldEntryStore", message, error)
        }
}
