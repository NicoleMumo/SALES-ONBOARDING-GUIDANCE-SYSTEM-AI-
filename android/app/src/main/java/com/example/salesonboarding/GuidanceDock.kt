package com.example.salesonboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Gentle words, so a warning prompts the agent to help without alarming them. */
internal fun riskTitle(level: CompletionRiskLevel): String = when (level) {
    CompletionRiskLevel.HIGH -> "This customer may need help now"
    else -> "Check in with the customer"
}

/** What the guidance points at next. */
internal enum class GuidanceStep { FIX, SUGGESTED, NEXT_REQUIRED, DONE }

internal data class GuidancePointer(val step: GuidanceStep, val fieldId: String?)

/**
 * The question the guidance points at: a wrong answer to fix first, then the model's
 * suggestion, then the next required question in the form's order. Null while nothing is known.
 */
internal fun NextFieldGuidance.pointer(): GuidancePointer? {
    val next = nextFieldId
    return when {
        correction != null -> GuidancePointer(GuidanceStep.FIX, correction.fieldId)
        next != null && prediction?.fieldId == next -> GuidancePointer(GuidanceStep.SUGGESTED, next)
        next != null -> GuidancePointer(GuidanceStep.NEXT_REQUIRED, next)
        isComplete -> GuidancePointer(GuidanceStep.DONE, null)
        else -> null
    }
}

/**
 * Where the guidance points, never empty while anything is left to do: the engine's pick
 * ([pointer]) or, before its first answer, the first required question not yet answered.
 */
internal fun guidancePointer(
    guidance: NextFieldGuidance,
    fields: List<FormField>,
    state: LiveValidationState
): GuidancePointer? = guidance.pointer()
    ?: fields.firstOrNull { it.isRequiredFor(state.values) && !it.isAnsweredIn(state) }
        ?.let { GuidancePointer(GuidanceStep.NEXT_REQUIRED, it.fieldId) }

/**
 * Where Enter in question [from] should send the cursor: where the guidance points, unless
 * that is the question just left. Then a wrong answer is opened again to be fixed, and a
 * good one moves on to the next unanswered question in form order (the guidance may not have
 * caught up yet). Null when nothing is left to answer after it.
 */
internal fun followTarget(
    from: String?,
    pointer: GuidancePointer?,
    fields: List<FormField>,
    state: LiveValidationState
): String? {
    val target = pointer?.fieldId
    if (from == null || target != from) return target
    val left = fields.firstOrNull { it.fieldId == from } ?: return target
    if (errorFor(left, state) != null) return from
    val after = fields.indexOf(left)
    return fields.drop(after + 1).firstOrNull { !it.isAnsweredIn(state) }?.fieldId
}

/** Every answer that needs fixing, in the form's order. */
internal fun answersToFix(fields: List<FormField>, state: LiveValidationState): List<String> {
    val wrong = ValidationErrorDisplayMapper.forState(state).map { it.fieldId }.toSet()
    return fields.map { it.fieldId }.filter { it in wrong }
}

/** How the onboarding is going, in a word or two, for the status pill in the panel's header. */
internal fun riskPill(level: CompletionRiskLevel?): String = when (level) {
    null, CompletionRiskLevel.LOW -> "On track"
    CompletionRiskLevel.MEDIUM -> "Check in"
    CompletionRiskLevel.HIGH -> "Needs help"
}

/**
 * The guidance panel. It floats over the form, so the suggestion and the risk warning stay
 * in sight while the agent scrolls. Tapping the suggestion opens that question; the panel
 * can be dragged anywhere on the page. It has only two sizes, and moves smoothly between them:
 * full, which is always the same height whatever it shows, and compact, one row, the same
 * as the bar shown above the keyboard. Only the agent folds it, and the choice is remembered.
 * Things that would change its height open as pop-ups instead: the risk advice, the list of
 * answers to fix, and the greeting (a speech bubble above the panel).
 */
@Composable
internal fun BoxScope.GuidanceDock(
    pointer: GuidancePointer?,
    risk: CompletionRiskGuidance?,
    mood: GuideMood,
    /** Every answer that needs fixing, in the form's order. */
    fixes: List<String>,
    fieldName: (String) -> String,
    sectionName: (String) -> String?,
    autoFollow: Boolean,
    onAutoFollowChange: (Boolean) -> Unit,
    guideMoving: Boolean,
    onGuideMovingChange: (Boolean) -> Unit,
    /** Shown once when the form opens; tapping it dismisses it. */
    greeting: GuideGreeting?,
    onGreetingDismiss: () -> Unit,
    onReplayTour: () -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    onOpenField: (String) -> Unit,
    onReview: () -> Unit,
    /** While typing, folding goes back to the bar above the keyboard instead. */
    onCollapse: (() -> Unit)? = null
) {
    val c = Sm.colors
    val preferences = LocalContext.current.getSharedPreferences(GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    var folded by rememberSaveable { mutableStateOf(preferences.getBoolean(FOLDED_KEY, false)) }
    fun setFolded(value: Boolean) {
        folded = value
        preferences.edit().putBoolean(FOLDED_KEY, value).apply()
    }
    var dockSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(Modifier.matchParentSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        // Keeps the whole panel inside the page, measured from its home at the bottom centre.
        val keepInside = rememberUpdatedState { wanted: Offset ->
            val sideRoom = ((areaWidth - dockSize.width) / 2f).coerceAtLeast(0f)
            val upRoom = (areaHeight - dockSize.height).coerceAtLeast(0f)
            Offset(wanted.x.coerceIn(-sideRoom, sideRoom), wanted.y.coerceIn(-upRoom, 0f))
        }
        val currentOffset = rememberUpdatedState(offset)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .onSizeChanged { dockSize = it }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onOffsetChange(keepInside.value(currentOffset.value + drag))
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = greeting != null && !folded,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 },
                exit = fadeOut(tween(200))
            ) {
                greeting?.let { GreetingBubble(it, onGreetingDismiss) }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = c.ink, shadowElevation = 8.dp) {
                AnimatedContent(
                    targetState = folded,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 80)) togetherWith fadeOut(tween(100)))
                            .using(SizeTransform(clip = true) { _, _ -> tween(260, easing = FastOutSlowInEasing) })
                    },
                    label = "guidance size"
                ) { isFolded ->
                    if (isFolded) {
                        CompactGuidance(
                            pointer = pointer,
                            risk = risk,
                            mood = mood,
                            fixes = fixes,
                            guideMoving = guideMoving,
                            fieldName = fieldName,
                            onOpenField = onOpenField,
                            onReview = onReview,
                            onGuideTap = { setFolded(false) },
                            guideDescription = "Show the full guidance",
                            trailing = { FoldButton(folded = true) { setFolded(false) } }
                        )
                    } else {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DockHeader(
                                mood = mood,
                                risk = risk,
                                autoFollow = autoFollow,
                                onAutoFollowChange = onAutoFollowChange,
                                guideMoving = guideMoving,
                                onGuideMovingChange = onGuideMovingChange,
                                onReplayTour = onReplayTour,
                                onFold = { onCollapse?.invoke() ?: setFolded(true) }
                            )
                            MainCard(pointer, fixes, fieldName, sectionName, onOpenField, onReview)
                        }
                    }
                }
            }
        }
    }
}

private const val GUIDE_PREFERENCES = "salesmesh_form"
private const val FOLDED_KEY = "guide_folded"

@Composable
private fun DockHeader(
    mood: GuideMood,
    risk: CompletionRiskGuidance?,
    autoFollow: Boolean,
    onAutoFollowChange: (Boolean) -> Unit,
    guideMoving: Boolean,
    onGuideMovingChange: (Boolean) -> Unit,
    onReplayTour: () -> Unit,
    onFold: () -> Unit
) {
    val c = Sm.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The guide shows how the onboarding is going; tapping it folds the panel.
        GuideMascot(
            mood,
            Modifier
                .size(40.dp)
                .clickable(role = Role.Button, onClick = onFold)
                .semantics { contentDescription = "Fold the guidance to one line" },
            animated = guideMoving
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Guidance",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = c.cream
            )
            if (autoFollow) {
                Text("Auto-follow on", style = MaterialTheme.typography.labelSmall, color = c.cream.copy(alpha = 0.6f))
            }
        }
        StatusPill(risk)
        Box {
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(role = Role.Button) { menuOpen = true }
                    .semantics { contentDescription = "Guidance settings" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = c.cream)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Auto-follow: Next goes to the suggestion") },
                    leadingIcon = { if (autoFollow) Icon(Icons.Filled.Check, contentDescription = "On") },
                    onClick = { onAutoFollowChange(!autoFollow); menuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text("Moving guide (off saves battery)") },
                    leadingIcon = { if (guideMoving) Icon(Icons.Filled.Check, contentDescription = "On") },
                    onClick = { onGuideMovingChange(!guideMoving); menuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text("Replay the guide's tour") },
                    onClick = { menuOpen = false; onReplayTour() }
                )
            }
        }
        FoldButton(folded = false, onClick = onFold)
    }
}

@Composable
private fun FoldButton(folded: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (folded) "Show the full guidance" else "Fold the guidance to one line" },
        contentAlignment = Alignment.Center
    ) {
        SmIcon(if (folded) R.drawable.ic_sm_chevron_up else R.drawable.ic_sm_chevron_down, Sm.colors.cream)
    }
}

/** "On track", "Check in" or "Needs help"; tapping it shows the advice in a pop-up. */
@Composable
private fun StatusPill(risk: CompletionRiskGuidance?) {
    val c = Sm.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(28.dp)
                .background(c.cream.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .clickable(enabled = risk != null, role = Role.Button) { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RiskDot(risk?.level)
            Text(riskPill(risk?.level), style = MaterialTheme.typography.labelMedium, color = c.cream)
        }
        if (risk != null) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Column(
                    Modifier
                        .widthIn(max = 280.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(riskTitle(risk.level), style = MaterialTheme.typography.titleSmall)
                    Text(risk.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * The one thing to do next, always the same height: an answer to fix (with "All 3" listing
 * every answer to fix in a pop-up), the suggested question, the next required question, or
 * Review and submit. Its words fade from one step to the next.
 */
@Composable
private fun MainCard(
    pointer: GuidancePointer?,
    fixes: List<String>,
    fieldName: (String) -> String,
    sectionName: (String) -> String?,
    onOpenField: (String) -> Unit,
    onReview: () -> Unit
) {
    val c = Sm.colors
    val fix = pointer?.step == GuidanceStep.FIX
    val fieldId = pointer?.fieldId
    var listOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { if (fieldId != null) onOpenField(fieldId) else onReview() },
        enabled = pointer != null,
        shape = RoundedCornerShape(14.dp),
        color = if (fix) c.cherry else c.lime,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Crossfade(targetState = pointer, animationSpec = tween(220), modifier = Modifier.weight(1f), label = "next step") { shown ->
                val onCard = if (shown?.step == GuidanceStep.FIX) c.white else c.ink
                val label = when (shown?.step) {
                    GuidanceStep.FIX -> if (fixes.size > 1) "Fix ${fixes.size} answers" else "Fix this answer"
                    GuidanceStep.SUGGESTED -> "Suggested next"
                    GuidanceStep.NEXT_REQUIRED -> "Next required"
                    GuidanceStep.DONE -> "All required answers are in"
                    null -> "Getting ready"
                }
                val section = shown?.fieldId?.let(sectionName)
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        if (section != null) "$label · $section" else label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = onCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        shown?.fieldId?.let(fieldName) ?: if (shown?.step == GuidanceStep.DONE) "Review and submit" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = onCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (fix && fixes.size > 1) {
                Box {
                    Box(
                        Modifier
                            .height(32.dp)
                            .background(c.white.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { listOpen = true }
                            .padding(horizontal = 10.dp)
                            .semantics { contentDescription = "See all ${fixes.size} answers to fix" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("All ${fixes.size}", style = MaterialTheme.typography.labelMedium, color = c.white)
                    }
                    DropdownMenu(expanded = listOpen, onDismissRequest = { listOpen = false }) {
                        fixes.forEach { id ->
                            DropdownMenuItem(
                                text = { Text(listOfNotNull(fieldName(id), sectionName(id)).joinToString(" · ")) },
                                onClick = {
                                    listOpen = false
                                    onOpenField(id)
                                }
                            )
                        }
                    }
                }
            }
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                SmIcon(R.drawable.ic_sm_arrow, if (fix) c.white else c.ink, size = 20.dp)
            }
        }
    }
}

/**
 * The guidance in one row: the guide, a risk dot, what to do and where. Used both when the
 * agent folds the panel and, above the keyboard, while typing, so the two look the same.
 */
@Composable
private fun CompactGuidance(
    pointer: GuidancePointer?,
    risk: CompletionRiskGuidance?,
    mood: GuideMood,
    fixes: List<String>,
    guideMoving: Boolean,
    fieldName: (String) -> String,
    onOpenField: (String) -> Unit,
    onReview: () -> Unit,
    onGuideTap: () -> Unit,
    guideDescription: String,
    trailing: @Composable () -> Unit = {}
) {
    val c = Sm.colors
    val fix = pointer?.step == GuidanceStep.FIX
    val label = when (pointer?.step) {
        GuidanceStep.FIX -> if (fixes.size > 1) "Fix (${fixes.size})" else "Fix"
        GuidanceStep.SUGGESTED -> "Next"
        GuidanceStep.NEXT_REQUIRED -> "Next required"
        GuidanceStep.DONE -> "All in"
        null -> riskPill(risk?.level)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = pointer != null, role = Role.Button) {
                pointer?.fieldId?.let(onOpenField) ?: onReview()
            }
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GuideMascot(
            mood,
            Modifier
                .size(38.dp)
                .clickable(role = Role.Button, onClick = onGuideTap)
                .semantics { contentDescription = guideDescription },
            animated = guideMoving
        )
        if (risk != null) RiskDot(risk.level)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (fix) c.cherry else c.lime
        )
        Text(
            pointer?.fieldId?.let(fieldName) ?: if (pointer?.step == GuidanceStep.DONE) "Review and submit" else "",
            style = MaterialTheme.typography.titleSmall,
            color = c.cream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (pointer != null) SmIcon(R.drawable.ic_sm_arrow, c.cream, size = 18.dp)
        trailing()
    }
}

/**
 * The guidance while the keyboard is open: the same one-row bar as the folded panel, just
 * above the keyboard. It takes its own room instead of floating over the page, so the box
 * being typed in is never hidden behind it.
 */
@Composable
internal fun GuidanceStrip(
    pointer: GuidancePointer?,
    risk: CompletionRiskGuidance?,
    mood: GuideMood,
    fixes: List<String>,
    guideMoving: Boolean,
    fieldName: (String) -> String,
    onOpenField: (String) -> Unit,
    onReview: () -> Unit,
    /** Whether the full panel is open over the page while typing. */
    expanded: Boolean = false,
    /** Opens (or closes) the full panel over the page, even while typing. */
    onExpand: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Sm.colors.ink, RoundedCornerShape(20.dp))
    ) {
        CompactGuidance(
            pointer = pointer,
            risk = risk,
            mood = mood,
            fixes = fixes,
            guideMoving = guideMoving,
            fieldName = fieldName,
            onOpenField = onOpenField,
            onReview = onReview,
            onGuideTap = onExpand,
            guideDescription = if (expanded) "Close the full guidance" else "Open the full guidance",
            // A clear button to open the full panel, as well as the guide itself.
            trailing = { FoldButton(folded = !expanded, onClick = onExpand) }
        )
    }
}

/** The panel's folded or full setting, kept on the phone. */
internal object GuidancePanelSettings {
    fun unfold(context: Context) {
        context.getSharedPreferences(GUIDE_PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(FOLDED_KEY, false).apply()
    }
}

/** The guide's hello when the form opens, with a fun fact: a speech bubble above the panel. */
@Composable
private fun GreetingBubble(greeting: GuideGreeting, onDismiss: () -> Unit) {
    val c = Sm.colors
    Surface(
        onClick = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
        color = c.ink,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(greeting.hello, style = MaterialTheme.typography.titleSmall, color = c.cream)
            Text("Fun fact", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = c.lime)
            Text(greeting.fact, style = MaterialTheme.typography.bodySmall, color = c.cream.copy(alpha = 0.85f))
            Text("Tap to start", style = MaterialTheme.typography.labelSmall, color = c.cream.copy(alpha = 0.6f))
        }
    }
}

/** Lime when all is well, teal when a gentle check-in is due, cherry when the client may need help now. */
@Composable
private fun RiskDot(level: CompletionRiskLevel?) {
    val c = Sm.colors
    val colour = when (level) {
        CompletionRiskLevel.HIGH -> c.cherry
        CompletionRiskLevel.MEDIUM -> c.teal
        else -> c.lime
    }
    Box(Modifier.size(10.dp).background(colour, CircleShape))
}
