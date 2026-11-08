package com.example.salesonboarding

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay

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
    consentAtMillis: Long? = null,
    shareForTraining: Boolean = false
): OnboardingFormController = remember(
    sessionId, scope, entryStore, predictor, industry, context,
    agentUid, fields, playbook, initialModelUnavailable, agentEmail,
    initialValues, initialResumeFieldId, isResumingSession, riskModel, consentAtMillis,
    shareForTraining
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
        consentAtMillis = consentAtMillis,
        shareForTraining = shareForTraining
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
    // The question the guidance panel was asked to open; the section page scrolls to it.
    var jumpTarget by remember { mutableStateOf<String?>(null) }
    // Where the agent dragged the panel, kept while moving between sections.
    var dockX by rememberSaveable { mutableFloatStateOf(0f) }
    var dockY by rememberSaveable { mutableFloatStateOf(0f) }
    val preferences = LocalContext.current.getSharedPreferences(FORM_PREFERENCES, Context.MODE_PRIVATE)
    var autoFollow by remember { mutableStateOf(preferences.getBoolean(AUTO_FOLLOW_KEY, false)) }

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

    /** Opens the section holding [fieldId], then scrolls to that question and focuses it. */
    fun openField(fieldId: String) {
        val target = sections.indexOfFirst { section -> section.fields.any { it.fieldId == fieldId } }
        if (target < 0) return
        if (target != page) goTo(target)
        onSectionOpened(fieldId)
        jumpTarget = fieldId
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
                jumpTarget = jumpTarget,
                onJumpDone = { jumpTarget = null },
                onOpenField = ::openField,
                dockOffset = Offset(dockX, dockY),
                onDockOffsetChange = { dockX = it.x; dockY = it.y },
                autoFollow = autoFollow,
                onAutoFollowChange = {
                    autoFollow = it
                    preferences.edit().putBoolean(AUTO_FOLLOW_KEY, it).apply()
                },
                modifier = modifier
            )
        }
    }
}

private const val FORM_PREFERENCES = "salesmesh_form"
private const val AUTO_FOLLOW_KEY = "auto_follow"

/** How long the guidance gets to update after an answer is checked, before auto-follow reads it. */
private const val AUTO_FOLLOW_DELAY_MS = 250L

/** How long the guide smiles after an accepted answer. */
private const val GUIDE_SMILE_MS = 2000L

@OptIn(ExperimentalFoundationApi::class)
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
    jumpTarget: String?,
    onJumpDone: () -> Unit,
    onOpenField: (String) -> Unit,
    dockOffset: Offset,
    onDockOffsetChange: (Offset) -> Unit,
    autoFollow: Boolean,
    onAutoFollowChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    val section = sections[page]
    val focusManager = LocalFocusManager.current
    val allFields = remember(sections) { sections.flatMap { it.fields } }
    val sectionOf = remember(sections) {
        sections.flatMap { s -> s.fields.map { it.fieldId to s.title } }.toMap()
    }
    val bringers = remember(section) { section.fields.associate { it.fieldId to BringIntoViewRequester() } }
    val focusers = remember(section) { section.fields.associate { it.fieldId to FocusRequester() } }
    val latestGuidance by rememberUpdatedState(guidance)
    var followRequests by remember { mutableIntStateOf(0) }
    // The guide smiles for a moment after each accepted answer.
    val checks = state.interactionHistory.filter { it.validationPassed != null }
    var justPassed by remember { mutableStateOf(false) }
    LaunchedEffect(checks.size) {
        if (checks.lastOrNull()?.validationPassed == true) {
            justPassed = true
            delay(GUIDE_SMILE_MS)
            justPassed = false
        }
    }

    // Scroll to the question the panel opened, and put the cursor in it if it is a text box.
    LaunchedEffect(jumpTarget) {
        val target = jumpTarget ?: return@LaunchedEffect
        val bringer = bringers[target] ?: return@LaunchedEffect
        delay(50) // let the section lay out first
        bringer.bringIntoView()
        runCatching { focusers.getValue(target).requestFocus() } // choices have no text box
        onJumpDone()
    }
    // Auto-follow: after Next checks the answer, go where the updated guidance points.
    LaunchedEffect(followRequests) {
        if (followRequests == 0) return@LaunchedEffect
        delay(AUTO_FOLLOW_DELAY_MS)
        latestGuidance.pointer()?.fieldId?.let(onOpenField)
    }

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
        },
        // The suggestion and the risk warning float over the form, so they stay in sight.
        overlay = {
            GuidanceDock(
                pointer = guidance.pointer(),
                risk = guidance.riskGuidance,
                mood = guideMood(guidance, justPassed),
                fieldName = { id -> allFields.firstOrNull { it.fieldId == id }?.fieldName ?: id },
                sectionName = { id -> sectionOf[id]?.takeIf { it != section.title } },
                autoFollow = autoFollow,
                onAutoFollowChange = onAutoFollowChange,
                offset = dockOffset,
                onOffsetChange = onDockOffsetChange,
                onOpenField = onOpenField,
                onReview = { onGoTo(sections.size) }
            )
        }
    ) {
        ProgressSegments(sections, page, state, onGoTo)
        configurationWarning?.let { Banner(it, Tone.Caution) }
        if (!isOnline) {
            Banner("You are offline. Keep going; answers are sent when the connection returns.", Tone.Neutral)
        }
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            section.fields.forEachIndexed { index, field ->
                QuestionBlock(
                    field = field,
                    allFields = playbook.fields,
                    state = state,
                    // With auto-follow on, every box's Enter key follows the guidance.
                    imeAction = if (autoFollow || index != section.fields.lastIndex) ImeAction.Next else ImeAction.Done,
                    onValueChange = { onValueChange(field.fieldId, it) },
                    onCheck = { onCheckField(field.fieldId) },
                    modifier = Modifier.bringIntoViewRequester(bringers.getValue(field.fieldId)),
                    focusRequester = focusers.getValue(field.fieldId),
                    onNext = if (autoFollow) ({
                        focusManager.clearFocus() // leaving the box checks the answer
                        followRequests++
                    }) else null
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
        // Room to scroll the last question above the guidance panel.
        Box(Modifier.height(170.dp))
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
