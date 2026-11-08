package com.example.salesonboarding

import java.security.MessageDigest

/**
 * Anonymised copies of sessions, kept to retrain the models on real onboarding.
 *
 * A copy is saved only when the company's admin allows it (Company page) and the customer
 * agrees on the consent screen. It is written to `trainingData/`, which no one can read
 * through the app; the project owner exports it from the Firebase console and converts it
 * with `python ml/dataset.py from-firebase`. It holds the industry, customer type, number of
 * questions, the agent as a code, and each check: question, passed or not, and time. It
 * never holds an answer, a name or the session ID.
 */
object TrainingData {
    /** Where the copy of [sessionId] is kept. The same session always maps to the same record. */
    fun recordId(sessionId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("salesmesh-training:$sessionId".toByteArray())
        return "T" + digest.take(16).joinToString("") { "%02X".format(it) }
    }

    fun agentCode(agentUid: String): String = SessionCsvExport.agentPseudonym(agentUid)
}
