package com.jetsetter.pro.feature.flightboard

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
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

/** Status pills shown in the summary, in the order a traveller scans them. */
private val STATUS_ORDER = listOf(
    FlightBoardStatus.ON_TIME,
    FlightBoardStatus.BOARDING,
    FlightBoardStatus.DELAYED,
    FlightBoardStatus.DEPARTED,
)

/**
 * Stateful entry point: owns the [FlightboardViewModel], collects its [FlightboardUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [FlightBoardContent].
 */
@Composable
fun FlightBoardScreen(viewModel: FlightboardViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    FlightBoardContent(
        state = state,
        onSelectDirection = viewModel::selectDirection,
        onSelectSort = viewModel::selectSort,
        onToggleStatus = viewModel::toggleStatusFilter,
        onUpdateQuery = viewModel::updateQuery,
        onClearFilters = viewModel::clearFilters,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun FlightBoardContent(
    state: FlightboardUiState,
    onSelectDirection: (FlightBoardDirection) -> Unit,
    onSelectSort: (FlightBoardSort) -> Unit,
    onToggleStatus: (FlightBoardStatus) -> Unit,
    onUpdateQuery: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography

    // One-time entrance: fade + slight slide-in on first composition (~250ms).
    // Applied via graphicsLayer below the background so the page eases in over a static backdrop.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "flightBoardEnterAlpha",
    )
    val enterOffsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(durationMillis = 250),
        label = "flightBoardEnterOffsetY",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer {
                alpha = enterAlpha
                translationY = enterOffsetY
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text("Flight Board", style = typography.displayTitle, color = colors.textPrimary)
        Text(
            "${state.airportCode} · ${state.airportName}",
            style = typography.caption,
            color = colors.textSecondary,
        )

        DirectionToggle(selected = state.direction, onSelect = onSelectDirection)

        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier
                    .padding(top = spacing.large)
                    .align(Alignment.CenterHorizontally),
            )
        } else {
            SearchField(query = state.query, onUpdateQuery = onUpdateQuery)
            SummaryCard(state = state, onToggleStatus = onToggleStatus, onClearFilters = onClearFilters)
            SortRow(sort = state.sort, onSelectSort = onSelectSort)
            BoardCard(state = state, onClearFilters = onClearFilters)
        }
    }
}

@Composable
private fun DirectionToggle(
    selected: FlightBoardDirection,
    onSelect: (FlightBoardDirection) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .padding(spacing.xsmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.xsmall),
    ) {
        FlightBoardDirection.values().forEach { direction ->
            val isSelected = direction == selected
            val icon = if (direction == FlightBoardDirection.DEPARTURES) {
                Icons.Filled.FlightTakeoff
            } else {
                Icons.Filled.FlightLand
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(direction)
                    }
                    .semantics {
                        role = Role.Tab
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    direction.label,
                    style = typography.label,
                    color = if (isSelected) Color.White else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onUpdateQuery: (String) -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        PremiumTextField(
            value = query,
            onValueChange = onUpdateQuery,
            placeholder = "Search flight, city or airline",
            modifier = Modifier.weight(1f),
        )
        if (query.isNotBlank()) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUpdateQuery("")
                    }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(
    state: FlightboardUiState,
    onToggleStatus: (FlightBoardStatus) -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    state.direction.label.uppercase(),
                    style = typography.caption,
                    color = colors.textSecondary,
                )
                Text(
                    "${state.totalCount} flights",
                    style = typography.metric,
                    color = colors.textPrimary,
                )
            }
            if (state.totalCount > 0) {
                AccentTag(text = "${state.onTimePercent}% on-time", icon = Icons.Filled.Schedule)
            }
        }

        Spacer(Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            STATUS_ORDER.forEach { status ->
                StatusFilterChip(
                    status = status,
                    count = state.countFor(status),
                    selected = state.statusFilter == status,
                    onClick = { onToggleStatus(status) },
                )
            }
        }

        if (state.hasActiveFilter) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Showing ${state.visibleFlights.size} of ${state.totalCount}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
                Text(
                    "Clear",
                    style = typography.label,
                    color = colors.accent,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClearFilters()
                        }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusFilterChip(
    status: FlightBoardStatus,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val color = statusColor(status)
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.18f) else colors.surfaceElevated)
            .border(
                width = 0.7.dp,
                color = if (selected) color.copy(alpha = 0.55f) else colors.separator,
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            status.label,
            style = typography.label,
            color = if (selected) color else colors.textPrimary,
        )
        Text(
            count.toString(),
            style = typography.label,
            color = if (selected) color else colors.textSecondary,
        )
    }
}

@Composable
private fun SortRow(sort: FlightBoardSort, onSelectSort: (FlightBoardSort) -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text("Sort by".uppercase(), style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        FlightBoardSort.values().forEach { option ->
            ChoiceChip(
                label = option.label,
                selected = option == sort,
                onClick = { onSelectSort(option) },
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Text(
        text = label,
        style = typography.label,
        color = if (selected) Color.White else colors.textSecondary,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .border(
                width = 0.7.dp,
                color = if (selected) Color.Transparent else colors.separator,
                shape = CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun BoardCard(state: FlightboardUiState, onClearFilters: () -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        when {
            state.isBoardEmpty -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlightTakeoff,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(spacing.small))
                    Text(
                        "No flights on the board",
                        style = typography.cardTitle,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.xsmall))
                    Text(
                        "Check back shortly.",
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            state.isFilteredEmpty -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(spacing.small))
                    Text(
                        "No matching flights",
                        style = typography.cardTitle,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.xsmall))
                    Text(
                        "Nothing matches your search or filter.",
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.medium))
                    Text(
                        "Clear filters",
                        style = typography.label,
                        color = colors.accent,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClearFilters()
                            }
                            .semantics { role = Role.Button }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            else -> {
                state.visibleFlights.forEachIndexed { index, flight ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.separator),
                        )
                    }
                    FlightRow(flight)
                }
            }
        }
    }
}

@Composable
private fun FlightRow(flight: FlightBoardItem) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val mono = FontFamily.Monospace
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor(flight.status)),
        )
        Column {
            Text(
                flight.time,
                style = typography.bodyMedium.copy(fontFamily = mono),
                color = colors.textPrimary,
            )
            Text(
                flight.flightNumber,
                style = typography.caption.copy(fontFamily = mono),
                color = colors.textSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(flight.city, style = typography.bodyMedium, color = colors.textPrimary)
            Text(
                "${flight.airline} · Gate ${flight.gate} · ${flight.terminal}",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
        StatusBadge(flight.status)
    }
}

/** Colour-coded status pill — like [AccentTag] but tinted to the flight's live status. */
@Composable
private fun StatusBadge(status: FlightBoardStatus) {
    val typography = JetTheme.typography
    val color = statusColor(status)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(statusIcon(status), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(status.label, style = typography.label, color = color)
    }
}

@Composable
private fun statusColor(status: FlightBoardStatus): Color {
    val colors = JetTheme.colors
    return when (status) {
        FlightBoardStatus.ON_TIME -> colors.success
        FlightBoardStatus.BOARDING -> colors.blue
        FlightBoardStatus.DELAYED -> colors.warning
        FlightBoardStatus.DEPARTED -> colors.textSecondary
    }
}

private fun statusIcon(status: FlightBoardStatus): ImageVector = when (status) {
    FlightBoardStatus.ON_TIME -> Icons.Filled.CheckCircle
    FlightBoardStatus.BOARDING -> Icons.Filled.FlightTakeoff
    FlightBoardStatus.DELAYED -> Icons.Filled.Warning
    FlightBoardStatus.DEPARTED -> Icons.Filled.DoneAll
}

@Preview(showBackground = true, name = "Flight Board – departures")
@Composable
private fun FlightBoardContentPreview() {
    JetSetterTheme {
        FlightBoardContent(
            state = FlightboardUiState(
                airportName = "Harry Reid International",
                airportCode = "LAS",
                direction = FlightBoardDirection.DEPARTURES,
                allFlights = listOf(
                    FlightBoardItem("d1", "06:15", "DL 1423", "Delta", "Atlanta", "C22", "T1", FlightBoardStatus.BOARDING, FlightBoardDirection.DEPARTURES),
                    FlightBoardItem("d2", "06:50", "AA 188", "American", "Dallas/Fort Worth", "B14", "T1", FlightBoardStatus.ON_TIME, FlightBoardDirection.DEPARTURES),
                    FlightBoardItem("d3", "07:30", "UA 642", "United", "Chicago O'Hare", "D55", "T3", FlightBoardStatus.ON_TIME, FlightBoardDirection.DEPARTURES),
                    FlightBoardItem("d4", "08:05", "WN 2210", "Southwest", "Denver", "B22", "T1", FlightBoardStatus.DELAYED, FlightBoardDirection.DEPARTURES),
                    FlightBoardItem("d7", "11:00", "NK 877", "Spirit", "Orlando", "C19", "T1", FlightBoardStatus.DEPARTED, FlightBoardDirection.DEPARTURES),
                    FlightBoardItem("a1", "06:05", "DL 980", "Delta", "Minneapolis", "C20", "T1", FlightBoardStatus.ON_TIME, FlightBoardDirection.ARRIVALS),
                ),
            ),
            onSelectDirection = {},
            onSelectSort = {},
            onToggleStatus = {},
            onUpdateQuery = {},
            onClearFilters = {},
        )
    }
}
