package com.jetsetter.pro.feature.rentalcar

/**
 * Single immutable UI-state object for the Rental Cars screen — one object rather than
 * scattered flags, so impossible states can't arise and previews are trivial (guide §6).
 *
 * [rentalDays] is the validated source of truth used for every total, while [daysInput]
 * holds the raw text in the editor so an in-progress/invalid entry never corrupts the math.
 */
data class RentalcarUiState(
    val offers: List<RentalCarsOffer> = emptyList(),
    val selectedOfferId: String? = null,
    val rentalDays: Int = 3,
    val daysInput: String = "3",
    val daysError: String? = null,
    val sort: RentalCarsSort = RentalCarsSort.LOWEST_TOTAL,
    val isLoading: Boolean = false,
) {
    /** Offers ordered for display per the active [sort]. Total ∝ price/day for a fixed length. */
    val sortedOffers: List<RentalCarsOffer>
        get() = when (sort) {
            RentalCarsSort.LOWEST_TOTAL -> offers.sortedBy { it.pricePerDay }
            RentalCarsSort.HIGHEST_TOTAL -> offers.sortedByDescending { it.pricePerDay }
            RentalCarsSort.MOST_SEATS -> offers.sortedWith(
                compareByDescending<RentalCarsOffer> { it.seats }.thenBy { it.pricePerDay },
            )
        }

    /** The currently selected offer, or null when none is chosen. */
    val selectedOffer: RentalCarsOffer?
        get() = offers.firstOrNull { it.id == selectedOfferId }

    /** Cheapest offer by daily rate — used to badge the best value and the "starting from" total. */
    val cheapestOffer: RentalCarsOffer?
        get() = offers.minByOrNull { it.pricePerDay }
}
