package com.jetsetter.pro.feature.travelwallet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The kind of document held in the wallet. Each carries its own display [label], chip [icon],
 * and a [defaultDetailLabel] / [detailHint] so the "Add to wallet" form can suggest the most
 * useful field for that type (seat, confirmation #, policy #, …). Keeping this on the type means
 * the UI stays a pure function of the data. Names are module-prefixed to avoid collisions.
 *
 * Only the constant *name* is persisted by Moshi, so adding fields here is serialization-safe.
 */
enum class TravelWalletPassType(
    val label: String,
    val icon: ImageVector,
    val defaultDetailLabel: String,
    val detailHint: String,
) {
    BOARDING_PASS("Boarding Pass", Icons.Filled.FlightTakeoff, "Seat", "12A · Zone 1"),
    HOTEL("Hotel", Icons.Filled.Hotel, "Confirmation", "FS-8842193"),
    RENTAL_CAR("Rental Car", Icons.Filled.DirectionsCar, "Reservation", "H7-2290114"),
    EVENT_TICKET("Event Ticket", Icons.Filled.ConfirmationNumber, "Ticket", "Row 7 · Seat 18"),
    INSURANCE("Travel Insurance", Icons.Filled.HealthAndSafety, "Policy", "TG-4471209"),
}

/**
 * A single pass / document in the Travel Wallet. [keyDetailLabel] + [keyDetailValue] render the
 * most useful field for the type (seat, confirmation #, policy #, …); [date] is the relevant
 * date for the document. Plain data + enum so it serializes cleanly via Moshi for persistence.
 */
data class TravelWalletItem(
    val id: String,
    val type: TravelWalletPassType,
    val title: String,
    val subtitle: String,
    val keyDetailLabel: String,
    val keyDetailValue: String,
    val date: String,
    val isFavorite: Boolean = false,
)
