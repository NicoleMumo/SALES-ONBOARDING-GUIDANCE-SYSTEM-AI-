package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    AppScreen(
        title = "My sessions",
        subtitle = "Review completed sessions or continue a saved one.",
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = ::refresh, enabled = !isLoading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    ) {
        SessionHistoryContent(
            sessions = sessions,
            agents = emptyList(),
            isLoading = isLoading,
            errorMessage = errorMessage,
            onResumeSession = onResumeSession
        )
    }
}

// endregion

// region Session list (agents and admins)

/**
 * Searchable, filterable session cards, newest first. Answers are shown read-only under
 * "View answers"; [onResumeSession] adds a Resume button to unfinished sessions.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SessionHistoryContent(
    sessions: List<StoredOnboardingSession>,
    agents: List<SalesAgentAccount>,
    isLoading: Boolean,
    errorMessage: String?,
    onResumeSession: ((StoredOnboardingSession) -> Unit)? = null
) {
    var searchText by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("all") }
    var selectedIndustry by remember { mutableStateOf("all") }
    var expandedSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
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
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            listOf("all" to "All", "completed" to "Completed", "in_progress" to "In progress").forEach { (value, label) ->
                FilterChip(selected = selectedStatus == value, onClick = { selectedStatus = value }, label = { Text(label) })
            }
            listOf("banking" to "Banking", "telecom" to "Telecom").forEach { (value, label) ->
                FilterChip(
                    selected = selectedIndustry == value,
                    onClick = { selectedIndustry = if (selectedIndustry == value) "all" else value },
                    label = { Text(label) }
                )
            }
        }
        Text(
            "${filteredSessions.size} of ${sessions.size} sessions · newest first",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            errorMessage != null -> Banner(errorMessage, Tone.Negative)
            isLoading && sessions.isEmpty() -> Text("Loading sessions…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            filteredSessions.isEmpty() -> EmptyState(
                title = if (sessions.isEmpty()) "No sessions yet" else "Nothing matches",
                message = if (sessions.isEmpty()) "Onboarding sessions will appear here."
                else "Try a different search or filter.",
                icon = Icons.AutoMirrored.Filled.List
            )
            else -> filteredSessions.forEach { session ->
                SessionCard(
                    session = session,
                    agentName = agents.firstOrNull {
                        it.email.equals(session.agentEmail, ignoreCase = true)
                    }?.displayName,
                    expanded = session.sessionId in expandedSessionIds,
                    onResumeSession = if (
                        onResumeSession != null && session.status.equals("in_progress", ignoreCase = true)
                    ) {
                        { onResumeSession(session) }
                    } else {
                        null
                    },
                    onToggleDetails = {
                        expandedSessionIds = if (session.sessionId in expandedSessionIds) {
                            expandedSessionIds - session.sessionId
                        } else {
                            expandedSessionIds + session.sessionId
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: StoredOnboardingSession,
    agentName: String?,
    expanded: Boolean,
    onResumeSession: (() -> Unit)?,
    onToggleDetails: () -> Unit
) {
    val filledFieldIds = session.entries
        .filter { !it.value.isNullOrBlank() && it.isValid != false }
        .mapTo(mutableSetOf()) { it.fieldId }
        .apply { addAll(session.validatedFieldIds) }
    val totalFieldCount = session.fieldNames.size
    val completed = session.status.equals("completed", ignoreCase = true)

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(session.customerLabel(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(session.status.displayStatus(), if (completed) Tone.Positive else Tone.Neutral, showIcon = completed)
        }
        Text(
            listOf(session.industry.displayIndustry(), session.context.displayValue(), session.startedAt.displayDateTime())
                .filter(String::isNotBlank).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            buildList {
                if (totalFieldCount > 0) add("${filledFieldIds.size} of $totalFieldCount answered")
                add("Agent: ${agentName?.takeIf(String::isNotBlank) ?: session.agentEmail.ifBlank { session.agentId.shortAgentId() }}")
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onResumeSession != null) {
            PrimaryButton("Continue this session", onClick = onResumeSession, icon = Icons.AutoMirrored.Filled.ArrowForward)
        }
        TextButton(onClick = onToggleDetails) {
            Text(if (expanded) "Hide answers" else "View answers")
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (session.entries.isEmpty()) {
                Text(
                    "No answers saved yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            session.entries.sortedBy { session.fieldNames[it.fieldId] ?: it.fieldId }.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        session.fieldNames[entry.fieldId] ?: entry.fieldId.displayValue(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(entry.value.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Session ID ${session.sessionId} · agent account ${session.agentEmail.ifBlank { session.agentId.ifBlank { "unknown" } }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
