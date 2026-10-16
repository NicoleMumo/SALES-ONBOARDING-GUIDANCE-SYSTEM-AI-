package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import java.util.Locale

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
    var isLoadingAnalytics by remember { mutableStateOf(false) }
    var analyticsErrorMessage by remember { mutableStateOf<String?>(null) }

    fun <T> handle(result: Result<T>, onSuccess: (T) -> Unit = {}) {
        result.onSuccess {
            errorMessage = null
            onSuccess(it)
        }.onFailure {
            errorMessage = it.message ?: "Could not load admin data."
        }
    }

    LaunchedEffect(Unit) {
        store.observeAgents { handle(it) { agents = it } }
        store.loadRuleConfiguration { handle(it) {
            configuration = it
            onConfigurationChanged(it)
        } }
        isLoadingAnalytics = true
        analyticsErrorMessage = null
        store.loadAnalytics {
            isLoadingAnalytics = false
            it.onSuccess { analytics = it; analyticsErrorMessage = null }
                .onFailure { analyticsErrorMessage = it.message ?: "Could not load onboarding analytics." }
            handle(it)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                onAddField = { fieldId, name, description, type, rules, choices, onComplete ->
                    store.addCustomField(fieldId, name, description, type, rules, choices) {
                        onComplete(it)
                        handle(it) {
                            store.loadRuleConfiguration { loaded ->
                                handle(loaded) {
                                    configuration = it
                                    onConfigurationChanged(it)
                                }
                            }
                        }
                    }
                },
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
            AdminSection.ANALYTICS -> AnalyticsSection(
                analytics = analytics,
                agents = agents,
                isLoading = isLoadingAnalytics,
                errorMessage = analyticsErrorMessage,
                onRefresh = {
                    isLoadingAnalytics = true
                    analyticsErrorMessage = null
                    store.loadAnalytics {
                        isLoadingAnalytics = false
                        it.onSuccess { analytics = it; analyticsErrorMessage = null }
                            .onFailure { analyticsErrorMessage = it.message ?: "Could not load onboarding analytics." }
                        handle(it)
                    }
                }
            )
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
    onAddField: (
        String,
        String,
        String,
        String,
        Set<AdminRule>,
        Set<String>,
        (Result<Unit>) -> Unit
    ) -> Unit,
    onRequiredChanged: (String, Boolean) -> Unit
) {
        var fieldName by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var fieldType by remember { mutableStateOf("text") }
        var choiceOptions by remember { mutableStateOf("") }
        var selectedRules by remember { mutableStateOf(setOf(AdminRule.REQUIRED)) }
        var isSavingField by remember { mutableStateOf(false) }
        val fieldId = remember(fieldName) {
            fieldName.trim().lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Onboarding field rules", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Changes affect future onboarding sessions. Custom fields are not added to the AI model vocabulary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Add a custom field", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(fieldName, { fieldName = it }, Modifier.fillMaxWidth(), label = { Text("Field name") }, singleLine = true)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
                Text("Field ID: ${fieldId.ifBlank { "enter a name to generate an ID" }}", style = MaterialTheme.typography.bodySmall)
                Text("Input type", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("text", "number", "date", "email", "phone", "boolean", "choice")
                        .chunked(3)
                        .forEach { types ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                types.forEach { type ->
                                    androidx.compose.material3.FilterChip(
                                        selected = fieldType == type,
                                        onClick = { fieldType = type },
                                        label = { Text(type.replaceFirstChar(Char::uppercase)) }
                                    )
                                }
                            }
                        }
                }
                if (fieldType == "choice") {
                    OutlinedTextField(
                        value = choiceOptions,
                        onValueChange = { choiceOptions = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Approved choices (comma-separated)") },
                        supportingText = { Text("Agents will select one of these values.") }
                    )
                }
                Text("Validation rules", style = MaterialTheme.typography.labelLarge)
                AdminRule.values().forEach { rule ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = rule in selectedRules,
                            onCheckedChange = { checked ->
                                selectedRules = if (checked) selectedRules + rule
                                else selectedRules - rule
                            }
                        )
                        Text(rule.label)
                    }
                }
                val parsedChoices = choiceOptions.split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinctBy { it.lowercase(Locale.ROOT) }
                    .toSet()
                    .takeIf { fieldType == "choice" }
                    .orEmpty()
                Button(
                    onClick = {
                        onAddField(
                            fieldId,
                            fieldName.trim(),
                            description.trim(),
                            fieldType.trim().lowercase(Locale.ROOT),
                            selectedRules,
                            parsedChoices,
                        ) { result ->
                            if (result.isSuccess) {
                                fieldName = ""
                                description = ""
                                fieldType = "text"
                                choiceOptions = ""
                                selectedRules = setOf(AdminRule.REQUIRED)
                            }
                            isSavingField = false
                        }
                        isSavingField = true
                    },
                    enabled = fieldId.isNotBlank() && fieldName.isNotBlank() &&
                        description.isNotBlank() && !isSavingField &&
                        (fieldType != "choice" || parsedChoices.isNotEmpty()) &&
                        fieldId !in KnownFields.all.map { it.fieldId } &&
                        fieldId !in configuration.customFields.map { it.fieldId },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSavingField) "Saving..." else "Add field")
                }
                HorizontalDivider()
                configuration.customFields.forEach { field ->
                    val ruleLabels = AdminRule.values()
                        .filter { it in field.validationRule.toAdminRules(field.isRequired) }
                        .map(AdminRule::label)
                        .let { labels ->
                            if (field.validationRule.any {
                                    it is com.example.salesonboarding.validation.OneOf
                                }) labels + "Approved choices" else labels
                        }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(field.fieldName, style = MaterialTheme.typography.titleSmall)
                        Text(field.description, style = MaterialTheme.typography.bodySmall)
                        Text("ID: ${field.fieldId} | Type: ${field.fieldType}")
                        Text(
                            "Rules: ${ruleLabels.joinToString().ifBlank { "None" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        field.validationRule.filterIsInstance<com.example.salesonboarding.validation.OneOf>()
                            .flatMap { it.choices }
                            .takeIf { it.isNotEmpty() }
                            ?.let { choices ->
                                Text(
                                    "Options: ${choices.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                    }
                }
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
private fun AnalyticsSection(
    analytics: OnboardingAnalytics?,
    agents: List<SalesAgentAccount>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Onboarding analytics", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRefresh, enabled = !isLoading) {
                Text(if (isLoading) "Refreshing..." else "Refresh analytics")
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            } else if (analytics == null && !isLoading) {
                Text("No session data is available.")
            } else if (isLoading && analytics == null) {
                Text("Loading analytics...")
            } else if (analytics != null) {
                Text("Sessions: ${analytics.sessionCount}")
                Text("Completion rate: ${"%.1f".format(analytics.completionRate * 100)}%")
                Text("Average completion time: ${"%.1f".format(analytics.averageCompletionMinutes)} minutes")
                Text("Average form progress: ${"%.0f".format(analytics.averageReadinessPercent)}%")
                analytics.commonPausePoint?.let {
                    Text("Most common unfinished step: $it")
                }
                if (analytics.agentPerformance.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Team consistency", style = MaterialTheme.typography.titleSmall)
                    analytics.agentPerformance.forEach { performance ->
                        val displayName = agents.firstOrNull {
                            it.email.equals(performance.agentId, ignoreCase = true)
                        }?.displayName ?: performance.agentId
                        Text(
                            "$displayName — ${performance.completedCount}/${performance.sessionCount} completed " +
                                "(${ "%.0f".format(performance.completionRate * 100) }%)"
                        )
                    }
                }
            }
        }
    }
}

private fun List<com.example.salesonboarding.validation.ValidationRule>.toAdminRules(
    required: Boolean
): Set<AdminRule> = buildSet {
    if (required) add(AdminRule.REQUIRED)
    if (this@toAdminRules.any { it is com.example.salesonboarding.validation.NotBlank }) add(AdminRule.NOT_BLANK)
    if (this@toAdminRules.any { it is com.example.salesonboarding.validation.EmailFormat }) add(AdminRule.EMAIL)
    if (this@toAdminRules.any { it is com.example.salesonboarding.validation.KenyanPhoneFormat }) add(AdminRule.PHONE)
    if (this@toAdminRules.any { it is com.example.salesonboarding.validation.PositiveNumber }) add(AdminRule.POSITIVE_NUMBER)
    if (this@toAdminRules.any { it is com.example.salesonboarding.validation.MustBeTrue }) add(AdminRule.MUST_BE_TRUE)
    if (this@toAdminRules.any { it is com.example.salesonboarding.validation.DateFormatAndMinimumAge }) add(AdminRule.DATE)
}
