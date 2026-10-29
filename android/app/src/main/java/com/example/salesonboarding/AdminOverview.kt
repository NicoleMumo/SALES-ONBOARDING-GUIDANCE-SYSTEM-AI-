package com.example.salesonboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import java.util.Locale

// region Overview page

/**
 * The admin's first page: how the team is doing, in a few numbers and charts, each with a
 * one-line caption in plain words. Every chart is one series in [chartMarkColor] (no legend
 * needed); every bar carries its value, and the weekly line has a "Show numbers" table.
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
    modifier: Modifier = Modifier
) {
    AppScreen(
        title = "Overview",
        subtitle = "Company $companyId",
        modifier = modifier,
        actions = {
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onSignOut) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
            }
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
private fun OverviewContent(analytics: OnboardingAnalytics, agents: List<SalesAgentAccount>) {
    val report = analytics.consistency
    fun agentName(agentId: String) = agents.firstOrNull { it.email.equals(agentId, ignoreCase = true) }
        ?.displayName?.takeIf(String::isNotBlank) ?: agentId
    fun fieldName(fieldId: String) = analytics.fieldNames[fieldId]
        ?: fieldId.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

    StandardCard(report)

    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        StatTile("Sessions started", "${analytics.sessionCount}", "${analytics.completedCount} completed", Modifier.weight(1f))
        StatTile("Finished", percent(analytics.completionRate), "of sessions started", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        StatTile(
            "Average time",
            if (analytics.completedCount == 0) "—" else "${"%.0f".format(analytics.averageCompletionMinutes)} min",
            "per completed session",
            Modifier.weight(1f)
        )
        StatTile(
            "Average progress",
            "${"%.0f".format(analytics.averageReadinessPercent)}%",
            "of required answers, all sessions",
            Modifier.weight(1f)
        )
    }

    analytics.commonPausePoint?.let { pausePoint ->
        SectionCard(
            title = "Where unfinished sessions stop",
            subtitle = "The first required question still unanswered, most often"
        ) {
            Text(pausePoint, style = MaterialTheme.typography.titleLarge)
        }
    }

    WeeklyTrendCard(report.weekly)

    if (report.mostFailedFields.isNotEmpty()) {
        SectionCard(
            title = "Most corrected questions",
            subtitle = "Times an answer failed its check and had to be fixed. A question near the top may need a clearer hint."
        ) {
            BarList(report.mostFailedFields.map { BarRow(fieldName(it.fieldId), it.count.toDouble(), "${it.count}") })
        }
    }
    if (report.mostMisplacedFields.isNotEmpty()) {
        SectionCard(
            title = "Most often answered out of order",
            subtitle = "Times a question was answered away from its place in the standard order. Some reordering is normal."
        ) {
            BarList(report.mostMisplacedFields.map { BarRow(fieldName(it.fieldId), it.count.toDouble(), "${it.count}") })
        }
    }
    if (report.agents.isNotEmpty()) {
        SectionCard(
            title = "Agents: followed the standard",
            subtitle = "Share of each agent's completed sessions that followed the standard, lowest first. A few sessions can swing a lot."
        ) {
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
    }
    if (analytics.agentPerformance.isNotEmpty()) {
        SectionCard(
            title = "Agents: sessions finished",
            subtitle = "Share of each agent's started sessions that were completed."
        ) {
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
}

/** The headline: the share of completed sessions that followed the standard, with a meter. */
@Composable
private fun StandardCard(report: ConsistencyReport) {
    SectionCard(title = "Followed the standard") {
        if (report.sessionCount == 0) {
            Text("—", style = HeroStyle())
            Text(
                "No completed sessions to measure yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(percent(report.followedStandardShare), style = HeroStyle())
            Meter(report.followedStandardShare)
            Text(
                "${report.followedStandardCount} of ${report.sessionCount} completed sessions had at most " +
                    "${SessionConsistencyCalculator.MAX_FAILED_CHECKS} corrected answers and at most " +
                    "${SessionConsistencyCalculator.MAX_STEPS_OUT_OF_PLACE} questions out of the standard order. " +
                    "A different order is not automatically wrong.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (report.earlierSessionsLeftOut > 0) {
            Text(
                "${report.earlierSessionsLeftOut} earlier completed sessions are not counted: that version " +
                    "checked answers while they were typed, so a real correction cannot be told from a pause.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (report.sessionsWithoutChecks > 0) {
            Text(
                "${report.sessionsWithoutChecks} completed sessions have no saved answer checks (for example, " +
                    "a saved session submitted without changing an answer) and are not counted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeeklyTrendCard(weeks: List<WeeklyConsistency>) {
    var showNumbers by remember { mutableStateOf(false) }
    SectionCard(
        title = "Followed the standard, by week",
        subtitle = "Weeks are counted back from the most recent session; the latest is on the right.",
        action = if (weeks.size >= 2) {
            { TextButton(onClick = { showNumbers = !showNumbers }) { Text(if (showNumbers) "Hide numbers" else "Show numbers") } }
        } else null
    ) {
        if (weeks.size < 2) {
            Text(
                "The trend appears once there are completed sessions in two different weeks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        WeeklyLineChart(weeks)
        if (showNumbers) {
            weeks.sortedBy { it.weeksAgo }.forEachIndexed { index, week ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
}

// endregion

// region Chart parts

@Composable
private fun HeroStyle() = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 52.sp)

@Composable
private fun StatTile(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A single share against 100%: the fill in the chart green on a lighter track of the same hue. */
@Composable
private fun Meter(share: Double) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
            .clearAndSetSemantics { } // the number above already says it
    ) {
        Box(
            Modifier
                .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(chartMarkColor(), RoundedCornerShape(6.dp))
        )
    }
}

private data class BarRow(val label: String, val value: Double, val valueLabel: String, val detail: String? = null)

/**
 * Horizontal bars from one baseline, 16 dp thick with a 4 dp rounded end, the value
 * written at the tip in text colour. Labels sit above the bars, so long names fit.
 */
@Composable
private fun BarList(rows: List<BarRow>, maxValue: Double = rows.maxOfOrNull { it.value } ?: 0.0) {
    val color = chartMarkColor()
    val barShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val room = maxWidth - ValueLabelRoom
                    val fraction = if (maxValue > 0) (row.value / maxValue).coerceIn(0.0, 1.0).toFloat() else 0f
                    val barWidth = if (row.value > 0) max(room * fraction, 4.dp) else 0.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(barWidth).height(16.dp).background(color, barShape))
                        Spacer(Modifier.width(Space.s))
                        Text(row.valueLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
                row.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val ValueLabelRoom = 56.dp

/**
 * The weekly share as a 2 dp line with 8 dp points ringed in the card colour, solid
 * hairline gridlines at 0, 50 and 100%, and only the latest value written on the chart.
 * Weeks without completed sessions have no point. TalkBack reads every week's value.
 */
@Composable
private fun WeeklyLineChart(weeks: List<WeeklyConsistency>) {
    val color = chartMarkColor()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface)
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

// region Preview

@Preview(name = "Admin overview", showBackground = true, heightDp = 1800)
@Composable
private fun AdminOverviewPreview() {
    val report = ConsistencyReport(
        sessionCount = 24,
        followedStandardCount = 19,
        agents = listOf(
            AgentConsistency("otieno@example.com", 6, 3, 1.5, 2.2),
            AgentConsistency("wanjiku@example.com", 10, 9, 0.4, 0.6),
            AgentConsistency("achieng@example.com", 8, 7, 0.8, 0.9)
        ),
        mostMisplacedFields = listOf(FieldCount("income", 6), FieldCount("home_address", 3)),
        mostFailedFields = listOf(FieldCount("date_of_birth", 9), FieldCount("mobile_phone_number", 5), FieldCount("identification_document_number", 2)),
        weekly = listOf(WeeklyConsistency(3, 5, 3), WeeklyConsistency(2, 6, 5), WeeklyConsistency(1, 7, 5), WeeklyConsistency(0, 6, 6)),
        earlierSessionsLeftOut = 7
    )
    val analytics = OnboardingAnalytics(
        sessionCount = 31,
        completedCount = 24,
        averageCompletionMinutes = 11.4,
        commonPausePoint = "Identification document number",
        agentPerformance = listOf(
            AgentPerformance("wanjiku@example.com", 11, 10),
            AgentPerformance("achieng@example.com", 10, 8),
            AgentPerformance("otieno@example.com", 10, 6)
        ),
        consistency = report,
        fieldNames = mapOf(
            "income" to "Income", "home_address" to "Home address", "date_of_birth" to "Date of birth",
            "mobile_phone_number" to "Mobile phone number", "identification_document_number" to "Identification document number"
        )
    )
    SalesOnboardingTheme(darkTheme = false) {
        AdminOverviewPage(
            companyId = "demo-company",
            analytics = analytics,
            agents = listOf(
                SalesAgentAccount(id = "1", email = "wanjiku@example.com", displayName = "Jane Wanjiku"),
                SalesAgentAccount(id = "2", email = "otieno@example.com", displayName = "Brian Otieno"),
                SalesAgentAccount(id = "3", email = "achieng@example.com", displayName = "Mary Achieng")
            ),
            isLoading = false,
            errorMessage = null,
            onRefresh = {},
            onSignOut = {}
        )
    }
}

// endregion
