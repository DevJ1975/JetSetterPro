package com.jetsetter.pro.feature.rentalcar

import java.util.UUID

/** Rental agency offering a car for this trip. */
enum class RentalCarsCompany(val label: String) {
    HERTZ("Hertz"),
    ENTERPRISE("Enterprise"),
    NATIONAL("National"),
}

/** Gearbox type for a rental offer. */
enum class RentalCarsTransmission(val label: String) {
    AUTOMATIC("Automatic"),
    MANUAL("Manual"),
}

/** How the offer list is ordered. Labels double as the sort-chip captions. */
enum class RentalCarsSort(val label: String) {
    LOWEST_TOTAL("Lowest total"),
    HIGHEST_TOTAL("Highest total"),
    MOST_SEATS("Most seats"),
}

/**
 * A single bookable rental-car offer. Module-prefixed name avoids collisions with
 * other feature modules (guide §6).
 *
 * Only the daily rate is stored — the trip total is always derived from
 * [totalFor] so it can never drift out of sync with the chosen number of days
 * (no baked-in static value that contradicts the inputs).
 */
data class RentalCarsOffer(
    val id: String = UUID.randomUUID().toString(),
    val company: RentalCarsCompany,
    val carClass: String,
    val exampleModel: String,
    val transmission: RentalCarsTransmission,
    val seats: Int,
    val pricePerDay: Double,
    val unlimitedMileage: Boolean = true,
) {
    /** Real, self-consistent total: price/day × days. */
    fun totalFor(days: Int): Double = pricePerDay * days
}

/**
 * Tester-entered state we persist between launches via [com.jetsetter.pro.core.data.prefs.ModuleStateStore]
 * (serialized with Moshi, mirroring the Converters idiom). Offer ids are stable in the
 * repository, so a restored [selectedOfferId] still matches after a restart.
 */
data class RentalcarPersistedState(
    val rentalDays: Int = 3,
    val selectedOfferId: String? = null,
    val sort: RentalCarsSort = RentalCarsSort.LOWEST_TOTAL,
)
