package com.example.salesonboarding

import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.validation.FieldEntryStore
import com.google.firebase.database.DatabaseReference

class FirebaseFieldEntryStore(
    database: DatabaseReference
) : FieldEntryStore {
    private val entries = database.child("onboardingSessions")

    override fun save(entry: FieldEntry) {
        val entryData = mapOf(
            "entryId" to entry.entryId,
            "sessionId" to entry.sessionId,
            "fieldId" to entry.fieldId,
            "value" to entry.value,
            "timestamp" to entry.timestamp,
            "isValid" to entry.isValid
        )

        entries
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
}
