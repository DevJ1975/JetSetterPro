package com.jetsetter.pro.feature.inflight

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stateful entry point: owns the [InflightViewModel], collects its [InflightUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [InFlightContent].
 */
@Composable
fun InFlightScreen(viewModel: InflightViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    InFlightContent(
        state = state,
        onTogglePlay = viewModel::togglePlay,
        onSeek = viewModel::seekTo,
        onReset = viewModel::reset,
        onSelectFlight = viewModel::selectFlight,
        onToggleUnits = viewModel::toggleUnits,
        onSetRate = viewModel::setPlaybackRate,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun InFlightContent(
    state: InflightUiState,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onReset: () -> Unit,
    onSelectFlight: (String) -> Unit,
    onToggleUnits: () -> Unit,
    onSetRate: (Int) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: content fades + rises slightly on first composition (~250ms).
    // The graphicsLayer sits after the background, so the backdrop stays opaque and the
    // render-only transform never affects scroll measurement.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "entrance",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 16.dp.toPx()
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Column {
            Text("In-Flight", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
            Text(
                "Live telemetry tracker",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        val flight = state.flight
        val status = state.status
        when {
            state.isLoading -> LoadingCard(isLoading = true)
            flight == null || status == null -> LoadingCard(isLoading = false)
            else -> {
                if (state.flights.size > 1) {
                    FlightSelector(
                        flights = state.flights,
                        selectedId = state.selectedFlightId,
                        onSelect = onSelectFlight,
                    )
                }
                RouteHeaderCard(flight = flight, status = status)
                ProgressCard(flight = flight, status = status, useMetric = state.useMetric, onSeek = onSeek)
                EtaCountdownCard(flight = flight, status = status)
                MetricsGrid(status = status, useMetric = state.useMetric)
                SimulationCard(
                    isPlaying = state.isPlaying,
                    isComplete = state.isComplete,
                    useMetric = state.useMetric,
                    playbackRate = state.playbackRate,
                    onTogglePlay = onTogglePlay,
                    onReset = onReset,
                    onToggleUnits = onToggleUnits,
                    onSetRate = onSetRate,
                )
            }
        }
    }
}

@Composable
private fun FlightSelector(
    flights: List<InFlightFlight>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        flights.forEach { flight ->
            SelectChip(
                text = flight.flightNumber,
                subtitle = "${flight.originCode}→${flight.destinationCode}",
                selected = flight.id == selectedId,
                onClick = { onSelect(flight.id) },
            )
        }
    }
}

@Composable
private fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.surface, shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Tab
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.accent else colors.textPrimary,
        )
        if (subtitle != null) {
            Text(subtitle, style = typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun RouteHeaderCard(flight: InFlightFlight, status: InFlightStatus) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(flight.flightNumber, style = typography.cardTitle, color = colors.textPrimary)
                Text(flight.airline, style = typography.caption, color = colors.textSecondary)
            }
            AccentTag(text = status.phase.label.uppercase(Locale.US), icon = phaseIcon(status.phase))
        }

        Spacer(Modifier.height(JetTheme.spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Endpoint(
                code = flight.originCode,
                city = flight.originCity,
                alignEnd = false,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Flight,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Endpoint(
                code = flight.destinationCode,
                city = flight.destinationCity,
                alignEnd = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(JetTheme.spacing.medium))

        Text(
            "${flight.aircraftType} · ${flight.tailNumber}",
            style = typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun Endpoint(code: String, city: String, alignEnd: Boolean, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(code, style = typography.metric, color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(city, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun ProgressCard(
    flight: InFlightFlight,
    status: InFlightStatus,
    useMetric: Boolean,
    onSeek: (Float) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val percent = (status.progress * 100f).roundToInt()
    val covered = if (useMetric) {
        grouped((status.distanceCoveredMiles * 1.60934f).roundToInt())
    } else {
        grouped(status.distanceCoveredMiles)
    }
    val total = if (useMetric) {
        grouped((flight.totalDistanceMiles * 1.60934f).roundToInt())
    } else {
        grouped(flight.totalDistanceMiles)
    }
    val distanceUnit = if (useMetric) "km" else "mi"
    val animated by animateFloatAsState(targetValue = status.progress, label = "progress")
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("FLIGHT PROGRESS", style = typography.label, color = colors.textSecondary)
                Spacer(Modifier.height(JetTheme.spacing.xsmall))
                Text("$percent%", style = typography.metric, color = colors.textPrimary)
            }
            Text(
                "$covered / $total $distanceUnit",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(JetTheme.spacing.small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
        ) {
            Icon(Icons.Filled.FlightTakeoff, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(CircleShape),
                color = colors.accent,
                trackColor = colors.separator,
            )
            Icon(Icons.Filled.FlightLand, null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }

        // Scrub control — dragging recomputes every metric live (progress drives the readouts).
        Slider(
            value = status.progress,
            onValueChange = onSeek,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent.copy(alpha = 0.45f),
                inactiveTrackColor = colors.separator,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${flight.originCode} · dep ${flight.departedLocal}",
                style = typography.caption,
                color = colors.textSecondary,
            )
            Text(
                "${flight.destinationCode} · drag to scrub",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun EtaCountdownCard(flight: InFlightFlight, status: InFlightStatus) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val landed = status.isComplete
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (landed) "ARRIVED" else "TIME TO ARRIVAL",
                    style = typography.label,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(JetTheme.spacing.xsmall))
                Text(
                    if (landed) "Landed" else countdownText(status.secondsRemaining),
                    style = typography.metric,
                    color = if (landed) colors.success else colors.textPrimary,
                )
                Spacer(Modifier.height(JetTheme.spacing.xsmall))
                Text(
                    "Arrives ${flight.scheduledArrivalLocal} · ${flight.destinationCity}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Icon(Icons.Outlined.AccessTime, null, tint = colors.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(JetTheme.spacing.small))
                AccentTag(text = if (landed) "On the ground" else "On time")
            }
        }
    }
}

@Composable
private fun MetricsGrid(status: InFlightStatus, useMetric: Boolean) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val altitude = if (useMetric) {
        grouped((status.altitudeFeet * 0.3048f).roundToInt()) to "m"
    } else {
        grouped(status.altitudeFeet) to "ft"
    }
    val speed = if (useMetric) {
        grouped((status.groundSpeedMph * 1.60934f).roundToInt()) to "km/h"
    } else {
        grouped(status.groundSpeedMph) to "mph"
    }
    val distance = if (useMetric) {
        grouped((status.distanceRemainingMiles * 1.60934f).roundToInt()) to "km"
    } else {
        grouped(status.distanceRemainingMiles) to "mi"
    }
    val vsRaw = if (useMetric) (status.verticalSpeedFpm * 0.3048f).roundToInt() else status.verticalSpeedFpm
    val vsUnit = if (useMetric) "m/min" else "ft/min"
    val vsValue = if (vsRaw == 0) "Level" else "${if (vsRaw > 0) "+" else "−"}${grouped(abs(vsRaw))}"
    val vsIcon = when {
        vsRaw > 0 -> Icons.Filled.TrendingUp
        vsRaw < 0 -> Icons.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val vsTint = when {
        vsRaw > 0 -> colors.blue
        vsRaw < 0 -> colors.warning
        else -> colors.success
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Terrain,
                tint = colors.blue,
                label = "ALTITUDE",
                value = altitude.first,
                unit = altitude.second,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Bolt,
                tint = colors.accent,
                label = "GROUND SPEED",
                value = speed.first,
                unit = speed.second,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = vsIcon,
                tint = vsTint,
                label = "VERTICAL SPEED",
                value = vsValue,
                unit = if (vsRaw == 0) "" else vsUnit,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Place,
                tint = colors.warning,
                label = "DISTANCE LEFT",
                value = distance.first,
                unit = distance.second,
            )
        }
    }
}

@Composable
private fun RowScope.MetricTile(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = typography.label, color = colors.textSecondary)
        }
        Spacer(Modifier.height(JetTheme.spacing.small))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = typography.cardTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Text(unit, style = typography.caption, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SimulationCard(
    isPlaying: Boolean,
    isComplete: Boolean,
    useMetric: Boolean,
    playbackRate: Int,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit,
    onToggleUnits: () -> Unit,
    onSetRate: (Int) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePlay()
                },
                enabled = !isComplete,
                modifier = Modifier.weight(1f),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    when {
                        isComplete -> "Arrived"
                        isPlaying -> "Pause"
                        else -> "Play"
                    },
                    style = typography.bodyMedium,
                )
            }
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReset()
                },
                modifier = Modifier.weight(1f),
                shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
            ) {
                Icon(Icons.Filled.Refresh, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Reset", style = typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(spacing.medium))
        Text("SPEED", style = typography.label, color = colors.textSecondary)
        Spacer(Modifier.height(JetTheme.spacing.xsmall))
        SegmentedControl(
            options = InflightRepository.RATES,
            selected = playbackRate,
            optionLabel = { "${it}×" },
            onSelect = onSetRate,
        )

        Spacer(Modifier.height(spacing.medium))
        Text("UNITS", style = typography.label, color = colors.textSecondary)
        Spacer(Modifier.height(JetTheme.spacing.xsmall))
        SegmentedControl(
            options = listOf(false, true),
            selected = useMetric,
            optionLabel = { if (it) "Metric" else "Imperial" },
            onSelect = { if (it != useMetric) onToggleUnits() },
        )
    }
}

@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val outerShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(colors.surfaceElevated, outerShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val innerShape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(innerShape)
                    .background(if (isSelected) colors.accent else Color.Transparent, innerShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option)
                    }
                    .semantics {
                        role = Role.Tab
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    optionLabel(option),
                    style = typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color.White else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard(isLoading: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = JetTheme.spacing.small),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.FlightTakeoff,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(JetTheme.spacing.small))
                Text(
                    "No active flight",
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(JetTheme.spacing.xsmall))
                Text(
                    "Live tracking appears here once you're airborne.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun phaseIcon(phase: InFlightPhase): ImageVector = when (phase) {
    InFlightPhase.BOARDING, InFlightPhase.TAXI -> Icons.Filled.Flight
    InFlightPhase.CLIMB -> Icons.Filled.FlightTakeoff
    InFlightPhase.CRUISE -> Icons.Filled.Flight
    InFlightPhase.DESCENT, InFlightPhase.LANDED -> Icons.Filled.FlightLand
}

/** Live countdown: "1:32:05" (with hours) or "32:05" (under an hour). */
private fun countdownText(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.US, "%d:%02d", m, sec)
    }
}

private fun grouped(value: Int): String = String.format(Locale.US, "%,d", value)

@Preview(showBackground = true, name = "In-Flight – cruising")
@Composable
private fun InFlightContentPreview() {
    val flights = listOf(
        InFlightFlight(
            id = "dl1423",
            flightNumber = "DL 1423",
            airline = "Delta Air Lines",
            originCode = "LAS",
            originCity = "Las Vegas",
            destinationCode = "ATL",
            destinationCity = "Atlanta",
            aircraftType = "Airbus A321neo",
            tailNumber = "N512DN",
            departedLocal = "7:05 AM PDT",
            scheduledArrivalLocal = "2:10 PM EDT",
            totalDistanceMiles = 1946,
            totalFlightMinutes = 245,
            cruiseAltitudeFeet = 38000,
            cruiseSpeedMph = 521,
            initialProgress = 0.62f,
        ),
        InFlightFlight(
            id = "as1011",
            flightNumber = "AS 1011",
            airline = "Alaska Airlines",
            originCode = "SEA",
            originCity = "Seattle",
            destinationCode = "SFO",
            destinationCity = "San Francisco",
            aircraftType = "Boeing 737 MAX 9",
            tailNumber = "N932AK",
            departedLocal = "10:15 AM PDT",
            scheduledArrivalLocal = "12:25 PM PDT",
            totalDistanceMiles = 679,
            totalFlightMinutes = 130,
            cruiseAltitudeFeet = 34000,
            cruiseSpeedMph = 489,
            initialProgress = 0.86f,
        ),
    )
    JetSetterTheme {
        InFlightContent(
            state = InflightUiState(
                isLoading = false,
                flights = flights,
                selectedFlightId = "dl1423",
                flight = flights.first(),
                status = InFlightStatus(
                    progress = 0.62f,
                    phase = InFlightPhase.CRUISE,
                    altitudeFeet = 38000,
                    groundSpeedMph = 521,
                    verticalSpeedFpm = 0,
                    secondsElapsed = 9114,
                    secondsRemaining = 5586,
                    distanceCoveredMiles = 1206,
                    distanceRemainingMiles = 740,
                ),
                isPlaying = false,
                playbackRate = 1,
            ),
            onTogglePlay = {},
            onSeek = {},
            onReset = {},
            onSelectFlight = {},
            onToggleUnits = {},
            onSetRate = {},
        )
    }
}
