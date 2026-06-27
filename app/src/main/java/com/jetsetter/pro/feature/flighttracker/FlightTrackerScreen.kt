package com.jetsetter.pro.feature.flighttracker

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [FlighttrackerViewModel], collects its [FlighttrackerUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [FlightTrackerContent].
 */
@Composable
fun FlightTrackerScreen(viewModel: FlighttrackerViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    FlightTrackerContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onSearch = viewModel::onSearch,
        onSelectRecent = viewModel::onSelectRecent,
        onSelectFlight = viewModel::onSelectFlight,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun FlightTrackerContent(
    state: FlighttrackerUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
    onSelectRecent: (String) -> Unit,
    onSelectFlight: (FlightSnapshot) -> Unit,
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

        // One-time entrance: quick fade + slight upward slide on first composition. Uses a
        // graphicsLayer transform so it never affects layout or scrolling.
        var revealed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { revealed = true }
        val entranceAlpha by animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "entranceAlpha",
        )
        val slidePx = with(LocalDensity.current) { spacing.large.toPx() }
        val entranceTranslateY by animateFloatAsState(
            targetValue = if (revealed) 0f else slidePx,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "entranceTranslateY",
        )

        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = entranceAlpha
                    translationY = entranceTranslateY
                }
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = spacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Flight Tracker", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
                Text(
                    "Live status for ${state.totalCount} flights",
                    style = JetTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }

            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onSearch = onSearch,
            )

            state.selected?.let { LiveStatusCard(it) }

            if (state.recentIdents.isNotEmpty() && !state.isFiltering) {
                RecentSearches(idents = state.recentIdents, onSelectRecent = onSelectRecent)
            }

            if (state.isNotFound) {
                NotFoundCard(query = state.query)
            } else {
                ResultsHeader(
                    isFiltering = state.isFiltering,
                    shown = state.results.size,
                    total = state.totalCount,
                )
                if (state.results.isEmpty()) {
                    EmptyBoardCard()
                } else {
                    state.results.forEach { flight ->
                        FlightRow(
                            flight = flight,
                            isTracked = flight.ident == state.selected?.ident,
                            onClick = { onSelectFlight(flight) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
) {
    val colors = JetTheme.colors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search by flight ident") },
        placeholder = { Text("e.g. DL1423") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accent)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = colors.textSecondary)
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.separator,
            cursorColor = colors.accent,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedPlaceholderColor = colors.textSecondary,
            unfocusedPlaceholderColor = colors.textSecondary,
        ),
    )
}

@Composable
private fun LiveStatusCard(flight: FlightSnapshot) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val statusColor = statusColorFor(flight)

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(flight.ident, style = typography.cardTitle, color = colors.textPrimary)
                Text(flight.airline, style = typography.caption, color = colors.textSecondary)
            }
            StatusChip(label = flight.statusLabel, color = statusColor, icon = phaseIcon(flight.phase))
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Endpoint(code = flight.originCode, city = flight.originCity, alignEnd = false)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
            )
            Endpoint(code = flight.destinationCode, city = flight.destinationCity, alignEnd = true)
        }

        Spacer(Modifier.height(16.dp))

        ProgressBar(progress = flight.progress, modifier = Modifier.fillMaxWidth(), barHeight = 8.dp)

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("DEPART", style = typography.caption, color = colors.textSecondary)
                Text(flight.departureTime, style = typography.cardTitle, color = colors.textPrimary)
                if (flight.isDelayed) {
                    Text(
                        flight.scheduledDepartureTime,
                        style = typography.caption,
                        color = colors.textSecondary,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ARRIVE", style = typography.caption, color = colors.textSecondary)
                Text(flight.arrivalTime, style = typography.cardTitle, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AccentTag(text = flight.countdownLabel, icon = Icons.Filled.Flight)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = colors.separator)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoColumn(label = "GATE", value = flight.gate, modifier = Modifier.weight(1f))
            InfoColumn(label = "TERMINAL", value = flight.terminal, modifier = Modifier.weight(1f))
            InfoColumn(label = "FLOWN", value = "${flight.progressPercent}%", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Endpoint(code: String, city: String, alignEnd: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(code, style = typography.pageTitle, color = colors.textPrimary)
        Text(city, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 8.dp,
) {
    val colors = JetTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    // Ease the fill toward its real value instead of snapping. Target stays exactly [clamped].
    val animatedFraction by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progressFraction",
    )
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(CircleShape)
            .background(colors.separator),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.accent),
        )
    }
}

@Composable
private fun InfoColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = JetTheme.colors.textPrimary,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(modifier = modifier) {
        Text(label, style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun ResultsHeader(isFiltering: Boolean, shown: Int, total: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isFiltering) "MATCHES" else "FLIGHT BOARD",
            style = typography.caption,
            color = colors.textSecondary,
        )
        Text(
            if (isFiltering) "$shown of $total" else "$total flights",
            style = typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun FlightRow(flight: FlightSnapshot, isTracked: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val statusColor = statusColorFor(flight)
    val haptics = LocalHapticFeedback.current

    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(flight.ident, style = typography.bodyMedium, color = colors.textPrimary)
                    if (isTracked) AccentTag(text = "Tracking")
                }
                Text(
                    "${flight.originCode} → ${flight.destinationCode} · ${flight.airline}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(flight.statusLabel, style = typography.caption, color = statusColor)
                Text(flight.departureTime, style = typography.caption, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(progress = flight.progress, modifier = Modifier.fillMaxWidth(), barHeight = 4.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(idents: List<String>, onSelectRecent: (String) -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text("RECENT SEARCHES", style = JetTheme.typography.caption, color = colors.textSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            idents.forEach { ident ->
                AccentTag(
                    text = ident,
                    icon = Icons.Filled.Schedule,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectRecent(ident)
                        },
                )
            }
        }
    }
}

@Composable
private fun NotFoundCard(query: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.SearchOff,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "No flights match “${query.trim()}”",
                style = typography.cardTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Double-check the code, or try an airline + number such as DL1423 or UA512.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyBoardCard() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.FlightTakeoff,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(spacing.small))
            Text(
                "No flights on the board yet",
                style = typography.cardTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "Live departures and arrivals will appear here as flights are scheduled.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color, icon: ImageVector) {
    val typography = JetTheme.typography
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
        Text(label, style = typography.label, color = color)
    }
}

@Composable
private fun statusColorFor(flight: FlightSnapshot): Color {
    val colors = JetTheme.colors
    return when {
        flight.isDelayed -> colors.warning
        flight.phase == FlightPhase.LANDED -> colors.blue
        else -> colors.success
    }
}

private fun phaseIcon(phase: FlightPhase): ImageVector = when (phase) {
    FlightPhase.SCHEDULED -> Icons.Filled.Schedule
    FlightPhase.BOARDING -> Icons.Filled.FlightTakeoff
    FlightPhase.IN_AIR -> Icons.Filled.Flight
    FlightPhase.LANDED -> Icons.Filled.FlightLand
}

@Preview(showBackground = true, name = "Flight Tracker – populated")
@Composable
private fun FlightTrackerContentPreview() {
    val now = 8 * 60 // 08:00, fixed reference so the preview renders the same real values as the app
    val board = listOf(
        FlightTrackerFlight(
            ident = "DL1423", airline = "Delta Air Lines",
            originCode = "LAS", originCity = "Las Vegas",
            destinationCode = "ATL", destinationCity = "Atlanta",
            gate = "C22", terminal = "3", departureOffsetMin = -95L, durationMin = 258L,
        ),
        FlightTrackerFlight(
            ident = "UA512", airline = "United Airlines",
            originCode = "SFO", originCity = "San Francisco",
            destinationCode = "EWR", destinationCity = "Newark",
            gate = "F8", terminal = "3", departureOffsetMin = 30L, durationMin = 325L, delayMin = 45L,
        ),
        FlightTrackerFlight(
            ident = "B6701", airline = "JetBlue Airways",
            originCode = "BOS", originCity = "Boston",
            destinationCode = "FLL", destinationCity = "Fort Lauderdale",
            gate = "C6", terminal = "C", departureOffsetMin = -210L, durationMin = 185L,
        ),
    )
    JetSetterTheme {
        FlightTrackerContent(
            state = FlighttrackerUiState(
                query = "",
                selected = board[0].snapshotAt(now),
                results = board.map { it.snapshotAt(now) },
                recentIdents = listOf("UA512", "AA88"),
                totalCount = board.size,
            ),
            onQueryChange = {},
            onClearQuery = {},
            onSearch = {},
            onSelectRecent = {},
            onSelectFlight = {},
        )
    }
}
