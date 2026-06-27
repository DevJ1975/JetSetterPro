package com.jetsetter.pro.feature.intelligence

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [IntelligenceViewModel], collects its [IntelligenceUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [IntelligenceContent].
 */
@Composable
fun IntelligenceScreen(viewModel: IntelligenceViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    IntelligenceContent(
        state = state,
        onAct = viewModel::act,
        onDismiss = viewModel::dismiss,
        onRestore = viewModel::restore,
        onSetFilter = viewModel::setSeverityFilter,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun IntelligenceContent(
    state: IntelligenceUiState,
    onAct: (TravelIntelligenceItem) -> Unit,
    onDismiss: (TravelIntelligenceItem) -> Unit,
    onRestore: (TravelIntelligenceItem) -> Unit,
    onSetFilter: (IntelligenceSeverity?) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        // One-time entrance: a quick fade + gentle rise when the loaded content first appears.
        var contentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { contentVisible = true }
        val entrance by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "intelligenceEntrance",
        )

        // Apply the selected severity filter; summary still reflects the full active list.
        val visible = state.severityFilter
            ?.let { sev -> state.active.filter { it.severity == sev } }
            ?: state.active

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entrance
                    // Render-time transform only — does not affect measurement or scrolling.
                    translationY = (1f - entrance) * 12.dp.toPx()
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = spacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text("Travel Intelligence", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

            SummaryCard(active = state.active, actedCount = state.acted.size)

            if (state.active.isNotEmpty()) {
                SeverityFilterRow(
                    active = state.active,
                    selected = state.severityFilter,
                    onSelect = onSetFilter,
                )
            }

            SectionHeader(text = "ACTIVE INSIGHTS", count = state.active.size)
            when {
                state.active.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "You're all caught up",
                    message = "New proactive insights about your flights, hotels, and security waits will appear here.",
                )

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.FilterAlt,
                    title = "No ${state.severityFilter?.label?.lowercase()}-severity insights",
                    message = "Nothing matches this filter right now.",
                    actionLabel = "Show all",
                    onAction = { onSetFilter(null) },
                )

                else -> visible.forEach { item ->
                    InsightCard(
                        item = item,
                        acted = item.id in state.acted,
                        onAct = { onAct(item) },
                        onDismiss = { onDismiss(item) },
                    )
                }
            }

            if (state.dismissed.isNotEmpty()) {
                DismissedSection(items = state.dismissed, onRestore = onRestore)
            }
        }
    }
}

@Composable
private fun SummaryCard(active: List<TravelIntelligenceItem>, actedCount: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography

    val alerts = active.count { it.priority == IntelligencePriority.ALERT }
    val opportunities = active.count { it.priority == IntelligencePriority.OPPORTUNITY }
    val headsUp = active.count { it.priority == IntelligencePriority.INFO }
    val savingsCents = active.sumOf { (it.metric as? InsightMetric.CostSaving)?.savedCents ?: 0 }

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("ACTIVE INSIGHTS", style = typography.caption, color = colors.textSecondary)
                Text(active.size.toString(), style = typography.metric, color = colors.textPrimary)
            }
            AccentTag(text = "Live", icon = Icons.Filled.AutoAwesome)
        }

        // Real, derived breakdown of what's currently active.
        val stats = buildList {
            if (alerts > 0) add(Triple(alerts, "action needed", colors.danger))
            if (opportunities > 0) add(Triple(opportunities, "opportunities", colors.success))
            if (headsUp > 0) add(Triple(headsUp, "heads up", colors.blue))
        }
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stats.forEach { (count, label, color) -> StatChip(count = count, label = label, color = color) }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (savingsCents > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Savings, contentDescription = null, tint = colors.success, modifier = Modifier.size(16.dp))
                Text(
                    "Up to ${usd(savingsCents)} in potential savings found",
                    style = typography.bodyMedium,
                    color = colors.success,
                )
            }
        } else {
            Text(
                "We watch your trip in the background and surface what needs your attention.",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
        if (actedCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text("$actedCount handled this trip", style = typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SeverityFilterRow(
    active: List<TravelIntelligenceItem>,
    selected: IntelligenceSeverity?,
    onSelect: (IntelligenceSeverity?) -> Unit,
) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            label = "All",
            count = active.size,
            selected = selected == null,
            accent = colors.accent,
            onClick = { onSelect(null) },
        )
        IntelligenceSeverity.entries
            .sortedByDescending { it.weight }
            .forEach { severity ->
                val count = active.count { it.severity == severity }
                if (count > 0) {
                    FilterChip(
                        label = severity.label,
                        count = count,
                        selected = selected == severity,
                        accent = severityColor(severity),
                        onClick = { onSelect(severity) },
                    )
                }
            }
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) accent else colors.surfaceElevated)
            .border(
                width = 0.5.dp,
                color = if (selected) accent else colors.separator,
                shape = CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Tab
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            style = typography.label,
            color = if (selected) Color.White else colors.textSecondary,
        )
        Text(
            count.toString(),
            style = typography.label,
            color = if (selected) Color.White.copy(alpha = 0.85f) else colors.textSecondary.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun InsightCard(
    item: TravelIntelligenceItem,
    acted: Boolean,
    onAct: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val accent = priorityColor(item.priority)
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(item.category), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, style = typography.cardTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    SeverityChip(item.severity)
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .semantics { role = Role.Button }
                            .padding(2.dp)
                            .size(18.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(item.detail, style = typography.bodyMedium, color = colors.textSecondary)

                item.metric?.let { metric ->
                    Spacer(Modifier.height(10.dp))
                    MetricStrip(metric)
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (acted) {
                        ActedTag()
                    } else {
                        AccentTag(
                            text = item.actionLabel,
                            icon = categoryIcon(item.category),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAct()
                                }
                                .semantics { role = Role.Button },
                        )
                    }
                    PriorityTag(item.priority)
                }
            }
        }
    }
}

/** A row of derived-figure chips for a metric — every number here is computed, never stored. */
@Composable
private fun MetricStrip(metric: InsightMetric) {
    val colors = JetTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (metric) {
            is InsightMetric.CostSaving -> {
                MetricChip(text = "${usd(metric.proposedCents)}/${metric.cadence}", color = colors.textSecondary)
                MetricChip(text = "Save ${usd(metric.savedCents)}/${metric.cadence}", color = colors.success)
            }

            is InsightMetric.PointsCost -> {
                MetricChip(text = "${points(metric.costPoints)} mi", color = colors.textSecondary)
                if (metric.affordable) {
                    MetricChip(text = "${points(metric.remainingPoints)} mi left", color = colors.success)
                } else {
                    MetricChip(text = "${points(-metric.remainingPoints)} mi short", color = colors.danger)
                }
            }

            is InsightMetric.Outstanding -> {
                val noun = if (metric.count == 1) "receipt" else "receipts"
                MetricChip(text = "${metric.count} $noun", color = colors.warning)
                MetricChip(text = "${usd(metric.totalCents)} total", color = colors.textSecondary)
            }

            is InsightMetric.Wait -> {
                MetricChip(text = "${metric.currentMinutes} min wait", color = colors.textSecondary)
                val over = metric.overBufferMinutes
                when {
                    over > 0 -> MetricChip(text = "$over min over buffer", color = colors.danger)
                    over < 0 -> MetricChip(text = "${-over} min spare", color = colors.success)
                    else -> MetricChip(text = "On your buffer", color = colors.warning)
                }
            }
        }
    }
}

@Composable
private fun MetricChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun StatChip(count: Int, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(count.toString(), style = JetTheme.typography.label, color = color)
        Text(label, style = JetTheme.typography.label, color = color.copy(alpha = 0.85f))
    }
}

@Composable
private fun DismissedSection(items: List<TravelIntelligenceItem>, onRestore: (TravelIntelligenceItem) -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    var expanded by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable { expanded = !expanded }
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("DISMISSED", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text(items.size.toString(), style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.medium)) {
            items.forEach { item -> HistoryCard(item = item, onRestore = { onRestore(item) }) }
        }
    }
}

@Composable
private fun HistoryCard(item: TravelIntelligenceItem, onRestore: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                categoryIcon(item.category),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = typography.bodyMedium, color = colors.textPrimary)
                Text(item.category.label, style = typography.caption, color = colors.textSecondary)
            }
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable { onRestore() }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = colors.blue, modifier = Modifier.size(14.dp))
                Text("Restore", style = typography.label, color = colors.blue)
            }
        }
    }
}

@Composable
private fun SeverityChip(severity: IntelligenceSeverity) {
    val color = severityColor(severity)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(severity.label, style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun PriorityTag(priority: IntelligencePriority) {
    val color = priorityColor(priority)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(priority.label, style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun ActedTag() {
    val color = JetTheme.colors.success
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text("Done", style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun SectionHeader(text: String, count: Int) {
    val colors = JetTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = JetTheme.typography.caption, color = colors.textSecondary)
        Text(count.toString(), style = JetTheme.typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(message, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            AccentTag(
                text = actionLabel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAction()
                    }
                    .semantics { role = Role.Button },
            )
        }
    }
}

@Composable
private fun severityColor(severity: IntelligenceSeverity): Color = when (severity) {
    IntelligenceSeverity.HIGH -> JetTheme.colors.danger
    IntelligenceSeverity.MEDIUM -> JetTheme.colors.warning
    IntelligenceSeverity.LOW -> JetTheme.colors.blue
}

@Composable
private fun priorityColor(priority: IntelligencePriority): Color = when (priority) {
    IntelligencePriority.ALERT -> JetTheme.colors.danger
    IntelligencePriority.OPPORTUNITY -> JetTheme.colors.success
    IntelligencePriority.INFO -> JetTheme.colors.blue
}

private fun categoryIcon(category: IntelligenceCategory): ImageVector = when (category) {
    IntelligenceCategory.FLIGHT -> Icons.Filled.FlightTakeoff
    IntelligenceCategory.HOTEL -> Icons.Filled.Hotel
    IntelligenceCategory.SECURITY -> Icons.Filled.Security
    IntelligenceCategory.EXPENSE -> Icons.AutoMirrored.Filled.ReceiptLong
    IntelligenceCategory.WEATHER -> Icons.Filled.WbCloudy
}

/** Whole-dollar currency from cents, with thousands separators (e.g. 134800 → "$1,348"). */
private fun usd(cents: Int): String = "$" + "%,d".format(cents / 100)

/** Points/miles with thousands separators (e.g. 14500 → "14,500"). */
private fun points(value: Int): String = "%,d".format(value)

@Preview(showBackground = true, name = "Travel Intelligence")
@Composable
private fun IntelligenceContentPreview() {
    JetSetterTheme {
        IntelligenceContent(
            state = IntelligenceUiState(
                active = listOf(
                    TravelIntelligenceItem(
                        id = "1",
                        category = IntelligenceCategory.SECURITY,
                        priority = IntelligencePriority.ALERT,
                        severity = IntelligenceSeverity.HIGH,
                        title = "TSA wait rising at LAS",
                        detail = "Security at Harry Reid Terminal 1 is climbing. Leave by 6:05a to stay ahead of it.",
                        actionLabel = "Set reminder",
                        metric = InsightMetric.Wait(currentMinutes = 32, bufferMinutes = 30),
                    ),
                    TravelIntelligenceItem(
                        id = "2",
                        category = IntelligenceCategory.HOTEL,
                        priority = IntelligencePriority.OPPORTUNITY,
                        severity = IntelligenceSeverity.MEDIUM,
                        title = "Cheaper hotel found near the venue",
                        detail = "Loews Atlanta is 0.3 mi away and fully refundable.",
                        actionLabel = "View hotel",
                        metric = InsightMetric.CostSaving(currentCents = 31_000, proposedCents = 21_400, cadence = "night"),
                    ),
                ),
                acted = setOf("2"),
                dismissed = listOf(
                    TravelIntelligenceItem(
                        id = "3",
                        category = IntelligenceCategory.SECURITY,
                        priority = IntelligencePriority.OPPORTUNITY,
                        severity = IntelligenceSeverity.LOW,
                        title = "Sky Club near your gate",
                        detail = "Included with your membership.",
                        actionLabel = "Get directions",
                        dismissed = true,
                    ),
                ),
            ),
            onAct = {},
            onDismiss = {},
            onRestore = {},
            onSetFilter = {},
        )
    }
}
