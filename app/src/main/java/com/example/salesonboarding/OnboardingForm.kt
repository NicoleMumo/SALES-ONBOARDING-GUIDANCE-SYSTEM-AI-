package com.example.salesonboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

// region The form, connected to its controller

@Composable
fun OnboardingScreen(
    controller: OnboardingFormController,
    onNewOnboarding: () -> Unit = {},
    onReturnToDashboard: () -> Unit = {},
    configurationWarning: String? = null,
    modifier: Modifier = Modifier
) {
    val state by controller.stateFlow.collectAsState()
    val guidance by controller.guidanceFlow.collectAsState()
    val draftSaveStatus by controller.draftSaveStatusFlow.collectAsState()

    OnboardingForm(
        playbook = controller.playbook,
        state = state,
        guidance = guidance,
        readiness = controller.readiness(state),
        canSubmit = controller.canContinue(state),
        isOnline = rememberNetworkStatus(),
        onValueChange = { fieldId, value ->
            controller.setResumeField(fieldId)
            controller.updateField(fieldId, value)
        },
        onCheckField = controller::checkField,
        onCheckPending = controller::checkPendingFields,
        // Read the latest state: pending answers were checked just before submitting.
        onSubmit = { onResult -> controller.submit(controller.stateFlow.value, onResult) },
        draftSaveStatus = draftSaveStatus.takeIf { controller.supportsDraftSaving },
        configurationWarning = configurationWarning,
        initialFieldId = controller.resumeFieldId,
        onSectionOpened = controller::setResumeField,
        onNewOnboarding = onNewOnboarding,
        onReturnToDashboard = onReturnToDashboard,
        modifier = modifier
    )
}

@Composable
fun rememberOnboardingController(
    sessionId: String,
    scope: CoroutineScope,
    entryStore: FieldEntryStore,
    predictor: NextFieldPredictor? = null,
    industry: String = "telecom",
    context: String = "individual",
    agentUid: String = "unknown-agent",
    fields: List<FormField> = OnboardingFormController.DEFAULT_FIELDS,
    playbook: OnboardingPlaybook? = null,
    initialModelUnavailable: Boolean = false,
    agentEmail: String = "",
    initialValues: Map<String, String?> = emptyMap(),
    initialResumeFieldId: String? = null,
    isResumingSession: Boolean = false,
    riskModel: TrainedCompletionRiskModel? = null
): OnboardingFormController = remember(
    sessionId, scope, entryStore, predictor, industry, context,
    agentUid, fields, playbook, initialModelUnavailable, agentEmail,
    initialValues, initialResumeFieldId, isResumingSession, riskModel
) {
    OnboardingFormController(
        sessionId,
        fields = fields,
        scope = scope,
        entryStore = entryStore,
        predictor = predictor,
        industry = industry,
        context = context,
        agentId = agentUid,
        playbook = playbook,
        initialModelUnavailable = initialModelUnavailable,
        agentEmail = agentEmail,
        initialValues = initialValues,
        initialResumeFieldId = initialResumeFieldId,
        isResumingSession = isResumingSession,
        riskModel = riskModel
    )
}

// endregion

// region Pages: sections, review, finished

/**
 * The agent's form: one page per section (see [sections]), then a review page. The agent
 * can move between sections freely; submitting needs every required answer to be valid.
 */
@Composable
fun OnboardingForm(
    playbook: OnboardingPlaybook,
    state: LiveValidationState,
    guidance: NextFieldGuidance,
    readiness: OnboardingReadiness,
    canSubmit: Boolean,
    isOnline: Boolean,
    onValueChange: (fieldId: String, value: String) -> Unit,
    onCheckField: (fieldId: String) -> Unit,
    onCheckPending: () -> Unit,
    onSubmit: (onResult: (Result<Unit>) -> Unit) -> Boolean,
    modifier: Modifier = Modifier,
    draftSaveStatus: DraftSaveStatus? = null,
    configurationWarning: String? = null,
    initialFieldId: String? = null,
    onSectionOpened: (fieldId: String) -> Unit = {},
    onNewOnboarding: () -> Unit = {},
    onReturnToDashboard: () -> Unit = {}
) {
    val sections = remember(playbook) { playbook.sections() }
    val reviewPage = sections.size
    val focusManager = LocalFocusManager.current
    var page by remember(playbook.id, playbook.version) {
        mutableIntStateOf(
            sections.indexOfFirst { section -> section.fields.any { it.fieldId == initialFieldId } }
                .coerceAtLeast(0)
        )
    }
    var submitted by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    fun goTo(target: Int) {
        // Leaving the page checks the box being typed in, and any other unchecked answer.
        focusManager.clearFocus()
        onCheckPending()
        page = target.coerceIn(0, reviewPage)
        sections.getOrNull(page)?.let { section ->
            val firstOpen = section.fields.firstOrNull { !it.isAnsweredIn(state) } ?: section.fields.first()
            onSectionOpened(firstOpen.fieldId)
        }
    }
    // The phone's Back button follows each page's back arrow (see AppScreen).

    when {
        submitted -> CompletionPage(
            fields = playbook.fields,
            values = state.values,
            onNewOnboarding = onNewOnboarding,
            onReturnToDashboard = onReturnToDashboard,
            modifier = modifier
        )
        page == reviewPage -> ReviewPage(
            sections = sections,
            state = state,
            readiness = readiness,
            canSubmit = canSubmit,
            isSubmitting = isSubmitting,
            submitError = submitError,
            onGoTo = ::goTo,
            onSubmit = {
                if (!isSubmitting && canSubmit) {
                    isSubmitting = true
                    submitError = null
                    val accepted = onSubmit { result ->
                        isSubmitting = false
                        result.onSuccess { submitted = true }
                            .onFailure { submitError = it.message ?: "Could not save this onboarding session." }
                    }
                    if (!accepted) isSubmitting = false
                }
            },
            modifier = modifier
        )
        // Keyed by page so each section opens at its top, with its own scroll position.
        else -> key(page) {
            SectionPage(
                playbook = playbook,
                sections = sections,
                page = page,
                state = state,
                guidance = guidance,
                isOnline = isOnline,
                draftSaveStatus = draftSaveStatus,
                configurationWarning = configurationWarning,
                onGoTo = ::goTo,
                onValueChange = onValueChange,
                onCheckField = onCheckField,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun SectionPage(
    playbook: OnboardingPlaybook,
    sections: List<FormSection>,
    page: Int,
    state: LiveValidationState,
    guidance: NextFieldGuidance,
    isOnline: Boolean,
    draftSaveStatus: DraftSaveStatus?,
    configurationWarning: String?,
    onGoTo: (Int) -> Unit,
    onValueChange: (fieldId: String, value: String) -> Unit,
    onCheckField: (fieldId: String) -> Unit,
    modifier: Modifier
) {
    val section = sections[page]
    // Only the model's pick is tagged; without a model the section order is the guide.
    val suggestedId = guidance.prediction?.fieldId?.takeIf { it == guidance.nextFieldId }
    val suggestedPage = sections.indexOfFirst { s -> s.fields.any { it.fieldId == suggestedId } }
    val required = section.fields.filter { it.isRequiredFor(state.values) }

    AppScreen(
        title = section.title,
        subtitle = "${playbook.title} · step ${page + 1} of ${sections.size}",
        onBack = if (page > 0) ({ onGoTo(page - 1) }) else null,
        actions = { if (!isOnline) StatusPill("Offline", Tone.Caution) },
        modifier = modifier,
        bottomBar = {
            draftSaveStatus?.let { DraftStatusLine(it) }
            if (required.isNotEmpty()) {
                Text(
                    "${required.count { it.isAnsweredIn(state) }} of ${required.size} required answered in this section",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrimaryButton(
                text = if (page == sections.lastIndex) "Review answers" else "Next: ${sections[page + 1].title}",
                onClick = { onGoTo(page + 1) },
                icon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    ) {
        StepBar(sections, page, state, onGoTo)
        configurationWarning?.let { Banner(it, Tone.Caution) }
        if (!isOnline) {
            Banner("You are offline. Keep going; answers are sent when the connection returns.", Tone.Neutral)
        }
        guidance.riskGuidance?.let { risk ->
            val high = risk.level == CompletionRiskLevel.HIGH
            Banner(
                risk.message,
                if (high) Tone.Negative else Tone.Caution,
                title = if (high) "High chance this customer stops" else "This customer may not finish"
            )
        }
        if (suggestedPage >= 0 && suggestedPage != page) {
            val target = sections[suggestedPage]
            Banner(
                "${target.fields.first { it.fieldId == suggestedId }.fieldName}, in ${target.title}.",
                Tone.Ai,
                title = "Suggested next",
                action = { TextButton(onClick = { onGoTo(suggestedPage) }) { Text("Go to ${target.title}") } }
            )
        }
        Text(
            "Questions marked * are required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        section.fields.forEachIndexed { index, field ->
            QuestionCard(
                field = field,
                allFields = playbook.fields,
                state = state,
                isSuggested = field.fieldId == suggestedId,
                imeAction = if (index == section.fields.lastIndex) ImeAction.Done else ImeAction.Next,
                onValueChange = { onValueChange(field.fieldId, it) },
                onCheck = { onCheckField(field.fieldId) }
            )
        }
        if (guidance.modelUnavailable) {
            Text(
                "Suggestions are off on this phone. Follow the order shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewPage(
    sections: List<FormSection>,
    state: LiveValidationState,
    readiness: OnboardingReadiness,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    submitError: String?,
    onGoTo: (Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier
) {
    val ready = canSubmit && readiness.isReady
    AppScreen(
        title = "Review answers",
        subtitle = "Check each answer with the customer, then submit.",
        onBack = { onGoTo(sections.lastIndex) },
        modifier = modifier,
        bottomBar = {
            submitError?.let { Banner(it, Tone.Negative) }
            PrimaryButton(
                text = if (isSubmitting) "Saving…" else "Submit",
                onClick = onSubmit,
                enabled = ready,
                isLoading = isSubmitting,
                icon = Icons.Filled.Check
            )
        }
    ) {
        StepBar(sections, sections.size, state, onGoTo)
        val missing = readiness.incompleteRequiredFields + readiness.invalidOptionalFields
        if (missing.isEmpty()) {
            Banner("Every required question is answered.", Tone.Positive)
        } else {
            Banner(missing.joinToString(), Tone.Caution, title = "Still needed before you can submit (${missing.size})")
        }
        sections.forEachIndexed { index, section ->
            SectionCard(
                title = section.title,
                action = {
                    TextButton(onClick = { onGoTo(index) }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Edit")
                    }
                }
            ) {
                section.fields.forEachIndexed { fieldIndex, field ->
                    if (fieldIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AnswerRow(field, state)
                }
            }
        }
    }
}

@Composable
private fun AnswerRow(field: FormField, state: LiveValidationState) {
    val value = state.values[field.fieldId]
    val error = errorFor(field, state)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(field.fieldName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            !value.isNullOrBlank() -> Text(displayAnswer(field, value), style = MaterialTheme.typography.bodyLarge)
            field.isRequiredFor(state.values) -> Text(
                "Not answered (required)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            else -> Text(
                "Not answered",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CompletionPage(
    fields: List<FormField>,
    values: Map<String, String?>,
    onNewOnboarding: () -> Unit,
    onReturnToDashboard: () -> Unit,
    modifier: Modifier
) {
    val answered = fields.count { !values[it.fieldId].isNullOrBlank() }
    val summary = listOf("full_name", "business_name", "mobile_phone_number", "contact_phone_number", "email_address")
        .mapNotNull { id ->
            val field = fields.firstOrNull { it.fieldId == id } ?: return@mapNotNull null
            values[id]?.takeIf(String::isNotBlank)?.let { field.fieldName to it }
        }
    AppScreen(
        title = "Onboarding complete",
        subtitle = "The customer's details were saved.",
        modifier = modifier,
        bottomBar = {
            PrimaryButton("Onboard another customer", onClick = onNewOnboarding, icon = Icons.Filled.Add)
            SecondaryButton("Back to home", onClick = onReturnToDashboard, icon = Icons.Filled.Home)
        }
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.l), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        SectionCard(title = "Customer summary", subtitle = "$answered of ${fields.size} questions answered") {
            if (summary.isEmpty()) {
                Text("Customer record saved.", style = MaterialTheme.typography.bodyMedium)
            }
            summary.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// endregion

// region Step bar and status

private enum class SectionStatus { Todo, Started, Done, NeedsFix }

private fun FormSection.status(state: LiveValidationState): SectionStatus {
    if (fields.any { errorFor(it, state) != null }) return SectionStatus.NeedsFix
    val anyAnswered = fields.any { !state.values[it.fieldId].isNullOrBlank() }
    val required = fields.filter { it.isRequiredFor(state.values) }
    return when {
        required.all { it.isAnsweredIn(state) } && (required.isNotEmpty() || anyAnswered) -> SectionStatus.Done
        anyAnswered -> SectionStatus.Started
        else -> SectionStatus.Todo
    }
}

/** One pill per section plus "Review"; tap any of them to go there. [page] == sections.size is the review. */
@Composable
private fun StepBar(sections: List<FormSection>, page: Int, state: LiveValidationState, onGoTo: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(page) { listState.animateScrollToItem(page) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        itemsIndexed(sections) { index, section ->
            StepPill(index + 1, section.title, section.status(state), index == page) { onGoTo(index) }
        }
        item {
            StepPill(sections.size + 1, "Review", SectionStatus.Todo, page == sections.size) { onGoTo(sections.size) }
        }
    }
}

@Composable
private fun StepPill(number: Int, title: String, status: SectionStatus, isCurrent: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        isCurrent -> scheme.primary to scheme.onPrimary
        status == SectionStatus.NeedsFix -> scheme.errorContainer to scheme.onErrorContainer
        status == SectionStatus.Done -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    val statusWords = when (status) {
        SectionStatus.Done -> "done"
        SectionStatus.NeedsFix -> "needs a fix"
        SectionStatus.Started -> "started"
        SectionStatus.Todo -> "not started"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .semantics {
                contentDescription = "Step $number, $title, $statusWords" + if (isCurrent) ", current" else ""
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (status) {
                SectionStatus.Done -> Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                SectionStatus.NeedsFix -> Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                else -> Text("$number", style = MaterialTheme.typography.labelMedium)
            }
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DraftStatusLine(status: DraftSaveStatus) {
    val (text, color) = when {
        status.errorMessage != null -> status.errorMessage to MaterialTheme.colorScheme.error
        status.isSaving -> "Saving draft…" to MaterialTheme.colorScheme.onSurfaceVariant
        status.savedAtMillis != null -> "Draft saved" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

// endregion

// region Preview

@Preview(name = "Agent form, banking", showBackground = true, heightDp = 1400)
@Composable
private fun OnboardingFormPreview() {
    val playbook = OnboardingPlaybooks.forIndustry("banking", FieldRuleConfiguration(), "individual")
    SalesOnboardingTheme(darkTheme = false) {
        OnboardingForm(
            playbook = playbook,
            state = LiveValidationState(
                values = mapOf("full_name" to "Jane Wanjiku", "email_address" to "jane@example"),
                results = mapOf(
                    "full_name" to ValidationResult.valid,
                    "email_address" to ValidationResult.invalid("Enter a valid email address.")
                ),
                touchedFieldIds = setOf("full_name", "email_address")
            ),
            guidance = NextFieldGuidance(
                prediction = FieldPrediction("mobile_phone_number", 0.9f),
                nextFieldId = "mobile_phone_number",
                riskGuidance = CompletionRiskGuidance(
                    CompletionRiskLevel.MEDIUM,
                    "Offer extra help and review the information with the customer.",
                    emptyList()
                )
            ),
            readiness = OnboardingReadiness(13, 1, listOf("Email address"), emptyList()),
            canSubmit = false,
            isOnline = true,
            onValueChange = { _, _ -> },
            onCheckField = {},
            onCheckPending = {},
            onSubmit = { false },
            draftSaveStatus = DraftSaveStatus(savedAtMillis = 0L)
        )
    }
}

// endregion
