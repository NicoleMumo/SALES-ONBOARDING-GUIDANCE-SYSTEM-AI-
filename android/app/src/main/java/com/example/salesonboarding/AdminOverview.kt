package com.example.salesonboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// region Overview page

/** The "More details" sheets under the four bars. */
private enum class OverviewSheet(val label: String) {
    TREND("Weekly trend"),
    CORRECTED("Corrected questions"),
    AGENTS("Agents")
}

/** One of the four bars; tapping it shows [detail] underneath. */
private data class OverviewBar(val label: String, val share: Double, val detail: String)

/**
 * The admin's first page: one big number (sessions started), four bars that each explain
 * themselves when tapped, and chips that open the longer charts. Every number comes from
 * [analytics]; with nothing to measure a bar shows a dash, never a made-up value.
 */
@Composable
fun AdminOverviewPage(
    companyId: String,
    analytics: OnboardingAnalytics?,
    agents: List<SalesAgentAccount>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null
) {
    AppScreen(
        title = "Overview",
        subtitle = "Company $companyId",
        modifier = modifier,
        bottomBar = bottomBar,
        actions = {
            HeaderButton(R.drawable.ic_sm_refresh, "Refresh", onRefresh, enabled = !isLoading)
            HeaderButton(R.drawable.ic_sm_sign_out, "Sign out", onSignOut)
        }
    ) {
        errorMessage?.let {
            Banner(it, Tone.Negative, action = { TextButton(onClick = onRefresh) { Text("Try again") } })
        }
        when {
            analytics == null && isLoading -> LoadingState(
                "Loading team results…",
                Modifier.heightIn(min = 320.dp)
            )
            analytics == null -> EmptyState(
                title = "No results yet",
                message = "Results appear here once agents start onboarding customers."
            )
            // While refreshing, keep the old numbers on screen, dimmed, instead of a jump.
            else -> Column(
                modifier = Modifier.alpha(if (isLoading) 0.6f else 1f),
                verticalArrangement = Arrangement.spacedBy(Space.l)
            ) {
                OverviewContent(analytics, agents)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OverviewContent(analytics: OnboardingAnalytics, agents: List<SalesAgentAccount>) {
    val report = analytics.consistency
    var selectedBar by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<OverviewSheet?>(null) }

    fun agentName(agentId: String) = agents.firstOrNull { it.email.equals(agentId, ignoreCase = true) }
        ?.displayName?.takeIf(String::isNotBlank) ?: agentId
    fun fieldName(fieldId: String) = analytics.fieldNames[fieldId]
        ?: fieldId.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

    val bars = buildList {
        add(
            OverviewBar(
                "Finished", analytics.completionRate,
                "${analytics.completedCount} of ${analytics.sessionCount} sessions were completed." +
                    if (analytics.completedCount > 0) {
                        " On average they took ${"%.0f".format(analytics.averageCompletionMinutes)} minutes."
                    } else ""
            )
        )
        add(
            OverviewBar(
                "Orderly",
                if (report.sessionCount == 0) -1.0 else report.followedStandardShare,
                if (report.sessionCount == 0) "No completed sessions to measure yet." else
                    "${report.followedStandardCount} of ${report.sessionCount} completed sessions had at most " +
                        "${SessionConsistencyCalculator.MAX_FAILED_CHECKS} corrected answers and at most " +
                        "${SessionConsistencyCalculator.MAX_STEPS_OUT_OF_PLACE} questions out of the standard order. " +
                        "A different order is not automatically wrong."
            )
        )
        add(
            OverviewBar(
                "Filled", analytics.averageReadinessPercent / 100,
                "On average ${"%.0f".format(analytics.averageReadinessPercent)}% of the required answers are filled in, across all sessions."
            )
        )
        add(
            OverviewBar(
                "Unfinished", 1 - analytics.completionRate,
                analytics.commonPausePoint?.let { "Most unfinished sessions stop at: $it." }
                    ?: "Sessions that were started but not completed."
            )
        )
    }

    SectionCard {
        Text("SESSIONS STARTED", style = smCapsStyle(), color = Sm.colors.muted)
        Text(
            "${analytics.sessionCount}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 68.sp),
            color = Sm.colors.ink
        )
        bars.forEachIndexed { index, bar ->
            ShareBar(bar, selected = index == selectedBar, onClick = { selectedBar = index })
        }
        Surface(shape = RoundedCornerShape(14.dp), color = Sm.colors.cream, modifier = Modifier.fillMaxWidth()) {
            Text(
                bars[selectedBar].detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Sm.colors.ink,
                modifier = Modifier.padding(Space.m)
            )
        }
        if (report.earlierSessionsLeftOut > 0) {
            Text(
                "${report.earlierSessionsLeftOut} earlier completed sessions are not counted in Orderly: that version " +
                    "checked answers while they were typed, so a real correction cannot be told from a pause.",
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
        }
        if (report.sessionsWithoutChecks > 0) {
            Text(
                "${report.sessionsWithoutChecks} completed sessions have no saved answer checks and are not counted in Orderly.",
                style = MaterialTheme.typography.bodySmall,
                color = Sm.colors.muted
            )
        }
    }

    Text("MORE DETAILS", style = smCapsStyle(), color = Sm.colors.muted)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s)
    ) {
        OverviewSheet.entries.forEach { item ->
            SmChip(item.label, selected = false, onClick = { sheet = item })
        }
    }

    when (sheet) {
        OverviewSheet.TREND -> SmSheet("Weekly trend", onDismiss = { sheet = null }) {
            WeeklyTrend(report.weekly)
        }
        OverviewSheet.CORRECTED -> SmSheet("Corrected questions", onDismiss = { sheet = null }) {
            if (report.mostFailedFields.isEmpty() && report.mostMisplacedFields.isEmpty()) {
                SheetNote("Nothing to show yet.")
            }
            if (report.mostFailedFields.isNotEmpty()) {
                SheetNote("Times an answer failed its check and had to be fixed. A question near the top may need a clearer hint.")
                BarList(report.mostFailedFields.map { BarRow(fieldName(it.fieldId), it.count.toDouble(), "${it.count}") })
            }
            if (report.mostMisplacedFields.isNotEmpty()) {
                SheetNote("Most often answered out of order. Some reordering is normal.")
                BarList(report.mostMisplacedFields.map { BarRow(fieldName(it.fieldId), it.count.toDouble(), "${it.count}") })
            }
        }
        OverviewSheet.AGENTS -> SmSheet("Agents", onDismiss = { sheet = null }) {
            if (report.agents.isEmpty() && analytics.agentPerformance.isEmpty()) SheetNote("Nothing to show yet.")
            if (report.agents.isNotEmpty()) {
                SheetNote("Share of each agent's completed sessions that followed the standard. A few sessions can swing a lot.")
                BarList(
                    rows = report.agents.map { agent ->
                        BarRow(
                            label = agentName(agent.agentId),
                            value = agent.followedStandardShare,
                            valueLabel = percent(agent.followedStandardShare),
                            detail = "${agent.sessionCount} sessions · " +
                                "${"%.1f".format(agent.averageFailedChecks)} corrected, " +
                                "${"%.1f".format(agent.averageStepsOutOfPlace)} out of order per session"
                        )
                    },
                    maxValue = 1.0
                )
            }
            if (analytics.agentPerformance.isNotEmpty()) {
                SheetNote("Share of each agent's started sessions that were completed.")
                BarList(
                    rows = analytics.agentPerformance.map { agent ->
                        BarRow(
                            label = agentName(agent.agentId),
                            value = agent.completionRate,
                            valueLabel = percent(agent.completionRate),
                            detail = "${agent.completedCount} of ${agent.sessionCount} sessions"
                        )
                    },
                    maxValue = 1.0
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Sm.colors.muted)
}

@Composable
private fun WeeklyTrend(weeks: List<WeeklyConsistency>) {
    if (weeks.size < 2) {
        SheetNote("The trend appears once there are completed sessions in two different weeks.")
        return
    }
    var showNumbers by remember { mutableStateOf(false) }
    SheetNote("Share of completed sessions that followed the standard. Weeks are counted back from the most recent session; the latest is on the right.")
    WeeklyLineChart(weeks)
    TextButton(onClick = { showNumbers = !showNumbers }) { Text(if (showNumbers) "Hide numbers" else "Show numbers") }
    if (showNumbers) {
        weeks.sortedBy { it.weeksAgo }.forEachIndexed { index, week ->
            if (index > 0) HorizontalDivider(color = Sm.colors.divider)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weekLabel(week.weeksAgo), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${percent(week.followedStandardShare)} of ${week.sessionCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// endregion

// region Chart parts

/** A labelled share bar; a negative share means "nothing to measure" and shows a dash. */
@Composable
private fun ShareBar(bar: OverviewBar, selected: Boolean, onClick: () -> Unit) {
    val known = bar.share >= 0
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Sm.colors.cream else Sm.colors.white, shape)
            .border(1.5.dp, if (selected) Sm.colors.ink else Sm.colors.divider, shape)
            .clickable(onClick = onClick)
            .padding(Space.m)
            .semantics { contentDescription = "${bar.label}: ${if (known) percent(bar.share) else "not measured yet"}" },
        verticalArrangement = Arrangement.spacedBy(Space.s)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bar.label, style = MaterialTheme.typography.titleSmall, color = Sm.colors.ink, modifier = Modifier.weight(1f))
            Text(if (known) percent(bar.share) else "—", style = MaterialTheme.typography.titleSmall, color = Sm.colors.ink)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(Sm.colors.tealTrack, RoundedCornerShape(6.dp))
                .clearAndSetSemantics { } // the text above already says it
        ) {
            if (known) {
                Box(
                    Modifier
                        .fillMaxWidth(bar.share.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(chartMarkColor(), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

private data class BarRow(val label: String, val value: Double, val valueLabel: String, val detail: String? = null)

/** Horizontal bars from one baseline with the value at the tip; labels sit above so long names fit. */
@Composable
private fun BarList(rows: List<BarRow>, maxValue: Double = rows.maxOfOrNull { it.value } ?: 0.0) {
    val color = chartMarkColor()
    val barShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, color = Sm.colors.ink)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val room = maxWidth - ValueLabelRoom
                    val fraction = if (maxValue > 0) (row.value / maxValue).coerceIn(0.0, 1.0).toFloat() else 0f
                    val barWidth = if (row.value > 0) maxOf(room * fraction, 4.dp) else 0.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(barWidth).height(16.dp).background(color, barShape))
                        Spacer(Modifier.width(Space.s))
                        Text(row.valueLabel, style = MaterialTheme.typography.labelLarge, color = Sm.colors.ink)
                    }
                }
                row.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Sm.colors.muted)
                }
            }
        }
    }
}

private val ValueLabelRoom = 56.dp

/**
 * The weekly share as a 2 dp line with ringed points, hairline gridlines at 0, 50 and 100%,
 * and only the latest value written on the chart. Weeks without completed sessions have no
 * point. TalkBack reads every week's value.
 */
@Composable
private fun WeeklyLineChart(weeks: List<WeeklyConsistency>) {
    val color = chartMarkColor()
    val grid = Sm.colors.divider
    val surface = Sm.colors.white
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = Sm.colors.muted)
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = Sm.colors.ink)
    val measurer = rememberTextMeasurer()
    val points = weeks.sortedByDescending { it.weeksAgo } // oldest first, left to right
    val latest = points.last()
    val span = (SessionConsistencyCalculator.WEEKS_SHOWN - 1).toFloat()
    val description = "Share of completed sessions that followed the standard, by week. " +
        points.joinToString(". ") { "${weekLabel(it.weeksAgo)}: ${percent(it.followedStandardShare)} of ${it.sessionCount}" }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description }
    ) {
        val left = 40.dp.toPx()
        val right = 48.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 24.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom
        fun x(weeksAgo: Int) = left + plotWidth * (1f - weeksAgo / span)
        fun y(share: Double) = top + plotHeight * (1f - share.toFloat().coerceIn(0f, 1f))

        listOf(0.0, 0.5, 1.0).forEach { level ->
            drawLine(grid, Offset(left, y(level)), Offset(left + plotWidth, y(level)), strokeWidth = 1.dp.toPx())
            val label = measurer.measure("${(level * 100).toInt()}%", axisStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(level) - label.size.height / 2f))
        }
        val oldest = measurer.measure("${SessionConsistencyCalculator.WEEKS_SHOWN - 1} weeks before", axisStyle)
        drawText(oldest, topLeft = Offset(left, size.height - oldest.size.height))
        val newest = measurer.measure("Latest", axisStyle)
        drawText(newest, topLeft = Offset(left + plotWidth - newest.size.width, size.height - newest.size.height))

        val line = Path()
        points.forEachIndexed { index, week ->
            val point = Offset(x(week.weeksAgo), y(week.followedStandardShare))
            if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEach { week ->
            val center = Offset(x(week.weeksAgo), y(week.followedStandardShare))
            drawCircle(surface, radius = 6.dp.toPx(), center = center)
            drawCircle(color, radius = 4.dp.toPx(), center = center)
        }
        val end = measurer.measure(percent(latest.followedStandardShare), valueStyle)
        drawText(
            end,
            topLeft = Offset(x(latest.weeksAgo) + 8.dp.toPx(), y(latest.followedStandardShare) - end.size.height / 2f)
        )
    }
}

private fun weekLabel(weeksAgo: Int) = when (weeksAgo) {
    0 -> "Latest week"
    1 -> "1 week before"
    else -> "$weeksAgo weeks before"
}

private fun percent(share: Double) = "${"%.0f".format(share * 100)}%"

// endregion
