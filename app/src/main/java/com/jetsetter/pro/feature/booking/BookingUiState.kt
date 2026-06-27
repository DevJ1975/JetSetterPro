package com.jetsetter.pro.feature.booking

/**
 * Single immutable UI-state object for the Hotel Booking screen — one object rather than
 * scattered flags, so impossible states can't arise and previews are trivial.
 *
 * Raw inputs are stored as fields; everything the UI renders (filtered/sorted list, budget
 * validation, the selected stay, totals) is *derived* below so the numbers can never drift
 * out of sync with the inputs.
 */
data class BookingUiState(
    val summary: HotelSearchSummary = HotelSearchSummary(),
    val hotels: List<HotelBookingItem> = emptyList(),
    val selectedHotelId: String? = null,
    /** Which hotel's detail bottom sheet is open, if any. */
    val detailHotelId: String? = null,
    val sortMode: SortMode = SortMode.RECOMMENDED,
    /** Raw text from the "max nightly budget" field; parsed + validated on the fly. */
    val maxNightlyBudget: String = "",
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = false,
) {
    /** Validation message for the budget field, or null when the input is empty/valid. */
    val budgetError: String?
        get() {
            val raw = maxNightlyBudget.trim()
            if (raw.isEmpty()) return null
            val value = raw.toDoubleOrNull() ?: return "Enter a number, e.g. 300"
            return if (value <= 0) "Must be greater than 0" else null
        }

    /** The effective price ceiling, or null when there's no valid filter to apply. */
    val budgetCeiling: Double?
        get() = maxNightlyBudget.trim().toDoubleOrNull()?.takeIf { it > 0 }

    val hasActiveFilters: Boolean
        get() = showFavoritesOnly || maxNightlyBudget.isNotBlank()

    /** The results actually shown: favorites + budget filters applied, then sorted. */
    val visibleHotels: List<HotelBookingItem>
        get() {
            val ceiling = budgetCeiling
            return hotels
                .filter { !showFavoritesOnly || it.isFavorite }
                .filter { ceiling == null || it.pricePerNight <= ceiling }
                .sortedFor(sortMode)
        }

    val selectedHotel: HotelBookingItem?
        get() = hotels.firstOrNull { it.id == selectedHotelId }

    val detailHotel: HotelBookingItem?
        get() = hotels.firstOrNull { it.id == detailHotelId }

    val favoriteCount: Int
        get() = hotels.count { it.isFavorite }
}
