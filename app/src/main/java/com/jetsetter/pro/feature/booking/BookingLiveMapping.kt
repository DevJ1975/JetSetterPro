package com.jetsetter.pro.feature.booking

import com.jetsetter.pro.core.data.remote.expedia.ExpediaProperty
import com.jetsetter.pro.core.data.remote.expedia.lowestInclusiveTotal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The demo shop context + the Expedia Rapid → [HotelBookingItem] mapping (plan B7).
 *
 * The constants below are the single source of truth for BOTH the header display and the live
 * availability query, so what's on screen always describes what was actually shopped. The
 * property-id list is the Lisbon demo shop: Rapid has no free-text destination search on
 * `v3/properties/availability` — you shop explicit `property_id`s. The ids here are
 * placeholders to be curated from the Rapid content feed once the partner keys land
 * (docs/BOOKING_IMAGERY_PLAN.md); until then the keys are unconfigured and this list is never
 * sent anywhere.
 */

/** Stay length of the demo search window, in nights. */
internal const val NIGHTS: Int = 3

/** Party size of the demo search (adults). */
internal const val GUESTS: Int = 2

/** Currency every live quote is requested — and validated — in. */
internal const val LIVE_CURRENCY: String = "USD"

/** Rendered under the location pin on live cards (the demo shop is all-Lisbon). */
internal const val LIVE_NEIGHBORHOOD: String = "Lisbon"

/** Header date format ("Jul 14") — matches the previous hardcoded mock strings. */
internal val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/**
 * Rapid property ids for the Lisbon demo shop (placeholders until the Rapid partnership is
 * live — see file KDoc). Numeric strings per Rapid's id scheme.
 */
internal val LIVE_PROPERTY_IDS: List<String> = listOf(
    "11775754", // placeholder: Avenida da Liberdade area
    "20321165", // placeholder: Alfama area
    "34765290", // placeholder: Bairro Alto area
    "47119802", // placeholder: Baixa area
    "58234176", // placeholder: Sete Rios area
)

// Reuses the mock cards' palette so live placeholders sit naturally next to the sample art.
private val LIVE_PHOTO_COLORS = longArrayOf(0xFF4C6FBF, 0xFFB5793A, 0xFF3F8F73, 0xFF8E6FB0, 0xFFC25C6A)

/**
 * Maps one priced Rapid property onto the booking card model, or null when it can't be shown
 * honestly: no property id, no usable inclusive quote, or a quote in a currency other than
 * [expectedCurrency] (the screen renders bare `$` amounts — a mis-currencied price would lie).
 *
 * Availability carries no star rating, guest rating, photos, or amenities — those live in the
 * separate Rapid *content* API (a future enrichment; docs/BOOKING_IMAGERY_PLAN.md) — so ratings
 * map to honest zeros and the photo box gets a deterministic placeholder color per property.
 * `pricePerNight` = inclusive stay total / [nights], keeping the card's "nightly × nights"
 * breakdown reconciled with what Rapid actually quoted.
 */
internal fun ExpediaProperty.toHotelBookingItem(
    nights: Int,
    expectedCurrency: String,
): HotelBookingItem? {
    if (nights <= 0) return null
    val id = propertyId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val price = lowestInclusiveTotal() ?: return null
    if (price.currency != expectedCurrency.trim().uppercase(Locale.US)) return null
    return HotelBookingItem(
        // Stable, source-prefixed id so persisted favorites/selection survive a restart and
        // can never collide with the mock cards' ids.
        id = "expedia-$id",
        name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Property $id",
        starRating = 0,
        guestRating = 0.0,
        neighborhood = LIVE_NEIGHBORHOOD,
        pricePerNight = price.total / nights,
        photoColorHex = LIVE_PHOTO_COLORS[Math.floorMod(id.hashCode(), LIVE_PHOTO_COLORS.size)],
        amenities = emptyList(),
        freeCancellation = price.refundable,
    )
}
