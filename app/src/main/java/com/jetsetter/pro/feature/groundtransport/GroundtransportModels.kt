package com.jetsetter.pro.feature.groundtransport

/** Direction of the airport transfer the rider is estimating (to vs. from the airport). */
enum class GroundTransportDirection(val label: String) {
    TO_AIRPORT("To airport"),
    FROM_AIRPORT("From airport"),
}

/** Vehicle tier — drives the icon + accent treatment on each option card. */
enum class GroundTransportVehicleClass {
    STANDARD,
    PREMIUM,
    XL,
    TAXI,
}

/** How the ride list is ordered. */
enum class GroundTransportSort(val label: String) {
    PRICE("Price"),
    ARRIVAL("Arrival"),
}

/**
 * A single bookable ride estimate. Module-prefixed name to avoid colliding with other features'
 * models. Every money/time value here is *computed* from the rate card + the selected route in
 * [GroundtransportRepository] — nothing is a hand-typed constant that could drift from the inputs.
 * All money values are USD; [priceLow]/[priceHigh] form the quoted fare range.
 */
data class GroundTransportOption(
    val id: String,
    val service: String,
    val provider: String,
    val vehicleClass: GroundTransportVehicleClass,
    val capacity: Int,
    /** Minutes until the driver reaches the pickup point. */
    val etaMinutes: Int,
    val priceLow: Double,
    val priceHigh: Double,
    /** Demand multiplier baked into the fare (1.0 == no surge). */
    val surgeMultiplier: Double = 1.0,
    val rating: Double = 4.8,
) {
    val surge: Boolean get() = surgeMultiplier > 1.0

    /** Midpoint of the quoted range — used for "cheapest" comparisons and price sorting. */
    val priceMid: Double get() = (priceLow + priceHigh) / 2.0
}

/** A computed quote for one direction: the route facts plus every ride option. */
data class GroundTransportQuote(
    val distanceMiles: Double,
    val rideMinutes: Int,
    val options: List<GroundTransportOption>,
)

/**
 * A confirmed (mock) booking. Persisted via [com.jetsetter.pro.core.data.prefs.ModuleStateStore]
 * so a tester's booking — and the fare it was locked in at — survives an app restart.
 */
data class GroundTransportBooking(
    val directionName: String,
    val optionId: String,
    val service: String,
    val priceLow: Double,
    val priceHigh: Double,
    val etaMinutes: Int,
)

/**
 * Everything the screen persists between sessions, serialized as one Moshi JSON object (the same
 * idiom Room uses in `core/data/local/Converters.kt`). Enums are stored by `name` for stability.
 */
data class GroundTransportPersisted(
    val directionName: String = GroundTransportDirection.TO_AIRPORT.name,
    val passengers: Int = 1,
    val sortName: String = GroundTransportSort.PRICE.name,
    val booking: GroundTransportBooking? = null,
)
