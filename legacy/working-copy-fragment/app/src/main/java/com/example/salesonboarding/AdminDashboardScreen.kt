package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.salesonboarding.validation.FieldRuleConfiguration
import com.example.salesonboarding.validation.KnownFields

@Composable
fun AdminDashboardScreen(
    onSignOut: () -> Unit,
    store: AdminDataStore,
    onConfigurationChanged: (FieldRuleConfiguration) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(AdminSection.AGENTS) }
    var agents by remember { mutableStateOf<List<SalesAgentAccount>>(emptyList()) }
    var analytics by remember { mutableStateOf<OnboardingAnalytics?>(null) }
    var configuration by remember { mutableStateOf(FieldRuleConfiguration()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun <T> handle(result: Result<T>, onSuccess: (T) -> Unit = {}) {
        result.onSuccess(onSuccess).onFailure {
            errorMessage = it.message ?: "Could not load admin data."
        }
    }

    LaunchedEffect(Unit) {
        store.observeAgents { handle(it) { agents = it } }
        store.loadRuleConfiguration { handle(it) {
            configuration = it
            onConfigurationChanged(it)
        } }
        store.loadAnalytics { handle(it) { analytics = it } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SalesMesh admin",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Manage access, onboarding validation, and operational performance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminSection.values().forEach { section ->
                androidx.compose.material3.FilterChip(
                    selected = selectedSection == section,
                    onClick = { selectedSection = section },
                    label = { Text(section.label) }
                )
            }
        }

        when (selectedSection) {
            AdminSection.AGENTS -> AgentAccountsSection(
                agents = agents,
                onAdd = { email, name ->
                    store.addAgent(email, name) {
                        handle(it) {
                            store.observeAgents { refreshed -> handle(refreshed) { agents = it } }
                        }
                    }
                },
                onToggleActive = { agent ->
                    store.setAgentActive(agent.id, !agent.isActive) {
                        handle(it) {
                            agents = agents.map { current ->
                                if (current.id == agent.id) current.copy(isActive = !agent.isActive) else current
                            }
                        }
                    }
                }
            )
            AdminSection.RULES -> RuleConfigurationSection(
                configuration = configuration,
                onRequiredChanged = { fieldId, enabled ->
                    store.saveRequiredRule(fieldId, enabled) {
                        handle(it) {
                            store.loadRuleConfiguration { loaded ->
                                handle(loaded) {
                                    configuration = it
                                    onConfigurationChanged(it)
                                }
                            }
                        }
                    }
                }
            )
            AdminSection.ANALYTICS -> AnalyticsSection(analytics)
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}

private enum class AdminSection(val label: String) {
    AGENTS("Sales agents"),
    RULES("Field rules"),
    ANALYTICS("Analytics")
}

@Composable
private fun AgentAccountsSection(
    agents: List<SalesAgentAccount>,
    onAdd: (String, String) -> Unit,
    onToggleActive: (SalesAgentAccount) -> Unit
) {
        var email by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Sales agent accounts", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
                Button(
                    onClick = {
                        onAdd(email.trim(), name.trim())
                        email = ""
                        name = ""
                    },
                    enabled = email.contains("@") && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add agent")
                }
                HorizontalDivider()
                if (agents.isEmpty()) {
                    Text("No agent accounts have been added yet.")
                } else {
                    agents.forEach { agent ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(agent.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(agent.email, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = agent.isActive, onCheckedChange = { onToggleActive(agent) })
                        }
                    }
                }
            }
        }
    }

@Composable
private fun RuleConfigurationSection(
    configuration: FieldRuleConfiguration,
    onRequiredChanged: (String, Boolean) -> Unit
) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Onboarding field rules", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Required changes are saved for future onboarding sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                KnownFields.all.forEach { field ->
                    val required = configuration.rulesFor(field).any { it is com.example.salesonboarding.validation.Required }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(field.fieldName, Modifier.weight(1f))
                        Switch(
                            checked = required,
                            onCheckedChange = { onRequiredChanged(field.fieldId, it) }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun AnalyticsSection(analytics: OnboardingAnalytics?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Onboarding analytics", style = MaterialTheme.typography.titleMedium)
            if (analytics == null) {
                Text("Loading analytics...")
            } else {
                Text("Sessions: ${analytics.sessionCount}")
                Text("Completion rate: ${"%.1f".format(analytics.completionRate * 100)}%")
                Text("Average completion time: ${"%.1f".format(analytics.averageCompletionMinutes)} minutes")
            }
        }
    }
}
