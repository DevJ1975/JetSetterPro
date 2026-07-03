package com.jetsetter.pro.feature.departureoptimizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetColors
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale

/**
 * Stateful entry point: owns the [DepartureoptimizerViewModel], collects its UI state
 * lifecycle-aware, and forwards events. Holds no logic — see [DepartureOptimizerContent].
 */
@Composable
fun DepartureOptimizerScreen(viewModel: DepartureoptimizerViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    DepartureOptimizerContent(
        state = state,
        onRefresh = viewModel::refresh,
        onShowAdjust = viewModel::showAdjustSheet,
        onDismissAdjust = viewModel::dismissAdjustSheet,
        onSaveBuffers = viewModel::updateBuffers,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun DepartureOptimizerContent(
    state: DepartureoptimizerUiState,
    onRefresh: () -> Unit,
    onShowAdjust: () -> Unit,
    onDismissAdjust: () -> Unit,
    onSaveBuffers: (parkingMinutes: Int, gateMinutes: Int) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val est = state.estimate

    // One-time entrance: a quick fade + subtle upward slide on first composition. Purely visual
    // (driven via graphicsLayer so it never affects layout or scrolling). Starts already-visible in
    // @Preview/inspection mode, where LaunchedEffect doesn't run, so static previews still render.
    val inspection = LocalInspectionMode.current
    var entered by remember { mutableStateOf(inspection) }
    // In-app route guidance (product rule: the experience never leaves the app).
    var showRouteSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "departureEntrance",
    )
    val slidePx = with(LocalDensity.current) { spacing.medium.toPx() }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = enter
                        translationY = (1f - enter) * slidePx
                    }
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = spacing.medium)
                    .padding(top = spacing.large, bottom = spacing.xlarge),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                Text(
                    "Departure Optimizer",
                    style = JetTheme.typography.displayTitle,
                    color = colors.textPrimary,
                )

                FlightHeaderCard(est)
                LeaveByCard(est)
                NavigateButton(est = est, onOpenRoute = { showRouteSheet = true })
                StatRow(est)
                ArithmeticCard(est, onShowAdjust = onShowAdjust)
                RiskCard(est)

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    },
                    enabled = !state.isRefreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White,
                        disabledContainerColor = colors.accent.copy(alpha = 0.5f),
                        disabledContentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(spacing.small))
                        Text("Recalculating…", style = JetTheme.typography.label, color = Color.White)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(spacing.small))
                        Text("Re-roll live drive + TSA", style = JetTheme.typography.label, color = Color.White)
                    }
                }

                Text(
                    footerLabel(state),
                    style = JetTheme.typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showRouteSheet) {
        RouteMapSheet(est = est, onDismiss = { showRouteSheet = false })
    }

    if (state.showAdjustSheet) {
        AdjustBuffersSheet(
            initialParking = est.parkingBufferMinutes,
            initialGate = est.gateBufferMinutes,
            onDismiss = onDismissAdjust,
            onConfirm = onSaveBuffers,
        )
    }
}

@Composable
private fun FlightHeaderCard(est: DepartureOptimizerEstimate) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Filled.FlightTakeoff,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text("${est.flightNumber} · ${est.route}", style = typography.cardTitle, color = colors.textPrimary)
                Text(est.terminal, style = typography.caption, color = colors.textSecondary)
            }
        }
    }
}

/**
 * Hero card. The big "leave by" time and the readiness pill are both colored by total slack so the
 * traveler reads their status at a glance, then the supporting copy spells out exactly how much
 * cushion remains relative to the simulated current time.
 */
@Composable
private fun LeaveByCard(est: DepartureOptimizerEstimate) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val statusColor = statusColor(est.status, colors)
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("RECOMMENDED — LEAVE BY", style = typography.caption, color = colors.textSecondary)
            StatusPill(label = statusLabel(est), color = statusColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(formatClock(est.leaveByMinuteOfDay), style = typography.metric, color = statusColor)
        Spacer(Modifier.height(2.dp))
        Text(slackCopy(est), style = typography.bodyMedium, color = colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(
            "to comfortably make your ${formatClock(est.departureMinuteOfDay)} departure",
            style = typography.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(est.airport, style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        AccentTag(text = "${formatDuration(est.totalLeadMinutes)} total lead time", icon = Icons.Outlined.AccessTime)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = JetTheme.typography.label, color = color)
    }
}

/**
 * Opens the in-app route guidance sheet ([RouteMapSheet]) — map, live conditions, and a simulated
 * drive to the airport, all without leaving JetSetter Pro (the product's in-app-only rule).
 */
@Composable
private fun NavigateButton(est: DepartureOptimizerEstimate, onOpenRoute: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current

    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onOpenRoute()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.success,
            contentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(JetTheme.spacing.small))
        Text("Navigate to ${est.airport.substringBefore(" (")}", style = JetTheme.typography.label, color = Color.White)
    }
}

@Composable
private fun StatRow(est: DepartureOptimizerEstimate) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.AccessTime,
            label = "SLACK",
            value = slackValue(est),
            valueColor = statusColor(est.status, colors),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Security,
            label = "TSA WAIT",
            value = formatDuration(est.tsaWaitMinutes),
            valueColor = riskColor(est.securityRisk, colors),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.DirectionsCar,
            label = "DRIVE",
            value = formatDuration(est.driveMinutes),
            valueColor = riskColor(est.trafficRisk, colors),
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = modifier.semantics(mergeDescendants = true) {}, contentPadding = 12.dp) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = typography.caption, color = colors.textSecondary)
        Text(value, style = typography.cardTitle, color = valueColor ?: colors.textPrimary)
    }
}

/**
 * Transparent "leave by" arithmetic: the scheduled departure at the top, every factor subtracted
 * line by line (live feed vs. personal buffer is labelled), and the resulting leave-by time at the
 * bottom — so the headline number is always visibly the sum of its parts.
 */
@Composable
private fun ArithmeticCard(est: DepartureOptimizerEstimate, onShowAdjust: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("LEAVE-BY MATH", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text("Scheduled departure", style = typography.bodyMedium, color = colors.textPrimary)
            }
            Text(formatClock(est.departureMinuteOfDay), style = typography.cardTitle, color = colors.textPrimary)
        }

        Spacer(Modifier.height(8.dp))
        Divider(colors)
        Spacer(Modifier.height(4.dp))

        FactorRow(Icons.Filled.DirectionsCar, "Drive to airport", "Live traffic feed", est.driveMinutes)
        FactorRow(Icons.Filled.LocalParking, "Park & shuttle", "Personal buffer", est.parkingBufferMinutes)
        FactorRow(Icons.Filled.Security, "Security (TSA)", "Live checkpoint wait", est.tsaWaitMinutes)
        FactorRow(Icons.Filled.DirectionsWalk, "Gate & boarding", "Personal buffer", est.gateBufferMinutes)

        Spacer(Modifier.height(4.dp))
        Divider(colors)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Leave by", style = typography.cardTitle, color = colors.textPrimary)
            Text(formatClock(est.leaveByMinuteOfDay), style = typography.cardTitle, color = colors.accent)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "= ${formatClock(est.departureMinuteOfDay)} − ${formatDuration(est.totalLeadMinutes)} total lead",
            style = typography.caption,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onShowAdjust,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Adjust personal buffers", style = typography.label)
        }
    }
}

@Composable
private fun FactorRow(icon: ImageVector, label: String, detail: String, minutes: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = typography.bodyMedium, color = colors.textPrimary)
            Text(detail, style = typography.caption, color = colors.textSecondary)
        }
        Text("− ${formatDuration(minutes)}", style = typography.bodyMedium, color = colors.textPrimary)
    }
}

@Composable
private fun Divider(colors: JetColors) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
}

@Composable
private fun RiskCard(est: DepartureOptimizerEstimate) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("LIVE CONDITIONS", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        RiskRow(Icons.Filled.DirectionsCar, "Traffic", "${formatDuration(est.driveMinutes)} drive", est.trafficRisk)
        Spacer(Modifier.height(8.dp))
        RiskRow(Icons.Filled.Security, "Security", "${formatDuration(est.tsaWaitMinutes)} wait", est.securityRisk)
        Spacer(Modifier.height(8.dp))
        RiskRow(Icons.Filled.WbSunny, "Weather", est.weatherSummary, est.weatherRisk)
    }
}

@Composable
private fun RiskRow(icon: ImageVector, label: String, detail: String, risk: DepartureOptimizerRisk) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val tint = riskColor(risk, colors)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = typography.bodyMedium, color = colors.textPrimary)
            Text(detail, style = typography.caption, color = colors.textSecondary)
        }
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tint))
        Text(risk.label, style = typography.label, color = tint)
    }
}

/**
 * "Adjust buffers" modal bottom sheet. Inputs are local, transient draft state seeded from the
 * persisted values; validation lives here (whole minutes, 0–180) so the ViewModel only ever
 * receives clean ints. Confirm is disabled until both fields parse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustBuffersSheet(
    initialParking: Int,
    initialGate: Int,
    onDismiss: () -> Unit,
    onConfirm: (parkingMinutes: Int, gateMinutes: Int) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var parking by rememberSaveable { mutableStateOf(initialParking.toString()) }
    var gate by rememberSaveable { mutableStateOf(initialGate.toString()) }

    val parkingMinutes = parking.trim().toIntOrNull()
    val gateMinutes = gate.trim().toIntOrNull()
    val parkingValid = parkingMinutes != null && parkingMinutes in 0..MAX_BUFFER
    val gateValid = gateMinutes != null && gateMinutes in 0..MAX_BUFFER

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.separator) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text("Adjust personal buffers", style = typography.pageTitle, color = colors.textPrimary)
            Text(
                "Live drive + TSA are estimated for you. These two cushions are yours to set.",
                style = typography.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.xsmall))

            BufferField(
                label = "Park & shuttle buffer (min)",
                value = parking,
                onValueChange = { parking = it },
                isError = !parkingValid,
            )
            BufferField(
                label = "Gate & boarding buffer (min)",
                value = gate,
                onValueChange = { gate = it },
                isError = !gateValid,
            )

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    if (parkingMinutes != null && gateMinutes != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm(parkingMinutes, gateMinutes)
                    }
                },
                enabled = parkingValid && gateValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save buffers", style = typography.cardTitle)
            }
        }
    }
}

@Composable
private fun BufferField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Text(label, style = typography.label, color = colors.textSecondary)
    PremiumTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "0",
        modifier = Modifier.fillMaxWidth(),
    )
    if (isError) {
        Text(
            "Enter whole minutes between 0 and $MAX_BUFFER",
            style = typography.caption,
            color = colors.danger,
        )
    }
}

private const val MAX_BUFFER = 180

private fun statusColor(status: DepartureOptimizerStatus, colors: JetColors): Color = when (status) {
    DepartureOptimizerStatus.ON_TRACK -> colors.success
    DepartureOptimizerStatus.TIGHT -> colors.warning
    DepartureOptimizerStatus.LEAVE_NOW -> colors.danger
}

private fun riskColor(risk: DepartureOptimizerRisk, colors: JetColors): Color = when (risk) {
    DepartureOptimizerRisk.LOW -> colors.success
    DepartureOptimizerRisk.MODERATE -> colors.warning
    DepartureOptimizerRisk.HIGH -> colors.danger
}

/** Headline pill label — distinguishes "behind schedule" from "tight but still ahead". */
private fun statusLabel(est: DepartureOptimizerEstimate): String =
    if (est.slackMinutes < 0) "Running late" else est.status.label

/** Supporting copy under the hero number. */
private fun slackCopy(est: DepartureOptimizerEstimate): String {
    val slack = est.slackMinutes
    return when {
        slack > 0 -> "${formatDuration(slack)} of cushion before you need to leave"
        slack == 0 -> "It's go time — head out now"
        else -> "${formatDuration(-slack)} behind schedule — leave immediately"
    }
}

/** Compact slack value for the stat card (negative shown with a leading minus). */
private fun slackValue(est: DepartureOptimizerEstimate): String {
    val slack = est.slackMinutes
    return if (slack < 0) "−${formatDuration(-slack)}" else formatDuration(slack)
}

private fun footerLabel(state: DepartureoptimizerUiState): String {
    val now = formatClock(state.estimate.nowMinuteOfDay)
    val rolls = if (state.refreshCount > 0) " · re-rolled ${state.refreshCount}×" else ""
    return "${state.lastUpdatedLabel} · as of $now$rolls · drive + TSA are simulated"
}

private fun formatClock(minuteOfDay: Int): String {
    val normalized = ((minuteOfDay % 1440) + 1440) % 1440
    val hour24 = normalized / 60
    val minute = normalized % 60
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

@Preview(showBackground = true, name = "Departure Optimizer — on track")
@Composable
private fun DepartureOptimizerContentPreview() {
    JetSetterTheme {
        DepartureOptimizerContent(
            state = DepartureoptimizerUiState(
                isLoading = false,
                estimate = DepartureOptimizerEstimate(
                    driveMinutes = 28,
                    tsaWaitMinutes = 14,
                    trafficRisk = DepartureOptimizerRisk.LOW,
                    securityRisk = DepartureOptimizerRisk.LOW,
                ),
            ),
            onRefresh = {},
            onShowAdjust = {},
            onDismissAdjust = {},
            onSaveBuffers = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Departure Optimizer — leave now")
@Composable
private fun DepartureOptimizerLatePreview() {
    JetSetterTheme {
        DepartureOptimizerContent(
            state = DepartureoptimizerUiState(
                isLoading = false,
                refreshCount = 3,
                lastUpdatedLabel = "Updated just now",
                estimate = DepartureOptimizerEstimate(
                    nowMinuteOfDay = 5 * 60 + 20,
                    driveMinutes = 49,
                    tsaWaitMinutes = 41,
                    trafficRisk = DepartureOptimizerRisk.HIGH,
                    securityRisk = DepartureOptimizerRisk.HIGH,
                ),
            ),
            onRefresh = {},
            onShowAdjust = {},
            onDismissAdjust = {},
            onSaveBuffers = { _, _ -> },
        )
    }
}
