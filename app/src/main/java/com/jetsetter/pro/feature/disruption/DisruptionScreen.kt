package com.jetsetter.pro.feature.disruption

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.RequestNotificationPermissionOnce
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale

/**
 * Stateful entry point: owns the [DisruptionViewModel], collects its [DisruptionUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [DisruptionContent].
 */
@Composable
fun DisruptionScreen(viewModel: DisruptionViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    // The "Traveler notified" timeline step posts a real push — ask for permission in context.
    RequestNotificationPermissionOnce()
    DisruptionContent(
        state = state,
        onSelectAlternative = viewModel::selectAlternative,
        onSetSortMode = viewModel::setSortMode,
        onConfirmRebooking = viewModel::confirmRebooking,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun DisruptionContent(
    state: DisruptionUiState,
    onSelectAlternative: (String) -> Unit,
    onSetSortMode: (DisruptionSortMode) -> Unit,
    onConfirmRebooking: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: a quick fade + gentle slide-in on first composition. The transform lives
    // in graphicsLayer so it stays render-only and never affects scroll measurement, and the
    // background sits on a fixed wrapper so the slide can never reveal a gap at the top.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "disruptionEntranceAlpha",
    )
    val entranceOffsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "disruptionEntranceOffset",
    )

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer {
                alpha = entranceAlpha
                translationY = entranceOffsetY
            }
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xsmall)) {
            Text("Disruption Monitor", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
            Text(
                "IRIS auto-pilot is handling this disruption for you.",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        val flight = state.monitoredFlight
        if (state.isLoading || flight == null) {
            LoadingCard()
            return@Column
        }

        MonitoredFlightCard(flight = flight)

        SectionHeader("Alternative flights")
        if (state.alternatives.isEmpty()) {
            EmptyAlternativesCard()
        } else {
            if (!state.isRebooked) {
                SortChipRow(active = state.sortMode, onSelect = onSetSortMode)
            }
            state.sortedAlternatives.forEach { alternative ->
                AlternativeCard(
                    alternative = alternative,
                    flight = flight,
                    isSelected = alternative.id == state.selectedAlternativeId,
                    isLocked = state.isRebooked || state.isRebooking,
                    isRebooked = state.isRebooked,
                    onSelect = { onSelectAlternative(alternative.id) },
                )
            }

            RebookActionCard(
                selected = state.selectedAlternative,
                isRebooking = state.isRebooking,
                isRebooked = state.isRebooked,
                onConfirmRebooking = onConfirmRebooking,
            )
        }

        SectionHeader("Auto-response")
        AutoResponseCard(
            steps = state.responseSteps,
            completed = state.completedStepCount,
            isRebooked = state.isRebooked,
        )
    }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(Locale.US),
        style = JetTheme.typography.label,
        color = JetTheme.colors.textSecondary,
    )
}

@Composable
private fun LoadingCard() {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Text("Scanning flights…", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
        }
        Spacer(Modifier.height(spacing.xsmall))
        Text(
            "IRIS is checking your trips for delays and cancellations.",
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun MonitoredFlightCard(flight: DisruptionMonitoredFlight) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val rebooked = flight.status == DisruptionFlightStatus.REBOOKED
    val statusColor = if (rebooked) colors.success else colors.danger
    val statusIcon = if (rebooked) Icons.Filled.CheckCircle else Icons.Outlined.WarningAmber
    val statusText = if (rebooked) "Rebooked" else "Delayed ${durationLabel(flight.delayMinutes)}"

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.FlightTakeoff,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(flight.flightNumber, style = typography.cardTitle, color = colors.textPrimary)
            }
            StatusPill(text = statusText, color = statusColor, icon = statusIcon)
        }

        Spacer(Modifier.height(4.dp))
        Text(flight.route, style = typography.metric, color = colors.textPrimary)
        Text(
            "${flight.origin}  ·  Gate ${flight.gate}",
            style = typography.caption,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.large)) {
            TimeColumn(label = "SCHEDULED", value = flight.scheduledDeparture, struck = true)
            TimeColumn(
                label = "NOW DEPARTS",
                value = flight.revisedDeparture,
                emphasizeColor = if (rebooked) colors.textSecondary else colors.danger,
            )
            TimeColumn(
                label = "EST. ARRIVAL",
                value = flight.revisedArrival,
                emphasizeColor = if (rebooked) colors.textSecondary else colors.warning,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
            Text(
                "${flight.reason} · ${durationLabel(flight.arrivalDelayMinutes)} behind arrival",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TimeColumn(
    label: String,
    value: String,
    struck: Boolean = false,
    emphasizeColor: Color? = null,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column {
        Text(label, style = typography.caption, color = colors.textSecondary)
        Text(
            value,
            style = typography.cardTitle.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            ),
            color = emphasizeColor ?: if (struck) colors.textSecondary else colors.textPrimary,
        )
    }
}

@Composable
private fun SortChipRow(active: DisruptionSortMode, onSelect: (DisruptionSortMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small)) {
        DisruptionSortMode.entries.forEach { mode ->
            SortChip(label = mode.label, selected = mode == active, onClick = { onSelect(mode) })
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val background = if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent
    val borderColor = if (selected) colors.accent.copy(alpha = 0.5f) else colors.separator
    val content = if (selected) colors.accent else colors.textSecondary
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Tab
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = JetTheme.typography.label, color = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlternativeCard(
    alternative: DisruptionAlternative,
    flight: DisruptionMonitoredFlight,
    isSelected: Boolean,
    isLocked: Boolean,
    isRebooked: Boolean,
    onSelect: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val dimmed = isRebooked && !isSelected
    val borderColor = if (isSelected) colors.accent else Color.Transparent
    val contentAlpha = if (dimmed) 0.45f else 1f

    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (!isLocked) {
                    Modifier
                        .clickable(role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect()
                        }
                        .semantics {
                            stateDescription = if (isSelected) "Selected" else "Not selected"
                        }
                } else {
                    Modifier
                },
            )
            .border(if (isSelected) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().alphaIf(contentAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(alternative.carrier, style = typography.cardTitle, color = colors.textPrimary)
                if (alternative.isRecommended) {
                    AccentTag(text = "AI pick", icon = Icons.Filled.AutoAwesome)
                }
            }
            Text(money(alternative.price), style = typography.cardTitle, color = colors.textPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().alphaIf(contentAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(
                    "${alternative.departureTime} → ${alternative.arrivalTime}",
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
            Text(
                "${durationLabel(alternative.durationMinutes)} nonstop",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }

        // Real, derived deltas vs the disrupted flight: how much sooner you'd arrive and how the
        // fare compares to what the traveler already paid. FlowRow wraps the pills on narrow screens.
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth().alphaIf(contentAlpha),
            horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.xsmall),
        ) {
            ArrivalDeltaTag(alternativeArrivalMin = alternative.arrivalMin, delayedArrivalMin = flight.revisedArrivalMin)
            FareDeltaTag(delta = alternative.price - flight.fare)
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().alphaIf(contentAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.EventSeat, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(
                    "${alternative.cabin} · ${alternative.seatsLeft} ${if (alternative.seatsLeft == 1) "seat" else "seats"} left",
                    style = typography.caption,
                    color = if (alternative.seatsLeft <= 2) colors.warning else colors.textSecondary,
                )
            }
            if (isSelected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Text(if (isRebooked) "Booked" else "Selected", style = typography.label, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun ArrivalDeltaTag(alternativeArrivalMin: Int, delayedArrivalMin: Int) {
    val colors = JetTheme.colors
    val diff = delayedArrivalMin - alternativeArrivalMin // positive = arrives earlier than the delayed flight
    val (text, color, icon) = when {
        diff > 0 -> Triple("Arrives ${durationLabel(diff)} sooner", colors.success, Icons.Filled.ArrowUpward)
        diff < 0 -> Triple("Arrives ${durationLabel(-diff)} later", colors.warning, Icons.Filled.ArrowDownward)
        else -> Triple("Same arrival", colors.textSecondary, Icons.Outlined.Schedule)
    }
    StatusPill(text = text, color = color, icon = icon)
}

@Composable
private fun FareDeltaTag(delta: Double) {
    val colors = JetTheme.colors
    val (text, color, icon) = when {
        delta > 0.0 -> Triple("+${money(delta)} vs fare", colors.warning, Icons.Filled.ArrowUpward)
        delta < 0.0 -> Triple("${money(delta)} vs fare", colors.success, Icons.Filled.ArrowDownward)
        else -> Triple("Same fare", colors.success, Icons.Filled.CheckCircle)
    }
    StatusPill(text = text, color = color, icon = icon)
}

@Composable
private fun EmptyAlternativesCard() {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text("No same-day alternatives", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "IRIS is still searching nearby airports and partner carriers. We'll notify you the moment a seat opens up.",
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun RebookActionCard(
    selected: DisruptionAlternative?,
    isRebooking: Boolean,
    isRebooked: Boolean,
    onConfirmRebooking: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current

    JetCard(modifier = Modifier.fillMaxWidth()) {
        if (isRebooked && selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp))
                Text("Rebooked on ${selected.carrier}", style = typography.cardTitle, color = colors.textPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Confirmation sent. New seat in ${selected.cabin}, departing ${selected.departureTime} and arriving ${selected.arrivalTime}.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            Text(
                if (selected != null) "Confirm auto-rebooking on ${selected.carrier}?" else "Select an alternative to rebook",
                style = typography.cardTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "IRIS will rebook, reassign your seat, and update your itinerary automatically.",
                style = typography.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmRebooking()
                },
                enabled = selected != null && !isRebooking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.separator,
                    disabledContentColor = colors.textSecondary,
                ),
            ) {
                if (isRebooking) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rebooking…", style = typography.cardTitle)
                } else {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selected != null) "Confirm rebooking · ${money(selected.price)}" else "Confirm rebooking",
                        style = typography.cardTitle,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoResponseCard(steps: List<DisruptionResponseStep>, completed: Int, isRebooked: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    // Visual-only completion meter. The fraction is derived from the already-computed step counts
    // (math unchanged) and animated so it eases toward its target instead of snapping as steps finish.
    val targetProgress = if (steps.isEmpty()) 0f else completed.toFloat() / steps.size
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "autoResponseProgress",
    )

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("5-step auto-response", style = typography.cardTitle, color = colors.textPrimary)
                Text("$completed of ${steps.size} complete", style = typography.caption, color = colors.textSecondary)
            }
            AccentTag(
                text = if (isRebooked) "Complete" else "Auto-pilot on",
                icon = Icons.Filled.AutoAwesome,
            )
        }
        Spacer(Modifier.height(spacing.small))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.separator),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (isRebooked) colors.success else colors.accent),
            )
        }
        Spacer(Modifier.height(12.dp))
        steps.forEachIndexed { index, step ->
            TimelineStep(step = step, isLast = index == steps.lastIndex)
        }
    }
}

@Composable
private fun TimelineStep(step: DisruptionResponseStep, isLast: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val done = step.status == DisruptionStepStatus.DONE
    val active = step.status == DisruptionStepStatus.ACTIVE
    val connectorColor = if (done) colors.success else colors.separator
    val titleColor = when {
        done -> colors.textPrimary
        active -> colors.accent
        else -> colors.textSecondary
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            when (step.status) {
                DisruptionStepStatus.DONE ->
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp))
                DisruptionStepStatus.ACTIVE ->
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                DisruptionStepStatus.PENDING ->
                    Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(connectorColor),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 14.dp)) {
            Text(step.title, style = typography.bodyMedium, color = titleColor)
            Text(step.detail, style = typography.caption, color = colors.textSecondary)
        }
    }
}

/** Color-aware status pill. Mirrors [AccentTag] but lets the disruption screen flag danger/success states. */
@Composable
private fun StatusPill(text: String, color: Color, icon: ImageVector) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, style = JetTheme.typography.label, color = color)
    }
}

/** Dims a rebooked card's non-selected content so the booked option stays the focal point. */
private fun Modifier.alphaIf(value: Float): Modifier =
    if (value < 1f) this.alpha(value) else this

private fun money(amount: Double): String {
    val abs = String.format(Locale.US, "$%,.2f", kotlin.math.abs(amount))
    return if (amount < 0) "-$abs" else abs
}

@Preview(showBackground = true, name = "Disruption – delayed")
@Composable
private fun DisruptionContentPreview() {
    JetSetterTheme {
        DisruptionContent(
            state = DisruptionUiState(
                monitoredFlight = DisruptionMonitoredFlight(
                    flightNumber = "DL 1423",
                    route = "LAS → ATL",
                    origin = "Las Vegas (LAS)",
                    destination = "Atlanta (ATL)",
                    scheduledDepartureMin = 7 * 60,
                    revisedDepartureMin = 8 * 60 + 35,
                    scheduledArrivalMin = 14 * 60 + 15,
                    revisedArrivalMin = 17 * 60 + 15,
                    gate = "C22",
                    fare = 342.0,
                    status = DisruptionFlightStatus.DELAYED,
                    reason = "Late inbound aircraft · weather hold at ATL",
                ),
                alternatives = listOf(
                    DisruptionAlternative(
                        id = "alt-dl2207",
                        carrier = "DL 2207",
                        route = "LAS → ATL",
                        departureMin = 9 * 60 + 45,
                        arrivalMin = 16 * 60 + 58,
                        cabin = "Comfort+",
                        seatsLeft = 6,
                        price = 289.0,
                        valueScore = 0.55,
                        isRecommended = true,
                    ),
                    DisruptionAlternative(
                        id = "alt-aa218",
                        carrier = "AA 218",
                        route = "LAS → ATL",
                        departureMin = 8 * 60 + 10,
                        arrivalMin = 15 * 60 + 25,
                        cabin = "First",
                        seatsLeft = 2,
                        price = 412.0,
                        valueScore = 0.45,
                    ),
                    DisruptionAlternative(
                        id = "alt-wn1190",
                        carrier = "WN 1190",
                        route = "LAS → ATL",
                        departureMin = 11 * 60 + 20,
                        arrivalMin = 18 * 60 + 40,
                        cabin = "Main",
                        seatsLeft = 14,
                        price = 198.0,
                        valueScore = 0.50,
                    ),
                ),
                responseSteps = listOf(
                    DisruptionResponseStep("s1", "Disruption detected", "DL 1423 flagged 1h 35m late", DisruptionStepStatus.DONE),
                    DisruptionResponseStep("s2", "Rebooking options found", "3 same-day alternatives located", DisruptionStepStatus.DONE),
                    DisruptionResponseStep("s3", "Backup hotel held", "Refundable room in Atlanta held until 6:00 PM", DisruptionStepStatus.DONE),
                    DisruptionResponseStep("s4", "Traveler notified", "Push + SMS sent with the top recommendation", DisruptionStepStatus.DONE),
                    DisruptionResponseStep("s5", "Awaiting your confirmation", "Pick an alternative to auto-rebook in one tap", DisruptionStepStatus.ACTIVE),
                ),
                selectedAlternativeId = "alt-dl2207",
                recommendedAlternativeId = "alt-dl2207",
            ),
            onSelectAlternative = {},
            onSetSortMode = {},
            onConfirmRebooking = {},
        )
    }
}
