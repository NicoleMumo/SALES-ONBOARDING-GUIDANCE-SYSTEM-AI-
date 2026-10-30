package com.example.salesonboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// region Agent's own sessions

@Composable
fun AgentSessionHistoryScreen(
    agentUid: String,
    store: SessionHistoryStore,
    onBack: () -> Unit,
    onResumeSession: (StoredOnboardingSession) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sessions by remember(agentUid) { mutableStateOf<List<StoredOnboardingSession>>(emptyList()) }
    var isLoading by remember(agentUid) { mutableStateOf(false) }
    var errorMessage by remember(agentUid) { mutableStateOf<String?>(null) }

    fun refresh() {
        isLoading = true
        errorMessage = null
        store.loadAgentSessions(agentUid) { result ->
            isLoading = false
            result.onSuccess { sessions = it }
                .onFailure { errorMessage = it.message ?: "Could not load your onboarding sessions." }
        }
    }

    LaunchedEffect(agentUid, store) { refresh() }
    var openSession by remember(agentUid) { mutableStateOf<StoredOnboardingSession?>(null) }
    openSession?.let { session ->
        SessionDetailScreen(
            session = session,
            agentName = null,
            onBack = { openSession = null },
            modifier = modifier,
            onResumeSession = if (session.status.equals("in_progress", ignoreCase = true)) {
                { onResumeSession(session) }
            } else null
        )
        return
    }
    AppScreen(
        title = "My sessions",
        subtitle = "Review completed sessions or continue a saved one.",
        onBack = onBack,
        modifier = modifier,
        actions = {
            HeaderButton(
                icon = R.drawable.ic_sm_refresh,
                description = "Refresh",
                onClick = ::refresh,
                enabled = !isLoading
            )
        }
    ) {
        SessionHistoryContent(
            sessions = sessions,
            agents = emptyList(),
            isLoading = isLoading,
            errorMessage = errorMessage,
            onOpenSession = { openSession = it },
            onResumeSession = onResumeSession
        )
    }
}

// endregion

// region Session list (agents and admins)

/**
 * Searchable, filterable session cards, newest first. Tapping a card calls [onOpenSession]
 * (the detail page); [onResumeSession] adds a Continue button to unfinished sessions.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SessionHistoryContent(
    sessions: List<StoredOnboardingSession>,
    agents: List<SalesAgentAccount>,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenSession: (StoredOnboardingSession) -> Unit,
    onResumeSession: ((StoredOnboardingSession) -> Unit)? = null
) {
    var searchText by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("all") }
    var selectedIndustry by remember { mutableStateOf("all") }
    val search = searchText.trim().lowercase(Locale.ROOT)
    val filteredSessions = sessions
        .sortedByDescending { it.startedAt ?: 0L }
        .filter { session ->
            val statusMatches = selectedStatus == "all" ||
                session.status.equals(selectedStatus, ignoreCase = true)
            val normalizedIndustry = session.industry.normalizedIndustry()
            val industryMatches = selectedIndustry == "all" || normalizedIndustry == selectedIndustry
            val agentName = agents.firstOrNull {
                it.email.equals(session.agentEmail, ignoreCase = true)
            }?.displayName.orEmpty()
            val searchableText = buildString {
                append(session.customerLabel())
                append(' ')
                append(session.agentEmail)
                append(' ')
                append(session.agentId)
                append(' ')
                append(agentName)
                append(' ')
                append(session.sessionId)
                append(' ')
                append(session.industry)
                append(' ')
                append(session.context)
                session.entries.forEach { entry ->
                    append(' ')
                    append(session.fieldNames[entry.fieldId].orEmpty())
                    append(' ')
                    append(entry.value.orEmpty())
                }
            }.lowercase(Locale.ROOT)
            statusMatches && industryMatches && (search.isBlank() || search in searchableText)
        }

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search names, contact details, agents or session ID") },
            leadingIcon = { SmIcon(R.drawable.ic_sm_search, Sm.colors.muted) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Sm.colors.ink,
                unfocusedBorderColor = Sm.colors.border,
                focusedTextColor = Sm.colors.ink,
                unfocusedTextColor = Sm.colors.ink,
                cursorColor = Sm.colors.ink
            )
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            listOf("all" to "All", "completed" to "Completed", "in_progress" to "In progress").forEach { (value, label) ->
                SmChip(label, selected = selectedStatus == value, onClick = { selectedStatus = value })
            }
            listOf("banking" to "Banking", "telecom" to "Telecom").forEach { (value, label) ->
                SmChip(
                    label,
                    selected = selectedIndustry == value,
                    onClick = { selectedIndustry = if (selectedIndustry == value) "all" else value }
                )
            }
        }
        Text(
            "${filteredSessions.size} of ${sessions.size} sessions · newest first",
            style = MaterialTheme.typography.bodySmall,
            color = Sm.colors.muted
        )

        when {
            errorMessage != null -> Banner(errorMessage, Tone.Negative)
            isLoading && sessions.isEmpty() -> Text("Loading sessions…", color = Sm.colors.muted)
            filteredSessions.isEmpty() -> EmptyState(
                title = if (sessions.isEmpty()) "No sessions yet" else "Nothing matches",
                message = if (sessions.isEmpty()) "Onboarding sessions will appear here."
                else "Try a different search or filter.",
                iconRes = R.drawable.ic_sm_sessions
            )
            else -> filteredSessions.forEach { session ->
                SessionCard(
                    session = session,
                    agentName = agents.firstOrNull {
                        it.email.equals(session.agentEmail, ignoreCase = true)
                    }?.displayName,
                    onResumeSession = if (
                        onResumeSession != null && session.status.equals("in_progress", ignoreCase = true)
                    ) {
                        { onResumeSession(session) }
                    } else {
                        null
                    },
                    onOpen = { onOpenSession(session) }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: StoredOnboardingSession,
    agentName: String?,
    onResumeSession: (() -> Unit)?,
    onOpen: () -> Unit
) {
    val totalFieldCount = session.fieldNames.size
    val completed = session.status.equals("completed", ignoreCase = true)

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        color = Sm.colors.white,
        border = BorderStroke(1.5.dp, Sm.colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    session.customerLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Sm.colors.ink,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(session.status.displayStatus(), if (completed) Tone.Positive else Tone.Neutral, showIcon = completed)
            }
            Text(
                listOf(session.industry.displayIndustry(), session.context.displayValue(), session.startedAt.displayDateTime())
                    .filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
            Text(
                buildList {
                    if (totalFieldCount > 0) add("${session.filledCount()} of $totalFieldCount answered")
                    add("Agent: ${session.agentLabel(agentName)}")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
            if (onResumeSession != null) {
                PrimaryButton("Continue this session", onClick = onResumeSession)
            }
        }
    }
}

/**
 * One session on its own page: who, when, how far it got, and every saved answer, read-only.
 * [onResumeSession] adds a Continue button for an unfinished session (agents only).
 */
@Composable
fun SessionDetailScreen(
    session: StoredOnboardingSession,
    agentName: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onResumeSession: (() -> Unit)? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null
) {
    val completed = session.status.equals("completed", ignoreCase = true)
    val total = session.fieldNames.size
    val filled = session.filledCount()
    val answers = session.entries
        .filter { !it.value.isNullOrBlank() }
        .sortedBy { session.fieldNames[it.fieldId] ?: it.fieldId }

    AppScreen(
        title = session.customerLabel(),
        subtitle = session.playbookTitle.ifBlank { session.industry.displayIndustry() },
        onBack = onBack,
        bottomBar = bottomBar,
        modifier = modifier
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(session.status.displayStatus(), if (completed) Tone.Positive else Tone.Neutral, showIcon = completed)
            if (total > 0) {
                Text("$filled of $total answered", style = MaterialTheme.typography.bodySmall, color = Sm.colors.muted)
            }
        }
        SectionCard(title = "Session") {
            DetailRow("Customer type", listOf(session.industry.displayIndustry(), session.context.displayValue()).filter(String::isNotBlank).joinToString(" · "))
            DetailRow("Started", session.startedAt.displayDateTime())
            session.completedAt?.let { DetailRow("Finished", it.displayDateTime()) }
            DetailRow("Agent", session.agentLabel(agentName))
            DetailRow("Session ID", session.sessionId)
        }
        SectionCard(title = "Answers") {
            if (answers.isEmpty()) {
                Text("No answers saved yet.", style = MaterialTheme.typography.bodyMedium, color = Sm.colors.muted)
            }
            answers.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = Sm.colors.divider)
                DetailRow(session.fieldNames[entry.fieldId] ?: entry.fieldId.displayValue(), entry.value.orEmpty())
            }
        }
        if (onResumeSession != null) {
            PrimaryButton("Continue this session", onClick = onResumeSession)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label.uppercase(Locale.ROOT), style = smCapsStyle(), color = Sm.colors.muted)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = Sm.colors.ink)
    }
}

private fun StoredOnboardingSession.filledCount(): Int = entries
    .filter { !it.value.isNullOrBlank() && it.isValid != false }
    .mapTo(mutableSetOf()) { it.fieldId }
    .apply { addAll(validatedFieldIds) }
    .size

private fun StoredOnboardingSession.agentLabel(agentName: String?): String =
    agentName?.takeIf(String::isNotBlank) ?: agentEmail.ifBlank { agentId.shortAgentId() }

// endregion

// region Helpers

private fun StoredOnboardingSession.customerLabel(): String {
    val preferredIds = listOf(
        "full_name", "customer_name", "business_name", "company_name", "legal_name",
        "organization_name", "first_name"
    )
    val entriesById = entries.associateBy { it.fieldId }
    preferredIds.firstNotNullOfOrNull { id ->
        entriesById[id]?.value?.takeIf(String::isNotBlank)
    }?.let { return it }
    entries.firstOrNull { entry ->
        entry.fieldId.contains("email", ignoreCase = true) ||
            entry.fieldId.contains("phone", ignoreCase = true)
    }?.value?.takeIf(String::isNotBlank)?.let { return it }
    return "Session ${sessionId.take(8)}"
}

private fun String.normalizedIndustry(): String = when {
    contains("bank", ignoreCase = true) -> "banking"
    contains("tele", ignoreCase = true) -> "telecom"
    else -> lowercase(Locale.ROOT)
}

private fun String.displayIndustry(): String = when (normalizedIndustry()) {
    "banking" -> "Banking"
    "telecom" -> "Telecom"
    else -> displayValue()
}

private fun String.displayStatus(): String = when (lowercase(Locale.ROOT)) {
    "completed" -> "Completed"
    "in_progress" -> "In progress"
    "cancelled", "canceled" -> "Cancelled"
    else -> displayValue()
}

private fun String.displayValue(): String = replace('_', ' ').replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
}

private fun String.shortAgentId(): String = if (isBlank()) "Unknown" else "${take(8)}…"

private fun Long?.displayDateTime(): String = this?.let {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
} ?: "Date unavailable"

// endregion
