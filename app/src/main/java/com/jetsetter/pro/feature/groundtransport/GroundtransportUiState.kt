package com.jetsetter.pro.feature.groundtransport

/**
 * Single immutable UI-state object for the Ground Transport screen — one object rather than
 * scattered flags, so impossible states can't arise and previews stay trivial. Display-time
 * derivations (sorting, fit, cheapest/fastest) are pure getters off the raw inputs, keeping the
 * stateless content a plain function of this object.
 */
data class GroundtransportUiState(
    val direction: GroundTransportDirection = GroundTransportDirection.TO_AIRPORT,
    val airportName: String = "LAS · Harry Reid International",
    val pickup: String = "Bellagio Resort & Casino",
    val dropoff: String = "LAS · Harry Reid International",
    val distanceMiles: Double = 0.0,
    val rideMinutes: Int = 0,
    val options: List<GroundTransportOption> = emptyList(),
    val passengers: Int = 1,
    val sort: GroundTransportSort = GroundTransportSort.PRICE,
    val isLoading: Boolean = false,
    /** Id of the row whose mock booking is in flight (shows the per-row spinner). */
    val bookingInProgressId: String? = null,
    /** The active confirmed booking, if any (persisted across restarts). */
    val booking: GroundTransportBooking? = null,
) {
    val maxPassengers: Int get() = 7

    /** Whether [option] can seat the current party. */
    fun fits(option: GroundTransportOption): Boolean = option.capacity >= passengers

    /** True when no listed vehicle can seat the party — drives the validation/empty state. */
    val hasBookableOption: Boolean get() = options.any(::fits)

    /** Count of vehicles that can seat the party (shown in the section header). */
    val bookableCount: Int get() = options.count(::fits)

    /** Options ordered for display: seatable first, then by the active [sort] key. */
    val orderedOptions: List<GroundTransportOption>
        get() {
            val byKey = when (sort) {
                GroundTransportSort.PRICE -> compareBy<GroundTransportOption> { it.priceMid }
                GroundTransportSort.ARRIVAL -> compareBy<GroundTransportOption> { it.etaMinutes }
            }
            return options.sortedWith(
                compareByDescending<GroundTransportOption> { fits(it) }.then(byKey),
            )
        }

    /** Cheapest seatable option (for the "Cheapest" badge), or null if none fit. */
    val cheapestId: String? get() = options.filter(::fits).minByOrNull { it.priceMid }?.id

    /** Fastest-to-arrive seatable option (for the "Fastest" badge), or null if none fit. */
    val fastestId: String? get() = options.filter(::fits).minByOrNull { it.etaMinutes }?.id

    /** Whether [option] is the active booking for the *current* direction. */
    fun isBooked(option: GroundTransportOption): Boolean =
        booking?.let { it.directionName == direction.name && it.optionId == option.id } == true
}
