package com.example.salesonboarding

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * The guidance panel. It floats over the form, so the suggestion and the risk warning stay
 * in sight while the agent scrolls. Tapping the suggestion opens that question; the panel
 * can be dragged anywhere on the page and folded to one line. "Auto-follow" makes the
 * keyboard's Next key go to the suggested question instead of the box below.
 */
@Composable
internal fun BoxScope.GuidanceDock(
    pointer: GuidancePointer?,
    risk: CompletionRiskGuidance?,
    mood: GuideMood,
    fieldName: (String) -> String,
    sectionName: (String) -> String?,
    autoFollow: Boolean,
    onAutoFollowChange: (Boolean) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    onOpenField: (String) -> Unit,
    onReview: () -> Unit
) {
    if (pointer == null && risk == null) return
    val c = Sm.colors
    var folded by rememberSaveable { mutableStateOf(false) }
    var showRiskDetail by remember { mutableStateOf(false) }
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

        Surface(
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
            shape = RoundedCornerShape(20.dp),
            color = c.ink,
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DockHeader(
                    mood = mood,
                    autoFollow = autoFollow,
                    onAutoFollowChange = onAutoFollowChange,
                    folded = folded,
                    onFold = { folded = !folded }
                )
                if (!folded && risk != null) {
                    RiskLine(risk, expanded = showRiskDetail, onToggle = { showRiskDetail = !showRiskDetail })
                }
                if (pointer != null) {
                    PointerLine(pointer, fieldName, sectionName, onOpenField, onReview)
                }
            }
        }
    }
}

@Composable
private fun DockHeader(
    mood: GuideMood,
    autoFollow: Boolean,
    onAutoFollowChange: (Boolean) -> Unit,
    folded: Boolean,
    onFold: () -> Unit
) {
    val c = Sm.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The guide shows how the onboarding is going; tapping it folds or opens the panel.
        GuideMascot(
            mood,
            Modifier
                .size(44.dp)
                .clickable(role = Role.Button, onClick = onFold)
        )
        Text(
            "Guidance",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = c.cream.copy(alpha = 0.75f)
        )
        Box(Modifier.weight(1f))
        Text("Auto-follow", style = MaterialTheme.typography.labelMedium, color = c.cream.copy(alpha = 0.75f))
        SmSwitch(checked = autoFollow, onCheckedChange = onAutoFollowChange)
        Box(
            Modifier
                .size(36.dp)
                .clickable(role = Role.Button, onClick = onFold)
                .semantics { contentDescription = if (folded) "Show the full guidance" else "Fold the guidance to one line" },
            contentAlignment = Alignment.Center
        ) {
            SmIcon(if (folded) R.drawable.ic_sm_chevron_up else R.drawable.ic_sm_chevron_down, c.cream)
        }
    }
}

@Composable
private fun RiskDot(level: CompletionRiskLevel) {
    val c = Sm.colors
    Box(
        Modifier
            .size(10.dp)
            .background(if (level == CompletionRiskLevel.HIGH) c.cherry else c.lime, CircleShape)
    )
}

@Composable
private fun RiskLine(risk: CompletionRiskGuidance, expanded: Boolean, onToggle: () -> Unit) {
    val c = Sm.colors
    val high = risk.level == CompletionRiskLevel.HIGH
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RiskDot(risk.level)
            Text(
                if (high) "High chance this customer stops" else "This customer may not finish",
                style = MaterialTheme.typography.titleSmall,
                color = c.cream,
                modifier = Modifier.weight(1f)
            )
            SmIcon(if (expanded) R.drawable.ic_sm_chevron_up else R.drawable.ic_sm_chevron_down, c.cream.copy(alpha = 0.75f), size = 18.dp)
        }
        if (expanded) {
            Text(risk.message, style = MaterialTheme.typography.bodySmall, color = c.cream.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun PointerLine(
    pointer: GuidancePointer,
    fieldName: (String) -> String,
    sectionName: (String) -> String?,
    onOpenField: (String) -> Unit,
    onReview: () -> Unit
) {
    val c = Sm.colors
    val fieldId = pointer.fieldId
    val label = when (pointer.step) {
        GuidanceStep.FIX -> "Fix this answer"
        GuidanceStep.SUGGESTED -> "Suggested next"
        GuidanceStep.NEXT_REQUIRED -> "Next required"
        GuidanceStep.DONE -> "All required answers are in"
    }
    val section = fieldId?.let(sectionName)
    Surface(
        onClick = { if (fieldId != null) onOpenField(fieldId) else onReview() },
        shape = RoundedCornerShape(14.dp),
        color = if (pointer.step == GuidanceStep.FIX) c.cherry else c.lime,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (section != null) "$label · $section" else label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = if (pointer.step == GuidanceStep.FIX) c.white else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    fieldId?.let(fieldName) ?: "Review and submit",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (pointer.step == GuidanceStep.FIX) c.white else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.width(8.dp))
            SmIcon(R.drawable.ic_sm_arrow, if (pointer.step == GuidanceStep.FIX) c.white else c.ink, size = 20.dp)
        }
    }
}
