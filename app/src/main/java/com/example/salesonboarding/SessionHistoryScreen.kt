package com.example.salesonboarding

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
    SessionHistoryContent(
        title = "My onboarded clients",
        description = "Review completed sessions or continue a saved draft.",
        sessions = sessions,
        agents = emptyList(),
        isLoading = isLoading,
        errorMessage = errorMessage,
        onRefresh = ::refresh,
        onBack = onBack,
        onResumeSession = onResumeSession,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    )
}

@Composable
fun SessionHistoryContent(
    title: String,
    description: String,
    sessions: List<StoredOnboardingSession>,
    agents: List<SalesAgentAccount>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
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

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text(if (isLoading) "Refreshing..." else "Refresh")
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search clients, contact details, agents, or session ID") },
            singleLine = true
        )

        Text("Status", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("all" to "All", "completed" to "Completed", "in_progress" to "In progress")
                .forEach { (value, label) ->
                    FilterChip(
                        selected = selectedStatus == value,
                        onClick = { selectedStatus = value },
                        label = { Text(label) }
                    )
                }
        }

        Text("Industry", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("all" to "All", "banking" to "Banking", "telecom" to "Telecom")
                .forEach { (value, label) ->
                    FilterChip(
                        selected = selectedIndustry == value,
                        onClick = { selectedIndustry = value },
                        label = { Text(label) }
                    )
                }
        }

        Text(
            "${filteredSessions.size} of ${sessions.size} sessions · newest first",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (errorMessage != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else if (isLoading && sessions.isEmpty()) {
            Text("Loading sessions...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (filteredSessions.isEmpty()) {
            Text(
                if (sessions.isEmpty()) "No onboarding sessions are available yet."
                else "No sessions match this search and these filters.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredSessions.forEach { session ->
                SessionHistoryCard(
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
private fun SessionHistoryCard(
    session: StoredOnboardingSession,
    agentName: String?,
    expanded: Boolean,
    onResumeSession: (() -> Unit)?,
    onToggleDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(session.customerLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(session.industry.displayIndustry(), session.context.displayValue())
                    .filter(String::isNotBlank).joinToString(" · ").ifBlank { "Onboarding session" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${session.status.displayStatus()} · ${session.startedAt.displayDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onResumeSession != null) {
                Text(
                    "Your saved answers are ready to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = onResumeSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Resume onboarding")
                }
            }
            val filledFieldIds = session.entries
                .filter { !it.value.isNullOrBlank() && it.isValid != false }
                .mapTo(mutableSetOf()) { it.fieldId }
                .apply { addAll(session.validatedFieldIds) }
            val totalFieldCount = session.fieldNames.size
            if (totalFieldCount > 0) {
                Text(
                    "Fields filled: ${filledFieldIds.size} / $totalFieldCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Agent: ${agentName?.takeIf(String::isNotBlank) ?: session.agentEmail.ifBlank { session.agentId.shortAgentId() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onToggleDetails) {
                Text(if (expanded) "Hide session details" else "View session details")
            }
            if (expanded) {
                HorizontalDivider()
                Text("Session ID: ${session.sessionId}", style = MaterialTheme.typography.labelSmall)
                if (totalFieldCount > 0) {
                    Text(
                        "${filledFieldIds.size} of $totalFieldCount fields filled",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "Agent account: ${session.agentEmail.ifBlank { session.agentId.ifBlank { "Unknown" } }}",
                    style = MaterialTheme.typography.labelSmall
                )
                if (session.entries.isEmpty()) {
                    Text(
                        "Customer answers are not saved until the agent submits the session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    session.entries.sortedBy { session.fieldNames[it.fieldId] ?: it.fieldId }
                        .forEach { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    session.fieldNames[entry.fieldId] ?: entry.fieldId.displayValue(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(entry.value.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                }
            }
        }
    }
}

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
