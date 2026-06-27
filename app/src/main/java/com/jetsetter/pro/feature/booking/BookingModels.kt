package com.jetsetter.pro.feature.booking

import java.util.UUID

/**
 * Summary of the active hotel search — destination, stay window and party size.
 * Module-prefixed name to avoid colliding with other features' models.
 */
data class HotelSearchSummary(
    val destination: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val nights: Int = 0,
    val guests: Int = 0,
)

/** A single hotel search result rendered as a card on the Hotel Booking screen. */
data class HotelBookingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val starRating: Int,
    val guestRating: Double,
    val neighborhood: String,
    val pricePerNight: Double,
    /** ARGB hex used for the photo placeholder box (no network images in mock-first build). */
    val photoColorHex: Long,
    val amenities: List<String> = emptyList(),
    val freeCancellation: Boolean = false,
    val isFavorite: Boolean = false,
)

/** How the results list is ordered. Sorting is real — derived from the items, never faked. */
enum class SortMode(val label: String) {
    RECOMMENDED("Recommended"),
    PRICE_LOW("Price: low"),
    PRICE_HIGH("Price: high"),
    RATING("Top rated"),
}

/** Applies a [SortMode] to a result list. Stable, comparator-based — no hard-coded ordering. */
fun List<HotelBookingItem>.sortedFor(mode: SortMode): List<HotelBookingItem> = when (mode) {
    // "Recommended" blends quality and value: best guest rating first, cheaper breaks ties.
    SortMode.RECOMMENDED -> sortedWith(
        compareByDescending<HotelBookingItem> { it.guestRating }.thenBy { it.pricePerNight },
    )
    SortMode.PRICE_LOW -> sortedBy { it.pricePerNight }
    SortMode.PRICE_HIGH -> sortedByDescending { it.pricePerNight }
    SortMode.RATING -> sortedByDescending { it.guestRating }
}

/**
 * A fully derived price quote for a stay: every figure is computed from [HotelBookingItem.pricePerNight]
 * and the stay length, so the breakdown always reconciles (nightly × nights = subtotal; + tax = total).
 */
data class PriceQuote(
    val nightly: Double,
    val nights: Int,
    val subtotal: Double,
    val taxesAndFees: Double,
    val total: Double,
)

/** Tax + fee rate applied to the stay subtotal (mock estimate; a real build pulls this per region). */
const val TAX_AND_FEE_RATE: Double = 0.12

/** Builds the [PriceQuote] for this hotel over [nights] nights. */
fun HotelBookingItem.quote(nights: Int): PriceQuote {
    val safeNights = nights.coerceAtLeast(0)
    val subtotal = pricePerNight * safeNights
    val taxes = subtotal * TAX_AND_FEE_RATE
    return PriceQuote(
        nightly = pricePerNight,
        nights = safeNights,
        subtotal = subtotal,
        taxesAndFees = taxes,
        total = subtotal + taxes,
    )
}

/**
 * Tester-mutable booking state persisted across restarts via `ModuleStateStore` (Moshi JSON).
 * Transient session filters (budget ceiling, favorites-only) are intentionally *not* persisted.
 */
data class BookingPreferences(
    val favoriteIds: List<String> = emptyList(),
    val selectedHotelId: String? = null,
    val sortMode: String = SortMode.RECOMMENDED.name,
)
