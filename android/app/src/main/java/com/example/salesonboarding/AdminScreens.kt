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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

// region Dashboard frame and data

private enum class AdminSection(val label: String, val icon: Int) {
    OVERVIEW("Overview", R.drawable.ic_sm_overview),
    AGENTS("Agents", R.drawable.ic_sm_agents),
    SESSIONS("Sessions", R.drawable.ic_sm_sessions),
    QUESTIONS("Questions", R.drawable.ic_sm_questions),
    COMPANY("Company", R.drawable.ic_sm_company)
}

/**
 * The admin's screens behind an always-visible bottom bar. State is keyed by company and
 * store, and late callbacks that belong to another company are ignored, so a previous
 * tenant's data can never appear here.
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
    var openSession by remember(companyId, store) { mutableStateOf<StoredOnboardingSession?>(null) }

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

    // Always visible, so no page is hidden off the edge of the screen.
    val bottomBar: @Composable ColumnScope.() -> Unit = {
        SmBottomBar(
            items = AdminSection.entries.map { SmNavItem(it.label, it.icon) },
            selected = selectedSection.ordinal,
            onSelect = { index ->
                selectedSection = AdminSection.entries[index]
                openSession = null
                if (selectedSection == AdminSection.SESSIONS) loadSessions()
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Sm.colors.white)
    ) {
        key(companyId, store) {
            when (selectedSection) {
                AdminSection.OVERVIEW -> AdminOverviewPage(
                    companyId = companyId,
                    analytics = analytics,
                    agents = agents,
                    isLoading = isLoadingAnalytics,
                    errorMessage = analyticsErrorMessage ?: errorMessage,
                    onRefresh = ::loadAnalytics,
                    onSignOut = onSignOut,
                    bottomBar = bottomBar
                )
                AdminSection.AGENTS -> AgentsPage(
                    agents = agents,
                    errorMessage = errorMessage,
                    statusMessage = agentStatusMessage,
                    onSignOut = onSignOut,
                    bottomBar = bottomBar,
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
                AdminSection.SESSIONS -> if (openSession != null) {
                    SessionDetailScreen(
                        session = openSession!!,
                        agentName = agents.firstOrNull {
                            it.email.equals(openSession!!.agentEmail, ignoreCase = true)
                        }?.displayName,
                        onBack = { openSession = null },
                        bottomBar = bottomBar
                    )
                } else AppScreen(
                    title = "Sessions",
                    subtitle = "The company's onboarding records. Answers are read-only.",
                    bottomBar = bottomBar,
                    actions = {
                        HeaderButton(R.drawable.ic_sm_refresh, "Refresh", ::loadSessions, enabled = !isLoadingSessions)
                        HeaderButton(R.drawable.ic_sm_sign_out, "Sign out", onSignOut)
                    }
                ) {
                    SessionHistoryContent(
                        sessions = sessions,
                        agents = agents,
                        isLoading = isLoadingSessions,
                        errorMessage = sessionsErrorMessage,
                        onOpenSession = { openSession = it }
                    )
                }
                AdminSection.QUESTIONS -> QuestionsPage(
                    configuration = configuration,
                    industry = companyIndustry,
                    errorMessage = errorMessage,
                    onSignOut = onSignOut,
                    bottomBar = bottomBar,
                    onUpdateField = { fieldId, name, description, type, rules, choices, onComplete ->
                        store.updateCustomField(fieldId, name, description, type, rules, choices) {
                            onComplete(it)
                            if (!isCurrentAdminScope()) return@updateCustomField
                            handle(it) {
                                val updatedConfiguration = configuration.withCustomField(
                                    customField(fieldId, name, description, type, rules, choices)
                                )
                                configuration = updatedConfiguration
                                onConfigurationChanged(updatedConfiguration)
                            }
                        }
                    },
                    onDeleteField = { fieldId, onComplete ->
                        store.deleteCustomField(fieldId) {
                            onComplete(it)
                            if (!isCurrentAdminScope()) return@deleteCustomField
                            handle(it) {
                                val updatedConfiguration = configuration.withoutCustomField(fieldId)
                                configuration = updatedConfiguration
                                onConfigurationChanged(updatedConfiguration)
                            }
                        }
                    },
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
                    bottomBar = bottomBar,
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
}

/** The frame of the Agents, Questions and Company pages: title, sign out, any error. */
@Composable
private fun AdminPage(
    title: String,
    subtitle: String,
    errorMessage: String?,
    onSignOut: () -> Unit,
    bottomBar: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppScreen(
        title = title,
        subtitle = subtitle,
        bottomBar = bottomBar,
        actions = { HeaderButton(R.drawable.ic_sm_sign_out, "Sign out", onSignOut) }
    ) {
        errorMessage?.let { Banner(it, Tone.Negative) }
        content()
    }
}

/** A plain text box in the app's colours. */
@Composable
private fun AdminTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    searchIcon: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = if (searchIcon) {
            { SmIcon(R.drawable.ic_sm_search, Sm.colors.muted) }
        } else null,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sm.colors.ink,
            unfocusedBorderColor = Sm.colors.border,
            errorBorderColor = Sm.colors.cherry,
            focusedTextColor = Sm.colors.ink,
            unfocusedTextColor = Sm.colors.ink,
            cursorColor = Sm.colors.ink,
            focusedLabelColor = Sm.colors.ink,
            unfocusedLabelColor = Sm.colors.muted
        )
    )
}

// endregion

// region Agents

private enum class AgentFilter(val label: String) {
    ALL("All"), ACTIVE("Active"), PENDING("Pending"), OFF("Inactive")
}

private fun SalesAgentAccount.matches(filter: AgentFilter) = when (filter) {
    AgentFilter.ALL -> true
    AgentFilter.ACTIVE -> isActive && isRegistered
    AgentFilter.PENDING -> isActive && !isRegistered
    AgentFilter.OFF -> !isActive
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AgentsPage(
    agents: List<SalesAgentAccount>,
    errorMessage: String?,
    statusMessage: String?,
    onSignOut: () -> Unit,
    bottomBar: @Composable ColumnScope.() -> Unit,
    onAdd: (String, String, (Result<Unit>) -> Unit) -> Unit,
    onToggleActive: (SalesAgentAccount) -> Unit
) {
    var showInvite by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(AgentFilter.ALL) }
    var search by remember { mutableStateOf("") }
    val query = search.trim().lowercase(Locale.ROOT)
    val shown = agents
        .filter { it.matches(filter) }
        .filter { query.isBlank() || query in it.displayName.lowercase(Locale.ROOT) || query in it.email.lowercase(Locale.ROOT) }
        .sortedBy { it.displayName.lowercase(Locale.ROOT) }

    AdminPage(
        title = "Agents",
        subtitle = "${agents.count(SalesAgentAccount::isActive)} active of ${agents.size} on the team",
        errorMessage = errorMessage,
        onSignOut = onSignOut,
        bottomBar = bottomBar
    ) {
        statusMessage?.let { Banner(it, Tone.Positive) }
        if (agents.isNotEmpty()) {
            val registered = agents.count(SalesAgentAccount::isRegistered)
            SectionCard(title = "Invites that registered") {
                ProgressBar(registered / agents.size.toFloat())
                Text(
                    "$registered of ${agents.size} invited agents have created their account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sm.colors.muted
                )
            }
        }
        PrimaryButton("Invite an agent", onClick = { showInvite = true }, iconRes = R.drawable.ic_sm_plus)
        if (agents.isNotEmpty()) {
            AdminTextField(search, { search = it }, placeholder = "Search by name or email", searchIcon = true)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s)
            ) {
                AgentFilter.entries.forEach { item ->
                    SmChip(
                        "${item.label} ${agents.count { it.matches(item) }}",
                        selected = filter == item,
                        onClick = { filter = item }
                    )
                }
            }
        }
        SectionCard(title = "Team") {
            if (shown.isEmpty()) {
                EmptyState(
                    title = if (agents.isEmpty()) "No agents yet" else "Nothing matches",
                    message = if (agents.isEmpty()) "Invite an agent to get started." else "Try a different search or filter.",
                    iconRes = R.drawable.ic_sm_agents
                )
            }
            shown.forEachIndexed { index, agent ->
                if (index > 0) HorizontalDivider(color = Sm.colors.divider)
                AgentRow(agent, onToggleActive)
            }
        }
    }

    if (showInvite) {
        InviteSheet(onDismiss = { showInvite = false }, onAdd = onAdd)
    }
}

@Composable
private fun InviteSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, (Result<Unit>) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var creationError by remember { mutableStateOf<String?>(null) }
    val normalizedEmail = email.trim()
    val isValidEmail = normalizedEmail.length <= 254 &&
        Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()

    SmSheet("Invite an agent", onDismiss = onDismiss) {
        Text(
            "Add their work email. Share the company ID with them; they register with this email to join.",
            style = MaterialTheme.typography.bodyMedium,
            color = Sm.colors.muted
        )
        creationError?.let { Banner(it, Tone.Negative) }
        AdminTextField(name, { name = it }, label = "Agent name")
        AdminTextField(
            email,
            { email = it.take(254) },
            label = "Work email",
            isError = email.isNotBlank() && !isValidEmail,
            supportingText = if (email.isNotBlank() && !isValidEmail) "Enter a valid work email address." else null
        )
        PrimaryButton(
            text = if (isCreating) "Adding agent…" else "Add agent",
            onClick = {
                isCreating = true
                creationError = null
                onAdd(email.trim(), name.trim()) { result ->
                    isCreating = false
                    result.onSuccess { onDismiss() }
                        .onFailure {
                            creationError = it.message ?: "Could not create this agent. Check the connection and try again."
                        }
                }
            },
            enabled = isValidEmail && name.isNotBlank(),
            isLoading = isCreating,
            iconRes = R.drawable.ic_sm_plus
        )
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
                .background(Sm.colors.lime, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                agent.displayName.firstOrNull()?.uppercase() ?: "A",
                color = Sm.colors.ink,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(agent.displayName, style = MaterialTheme.typography.titleSmall, color = Sm.colors.ink)
            Text(agent.email, style = MaterialTheme.typography.bodySmall, color = Sm.colors.muted)
            when {
                !agent.isActive -> StatusPill("Inactive", Tone.Neutral)
                agent.isRegistered -> StatusPill("Registered", Tone.Positive)
                else -> StatusPill("Invitation pending", Tone.Caution)
            }
        }
        SmSwitch(
            checked = agent.isActive,
            onCheckedChange = { onToggleActive(agent) },
            modifier = Modifier.semantics {
                contentDescription = if (agent.isActive) "${agent.displayName} is active" else "${agent.displayName} is inactive"
            }
        )
    }
}

/** A thin share bar: teal fill on a light teal track. */
@Composable
private fun ProgressBar(share: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 12.dp)
            .background(Sm.colors.tealTrack, RoundedCornerShape(6.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(share.coerceIn(0f, 1f))
                .heightIn(min = 12.dp)
                .background(Sm.colors.teal, RoundedCornerShape(6.dp))
        )
    }
}

// endregion

// region Questions

private typealias QuestionSaver =
    (String, String, String, String, Set<AdminRule>, Set<String>, (Result<Unit>) -> Unit) -> Unit

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestionsPage(
    configuration: FieldRuleConfiguration,
    industry: String?,
    errorMessage: String?,
    onSignOut: () -> Unit,
    bottomBar: @Composable ColumnScope.() -> Unit,
    onAddField: QuestionSaver,
    onUpdateField: QuestionSaver,
    onDeleteField: (String, (Result<Unit>) -> Unit) -> Unit,
    onRequiredChanged: (String, Boolean) -> Unit
) {
    var showCustom by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FormField?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FormField?>(null) }
    var search by remember { mutableStateOf("") }
    val query = search.trim().lowercase(Locale.ROOT)
    val locked = FieldRuleConfiguration.DEFAULT_LOCKED_FIELD_IDS
    // Only the questions this company's industry actually asks.
    val industryIds = industry?.let(OnboardingPlaybooks::fieldIdsFor)
    val ofIndustry = KnownFields.all.filter { industryIds == null || it.fieldId in industryIds }
    val requiredCount = ofIndustry.count { field -> configuration.rulesFor(field).any { it is Required } }
    val groups = ofIndustry
        .filter { query.isBlank() || query in it.fieldName.lowercase(Locale.ROOT) }
        .groupBy { QuestionGroups.of(it.fieldId) }

    AdminPage(
        title = "Questions",
        subtitle = "What agents ask, and which answers are required. Changes apply to new sessions.",
        errorMessage = errorMessage,
        onSignOut = onSignOut,
        bottomBar = bottomBar
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SmChip("Standard", selected = !showCustom, onClick = { showCustom = false })
            SmChip("Yours ${configuration.customFields.size}", selected = showCustom, onClick = { showCustom = true })
        }
        if (!showCustom) {
            Text(
                "$requiredCount of ${ofIndustry.size} required",
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
            AdminTextField(search, { search = it }, placeholder = "Search questions", searchIcon = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                TextButton(onClick = {
                    ofIndustry.filter { it.fieldId !in locked }.forEach { onRequiredChanged(it.fieldId, true) }
                }) { Text("Make all required") }
                TextButton(onClick = {
                    ofIndustry.filter { it.fieldId !in locked }.forEach { onRequiredChanged(it.fieldId, false) }
                }) { Text("Make all optional") }
            }
            if (groups.isEmpty()) {
                EmptyState("Nothing matches", "Try a different search.", iconRes = R.drawable.ic_sm_questions)
            }
            QuestionGroups.order.mapNotNull { name -> groups[name]?.let { name to it } }.forEach { (name, fields) ->
                Text(name.uppercase(Locale.ROOT), style = smCapsStyle(), color = Sm.colors.muted)
                SectionCard {
                    fields.forEachIndexed { index, field ->
                        if (index > 0) HorizontalDivider(color = Sm.colors.divider)
                        val required = configuration.rulesFor(field).any { it is Required }
                        val isLocked = field.fieldId in locked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s)
                        ) {
                            Text(field.fieldName, style = MaterialTheme.typography.bodyMedium, color = Sm.colors.ink, modifier = Modifier.weight(1f))
                            if (isLocked) SmIcon(R.drawable.ic_sm_lock, Sm.colors.muted, size = 18.dp, description = "Locked")
                            SmSwitch(
                                checked = required,
                                onCheckedChange = { onRequiredChanged(field.fieldId, it) },
                                enabled = !isLocked,
                                modifier = Modifier.semantics {
                                    contentDescription = "${field.fieldName} is " + if (required) "required" else "optional"
                                }
                            )
                        }
                    }
                }
            }
            Text(
                "Locked questions always stay required.",
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
        } else {
            PrimaryButton("Add a question", onClick = { showAdd = true }, iconRes = R.drawable.ic_sm_plus)
            SectionCard(subtitle = "Extra questions shown in a last \"Company questions\" section. The AI model was not trained on them.") {
                if (configuration.customFields.isEmpty()) {
                    EmptyState("None yet", "Questions you add appear here.", iconRes = R.drawable.ic_sm_questions)
                }
                configuration.customFields.forEachIndexed { index, field ->
                    if (index > 0) HorizontalDivider(color = Sm.colors.divider)
                    val ruleLabels = AdminRule.entries
                        .filter { it in field.validationRules.toAdminRules(field.isRequired) }
                        .map(AdminRule::label)
                        .let { labels -> if (field.validationRules.any { it is OneOf }) labels + "Approved choices" else labels }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(field.fieldName, style = MaterialTheme.typography.titleSmall, color = Sm.colors.ink)
                        Text(field.description, style = MaterialTheme.typography.bodySmall, color = Sm.colors.ink)
                        Text(
                            "ID ${field.fieldId} · ${field.fieldType} · rules: ${ruleLabels.joinToString().ifBlank { "none" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sm.colors.muted
                        )
                        field.validationRules.filterIsInstance<OneOf>().flatMap { it.choices }
                            .takeIf { it.isNotEmpty() }
                            ?.let { Text("Options: ${it.joinToString()}", style = MaterialTheme.typography.bodySmall, color = Sm.colors.ink) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            TextButton(onClick = { editing = field }) { Text("Edit") }
                            TextButton(onClick = { deleting = field }) { Text("Delete", color = Sm.colors.cherry) }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        QuestionSheet(configuration, existing = null, onDismiss = { showAdd = false }, onSave = onAddField)
    }
    editing?.let { field ->
        QuestionSheet(configuration, existing = field, onDismiss = { editing = null }, onSave = onUpdateField)
    }
    deleting?.let { field ->
        var isDeleting by remember(field) { mutableStateOf(false) }
        var deleteError by remember(field) { mutableStateOf<String?>(null) }
        SmSheet("Delete this question?", onDismiss = { deleting = null }) {
            Text(
                "\"${field.fieldName}\" will no longer be asked in new sessions. Sessions already saved keep their answers.",
                style = MaterialTheme.typography.bodyMedium,
                color = Sm.colors.muted
            )
            deleteError?.let { Banner(it, Tone.Negative) }
            PrimaryButton(
                text = if (isDeleting) "Deleting…" else "Delete question",
                onClick = {
                    isDeleting = true
                    onDeleteField(field.fieldId) { result ->
                        isDeleting = false
                        result.onSuccess { deleting = null }
                            .onFailure { deleteError = it.message ?: "Could not delete the question." }
                    }
                },
                isLoading = isDeleting
            )
            SecondaryButton("Keep it", onClick = { deleting = null })
        }
    }
}

/** Adds a question, or edits [existing] (its ID stays the same). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestionSheet(
    configuration: FieldRuleConfiguration,
    existing: FormField?,
    onDismiss: () -> Unit,
    onSave: QuestionSaver
) {
    var fieldName by remember { mutableStateOf(existing?.fieldName.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var fieldType by remember { mutableStateOf(existing?.fieldType ?: "text") }
    var choiceOptions by remember {
        mutableStateOf(existing?.validationRules?.filterIsInstance<OneOf>()?.flatMap { it.choices }?.joinToString(", ").orEmpty())
    }
    var selectedRules by remember {
        mutableStateOf(existing?.validationRules?.toAdminRules(existing.isRequired) ?: setOf(AdminRule.REQUIRED))
    }
    var isSavingField by remember { mutableStateOf(false) }
    val typedId = remember(fieldName) {
        fieldName.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }
    val fieldId = existing?.fieldId ?: typedId
    val parsedChoices = choiceOptions.split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toSet()
        .takeIf { fieldType == "choice" }
        .orEmpty()
    val idTaken = existing == null && (
        fieldId in KnownFields.all.map { it.fieldId } ||
            fieldId in configuration.customFields.map { it.fieldId }
        )

    SmSheet(if (existing == null) "Add a question" else "Edit question", onDismiss = onDismiss) {
        AdminTextField(fieldName, { fieldName = it }, label = "Question name")
        AdminTextField(description, { description = it }, label = "Instructions for the agent", singleLine = false)
        Text(
            when {
                fieldId.isBlank() -> "Enter a name to create the question's ID."
                idTaken -> "ID $fieldId is already used. Choose another name."
                else -> "ID: $fieldId"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (idTaken) Sm.colors.cherry else Sm.colors.muted
        )
        Text("ANSWER TYPE", style = smCapsStyle(), color = Sm.colors.muted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            listOf("text", "number", "date", "email", "phone", "boolean", "choice").forEach { type ->
                SmChip(type.replaceFirstChar(Char::uppercase), selected = fieldType == type, onClick = { fieldType = type })
            }
        }
        if (fieldType == "choice") {
            AdminTextField(
                choiceOptions,
                { choiceOptions = it },
                label = "Approved choices (comma-separated)",
                supportingText = "Agents will pick one of these."
            )
        }
        Text("CHECKS", style = smCapsStyle(), color = Sm.colors.muted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            AdminRule.entries.forEach { rule ->
                SmChip(
                    rule.label,
                    selected = rule in selectedRules,
                    onClick = { selectedRules = if (rule in selectedRules) selectedRules - rule else selectedRules + rule }
                )
            }
        }
        PrimaryButton(
            text = when {
                isSavingField -> "Saving question…"
                existing == null -> "Add question"
                else -> "Save changes"
            },
            onClick = {
                isSavingField = true
                onSave(
                    fieldId,
                    fieldName.trim(),
                    description.trim(),
                    fieldType.trim().lowercase(Locale.ROOT),
                    selectedRules,
                    parsedChoices
                ) { result ->
                    isSavingField = false
                    if (result.isSuccess) onDismiss()
                }
            },
            enabled = fieldId.isNotBlank() && fieldName.isNotBlank() && description.isNotBlank() &&
                (fieldType != "choice" || parsedChoices.isNotEmpty()) && !idTaken,
            isLoading = isSavingField,
            iconRes = if (existing == null) R.drawable.ic_sm_plus else R.drawable.ic_sm_check
        )
        if (isSavingField) {
            Text(
                "Waiting for the database to confirm the question. Keep this screen open.",
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
        }
    }
}

// endregion

// region Company

private val INDUSTRY_CHOICES = listOf(
    "banking" to "Banking",
    "telecom" to "Telecommunications"
)

@Composable
private fun CompanyPage(
    companyId: String,
    industry: String?,
    isSaving: Boolean,
    errorMessage: String?,
    onSignOut: () -> Unit,
    bottomBar: @Composable ColumnScope.() -> Unit,
    onSaveIndustry: (String) -> Unit
) {
    var selectedIndustry by remember(industry) { mutableStateOf(industry) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AdminPage(
        title = "Company",
        subtitle = "Your company's settings",
        errorMessage = errorMessage,
        onSignOut = onSignOut,
        bottomBar = bottomBar
    ) {
        SectionCard(
            title = "Company ID",
            subtitle = "Agents register with this ID and the work email you added on the Agents page."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    companyId,
                    style = MaterialTheme.typography.titleMedium,
                    color = Sm.colors.ink,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = if (copied) "Copied" else "Copy",
                    onClick = {
                        clipboard.setText(AnnotatedString(companyId))
                        copied = true
                    },
                    iconRes = R.drawable.ic_sm_copy
                )
            }
        }
        if (industry == null) {
            Banner(
                "Agents cannot sign in until you choose an industry.",
                Tone.Caution,
                title = "Industry not set"
            )
        }
        SectionCard(
            title = "Onboarding industry",
            subtitle = "Agents in this company use this industry's onboarding form."
        ) {
            INDUSTRY_CHOICES.forEach { (value, label) ->
                val chosen = selectedIndustry == value
                Surface(
                    onClick = { if (!isSaving) selectedIndustry = value },
                    shape = RoundedCornerShape(18.dp),
                    color = if (chosen) Sm.colors.ink else Sm.colors.white,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (chosen) Sm.colors.ink else Sm.colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(Space.l),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.m)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (chosen) Sm.colors.cream else Sm.colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        if (value == industry) StatusPill("Saved", Tone.Positive, showIcon = true)
                        else if (chosen) StatusPill("Not saved", Tone.Caution)
                    }
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
