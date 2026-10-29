package com.example.salesonboarding

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

// region Dashboard frame and data

private enum class AdminSection(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Home),
    AGENTS("Agents", Icons.Filled.Person),
    SESSIONS("Sessions", Icons.AutoMirrored.Filled.List),
    QUESTIONS("Questions", Icons.Filled.Edit),
    COMPANY("Company", Icons.Filled.Settings)
}

/**
 * The admin's screens behind an always-visible bottom navigation bar. State is keyed by
 * company and store, and late callbacks that belong to another company are ignored, so a
 * previous tenant's data can never appear here.
 */
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

    var selectedSection by remember(companyId, store) { mutableStateOf(AdminSection.OVERVIEW) }
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

    fun loadAnalytics() {
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
        loadAnalytics()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(Modifier.weight(1f)) {
            key(companyId, store) {
                when (selectedSection) {
                    AdminSection.OVERVIEW -> AdminOverviewPage(
                        companyId = companyId,
                        analytics = analytics,
                        agents = agents,
                        isLoading = isLoadingAnalytics,
                        errorMessage = analyticsErrorMessage ?: errorMessage,
                        onRefresh = ::loadAnalytics,
                        onSignOut = onSignOut
                    )
                    AdminSection.AGENTS -> AgentsPage(
                        agents = agents,
                        errorMessage = errorMessage,
                        statusMessage = agentStatusMessage,
                        onSignOut = onSignOut,
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
                    AdminSection.SESSIONS -> AppScreen(
                        title = "Sessions",
                        subtitle = "The company's onboarding records. Answers are read-only.",
                        actions = {
                            IconButton(onClick = ::loadSessions, enabled = !isLoadingSessions) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                            IconButton(onClick = onSignOut) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                            }
                        }
                    ) {
                        SessionHistoryContent(
                            sessions = sessions,
                            agents = agents,
                            isLoading = isLoadingSessions,
                            errorMessage = sessionsErrorMessage
                        )
                    }
                    AdminSection.QUESTIONS -> QuestionsPage(
                        configuration = configuration,
                        errorMessage = errorMessage,
                        onSignOut = onSignOut,
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
                    AdminSection.COMPANY -> CompanyPage(
                        companyId = companyId,
                        industry = companyIndustry,
                        isSaving = isSavingCompanyIndustry,
                        errorMessage = errorMessage,
                        onSignOut = onSignOut,
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
                }
            }
        }
        // Always visible, so no page is hidden off the edge of the screen. The activity's
        // Scaffold already pads the system navigation bar.
        NavigationBar(windowInsets = WindowInsets(0)) {
            AdminSection.entries.forEach { section ->
                NavigationBarItem(
                    selected = selectedSection == section,
                    onClick = {
                        selectedSection = section
                        if (section == AdminSection.SESSIONS) loadSessions()
                    },
                    icon = { Icon(section.icon, contentDescription = null) },
                    label = { Text(section.label) }
                )
            }
        }
    }
}

/** The frame of the Agents, Questions and Company pages: title, sign out, any error. */
@Composable
private fun AdminPage(
    title: String,
    subtitle: String,
    errorMessage: String?,
    onSignOut: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppScreen(
        title = title,
        subtitle = subtitle,
        actions = {
            IconButton(onClick = onSignOut) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
            }
        }
    ) {
        errorMessage?.let { Banner(it, Tone.Negative) }
        content()
    }
}

// endregion

// region Agents

@Composable
private fun AgentsPage(
    agents: List<SalesAgentAccount>,
    errorMessage: String?,
    statusMessage: String?,
    onSignOut: () -> Unit,
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

    AdminPage(
        title = "Agents",
        subtitle = "${agents.count(SalesAgentAccount::isActive)} active of ${agents.size} on the team",
        errorMessage = errorMessage,
        onSignOut = onSignOut
    ) {
        statusMessage?.let { Banner(it, Tone.Positive) }
        SectionCard(
            title = "Invite an agent",
            subtitle = "Add their work email. Share the company ID with them; they register with this email to join."
        ) {
            creationError?.let { Banner(it, Tone.Negative) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Agent name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(254) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Work email") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                isError = email.isNotBlank() && !isValidEmail,
                supportingText = if (email.isNotBlank() && !isValidEmail) {
                    { Text("Enter a valid work email address.") }
                } else null
            )
            PrimaryButton(
                text = if (isCreating) "Adding agent…" else "Add agent",
                onClick = {
                    isCreating = true
                    creationError = null
                    onAdd(email.trim(), name.trim()) { result ->
                        isCreating = false
                        result.onSuccess {
                            email = ""
                            name = ""
                        }.onFailure {
                            creationError = it.message ?: "Could not create this agent. Check the connection and try again."
                        }
                    }
                },
                enabled = isValidEmail && name.isNotBlank(),
                isLoading = isCreating,
                icon = Icons.Filled.Add
            )
        }
        SectionCard(title = "Team") {
            if (agents.isEmpty()) {
                EmptyState(
                    title = "No agents yet",
                    message = "Invite an agent above to get started.",
                    icon = Icons.Filled.Person
                )
            }
            agents.forEachIndexed { index, agent ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AgentRow(agent, onToggleActive)
            }
        }
    }
}

@Composable
private fun AgentRow(agent: SalesAgentAccount, onToggleActive: (SalesAgentAccount) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                agent.displayName.firstOrNull()?.uppercase() ?: "A",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(agent.displayName, style = MaterialTheme.typography.titleSmall)
            Text(agent.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                !agent.isActive -> StatusPill("Inactive", Tone.Neutral)
                agent.isRegistered -> StatusPill("Registered", Tone.Positive)
                else -> StatusPill("Invitation pending", Tone.Neutral)
            }
        }
        Switch(
            checked = agent.isActive,
            onCheckedChange = { onToggleActive(agent) },
            modifier = Modifier.semantics {
                contentDescription = if (agent.isActive) "${agent.displayName} is active" else "${agent.displayName} is inactive"
            }
        )
    }
}

// endregion

// region Questions

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestionsPage(
    configuration: FieldRuleConfiguration,
    errorMessage: String?,
    onSignOut: () -> Unit,
    onAddField: (String, String, String, String, Set<AdminRule>, Set<String>, (Result<Unit>) -> Unit) -> Unit,
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
    val parsedChoices = choiceOptions.split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toSet()
        .takeIf { fieldType == "choice" }
        .orEmpty()
    val idTaken = fieldId in KnownFields.all.map { it.fieldId } ||
        fieldId in configuration.customFields.map { it.fieldId }

    AdminPage(
        title = "Questions",
        subtitle = "What agents ask, and which answers are required. Changes apply to new sessions.",
        errorMessage = errorMessage,
        onSignOut = onSignOut
    ) {
        SectionCard(
            title = "Company questions",
            subtitle = "Extra questions shown in a last \"Company questions\" section. The AI model was not trained on them."
        ) {
            if (configuration.customFields.isEmpty()) {
                Text("None yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            configuration.customFields.forEach { field ->
                val ruleLabels = AdminRule.entries
                    .filter { it in field.validationRules.toAdminRules(field.isRequired) }
                    .map(AdminRule::label)
                    .let { labels -> if (field.validationRules.any { it is OneOf }) labels + "Approved choices" else labels }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(field.fieldName, style = MaterialTheme.typography.titleSmall)
                    Text(field.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "ID ${field.fieldId} · ${field.fieldType} · rules: ${ruleLabels.joinToString().ifBlank { "none" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    field.validationRules.filterIsInstance<OneOf>().flatMap { it.choices }
                        .takeIf { it.isNotEmpty() }
                        ?.let { Text("Options: ${it.joinToString()}", style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text("Add a question", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(fieldName, { fieldName = it }, Modifier.fillMaxWidth(), label = { Text("Question name") }, singleLine = true, shape = MaterialTheme.shapes.medium)
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Instructions for the agent") }, shape = MaterialTheme.shapes.medium)
            Text(
                when {
                    fieldId.isBlank() -> "Enter a name to create the question's ID."
                    idTaken -> "ID $fieldId is already used. Choose another name."
                    else -> "ID: $fieldId"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (idTaken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Answer type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf("text", "number", "date", "email", "phone", "boolean", "choice").forEach { type ->
                    FilterChip(
                        selected = fieldType == type,
                        onClick = { fieldType = type },
                        label = { Text(type.replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
            if (fieldType == "choice") {
                OutlinedTextField(
                    value = choiceOptions,
                    onValueChange = { choiceOptions = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Approved choices (comma-separated)") },
                    supportingText = { Text("Agents will pick one of these.") },
                    shape = MaterialTheme.shapes.medium
                )
            }
            Text("Checks", style = MaterialTheme.typography.labelLarge)
            AdminRule.entries.forEach { rule ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rule in selectedRules,
                        onCheckedChange = { checked -> selectedRules = if (checked) selectedRules + rule else selectedRules - rule }
                    )
                    Text(rule.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            PrimaryButton(
                text = if (isSavingField) "Saving question…" else "Add question",
                onClick = {
                    isSavingField = true
                    onAddField(
                        fieldId,
                        fieldName.trim(),
                        description.trim(),
                        fieldType.trim().lowercase(Locale.ROOT),
                        selectedRules,
                        parsedChoices
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
                enabled = fieldId.isNotBlank() && fieldName.isNotBlank() && description.isNotBlank() &&
                    (fieldType != "choice" || parsedChoices.isNotEmpty()) && !idTaken,
                isLoading = isSavingField,
                icon = Icons.Filled.Add
            )
            if (isSavingField) {
                Text(
                    "Waiting for the database to confirm the question. Keep this screen open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SectionCard(
            title = "Required questions",
            subtitle = "Turn a switch off to make a question optional. Some identity questions always stay required."
        ) {
            KnownFields.all.forEachIndexed { index, field ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val required = configuration.rulesFor(field).any { it is Required }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(field.fieldName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = required,
                        onCheckedChange = { onRequiredChanged(field.fieldId, it) },
                        modifier = Modifier.semantics {
                            contentDescription = "${field.fieldName} is " + if (required) "required" else "optional"
                        }
                    )
                }
            }
        }
    }
}

// endregion

// region Company

@Composable
private fun CompanyPage(
    companyId: String,
    industry: String?,
    isSaving: Boolean,
    errorMessage: String?,
    onSignOut: () -> Unit,
    onSaveIndustry: (String) -> Unit
) {
    var selectedIndustry by remember(industry) { mutableStateOf(industry) }
    AdminPage(
        title = "Company",
        subtitle = "Company ID $companyId",
        errorMessage = errorMessage,
        onSignOut = onSignOut
    ) {
        SectionCard(
            title = "Onboarding industry",
            subtitle = "Agents in this company use this industry's onboarding form. Agents cannot sign in until it is set."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf("banking" to "Banking", "telecom" to "Telecommunications").forEach { (value, label) ->
                    FilterChip(
                        selected = selectedIndustry == value,
                        onClick = { selectedIndustry = value },
                        enabled = !isSaving,
                        label = { Text(label) }
                    )
                }
            }
            PrimaryButton(
                text = when {
                    isSaving -> "Saving…"
                    industry == null -> "Set company industry"
                    else -> "Save industry"
                },
                onClick = { selectedIndustry?.let(onSaveIndustry) },
                enabled = selectedIndustry != null && selectedIndustry != industry,
                isLoading = isSaving
            )
        }
        SectionCard(title = "Inviting agents") {
            Text(
                "Agents register with the company ID \"$companyId\" and the work email you added on the Agents page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// endregion

// region Helpers

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

// endregion
