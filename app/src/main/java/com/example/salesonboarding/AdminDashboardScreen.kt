package com.example.salesonboarding

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AdminDashboardScreen(
    onSignOut: () -> Unit,
    store: AdminDataStore,
    companyId: String,
    onConfigurationChanged: (FieldRuleConfiguration) -> Unit,
    onCompanyIndustryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCompanyId = rememberUpdatedState(companyId)
    val activeStore = rememberUpdatedState(store)
    fun isCurrentAdminScope(): Boolean =
        activeCompanyId.value == companyId && activeStore.value === store

    var selectedSection by remember(companyId, store) { mutableStateOf(AdminSection.AGENTS) }
    var agents by remember(companyId, store) { mutableStateOf<List<SalesAgentAccount>>(emptyList()) }
    var analytics by remember(companyId, store) { mutableStateOf<OnboardingAnalytics?>(null) }
    var sessions by remember(companyId, store) { mutableStateOf<List<StoredOnboardingSession>>(emptyList()) }
    var configuration by remember(companyId, store) { mutableStateOf(FieldRuleConfiguration()) }
    var companyIndustry by remember(companyId, store) { mutableStateOf<String?>(null) }
    var isSavingCompanyIndustry by remember(companyId, store) { mutableStateOf(false) }
    var errorMessage by remember(companyId, store) { mutableStateOf<String?>(null) }
    var agentStatusMessage by remember(companyId, store) { mutableStateOf<String?>(null) }
    var isLoadingAnalytics by remember(companyId, store) { mutableStateOf(false) }
    var analyticsErrorMessage by remember(companyId, store) { mutableStateOf<String?>(null) }
    var isLoadingSessions by remember(companyId, store) { mutableStateOf(false) }
    var sessionsErrorMessage by remember(companyId, store) { mutableStateOf<String?>(null) }

    fun <T> handle(result: Result<T>, onSuccess: (T) -> Unit = {}) {
        result.onSuccess {
            errorMessage = null
            onSuccess(it)
        }.onFailure {
            errorMessage = it.message ?: "Could not load admin data."
        }
    }

    fun loadSessions() {
        isLoadingSessions = true
        sessionsErrorMessage = null
        store.loadSessions { result ->
            if (!isCurrentAdminScope()) return@loadSessions
            isLoadingSessions = false
            result.onSuccess { sessions = it; sessionsErrorMessage = null }
                .onFailure { sessionsErrorMessage = it.message ?: "Could not load onboarding sessions." }
        }
    }

    LaunchedEffect(companyId, store) {
        store.loadCompanyIndustry(companyId) {
            if (!isCurrentAdminScope()) return@loadCompanyIndustry
            handle(it) { companyIndustry = it }
        }
        store.observeAgents {
            if (!isCurrentAdminScope()) return@observeAgents
            handle(it) { agents = it }
        }
        store.loadRuleConfiguration { result ->
            if (!isCurrentAdminScope()) return@loadRuleConfiguration
            handle(result) {
                configuration = it
                onConfigurationChanged(it)
            }
        }
        isLoadingAnalytics = true
        analyticsErrorMessage = null
        store.loadAnalytics {
            if (!isCurrentAdminScope()) return@loadAnalytics
            isLoadingAnalytics = false
            it.onSuccess { analytics = it; analyticsErrorMessage = null }
                .onFailure { analyticsErrorMessage = it.message ?: "Could not load onboarding analytics." }
            handle(it)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "SalesMesh",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Company $companyId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Admin dashboard", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Manage your company’s agents, forms, and activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            agentStatusMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminSummaryCard(
                    label = "Agent profiles",
                    value = agents.size.toString(),
                    detail = "${agents.count(SalesAgentAccount::isActive)} active",
                    modifier = Modifier.weight(1f)
                )
                AdminSummaryCard(
                    label = "Onboarding sessions",
                    value = analytics?.sessionCount?.toString() ?: "—",
                    detail = "${analytics?.completedCount ?: 0} completed",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminSection.values().forEach { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = {
                            selectedSection = section
                            if (section == AdminSection.SESSIONS) loadSessions()
                        },
                        label = { Text(section.label) }
                    )
                }
            }

            key(companyId, store) {
                when (selectedSection) {
                    AdminSection.COMPANY -> CompanySettingsSection(
                        industry = companyIndustry,
                        isSaving = isSavingCompanyIndustry,
                        onSaveIndustry = { industry ->
                            isSavingCompanyIndustry = true
                            store.saveCompanyIndustry(industry) { result ->
                                if (!isCurrentAdminScope()) return@saveCompanyIndustry
                                isSavingCompanyIndustry = false
                                handle(result) {
                                    companyIndustry = industry
                                    onCompanyIndustryChanged(industry)
                                }
                            }
                        }
                    )
                    AdminSection.AGENTS -> AgentAccountsSection(
                        agents = agents,
                        onAdd = { email, name, onComplete ->
                            agentStatusMessage = null
                            store.addAgent(email, name) {
                                if (!isCurrentAdminScope()) return@addAgent
                                it.onSuccess {
                                    agentStatusMessage = "Invitation added. Share the company ID with the agent so they can register with this email, verify it, and join your company."
                                    store.observeAgents { refreshed ->
                                        if (!isCurrentAdminScope()) return@observeAgents
                                        handle(refreshed) { agents = it }
                                    }
                                    onComplete(Result.success(Unit))
                                }.onFailure { error ->
                                    errorMessage = error.message ?: "Could not add the agent profile."
                                    onComplete(Result.failure(error))
                                }
                            }
                        },
                        onToggleActive = { agent ->
                            store.setAgentActive(agent.id, !agent.isActive) {
                                if (!isCurrentAdminScope()) return@setAgentActive
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
                                if (!isCurrentAdminScope()) {
                                    onComplete(it)
                                    return@addCustomField
                                }
                                onComplete(it)
                                handle(it) {
                                    val updatedConfiguration = configuration.withCustomField(
                                        customField(fieldId, name, description, type, rules, choices)
                                    )
                                    configuration = updatedConfiguration
                                    if (isCurrentAdminScope()) {
                                        onConfigurationChanged(updatedConfiguration)
                                    }
                                }
                            }
                        },
                        onRequiredChanged = { fieldId, enabled ->
                            store.saveRequiredRule(fieldId, enabled) {
                                if (!isCurrentAdminScope()) return@saveRequiredRule
                                handle(it) {
                                    store.loadRuleConfiguration { loaded ->
                                        if (!isCurrentAdminScope()) return@loadRuleConfiguration
                                        handle(loaded) {
                                            configuration = it
                                            if (isCurrentAdminScope()) {
                                                onConfigurationChanged(it)
                                            }
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
                                if (!isCurrentAdminScope()) return@loadAnalytics
                                isLoadingAnalytics = false
                                it.onSuccess { analytics = it; analyticsErrorMessage = null }
                                    .onFailure { analyticsErrorMessage = it.message ?: "Could not load onboarding analytics." }
                                handle(it)
                            }
                        }
                    )
                    AdminSection.SESSIONS -> SessionHistoryContent(
                        title = "All client sessions",
                        description = "Search and filter the organization’s onboarding records. Details are read-only.",
                        sessions = sessions,
                        agents = agents,
                        isLoading = isLoadingSessions,
                        errorMessage = sessionsErrorMessage,
                        onRefresh = ::loadSessions
                    )
                }
            }

        }
    }
}

@Composable
private fun AdminSummaryCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class AdminSection(val label: String) {
    COMPANY("Company"),
    AGENTS("Sales agents"),
    SESSIONS("Client sessions"),
    RULES("Field rules"),
    ANALYTICS("Analytics")
}

@Composable
private fun CompanySettingsSection(
    industry: String?,
    isSaving: Boolean,
    onSaveIndustry: (String) -> Unit
) {
    var selectedIndustry by remember(industry) { mutableStateOf(industry) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Company onboarding industry", style = MaterialTheme.typography.titleMedium)
        Text(
            "Agents in this company will use the selected industry’s onboarding form. You can change it while the company setup is being finalized.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedIndustry == "banking",
                onClick = { selectedIndustry = "banking" },
                enabled = !isSaving,
                label = { Text("Banking") }
            )
            FilterChip(
                selected = selectedIndustry == "telecom",
                onClick = { selectedIndustry = "telecom" },
                enabled = !isSaving,
                label = { Text("Telecommunications") }
            )
        }
        Button(
            onClick = { selectedIndustry?.let(onSaveIndustry) },
            enabled = !isSaving && selectedIndustry != null && selectedIndustry != industry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSaving) "Saving..." else if (industry == null) "Set company industry" else "Save industry")
        }
    }
}

@Composable
private fun AgentAccountsSection(
    agents: List<SalesAgentAccount>,
    onAdd: (String, String, (Result<Unit>) -> Unit) -> Unit,
    onToggleActive: (SalesAgentAccount) -> Unit
) {
        var email by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var isCreating by remember { mutableStateOf(false) }
        var creationError by remember { mutableStateOf<String?>(null) }
        val normalizedEmail = email.trim()
        val isValidEmail = normalizedEmail.length <= 254 &&
            Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add an agent to the team", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add their work email here. Share the company ID with them; they will register with this email and verify it before joining.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                creationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Agent name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(254) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Work email") },
                    singleLine = true,
                    isError = email.isNotBlank() && !isValidEmail,
                    supportingText = {
                        if (email.isNotBlank() && !isValidEmail) {
                            Text("Enter a valid work email address.")
                        }
                    }
                )
                Button(
                    onClick = {
                        val submittedEmail = email.trim()
                        isCreating = true
                        creationError = null
                        onAdd(submittedEmail, name.trim()) { result ->
                            isCreating = false
                            result.onSuccess {
                                email = ""
                                name = ""
                            }.onFailure {
                                creationError = it.message ?: "Could not create this agent. Check the connection and try again."
                            }
                        }
                    },
                    enabled = !isCreating && isValidEmail && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCreating) "Adding agent..." else "Add agent")
                }
            }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Team members", style = MaterialTheme.typography.titleMedium)
                if (agents.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            "No agents yet. Add an agent above to get started.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    agents.forEach { agent ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        agent.displayName.firstOrNull()?.uppercase() ?: "A",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(agent.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        agent.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        when {
                                            !agent.isActive -> "Inactive"
                                            agent.isRegistered -> "Registered"
                                            else -> "Invitation pending"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (agent.isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = agent.isActive,
                                    onCheckedChange = { onToggleActive(agent) }
                                )
                            }
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
                    "Fields apply to agents in your company and affect new sessions. Custom fields are not added to the AI model vocabulary.",
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
                        isSavingField = true
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
                    },
                    enabled = fieldId.isNotBlank() && fieldName.isNotBlank() &&
                        description.isNotBlank() && !isSavingField &&
                        (fieldType != "choice" || parsedChoices.isNotEmpty()) &&
                        fieldId !in KnownFields.all.map { it.fieldId } &&
                        fieldId !in configuration.customFields.map { it.fieldId },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSavingField) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(if (isSavingField) "Saving field..." else "Add field")
                }
                if (isSavingField) {
                    Text(
                        "Waiting for the database to confirm the field. Keep this screen open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                configuration.customFields.forEach { field ->
                    val ruleLabels = AdminRule.values()
                        .filter { it in field.validationRules.toAdminRules(field.isRequired) }
                        .map(AdminRule::label)
                        .let { labels ->
                            if (field.validationRules.any {
                                    it is OneOf
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
                        field.validationRules.filterIsInstance<OneOf>()
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
                    val required = configuration.rulesFor(field).any { it is Required }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Team insights", style = MaterialTheme.typography.titleLarge)
                Text(
                    "A quick view of onboarding consistency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text(if (isLoading) "Refreshing..." else "Refresh")
            }
        }
        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else if (analytics == null && !isLoading) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    "No onboarding sessions are available yet.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isLoading && analytics == null) {
            Text("Loading team insights...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (analytics != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminSummaryCard(
                    "Completion rate",
                    "${"%.0f".format(analytics.completionRate * 100)}%",
                    "${analytics.completedCount} of ${analytics.sessionCount} sessions",
                    Modifier.weight(1f)
                )
                AdminSummaryCard(
                    "Average time",
                    "${"%.1f".format(analytics.averageCompletionMinutes)} min",
                    "For completed sessions",
                    Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminSummaryCard(
                    "Average progress",
                    "${"%.0f".format(analytics.averageReadinessPercent)}%",
                    "Required fields completed",
                    Modifier.weight(1f)
                )
                AdminSummaryCard(
                    "Total sessions",
                    analytics.sessionCount.toString(),
                    "Across the team",
                    Modifier.weight(1f)
                )
            }
            analytics.commonPausePoint?.let { pausePoint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Common unfinished step", style = MaterialTheme.typography.titleSmall)
                        Text(pausePoint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ConsistencySection(
                report = analytics.consistency,
                fieldNames = analytics.fieldNames,
                agents = agents
            )
            if (analytics.agentPerformance.isNotEmpty()) {
                Text("Completion by agent", style = MaterialTheme.typography.titleMedium)
                analytics.agentPerformance.forEach { performance ->
                    val displayName = agents.firstOrNull {
                        it.email.equals(performance.agentId, ignoreCase = true)
                    }?.displayName ?: performance.agentId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${performance.completedCount} of ${performance.sessionCount} sessions completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${"%.0f".format(performance.completionRate * 100)}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * How closely completed sessions followed the standard process. Plain counts, not
 * a model; see the "Process consistency" region of `Insights.kt` for the definitions
 * and where the limits come from.
 */
@Composable
private fun ConsistencySection(
    report: ConsistencyReport,
    fieldNames: Map<String, String>,
    agents: List<SalesAgentAccount>
) {
    fun percent(share: Double) = "${"%.0f".format(share * 100)}%"
    fun fieldName(fieldId: String) = fieldNames[fieldId] ?: fieldId.replace('_', ' ')

    Text("Process consistency", style = MaterialTheme.typography.titleMedium)
    Text(
        "A completed session follows the standard when at most " +
            "${SessionConsistencyCalculator.MAX_FAILED_CHECKS} answers had to be corrected and at " +
            "most ${SessionConsistencyCalculator.MAX_STEPS_OUT_OF_PLACE} questions came out of " +
            "the standard order. Agents see a section's questions together and can answer them " +
            "in any order, so a few questions out of order is normal; a different order is not " +
            "automatically wrong.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (report.earlierSessionsLeftOut > 0) {
        Text(
            "${report.earlierSessionsLeftOut} earlier completed sessions are not counted here: " +
                "that version checked answers while they were being typed, so its saved checks " +
                "cannot tell a real correction from a pause in typing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (report.sessionCount == 0) {
        Text(
            "No completed sessions with recorded steps yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    AdminSummaryCard(
        "Followed the standard",
        percent(report.followedStandardShare),
        "${report.followedStandardCount} of ${report.sessionCount} completed sessions",
        Modifier.fillMaxWidth()
    )

    if (report.weekly.size > 1) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("By week", style = MaterialTheme.typography.titleSmall)
                report.weekly.forEach { week ->
                    val label = when (week.weeksAgo) {
                        0 -> "Latest week"
                        1 -> "1 week earlier"
                        else -> "${week.weeksAgo} weeks earlier"
                    }
                    Text(
                        "$label: ${percent(week.followedStandardShare)} " +
                            "(${week.sessionCount} sessions)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { week.followedStandardShare.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    val fieldLists = listOf(
        "Most often corrected" to report.mostFailedFields,
        "Most often out of the standard order" to report.mostMisplacedFields
    ).filter { it.second.isNotEmpty() }
    fieldLists.forEach { (title, fields) ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                fields.forEach { field ->
                    Text(
                        "${fieldName(field.fieldId)}: ${field.count} times",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Text("By agent (least consistent first)", style = MaterialTheme.typography.titleSmall)
    report.agents.forEach { agent ->
        val displayName = agents.firstOrNull {
            it.email.equals(agent.agentId, ignoreCase = true)
        }?.displayName?.takeIf(String::isNotBlank) ?: agent.agentId
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${agent.sessionCount} sessions · " +
                            "${"%.1f".format(agent.averageFailedChecks)} corrected · " +
                            "${"%.1f".format(agent.averageStepsOutOfPlace)} out of order (averages)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    percent(agent.followedStandardShare),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun List<ValidationRule>.toAdminRules(
    required: Boolean
): Set<AdminRule> = buildSet {
    if (required) add(AdminRule.REQUIRED)
    if (this@toAdminRules.any { it is NotBlank }) add(AdminRule.NOT_BLANK)
    if (this@toAdminRules.any { it is EmailFormat }) add(AdminRule.EMAIL)
    if (this@toAdminRules.any { it is KenyanPhoneFormat }) add(AdminRule.PHONE)
    if (this@toAdminRules.any { it is PositiveNumber }) add(AdminRule.POSITIVE_NUMBER)
    if (this@toAdminRules.any { it is MustBeTrue }) add(AdminRule.MUST_BE_TRUE)
    if (this@toAdminRules.any { it is DateFormatAndMinimumAge }) add(AdminRule.DATE)
}

private fun customField(
    fieldId: String,
    name: String,
    description: String,
    fieldType: String,
    rules: Set<AdminRule>,
    choices: Set<String>
): FormField = FormField(
    fieldId = fieldId,
    fieldName = name,
    fieldType = fieldType,
    isRequired = rules.any {
        it == AdminRule.REQUIRED || it == AdminRule.NOT_BLANK || it == AdminRule.MUST_BE_TRUE
    },
    validationRules = rules.toValidationRules() +
        if (choices.isEmpty()) emptyList() else listOf(OneOf(choices)),
    description = description
)
