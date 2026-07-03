package com.jetsetter.pro.feature.checkin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AirplaneTicket
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

/**
 * Stateful entry point: owns the [CheckinViewModel], collects its [CheckinUiState] lifecycle-aware,
 * and forwards events. Holds no logic — see [CheckInContent].
 */
@Composable
fun CheckInScreen(viewModel: CheckinViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    CheckInContent(
        state = state,
        onToggleCheckIn = viewModel::toggleCheckIn,
        onOpenSeatPicker = viewModel::openSeatPicker,
        onDismissSeatPicker = viewModel::dismissSeatPicker,
        onConfirmSeat = viewModel::confirmSeat,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable and
 * testable. Every time-based value is derived from [CheckinUiState.nowMillis].
 */
@Composable
private fun CheckInContent(
    state: CheckinUiState,
    onToggleCheckIn: (CheckInFlight) -> Unit,
    onOpenSeatPicker: (CheckInFlight) -> Unit = {},
    onDismissSeatPicker: () -> Unit = {},
    onConfirmSeat: (String) -> Unit = {},
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: fade + slight upward slide on first composition (~250ms). Purely visual —
    // the boolean flips once and never resets, so the live clock's recompositions don't re-trigger it.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "checkinEntrance",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = enterProgress
                    translationY = (1f - enterProgress) * 16.dp.toPx()
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = spacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text("Check-In", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = spacing.xlarge)) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                state.flights.isEmpty() -> EmptyState()

                else -> {
                    SummaryCard(state = state)
                    state.flights.forEach { flight ->
                        FlightCard(
                            flight = flight,
                            nowMillis = state.nowMillis,
                            onToggle = { onToggleCheckIn(flight) },
                            onOpenSeatPicker = { onOpenSeatPicker(flight) },
                        )
                    }
                }
            }
        }

        // Seat map: opened by "Check in" (issues the pass on confirm) and by "Change seat".
        state.seatPickerFlight?.let { flight ->
            SeatMapSheet(
                flight = flight,
                onDismiss = onDismissSeatPicker,
                onConfirm = onConfirmSeat,
            )
        }
    }
}

@Composable
private fun SummaryCard(state: CheckinUiState) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("BOARDING PASSES", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text("${state.checkedInCount} of ${state.flights.size}", style = typography.metric, color = colors.textPrimary)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(
                text = "${state.checkedInCount} checked in",
                icon = Icons.Filled.CheckCircle,
                color = colors.success,
            )
            if (state.openCount > 0) {
                StatusChip(
                    text = "${state.openCount} open now",
                    icon = Icons.Outlined.Schedule,
                    color = colors.accent,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        val deadline = state.nextDeadlineFlight
        when {
            state.allCheckedIn ->
                HintRow(
                    icon = Icons.Filled.CheckCircle,
                    text = "All set — you're checked in for every flight.",
                    color = colors.success,
                )

            deadline != null ->
                HintRow(
                    icon = Icons.Outlined.Schedule,
                    text = "${deadline.flightNumber} check-in closes in " +
                        formatCountdown(deadline.checkInClosesAtMillis - state.nowMillis) + ".",
                    color = colors.warning,
                )

            state.openCount == 0 ->
                HintRow(
                    icon = Icons.Outlined.Schedule,
                    text = "No windows open right now — check back when one opens.",
                    color = colors.textSecondary,
                )

            else ->
                HintRow(
                    icon = Icons.Filled.CheckCircle,
                    text = "You're checked in for every open flight.",
                    color = colors.success,
                )
        }
    }
}

@Composable
private fun FlightCard(
    flight: CheckInFlight,
    nowMillis: Long,
    onToggle: () -> Unit,
    onOpenSeatPicker: () -> Unit = {},
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val windowState = flight.windowState(nowMillis)

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${flight.airline} · ${flight.flightNumber}",
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(flight.departureLabel(), style = typography.caption, color = colors.textSecondary)
            }
            StatusChip(
                text = statusChipText(flight, windowState),
                icon = statusChipIcon(flight, windowState),
                color = statusChipColor(flight, windowState, colors),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Route
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AirportColumn(code = flight.originCode, city = flight.originCity)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "to",
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            AirportColumn(code = flight.destinationCode, city = flight.destinationCity)
        }

        Spacer(Modifier.height(16.dp))

        DetailRow(icon = Icons.Outlined.AccessTime, label = "Departs", value = flight.departsInLabel(nowMillis))
        DetailRow(icon = Icons.Outlined.MeetingRoom, label = "Gate", value = flight.gate)
        DetailRow(icon = Icons.Outlined.EventSeat, label = "Seat", value = flight.seat)
        DetailRow(icon = Icons.Outlined.WorkspacePremium, label = "Cabin", value = flight.cabin.label)

        Spacer(Modifier.height(8.dp))
        WindowRow(flight = flight, windowState = windowState, nowMillis = nowMillis)

        if (flight.isCheckedIn) {
            Spacer(Modifier.height(12.dp))
            BoardingPassCard(
                flight = flight,
                nowMillis = nowMillis,
                onChangeSeat = onOpenSeatPicker.takeIf { windowState == CheckInWindowState.OPEN },
            )
        }

        Spacer(Modifier.height(16.dp))
        ActionButton(
            flight = flight,
            windowState = windowState,
            nowMillis = nowMillis,
            onToggle = onToggle,
            onOpenSeatPicker = onOpenSeatPicker,
        )
    }
}

@Composable
private fun ActionButton(
    flight: CheckInFlight,
    windowState: CheckInWindowState,
    nowMillis: Long,
    onToggle: () -> Unit,
    onOpenSeatPicker: () -> Unit = {},
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current

    // Cancel is offered only while the window is still open; otherwise the issued pass is final.
    val checkedInClosed = flight.isCheckedIn && windowState != CheckInWindowState.OPEN
    if (checkedInClosed) return

    // Checking in goes through the seat map (pick a seat → confirm issues the pass);
    // cancelling remains a direct toggle.
    val opensSeatPicker = !flight.isCheckedIn && windowState == CheckInWindowState.OPEN

    val config: ButtonConfig = when {
        flight.isCheckedIn -> ButtonConfig(
            label = "Cancel check-in",
            icon = Icons.Outlined.Lock,
            enabled = true,
            container = colors.surfaceElevated,
            content = colors.textPrimary,
        )

        windowState == CheckInWindowState.OPEN -> ButtonConfig(
            label = "Check in · choose seat",
            icon = Icons.Filled.FlightTakeoff,
            enabled = true,
            container = colors.accent,
            content = Color.White,
        )

        windowState == CheckInWindowState.NOT_OPEN_YET -> ButtonConfig(
            label = "Opens in ${formatCountdown(flight.checkInOpensAtMillis - nowMillis)}",
            icon = Icons.Outlined.Schedule,
            enabled = false,
            container = colors.accent,
            content = Color.White,
        )

        else -> ButtonConfig(
            label = if (flight.hasDeparted(nowMillis)) "Flight departed" else "Check-in closed",
            icon = Icons.Outlined.Lock,
            enabled = false,
            container = colors.accent,
            content = Color.White,
        )
    }

    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (opensSeatPicker) onOpenSeatPicker() else onToggle()
        },
        enabled = config.enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = config.container,
            contentColor = config.content,
            disabledContainerColor = colors.surfaceElevated,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        Icon(config.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(config.label, style = typography.cardTitle)
    }
}

private data class ButtonConfig(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val container: Color,
    val content: Color,
)

@Composable
private fun WindowRow(flight: CheckInFlight, windowState: CheckInWindowState, nowMillis: Long) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val color = when (windowState) {
        CheckInWindowState.OPEN -> colors.success
        CheckInWindowState.NOT_OPEN_YET -> colors.textSecondary
        CheckInWindowState.CLOSED -> colors.danger
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (windowState == CheckInWindowState.CLOSED) Icons.Outlined.Lock else Icons.Outlined.Schedule,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text("Check-in window", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(flight.windowLabel(nowMillis), style = typography.bodyMedium, color = color)
    }
}

@Composable
private fun BoardingPassCard(
    flight: CheckInFlight,
    nowMillis: Long,
    onChangeSeat: (() -> Unit)? = null,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val boarding = flight.boarding ?: return
    val sinceCheckIn = nowMillis - boarding.checkedInAtMillis

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.success.copy(alpha = 0.12f))
            .border(0.6.dp, colors.success.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.AirplaneTicket, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
            Text("BOARDING PASS", style = typography.label, color = colors.success)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Group ${boarding.zone} · Position ${boarding.position}",
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(boarding.cabinLabel, style = typography.caption, color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("SEAT", style = typography.caption, color = colors.textSecondary)
                Text(flight.seat, style = typography.cardTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sinceCheckIn < 60_000L) "Just checked in · Gate ${flight.gate}"
                else "Checked in ${formatCountdown(sinceCheckIn)} ago · Gate ${flight.gate}",
                style = typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            // Seat changes stay open until the check-in window closes.
            if (onChangeSeat != null) {
                Text(
                    "Change seat",
                    style = typography.label,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onChangeSeat() }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HintRow(icon: ImageVector, text: String, color: Color) {
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = typography.bodyMedium, color = color)
    }
}

@Composable
private fun AirportColumn(code: String, city: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column {
        Text(code, style = typography.cardTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(city, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Text(label, style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(value, style = typography.bodyMedium, color = colors.textPrimary)
    }
}

/** Semantic capsule chip — like [AccentTag] but tintable to success / warning / danger. */
@Composable
private fun StatusChip(text: String, icon: ImageVector, color: Color) {
    val typography = JetTheme.typography
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, style = typography.label, color = color)
    }
}

private fun statusChipText(flight: CheckInFlight, windowState: CheckInWindowState): String = when {
    flight.isCheckedIn -> "Checked in"
    windowState == CheckInWindowState.OPEN -> "Window open"
    windowState == CheckInWindowState.NOT_OPEN_YET -> "Opens soon"
    else -> "Closed"
}

private fun statusChipIcon(flight: CheckInFlight, windowState: CheckInWindowState): ImageVector = when {
    flight.isCheckedIn -> Icons.Filled.CheckCircle
    windowState == CheckInWindowState.CLOSED -> Icons.Outlined.Lock
    else -> Icons.Outlined.Schedule
}

private fun statusChipColor(
    flight: CheckInFlight,
    windowState: CheckInWindowState,
    colors: com.jetsetter.pro.ui.theme.JetColors,
): Color = when {
    flight.isCheckedIn -> colors.success
    windowState == CheckInWindowState.OPEN -> colors.accent
    windowState == CheckInWindowState.NOT_OPEN_YET -> colors.textSecondary
    else -> colors.danger
}

@Composable
private fun EmptyState() {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.AirplaneTicket,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(spacing.small))
            Text("No flights to check in", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "Upcoming flights eligible for check-in will appear here.",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, name = "Check-In – populated")
@Composable
private fun CheckInContentPreview() {
    val now = System.currentTimeMillis()
    val hour = 60 * 60 * 1000L
    val minute = 60 * 1000L
    JetSetterTheme {
        CheckInContent(
            state = CheckinUiState(
                nowMillis = now,
                flights = listOf(
                    CheckInFlight(
                        id = "dl1423",
                        airline = "Delta",
                        flightNumber = "DL 1423",
                        originCode = "LAS",
                        originCity = "Las Vegas",
                        destinationCode = "ATL",
                        destinationCity = "Atlanta",
                        gate = "C22",
                        seat = "3A",
                        cabin = Cabin.FIRST,
                        departureAtMillis = now + 4 * hour,
                        checkInClosesBeforeMinutes = 45,
                        boarding = BoardingAssignment(
                            zone = 1,
                            position = 7,
                            cabinLabel = Cabin.FIRST.label,
                            checkedInAtMillis = now - 12 * minute,
                        ),
                    ),
                    CheckInFlight(
                        id = "aa88",
                        airline = "American",
                        flightNumber = "AA 88",
                        originCode = "ATL",
                        originCity = "Atlanta",
                        destinationCode = "LHR",
                        destinationCity = "London Heathrow",
                        gate = "E15",
                        seat = "16C",
                        cabin = Cabin.MAIN,
                        departureAtMillis = now + 28 * hour,
                        checkInClosesBeforeMinutes = 90,
                    ),
                    CheckInFlight(
                        id = "ua512",
                        airline = "United",
                        flightNumber = "UA 512",
                        originCode = "SFO",
                        originCity = "San Francisco",
                        destinationCode = "EWR",
                        destinationCity = "Newark",
                        gate = "B7",
                        seat = "9F",
                        cabin = Cabin.PREMIUM,
                        departureAtMillis = now + 30 * minute,
                        checkInClosesBeforeMinutes = 45,
                    ),
                ),
            ),
            onToggleCheckIn = {},
        )
    }
}
