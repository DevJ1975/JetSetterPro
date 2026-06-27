package com.jetsetter.pro.feature.groundtransport

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

/**
 * Stateful entry point: owns the [GroundtransportViewModel], collects its
 * [GroundtransportUiState] lifecycle-aware, and forwards events. Holds no logic —
 * see [GroundTransportContent].
 */
@Composable
fun GroundTransportScreen(viewModel: GroundtransportViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    GroundTransportContent(
        state = state,
        onSelectDirection = viewModel::selectDirection,
        onSetPassengers = viewModel::setPassengers,
        onSetSort = viewModel::setSort,
        onBook = viewModel::book,
        onCancelBooking = viewModel::cancelBooking,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun GroundTransportContent(
    state: GroundtransportUiState,
    onSelectDirection: (GroundTransportDirection) -> Unit,
    onSetPassengers: (Int) -> Unit,
    onSetSort: (GroundTransportSort) -> Unit,
    onBook: (GroundTransportOption) -> Unit,
    onCancelBooking: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: quick fade + gentle slide-up on first composition (~250ms).
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "groundTransportEnterAlpha",
    )
    val enterOffset by animateDpAsState(
        targetValue = if (contentVisible) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 250),
        label = "groundTransportEnterOffset",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge)
            .offset(y = enterOffset)
            .alpha(enterAlpha),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text("Ground Transport", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

        state.booking?.let { booking ->
            ActiveBookingCard(booking = booking, onCancel = onCancelBooking)
        }

        DirectionToggle(selected = state.direction, onSelect = onSelectDirection)

        RouteCard(state = state)

        PassengerStepper(
            passengers = state.passengers,
            maxPassengers = state.maxPassengers,
            onChange = onSetPassengers,
        )

        SortSelector(selected = state.sort, onSelect = onSetSort)

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(spacing.large),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (state.options.isEmpty()) {
            EmptyOptionsCard()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RIDE OPTIONS", style = JetTheme.typography.caption, color = colors.textSecondary)
                Text(
                    "${state.bookableCount} of ${state.options.size} seat ${state.passengers}",
                    style = JetTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }

            if (!state.hasBookableOption) {
                NoSeatsCard(passengers = state.passengers)
            }

            state.orderedOptions.forEach { option ->
                OptionCard(
                    option = option,
                    fits = state.fits(option),
                    isBooked = state.isBooked(option),
                    isBooking = option.id == state.bookingInProgressId,
                    isCheapest = option.id == state.cheapestId,
                    isFastest = option.id == state.fastestId,
                    onBook = { onBook(option) },
                )
            }

            Text(
                "Estimates are mock data for beta — fares update with live demand at booking time.",
                style = JetTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
        }
    }
}

@Composable
private fun ActiveBookingCard(booking: GroundTransportBooking, onCancel: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val directionLabel = runCatching { GroundTransportDirection.valueOf(booking.directionName).label }
        .getOrDefault("Transfer")
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.success.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${booking.service} booked", style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "$directionLabel · driver ~${booking.etaMinutes} min away",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
                Text(
                    "${priceRange(booking.priceLow, booking.priceHigh)} fare locked",
                    style = typography.label,
                    color = colors.success,
                )
            }
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .border(0.6.dp, colors.danger.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .semantics(mergeDescendants = true) { role = Role.Button },
            ) {
                Text("Cancel", style = typography.label, color = colors.danger)
            }
        }
    }
}

@Composable
private fun DirectionToggle(
    selected: GroundTransportDirection,
    onSelect: (GroundTransportDirection) -> Unit,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(0.6.dp, colors.separator, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(GroundTransportDirection.TO_AIRPORT, GroundTransportDirection.FROM_AIRPORT).forEach { direction ->
            val isSelected = direction == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(direction)
                    }
                    .padding(vertical = 10.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    direction.label,
                    style = JetTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RouteCard(state: GroundtransportUiState) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        RoutePoint(icon = Icons.Filled.RadioButtonChecked, tint = colors.blue, label = "PICKUP", value = state.pickup)
        Box(
            modifier = Modifier
                .padding(start = 9.dp, top = 2.dp, bottom = 2.dp)
                .width(2.dp)
                .height(16.dp)
                .background(colors.separator),
        )
        RoutePoint(icon = Icons.Filled.Place, tint = colors.accent, label = "DROP-OFF", value = state.dropoff)

        if (state.distanceMiles > 0.0) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill(icon = Icons.Filled.Straighten, text = miles(state.distanceMiles))
                MetaPill(icon = Icons.Filled.Schedule, text = "${state.rideMinutes} min drive")
            }
        }
    }
}

@Composable
private fun RoutePoint(icon: ImageVector, tint: Color, label: String, value: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = typography.caption, color = colors.textSecondary)
            Text(value, style = typography.bodyMedium, color = colors.textPrimary)
        }
    }
}

@Composable
private fun PassengerStepper(passengers: Int, maxPassengers: Int, onChange: (Int) -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.People, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text("Passengers", style = typography.bodyMedium, color = colors.textPrimary)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(0.6.dp, colors.separator, RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(
                icon = Icons.Filled.Remove,
                contentDescription = "Remove passenger",
                enabled = passengers > 1,
            ) { onChange(passengers - 1) }
            Text(
                passengers.toString(),
                style = typography.cardTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            StepperButton(
                icon = Icons.Filled.Add,
                contentDescription = "Add passenger",
                enabled = passengers < maxPassengers,
            ) { onChange(passengers + 1) }
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val tint = if (enabled) colors.accent else colors.textSecondary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clickable(enabled = enabled) { onClick() }
            .semantics(mergeDescendants = true) { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SortSelector(selected: GroundTransportSort, onSelect: (GroundTransportSort) -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.SwapVert, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text("Sort by", style = typography.bodyMedium, color = colors.textPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroundTransportSort.entries.forEach { sort ->
                SortPill(label = sort.label, selected = sort == selected) { onSelect(sort) }
            }
        }
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .border(
                0.6.dp,
                if (selected) Color.Transparent else colors.separator,
                RoundedCornerShape(50),
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                stateDescription = if (selected) "Selected" else "Not selected"
            },
    ) {
        Text(
            label,
            style = JetTheme.typography.label,
            color = if (selected) Color.White else colors.textSecondary,
        )
    }
}

@Composable
private fun OptionCard(
    option: GroundTransportOption,
    fits: Boolean,
    isBooked: Boolean,
    isBooking: Boolean,
    isCheapest: Boolean,
    isFastest: Boolean,
    onBook: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    // Ease the fit/no-fit dim instead of snapping when party size changes. Target values unchanged.
    val cardAlpha by animateFloatAsState(
        targetValue = if (fits) 1f else 0.55f,
        animationSpec = tween(durationMillis = 250),
        label = "groundTransportOptionAlpha",
    )
    JetCard(modifier = Modifier.fillMaxWidth().alpha(cardAlpha)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(option.vehicleClass),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(option.service, style = typography.cardTitle, color = colors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = colors.warning, modifier = Modifier.size(11.dp))
                    Text(
                        "${rating(option.rating)} · ${option.provider}",
                        style = typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(priceRange(option.priceLow, option.priceHigh), style = typography.cardTitle, color = colors.textPrimary)
                Text("est. fare", style = typography.caption, color = colors.textSecondary)
            }
        }

        if (fits && (isCheapest || isFastest)) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCheapest) AccentTag(text = "Cheapest", icon = Icons.Filled.LocalOffer)
                if (isFastest) AccentTag(text = "Fastest", icon = Icons.Filled.Bolt)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetaPill(icon = Icons.Filled.Schedule, text = "${option.etaMinutes} min away")
            MetaPill(icon = Icons.Outlined.People, text = "Up to ${option.capacity}")
            if (option.surge) {
                MetaPill(icon = Icons.Filled.Bolt, text = "${surgeLabel(option.surgeMultiplier)} surge", tint = colors.warning)
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            isBooked -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp))
                Column {
                    Text("Booked — driver en route", style = typography.bodyMedium, color = colors.success)
                    Text("Arriving in ~${option.etaMinutes} min", style = typography.caption, color = colors.textSecondary)
                }
            }

            isBooking -> Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = colors.accent,
                    disabledContentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Booking…", style = typography.bodyMedium)
            }

            !fits -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                Text(
                    "Seats up to ${option.capacity} — reduce party size",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }

            else -> Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBook()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Book ${option.service}", style = typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NoSeatsCard(passengers: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.warning, modifier = Modifier.size(22.dp))
            Column {
                Text("No vehicle seats $passengers", style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "Lower the party size or split across two rides.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyOptionsCard() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text("No rides available", style = typography.cardTitle, color = colors.textPrimary)
            Text(
                "We couldn't find any rides for this route right now. Try again in a moment.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MetaPill(icon: ImageVector, text: String, tint: Color = JetTheme.colors.textSecondary) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceElevated)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text, style = JetTheme.typography.label, color = tint)
    }
}

private fun iconFor(vehicleClass: GroundTransportVehicleClass): ImageVector = when (vehicleClass) {
    GroundTransportVehicleClass.STANDARD -> Icons.Filled.DirectionsCar
    GroundTransportVehicleClass.PREMIUM -> Icons.Filled.Star
    GroundTransportVehicleClass.XL -> Icons.Filled.AirportShuttle
    GroundTransportVehicleClass.TAXI -> Icons.Filled.LocalTaxi
}

private fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.0f", amount)

private fun priceRange(low: Double, high: Double): String = "${money(low)} – ${money(high)}"

private fun miles(distance: Double): String = String.format(Locale.US, "%.1f mi", distance)

private fun rating(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun surgeLabel(multiplier: Double): String = String.format(Locale.US, "%.2f×", multiplier)

@Preview(showBackground = true, name = "Ground Transport – to airport")
@Composable
private fun GroundTransportContentPreview() {
    JetSetterTheme {
        GroundTransportContent(
            state = GroundtransportUiState(
                direction = GroundTransportDirection.TO_AIRPORT,
                pickup = "Bellagio Resort & Casino",
                dropoff = "LAS · Harry Reid International",
                distanceMiles = 4.5,
                rideMinutes = 12,
                passengers = 2,
                sort = GroundTransportSort.PRICE,
                booking = GroundTransportBooking(
                    directionName = GroundTransportDirection.TO_AIRPORT.name,
                    optionId = "lyft",
                    service = "Lyft",
                    priceLow = 16.0,
                    priceHigh = 19.0,
                    etaMinutes = 5,
                ),
                options = listOf(
                    GroundTransportOption(
                        id = "uberx",
                        service = "UberX",
                        provider = "Uber",
                        vehicleClass = GroundTransportVehicleClass.STANDARD,
                        capacity = 4,
                        etaMinutes = 4,
                        priceLow = 16.0,
                        priceHigh = 20.0,
                        rating = 4.8,
                    ),
                    GroundTransportOption(
                        id = "lyft",
                        service = "Lyft",
                        provider = "Lyft",
                        vehicleClass = GroundTransportVehicleClass.STANDARD,
                        capacity = 4,
                        etaMinutes = 5,
                        priceLow = 16.0,
                        priceHigh = 19.0,
                        rating = 4.7,
                    ),
                    GroundTransportOption(
                        id = "lyft-xl",
                        service = "Lyft XL",
                        provider = "Lyft · 6 seats",
                        vehicleClass = GroundTransportVehicleClass.XL,
                        capacity = 6,
                        etaMinutes = 8,
                        priceLow = 22.0,
                        priceHigh = 27.0,
                        rating = 4.7,
                    ),
                    GroundTransportOption(
                        id = "uber-black",
                        service = "Uber Black",
                        provider = "Uber · Premium sedan",
                        vehicleClass = GroundTransportVehicleClass.PREMIUM,
                        capacity = 4,
                        etaMinutes = 7,
                        priceLow = 47.0,
                        priceHigh = 56.0,
                        rating = 4.9,
                    ),
                ),
            ),
            onSelectDirection = {},
            onSetPassengers = {},
            onSetSort = {},
            onBook = {},
            onCancelBooking = {},
        )
    }
}
