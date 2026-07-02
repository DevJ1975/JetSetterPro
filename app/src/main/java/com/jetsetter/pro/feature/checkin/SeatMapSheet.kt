package com.jetsetter.pro.feature.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Interactive seat map presented as a bottom sheet during check-in (and for post-check-in seat
 * changes). Pure UI: the layout comes from [CheckinSeatMap.forFlight]; the only state is the
 * pending selection, hoisted out via [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatMapSheet(
    flight: CheckInFlight,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    val rows = remember(flight.id, flight.seat) { CheckinSeatMap.forFlight(flight) }
    var selectedSeat by remember(flight.id) { mutableStateOf(flight.seat) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(bottom = spacing.large)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Choose your seat", style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "${flight.flightNumber} · ${flight.originCode} → ${flight.destinationCode} · ${flight.cabin.label}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }

            SeatLegend()

            // Cabin nose marker, then the rows.
            Icon(
                Icons.Filled.FlightTakeoff,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.CenterHorizontally),
            )
            rows.forEach { row ->
                SeatRow(
                    row = row,
                    selectedSeat = selectedSeat,
                    onSelect = { seat ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedSeat = seat
                    },
                )
            }

            Spacer(Modifier.height(spacing.xsmall))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(selectedSeat)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Outlined.EventSeat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (flight.isCheckedIn) "Confirm seat $selectedSeat"
                    else "Check in with seat $selectedSeat",
                    style = typography.cardTitle,
                )
            }
        }
    }
}

@Composable
private fun SeatRow(
    row: SeatMapRow,
    selectedSeat: String,
    onSelect: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${row.row}",
            style = typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.left.forEach { seat ->
                SeatCell(seat = seat, isSelected = seat.id == selectedSeat, onSelect = onSelect)
            }
            Spacer(Modifier.width(24.dp)) // the aisle
            row.right.forEach { seat ->
                SeatCell(seat = seat, isSelected = seat.id == selectedSeat, onSelect = onSelect)
            }
        }
        Spacer(Modifier.width(24.dp)) // balance the row-number gutter so the aisle stays centered
    }
}

@Composable
private fun SeatCell(
    seat: SeatMapSeat,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val background = when {
        isSelected -> colors.accent
        seat.isOccupied -> colors.surfaceElevated
        else -> colors.accent.copy(alpha = 0.12f)
    }
    val textColor = when {
        isSelected -> Color.White
        seat.isOccupied -> colors.textSecondary.copy(alpha = 0.45f)
        else -> colors.textPrimary
    }
    Box(
        modifier = Modifier
            .padding(3.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(
                width = if (isSelected) 0.dp else 0.6.dp,
                color = if (seat.isOccupied) Color.Transparent else colors.accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(9.dp),
            )
            .clickable(enabled = !seat.isOccupied) { onSelect(seat.id) }
            .semantics {
                role = Role.RadioButton
                stateDescription = when {
                    isSelected -> "Selected"
                    seat.isOccupied -> "Occupied"
                    else -> "Available"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            seat.id,
            style = typography.label,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SeatLegend() {
    val colors = JetTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendSwatch(color = colors.accent.copy(alpha = 0.12f), border = colors.accent.copy(alpha = 0.35f), label = "Available")
        LegendSwatch(color = colors.accent, border = Color.Transparent, label = "Selected")
        LegendSwatch(color = colors.surfaceElevated, border = Color.Transparent, label = "Occupied")
    }
}

@Composable
private fun LegendSwatch(color: Color, border: Color, label: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(0.6.dp, border, RoundedCornerShape(4.dp)),
        )
        Text(label, style = typography.caption, color = colors.textSecondary)
    }
}
