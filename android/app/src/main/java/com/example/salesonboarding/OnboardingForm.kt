package com.example.salesonboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        customerType = controller.context.replaceFirstChar { it.titlecase(java.util.Locale.ROOT) },
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
        onLeave = onReturnToDashboard,
        onNewOnboarding = onNewOnboarding,
        onReturnToDashboard = onReturnToDashboard,
        currentState = { controller.stateFlow.value },
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
    riskModel: TrainedCompletionRiskModel? = null,
    consentAtMillis: Long? = null
): OnboardingFormController = remember(
    sessionId, scope, entryStore, predictor, industry, context,
    agentUid, fields, playbook, initialModelUnavailable, agentEmail,
    initialValues, initialResumeFieldId, isResumingSession, riskModel, consentAtMillis
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
        riskModel = riskModel,
        consentAtMillis = consentAtMillis
    )
}

// endregion

// region Pages: sections, review, finished

/**
 * The agent's form: one page per section (see [sections]), then a review page. The agent
 * can move between sections freely; submitting needs every required answer to be valid.
 * Going back from the first section leaves the form ([onLeave]); the draft is already saved.
 */
@Composable
fun OnboardingForm(
    playbook: OnboardingPlaybook,
    customerType: String,
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
    onLeave: () -> Unit = {},
    onNewOnboarding: () -> Unit = {},
    onReturnToDashboard: () -> Unit = {},
    /** The form state right now, including checks run since this composition. */
    currentState: () -> LiveValidationState = { state }
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
            // The answer just checked above is not in `state` yet, so read the current state.
            val latest = currentState()
            val firstOpen = section.fields.firstOrNull { !it.isAnsweredIn(latest) } ?: section.fields.first()
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
                customerType = customerType,
                sections = sections,
                page = page,
                state = state,
                guidance = guidance,
                isOnline = isOnline,
                draftSaveStatus = draftSaveStatus,
                configurationWarning = configurationWarning,
                onGoTo = ::goTo,
                onLeave = onLeave,
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
    customerType: String,
    sections: List<FormSection>,
    page: Int,
    state: LiveValidationState,
    guidance: NextFieldGuidance,
    isOnline: Boolean,
    draftSaveStatus: DraftSaveStatus?,
    configurationWarning: String?,
    onGoTo: (Int) -> Unit,
    onLeave: () -> Unit,
    onValueChange: (fieldId: String, value: String) -> Unit,
    onCheckField: (fieldId: String) -> Unit,
    modifier: Modifier
) {
    val section = sections[page]
    // Only the model's pick is offered; without a model the section order is the guide.
    val suggestedId = guidance.prediction?.fieldId?.takeIf { it == guidance.nextFieldId }
    val suggestedPage = sections.indexOfFirst { s -> s.fields.any { it.fieldId == suggestedId } }

    AppScreen(
        title = section.title,
        subtitle = "$customerType · Step ${page + 1} of ${sections.size}",
        onBack = if (page > 0) ({ onGoTo(page - 1) }) else onLeave,
        actions = { if (!isOnline) StatusPill("Offline", Tone.Caution) },
        modifier = modifier,
        bottomBar = {
            draftSaveStatus?.let { DraftStatusLine(it) }
            PrimaryButton(
                text = if (page == sections.lastIndex) "Review answers" else "Next: ${sections[page + 1].title}",
                onClick = { onGoTo(page + 1) }
            )
        }
    ) {
        ProgressSegments(sections, page, state, onGoTo)
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
        if (suggestedId != null) {
            val target = sections.getOrNull(suggestedPage)
            SuggestedNextRow(
                fieldName = sections.flatMap { it.fields }.first { it.fieldId == suggestedId }.fieldName,
                onClick = if (suggestedPage >= 0 && suggestedPage != page) ({ onGoTo(suggestedPage) }) else null,
                sectionName = target?.title?.takeIf { suggestedPage != page }
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            section.fields.forEachIndexed { index, field ->
                QuestionBlock(
                    field = field,
                    allFields = playbook.fields,
                    state = state,
                    imeAction = if (index == section.fields.lastIndex) ImeAction.Done else ImeAction.Next,
                    onValueChange = { onValueChange(field.fieldId, it) },
                    onCheck = { onCheckField(field.fieldId) }
                )
            }
        }
        if (guidance.modelUnavailable) {
            Text(
                "Suggestions are off on this phone. Follow the order shown.",
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
        }
    }
}

/** The AI's pick. A chevron and a tap show up when it is in another section. */
@Composable
private fun SuggestedNextRow(fieldName: String, sectionName: String?, onClick: (() -> Unit)?) {
    val c = Sm.colors
    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmIcon(R.drawable.ic_sm_plus, c.cherry, size = 24.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (sectionName == null) "Suggested next" else "Suggested next · $sectionName",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = c.mutedStrong
                )
                Text(fieldName, style = MaterialTheme.typography.titleMedium, color = c.ink)
            }
            if (onClick != null) SmIcon(R.drawable.ic_sm_chevron_right, c.ink)
        }
    }
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = shape,
            color = c.cream,
            border = BorderStroke(1.5.dp, c.switchOff),
            content = content
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = shape,
            color = c.cream,
            border = BorderStroke(1.5.dp, c.switchOff),
            content = content
        )
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
    val c = Sm.colors
    val ready = canSubmit && readiness.isReady
    AppScreen(
        title = "Review",
        subtitle = "Check each answer with the customer",
        onBack = { onGoTo(sections.lastIndex) },
        modifier = modifier,
        bottomBar = {
            submitError?.let { Banner(it, Tone.Negative) }
            PrimaryButton(
                text = if (isSubmitting) "Saving…" else "Submit",
                onClick = onSubmit,
                enabled = ready,
                isLoading = isSubmitting,
                iconRes = R.drawable.ic_sm_check
            )
        }
    ) {
        val missing = readiness.incompleteRequiredFields + readiness.invalidOptionalFields
        if (missing.isEmpty()) {
            Banner("Every required question is answered.", Tone.Positive)
        } else {
            Banner(missing.joinToString(), Tone.Caution, title = "Still needed (${missing.size})")
        }
        sections.forEachIndexed { index, section ->
            SectionCard(
                title = section.title,
                action = {
                    TextButton(onClick = { onGoTo(index) }) {
                        Text(
                            "Edit",
                            style = MaterialTheme.typography.titleSmall.copy(textDecoration = TextDecoration.Underline),
                            color = c.ink
                        )
                    }
                }
            ) {
                section.fields.forEachIndexed { fieldIndex, field ->
                    if (fieldIndex > 0) HorizontalDivider(color = c.divider)
                    AnswerRow(field, state)
                }
            }
        }
    }
}

@Composable
private fun AnswerRow(field: FormField, state: LiveValidationState) {
    val c = Sm.colors
    val value = state.values[field.fieldId]
    val error = errorFor(field, state)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(field.fieldName, style = MaterialTheme.typography.bodySmall, color = c.muted)
        when {
            !value.isNullOrBlank() -> Text(displayAnswer(field, value), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
            field.isRequiredFor(state.values) -> Text(
                "Not answered (required)",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = c.cherry
            )
            else -> Text(
                "Not answered",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal),
                color = c.muted
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.cherryDeep) }
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
    val c = Sm.colors
    val answered = fields.count { !values[it.fieldId].isNullOrBlank() }
    val summary = listOf("full_name", "business_name", "mobile_phone_number", "contact_phone_number", "email_address")
        .mapNotNull { id ->
            val field = fields.firstOrNull { it.fieldId == id } ?: return@mapNotNull null
            values[id]?.takeIf(String::isNotBlank)?.let { field.fieldName to it }
        }
    AppScreen(
        title = "Done",
        subtitle = "The customer's details were saved",
        modifier = modifier,
        bottomBar = {
            PrimaryButton("Onboard another customer", onClick = onNewOnboarding, iconRes = R.drawable.ic_sm_plus)
            SecondaryButton("Back to home", onClick = onReturnToDashboard)
        }
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.l), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(88.dp).background(c.lime, CircleShape),
                contentAlignment = Alignment.Center
            ) { SmIcon(R.drawable.ic_sm_check, c.ink, size = 44.dp) }
        }
        SectionCard(title = "Customer", subtitle = "$answered of ${fields.size} questions answered") {
            if (summary.isEmpty()) {
                Text("Customer record saved.", style = MaterialTheme.typography.bodyMedium)
            }
            summary.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
                }
            }
        }
    }
}

// endregion

// region Progress and status

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

/**
 * One thin bar per section (ink when done or current, cherry when a section needs a fix).
 * Each is also a button, so the agent can move to any section.
 */
@Composable
private fun ProgressSegments(sections: List<FormSection>, page: Int, state: LiveValidationState, onGoTo: (Int) -> Unit) {
    val c = Sm.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEachIndexed { index, section ->
            val status = section.status(state)
            val words = when (status) {
                SectionStatus.Done -> "done"
                SectionStatus.NeedsFix -> "needs a fix"
                SectionStatus.Started -> "started"
                SectionStatus.Todo -> "not started"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable(role = Role.Button) { onGoTo(index) }
                    .semantics {
                        contentDescription = "Step ${index + 1}, ${section.title}, $words" +
                            if (index == page) ", current" else ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            when {
                                status == SectionStatus.NeedsFix -> c.cherry
                                index == page || status == SectionStatus.Done -> c.ink
                                else -> c.line
                            },
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun DraftStatusLine(status: DraftSaveStatus) {
    val c = Sm.colors
    val (text, color) = when {
        status.errorMessage != null -> status.errorMessage to c.cherryDeep
        status.isSaving -> "Saving draft…" to c.muted
        status.savedAtMillis != null -> "Draft saved" to c.muted
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
    SalesOnboardingTheme {
        OnboardingForm(
            playbook = playbook,
            customerType = "Individual",
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
