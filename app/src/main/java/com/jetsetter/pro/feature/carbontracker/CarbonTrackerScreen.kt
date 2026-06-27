package com.jetsetter.pro.feature.carbontracker

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale

/**
 * Stateful entry point: owns the [CarbontrackerViewModel], collects its [CarbontrackerUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [CarbonTrackerContent].
 */
@Composable
fun CarbonTrackerScreen(viewModel: CarbontrackerViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    CarbonTrackerContent(
        state = state,
        minLegDistanceKm = viewModel.minLegDistanceKm,
        maxLegDistanceKm = viewModel.maxLegDistanceKm,
        onFlightCabinChanged = viewModel::onFlightCabinChanged,
        onAddLeg = viewModel::onAddLeg,
        onRemoveLeg = viewModel::onRemoveLeg,
        onOffsetSelected = viewModel::onOffsetSelected,
        onToggleOffsetPurchased = viewModel::onToggleOffsetPurchased,
    )
}

/** Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable. */
@Composable
private fun CarbonTrackerContent(
    state: CarbontrackerUiState,
    minLegDistanceKm: Int,
    maxLegDistanceKm: Int,
    onFlightCabinChanged: (String, CarbonCabinClass) -> Unit,
    onAddLeg: (String, Int, CarbonCabinClass) -> Unit,
    onRemoveLeg: (String) -> Unit,
    onOffsetSelected: (String) -> Unit,
    onToggleOffsetPurchased: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        // One-time entrance: fade + slight upward slide once data lands. Render-only transform
        // (graphicsLayer), so it never affects layout, measurement, or scroll behaviour.
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val entranceAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "carbonEntrance",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entranceAlpha
                    translationY = (1f - entranceAlpha) * 16.dp.toPx()
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = spacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xsmall)) {
                Text("Carbon Footprint", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
                Text(
                    "Per-passenger CO₂ for your trip",
                    style = JetTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            if (state.isEmpty) {
                EmptyState()
            } else {
                TripTotalCard(state)
                SectionHeader("FLIGHTS · ${state.flights.size}")
                state.flights.forEach { flight ->
                    FlightCard(
                        flight = flight,
                        onCabinChanged = { onFlightCabinChanged(flight.id, it) },
                        onRemove = if (flight.isCustom) ({ onRemoveLeg(flight.id) }) else null,
                    )
                }
            }

            SectionHeader("ADD A LEG")
            AddLegCard(
                minDistanceKm = minLegDistanceKm,
                maxDistanceKm = maxLegDistanceKm,
                onAddLeg = onAddLeg,
            )

            if (!state.isEmpty) {
                SectionHeader("OFFSET YOUR TRIP")
                OffsetCard(
                    offsets = state.offsets,
                    selectedOffset = state.selectedOffset,
                    tripTotalCo2Kg = state.tripTotalCo2Kg,
                    offsetCostUsd = state.offsetCostUsd,
                    purchased = state.offsetPurchased,
                    coverage = state.offsetCoverage,
                    onOffsetSelected = onOffsetSelected,
                    onTogglePurchased = onToggleOffsetPurchased,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripTotalCard(state: CarbontrackerUiState) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val total = state.tripTotalCo2Kg
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("TRIP TOTAL CO₂", style = typography.caption, color = colors.textSecondary)
        Text(kgFormat(total), style = typography.metric, color = colors.textPrimary)
        Text(
            "Across ${state.flights.size} flights · ${kmFormat(state.totalDistanceKm)} · " +
                "${perKmFormat(state.averageKgPerKm)} avg",
            style = typography.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(text = state.emissionLevel.label, color = levelColor(state.emissionLevel))
            if (state.offsetPurchased) {
                StatusChip(text = "Neutralized", color = colors.success, icon = Icons.Filled.CheckCircle)
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccentTag(text = "≈ ${treeYearsFor(total)} trees for a year", icon = Icons.Filled.Spa)
            AccentTag(text = "= ${kmFormat(carKmFor(total))} by car", icon = Icons.Filled.DirectionsCar)
        }
    }
}

@Composable
private fun FlightCard(
    flight: CarbonFlightEstimate,
    onCabinChanged: (CarbonCabinClass) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(flight.route, style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "${flight.flightNumber} · ${kmFormat(flight.distanceKm)}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    kgFormat(flight.co2Kg),
                    style = typography.cardTitle,
                    color = levelColor(flight.level),
                )
                if (onRemove != null) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemove()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove ${flight.route}",
                            tint = colors.danger,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = flight.level.label, color = levelColor(flight.level))
            AccentTag(text = flight.cabinClass.label, icon = Icons.Filled.EventSeat)
        }
        Spacer(Modifier.height(12.dp))
        Text("CABIN", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        CabinChips(selected = flight.cabinClass, onSelect = onCabinChanged)
    }
}

@Composable
private fun AddLegCard(
    minDistanceKm: Int,
    maxDistanceKm: Int,
    onAddLeg: (String, Int, CarbonCabinClass) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current

    var label by rememberSaveable { mutableStateOf("") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var cabinName by rememberSaveable { mutableStateOf(CarbonCabinClass.ECONOMY.name) }
    val cabin = cabinClassOf(cabinName)

    val parsed = distanceText.toIntOrNull()
    val error: String? = when {
        distanceText.isEmpty() -> null
        parsed == null -> "Numbers only"
        parsed < minDistanceKm -> "Minimum ${kmFormat(minDistanceKm)}"
        parsed > maxDistanceKm -> "Maximum ${kmFormat(maxDistanceKm)}"
        else -> null
    }
    val valid = parsed != null && error == null
    val previewKg = if (valid) carbonEstimateKg(parsed.toDouble(), cabin) else 0.0

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("Estimate another flight", style = typography.cardTitle, color = colors.textPrimary)
        Text(
            "Distance × cabin factor — added legs roll into your trip total.",
            style = typography.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        FieldLabel("Route label (optional)")
        Spacer(Modifier.height(6.dp))
        PremiumTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = "SFO → NRT",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        FieldLabel("Distance (km)")
        Spacer(Modifier.height(6.dp))
        PremiumTextField(
            value = distanceText,
            onValueChange = { raw -> distanceText = raw.filter { it.isDigit() }.take(5) },
            placeholder = "$minDistanceKm – ${kmFormat(maxDistanceKm)}",
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = typography.caption, color = colors.danger)
        }

        Spacer(Modifier.height(12.dp))
        FieldLabel("Cabin · ${perKmFormat(cabin.kgPerKm)}")
        Spacer(Modifier.height(6.dp))
        CabinChips(selected = cabin, onSelect = { cabinName = it.name })

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Estimated CO₂", style = typography.bodyMedium, color = colors.textSecondary)
            Text(
                if (valid) kgFormat(previewKg) else "—",
                style = typography.cardTitle,
                color = if (valid) levelColor(flightLevel(previewKg)) else colors.textSecondary,
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAddLeg(label, parsed ?: 0, cabin)
                label = ""
                distanceText = ""
                cabinName = CarbonCabinClass.ECONOMY.name
            },
            enabled = valid,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.White,
                disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add leg to trip", style = typography.cardTitle)
        }
    }
}

@Composable
private fun OffsetCard(
    offsets: List<CarbonOffsetSuggestion>,
    selectedOffset: CarbonOffsetSuggestion?,
    tripTotalCo2Kg: Double,
    offsetCostUsd: Double,
    purchased: Boolean,
    coverage: Float,
    onOffsetSelected: (String) -> Unit,
    onTogglePurchased: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("Neutralize this trip", style = typography.cardTitle, color = colors.textPrimary)
        Text(
            "Choose a programme to offset your ${kgFormat(tripTotalCo2Kg)} footprint.",
            style = typography.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        offsets.forEach { offset ->
            OffsetRow(
                offset = offset,
                selected = offset.id == selectedOffset?.id,
                tripTotalCo2Kg = tripTotalCo2Kg,
                onClick = { onOffsetSelected(offset.id) },
            )
        }

        if (selectedOffset != null) {
            // Ease the bar toward its exact computed coverage instead of snapping. The fraction
            // itself is unchanged — only the on-screen fill is animated.
            val animatedCoverage by animateFloatAsState(
                targetValue = coverage,
                animationSpec = tween(durationMillis = 600),
                label = "offsetCoverage",
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedCoverage },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = colors.success,
                trackColor = colors.separator,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${(coverage * 100).toInt()}% of ${kgFormat(tripTotalCo2Kg)} offset",
                style = typography.caption,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Offset with ${selectedOffset.provider}",
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Text(money(offsetCostUsd), style = typography.cardTitle, color = colors.success)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePurchased()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (purchased) colors.surfaceElevated else colors.accent,
                    contentColor = if (purchased) colors.textPrimary else Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (purchased) Icons.Outlined.DeleteOutline else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (purchased) "Remove offset" else "Purchase offset · ${money(offsetCostUsd)}",
                    style = typography.cardTitle,
                )
            }
        }
    }
}

@Composable
private fun OffsetRow(
    offset: CarbonOffsetSuggestion,
    selected: Boolean,
    tripTotalCo2Kg: Double,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) colors.success else colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                offset.title,
                style = typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(offset.description, style = typography.caption, color = colors.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                money(offsetCostUsd(offset, tripTotalCo2Kg)),
                style = typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                "$${String.format(Locale.US, "%.0f", offset.pricePerTonneUsd)}/t",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CabinChips(selected: CarbonCabinClass, onSelect: (CarbonCabinClass) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CarbonCabinClass.entries.forEach { option ->
            CabinChip(
                label = option.label,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun CabinChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Text(
        text = label,
        style = JetTheme.typography.label,
        color = if (selected) Color.White else colors.textSecondary,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .border(
                width = 0.5.dp,
                color = if (selected) colors.accent else colors.separator,
                shape = CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun StatusChip(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(text, style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun EmptyState() {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FlightTakeoff,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Text("No flights yet", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Add a leg below to estimate its CO₂ and build your trip footprint.",
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun levelColor(level: CarbonEmissionLevel): Color = when (level) {
    CarbonEmissionLevel.LOW -> JetTheme.colors.success
    CarbonEmissionLevel.MODERATE -> JetTheme.colors.warning
    CarbonEmissionLevel.HIGH -> JetTheme.colors.danger
}

private fun offsetCostUsd(offset: CarbonOffsetSuggestion, totalCo2Kg: Double): Double =
    offset.pricePerTonneUsd * (totalCo2Kg / 1000.0)

private fun kgFormat(kg: Double): String = String.format(Locale.US, "%,.0f kg", kg)

private fun kmFormat(km: Int): String = String.format(Locale.US, "%,d km", km)

private fun perKmFormat(kgPerKm: Double): String = String.format(Locale.US, "%.2f kg/km", kgPerKm)

private fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.2f", amount)

@Preview(showBackground = true, name = "Carbon Footprint")
@Composable
private fun CarbonTrackerContentPreview() {
    JetSetterTheme {
        CarbonTrackerContent(
            state = CarbontrackerUiState(
                baseFlights = listOf(
                    CarbonFlightEstimate("dl1423", "DL 1423", "LAS", "ATL", 2780, CarbonCabinClass.BUSINESS),
                    CarbonFlightEstimate("aa218", "AA 218", "ATL", "JFK", 1224, CarbonCabinClass.ECONOMY),
                    CarbonFlightEstimate("ua90", "UA 90", "JFK", "LHR", 5541, CarbonCabinClass.BUSINESS),
                ),
                customLegs = listOf(
                    CarbonFlightEstimate(
                        id = "custom1",
                        flightNumber = "Added leg",
                        origin = "",
                        destination = "",
                        distanceKm = 9500,
                        cabinClass = CarbonCabinClass.ECONOMY,
                        isCustom = true,
                        routeLabel = "LHR → SIN",
                    ),
                ),
                offsets = listOf(
                    CarbonOffsetSuggestion(
                        id = "reforest",
                        title = "Reforestation — Pará, Brazil",
                        provider = "Gold Standard",
                        description = "Native tree planting that restores degraded rainforest.",
                        pricePerTonneUsd = 18.0,
                    ),
                    CarbonOffsetSuggestion(
                        id = "dac",
                        title = "Direct Air Capture",
                        provider = "Climeworks",
                        description = "Permanent removal — CO2 filtered from the air and mineralized.",
                        pricePerTonneUsd = 120.0,
                    ),
                ),
                selectedOffsetId = "reforest",
                offsetPurchased = true,
            ),
            minLegDistanceKm = 50,
            maxLegDistanceKm = 18_000,
            onFlightCabinChanged = { _, _ -> },
            onAddLeg = { _, _, _ -> },
            onRemoveLeg = {},
            onOffsetSelected = {},
            onToggleOffsetPurchased = {},
        )
    }
}
