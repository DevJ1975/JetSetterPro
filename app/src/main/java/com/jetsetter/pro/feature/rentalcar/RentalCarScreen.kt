package com.jetsetter.pro.feature.rentalcar

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.time.LocalDate
import java.util.Locale

/**
 * Stateful entry point: owns the [RentalcarViewModel], collects its [RentalcarUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [RentalCarContent].
 */
@Composable
fun RentalCarScreen(viewModel: RentalcarViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RentalCarContent(
        state = state,
        onSelectOffer = viewModel::selectOffer,
        onSortChange = viewModel::setSort,
        onDaysChange = viewModel::onDaysChange,
        onIncrementDays = viewModel::incrementDays,
        onDecrementDays = viewModel::decrementDays,
        onOpenPartnerSite = { offer ->
            // Hand off to the partner's public reservation page for this offer's brand, using
            // the demo trip's pickup airport and the tester's chosen rental length as dates.
            val pickup = LocalDate.now()
            RentalcarDeepLinks.open(
                context = context,
                company = offer.company,
                pickupLocation = PICKUP_LOCATION,
                pickupDate = pickup,
                returnDate = pickup.plusDays(state.rentalDays.toLong()),
            )
        },
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun RentalCarContent(
    state: RentalcarUiState,
    onSelectOffer: (RentalCarsOffer) -> Unit,
    onSortChange: (RentalCarsSort) -> Unit,
    onDaysChange: (String) -> Unit,
    onIncrementDays: () -> Unit,
    onDecrementDays: () -> Unit,
    onOpenPartnerSite: (RentalCarsOffer) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                // One-time entrance: fade + gentle slide-up on first composition.
                var contentVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { contentVisible = true }
                val entrance by animateFloatAsState(
                    targetValue = if (contentVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "rentalCarEntrance",
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = entrance
                            translationY = (1f - entrance) * 16.dp.toPx()
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.medium)
                        .padding(top = spacing.large, bottom = spacing.xlarge),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    Text("Rental Cars", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

                    if (state.offers.isEmpty()) {
                        EmptyState()
                    } else {
                        SummaryCard(state = state)

                        RentalLengthCard(
                            daysInput = state.daysInput,
                            rentalDays = state.rentalDays,
                            error = state.daysError,
                            canDecrement = state.rentalDays > MIN_DAYS,
                            onDaysChange = onDaysChange,
                            onIncrement = onIncrementDays,
                            onDecrement = onDecrementDays,
                        )

                        SortRow(active = state.sort, onSortChange = onSortChange)

                        Text(
                            "${state.offers.size} OFFERS · ${state.rentalDays}-${dayWord(state.rentalDays).uppercase(Locale.US)} RENTAL",
                            style = JetTheme.typography.caption,
                            color = colors.textSecondary,
                        )

                        state.sortedOffers.forEach { offer ->
                            OfferCard(
                                offer = offer,
                                rentalDays = state.rentalDays,
                                isSelected = offer.id == state.selectedOfferId,
                                isCheapest = offer.id == state.cheapestOffer?.id,
                                onSelect = { onSelectOffer(offer) },
                                onOpenPartnerSite = { onOpenPartnerSite(offer) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: RentalcarUiState) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val selected = state.selectedOffer
    val headline = selected ?: state.cheapestOffer
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (selected != null) "SELECTED VEHICLE" else "STARTING FROM",
            style = typography.caption,
            color = colors.textSecondary,
        )
        if (headline != null) {
            val total = headline.totalFor(state.rentalDays)
            Text(money(total), style = typography.metric, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "${headline.company.label} · ${headline.carClass}",
                style = typography.bodyMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${money(headline.pricePerDay)}/day × ${state.rentalDays} ${dayWord(state.rentalDays)} = ${money(total)}",
                style = typography.caption,
                color = colors.accent,
            )
            if (selected == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pick a vehicle below to lock in your rate.",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RentalLengthCard(
    daysInput: String,
    rentalDays: Int,
    error: String?,
    canDecrement: Boolean,
    onDaysChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("RENTAL LENGTH", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepperButton(
                icon = Icons.Filled.Remove,
                contentDescription = "Decrease rental length",
                enabled = canDecrement,
                onClick = onDecrement,
            )
            PremiumTextField(
                value = daysInput,
                onValueChange = onDaysChange,
                placeholder = "0",
                modifier = Modifier.width(64.dp),
            )
            StepperButton(
                icon = Icons.Filled.Add,
                contentDescription = "Increase rental length",
                enabled = rentalDays < MAX_DAYS,
                onClick = onIncrement,
            )
            Text("days", style = typography.bodyMedium, color = colors.textSecondary)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = typography.caption, color = colors.danger)
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
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accent.copy(alpha = if (enabled) 0.15f else 0.05f))
            .border(
                0.6.dp,
                colors.accent.copy(alpha = if (enabled) 0.30f else 0.10f),
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.accent else colors.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SortRow(active: RentalCarsSort, onSortChange: (RentalCarsSort) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small)) {
        RentalCarsSort.values().forEach { sort ->
            SortChip(
                label = sort.label,
                selected = sort == active,
                onClick = { onSortChange(sort) },
            )
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else colors.surface)
            .border(
                0.6.dp,
                if (selected) colors.accent.copy(alpha = 0.50f) else colors.separator,
                CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Tab
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = JetTheme.typography.label,
            color = if (selected) colors.accent else colors.textSecondary,
        )
    }
}

@Composable
private fun OfferCard(
    offer: RentalCarsOffer,
    rentalDays: Int,
    isSelected: Boolean,
    isCheapest: Boolean,
    onSelect: () -> Unit,
    onOpenPartnerSite: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val onSelectWithHaptic = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onSelect()
    }
    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelectWithHaptic)
            .semantics {
                role = Role.Button
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, colors.success, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(offer.company.label, style = typography.cardTitle, color = colors.textPrimary)
                Text(offer.carClass, style = typography.bodyMedium, color = colors.accent)
                Text(offer.exampleModel, style = typography.caption, color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(offer.pricePerDay), style = typography.cardTitle, color = colors.textPrimary)
                Text("per day", style = typography.caption, color = colors.textSecondary)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small)) {
            AccentTag(
                text = offer.transmission.label,
                icon = if (offer.transmission == RentalCarsTransmission.AUTOMATIC) {
                    Icons.Outlined.Settings
                } else {
                    Icons.Filled.LocalGasStation
                },
            )
            AccentTag(text = "${offer.seats} seats", icon = Icons.Filled.EventSeat)
            if (offer.unlimitedMileage) {
                AccentTag(text = "Unlimited mi", icon = Icons.Outlined.AllInclusive)
            }
            if (isCheapest) {
                AccentTag(text = "Lowest total", icon = Icons.Filled.Star)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${money(offer.totalFor(rentalDays))} total",
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    "${money(offer.pricePerDay)}/day × $rentalDays ${dayWord(rentalDays)}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onSelectWithHaptic,
                modifier = Modifier.minimumInteractiveComponentSize(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) colors.success else colors.accent,
                    contentColor = Color.White,
                ),
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Selected", style = typography.label)
                } else {
                    Text("Select", style = typography.label)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OpenPartnerSiteButton(companyLabel = offer.company.label, onClick = onOpenPartnerSite)
    }
}

/**
 * Secondary hand-off action on each offer: opens the brand's own reservation site
 * (via [RentalcarDeepLinks]) prefilled with the demo itinerary. Styled like the sort
 * chips — quiet accent outline, so it never competes with the primary Select button.
 */
@Composable
private fun OpenPartnerSiteButton(companyLabel: String, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accent.copy(alpha = 0.10f))
            .border(0.6.dp, colors.accent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(14.dp),
        )
        Text("Open in $companyLabel", style = JetTheme.typography.label, color = colors.accent)
    }
}

@Composable
private fun EmptyState() {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text("No cars available", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "We couldn't find any rentals for these dates. Try adjusting your trip.",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val MIN_DAYS = 1
private const val MAX_DAYS = 60

/** Pickup point for partner hand-offs — the demo trip's origin airport (LAS, per the mock itinerary). */
private const val PICKUP_LOCATION = "LAS"

private fun dayWord(days: Int): String = if (days == 1) "day" else "days"

private fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.2f", amount)

@Preview(showBackground = true, name = "Rental Cars – populated")
@Composable
private fun RentalCarContentPreview() {
    JetSetterTheme {
        RentalCarContent(
            state = RentalcarUiState(
                rentalDays = 3,
                daysInput = "3",
                selectedOfferId = "preview-2",
                offers = listOf(
                    RentalCarsOffer(
                        id = "preview-1",
                        company = RentalCarsCompany.HERTZ,
                        carClass = "Full-size SUV",
                        exampleModel = "Chevrolet Tahoe or similar",
                        transmission = RentalCarsTransmission.AUTOMATIC,
                        seats = 7,
                        pricePerDay = 94.50,
                    ),
                    RentalCarsOffer(
                        id = "preview-2",
                        company = RentalCarsCompany.ENTERPRISE,
                        carClass = "Midsize Sedan",
                        exampleModel = "Toyota Camry or similar",
                        transmission = RentalCarsTransmission.AUTOMATIC,
                        seats = 5,
                        pricePerDay = 52.75,
                    ),
                ),
            ),
            onSelectOffer = {},
            onSortChange = {},
            onDaysChange = {},
            onIncrementDays = {},
            onDecrementDays = {},
            onOpenPartnerSite = {},
        )
    }
}

@Preview(showBackground = true, name = "Rental Cars – empty")
@Composable
private fun RentalCarEmptyPreview() {
    JetSetterTheme {
        RentalCarContent(
            state = RentalcarUiState(offers = emptyList(), isLoading = false),
            onSelectOffer = {},
            onSortChange = {},
            onDaysChange = {},
            onIncrementDays = {},
            onDecrementDays = {},
            onOpenPartnerSite = {},
        )
    }
}
