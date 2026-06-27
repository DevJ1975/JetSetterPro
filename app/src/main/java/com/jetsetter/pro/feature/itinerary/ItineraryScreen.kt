package com.jetsetter.pro.feature.itinerary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [ItineraryViewModel], collects its [ItineraryUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [ItineraryContent].
 */
@Composable
fun ItineraryScreen(viewModel: ItineraryViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    ItineraryContent(
        state = state,
        onTogglePacked = viewModel::togglePacked,
        onShowAddSheet = viewModel::showAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onAddTrip = viewModel::addTrip,
        onDeleteTrip = viewModel::delete,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable. This is the half the guide tells you to mirror when porting a feature.
 */
@Composable
private fun ItineraryContent(
    state: ItineraryUiState,
    onTogglePacked: (Trip, PackingItem) -> Unit,
    onShowAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onAddTrip: (name: String, destination: String, startDate: String, endDate: String) -> Unit,
    onDeleteTrip: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                // One-time entrance: fade + subtle upward slide when the list first appears.
                var contentVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { contentVisible = true }
                val contentAlpha by animateFloatAsState(
                    targetValue = if (contentVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "itineraryEntrance",
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = (1f - contentAlpha) * 24.dp.toPx()
                        },
                    contentPadding = PaddingValues(
                        start = spacing.medium,
                        end = spacing.medium,
                        top = spacing.large,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    item {
                        Text("Itinerary", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
                    }
                    state.errorMessage?.let { message ->
                        item { ErrorCard(message) }
                    }
                    if (state.trips.isEmpty()) {
                        item { EmptyState() }
                    } else {
                        items(state.trips, key = { it.id }) { trip ->
                            TripCard(
                                trip = trip,
                                onTogglePacked = { item -> onTogglePacked(trip, item) },
                                onDelete = { onDeleteTrip(trip.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onShowAddSheet()
            },
            containerColor = colors.accent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.medium),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add trip")
        }
    }

    if (state.showAddSheet) {
        AddTripSheet(onDismiss = onDismissAddSheet, onConfirm = onAddTrip)
    }
}

/**
 * "Add Trip" modal bottom sheet. Inputs are local, transient draft state (kept across config
 * changes via [rememberSaveable]); the parent only tracks whether the sheet is shown. On confirm
 * the raw strings are handed up to the ViewModel, which trims and persists them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTripSheet(
    onDismiss: () -> Unit,
    onConfirm: (name: String, destination: String, startDate: String, endDate: String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }

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
            Text("New trip", style = typography.pageTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))

            FieldLabel("Trip name")
            PremiumTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Atlanta Board Meeting",
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Destination")
            PremiumTextField(
                value = destination,
                onValueChange = { destination = it },
                placeholder = "Atlanta, GA",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Start date")
                    PremiumTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        placeholder = "2026-07-14",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("End date")
                    PremiumTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        placeholder = "2026-07-16",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(name, destination, startDate, endDate)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add trip", style = typography.cardTitle)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun TripCard(
    trip: Trip,
    onTogglePacked: (PackingItem) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trip.name, style = typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${trip.startDate} → ${trip.endDate}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete ${trip.name}",
                    tint = colors.danger,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentTag(text = trip.destination, icon = Icons.Filled.Place)
            if (trip.items.isNotEmpty()) {
                AccentTag(text = "${trip.items.size} stops", icon = Icons.Filled.Event)
            }
        }
        if (trip.items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            trip.sortedItems.forEach { item -> ItineraryRow(item) }
        }
        if (trip.packingList.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("PACKING", style = typography.caption, color = colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            trip.packingList.forEach { packingItem ->
                PackingRow(packingItem, onToggle = { onTogglePacked(packingItem) })
            }
        }
    }
}

@Composable
private fun ItineraryRow(item: ItineraryItem) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(item.type.colorHex)))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = typography.bodyMedium, color = colors.textPrimary)
            val location = item.location
            if (location != null) {
                Text(location, style = typography.caption, color = colors.textSecondary)
            }
        }
        Text(item.type.label, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun PackingRow(item: PackingItem, onToggle: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                stateDescription = if (item.isPacked) "Packed" else "Not packed"
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (item.isPacked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.isPacked) colors.success else colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            item.name,
            style = typography.bodyMedium,
            color = if (item.isPacked) colors.textSecondary else colors.textPrimary,
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("Couldn't load trips", style = JetTheme.typography.cardTitle, color = colors.danger)
        Spacer(Modifier.height(4.dp))
        Text(message, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@Composable
private fun EmptyState() {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.FlightTakeoff,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(spacing.small))
            Text("No trips yet", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "Tap + to add your first trip.",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Preview(showBackground = true, name = "Itinerary – populated")
@Composable
private fun ItineraryContentPreview() {
    JetSetterTheme {
        ItineraryContent(
            state = ItineraryUiState(
                trips = listOf(
                    Trip(
                        name = "Atlanta Board Meeting",
                        destination = "Atlanta, GA",
                        startDate = "2026-07-14",
                        endDate = "2026-07-16",
                        items = listOf(
                            ItineraryItem(
                                title = "DL 1423 LAS → ATL",
                                type = ItineraryItemType.FLIGHT,
                                startDate = "2026-07-14T07:00",
                                location = "Gate C22",
                            ),
                            ItineraryItem(
                                title = "Four Seasons Atlanta",
                                type = ItineraryItemType.HOTEL,
                                startDate = "2026-07-14T15:00",
                            ),
                        ),
                        packingList = listOf(
                            PackingItem(name = "Suit", isPacked = true),
                            PackingItem(name = "Laptop + charger"),
                        ),
                    ),
                ),
            ),
            onTogglePacked = { _, _ -> },
            onShowAddSheet = {},
            onDismissAddSheet = {},
            onAddTrip = { _, _, _, _ -> },
            onDeleteTrip = {},
        )
    }
}
