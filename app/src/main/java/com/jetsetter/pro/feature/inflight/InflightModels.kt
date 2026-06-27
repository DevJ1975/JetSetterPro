package com.jetsetter.pro.feature.inflight

/**
 * Static identity + planned figures for a flight. Held immutable; the live, changing values live
 * in [InFlightStatus] and are recomputed deterministically from a single progress fraction so the
 * preview, the seeded state, and the running simulation all agree.
 *
 * Names are module-prefixed (`InFlight…`) to avoid colliding with other feature modules.
 */
data class InFlightFlight(
    val id: String,
    val flightNumber: String,
    val airline: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    val aircraftType: String,
    val tailNumber: String,
    val departedLocal: String,
    val scheduledArrivalLocal: String,
    val totalDistanceMiles: Int,
    val totalFlightMinutes: Int,
    val cruiseAltitudeFeet: Int,
    val cruiseSpeedMph: Int,
    /** Where the flight sits when the tracker first opens (0f..1f along the route). */
    val initialProgress: Float,
) {
    val totalSeconds: Int get() = totalFlightMinutes * 60
}

/**
 * Live, derived snapshot of the flight at a given [progress] (0f..1f). Every field is a pure
 * function of progress + the flight's planned figures — no static values that contradict inputs.
 */
data class InFlightStatus(
    val progress: Float,
    val phase: InFlightPhase,
    val altitudeFeet: Int,
    val groundSpeedMph: Int,
    /** Climb (+) / descent (−) rate in feet per minute; 0 in cruise / on the ground. */
    val verticalSpeedFpm: Int,
    val secondsElapsed: Int,
    val secondsRemaining: Int,
    val distanceCoveredMiles: Int,
    val distanceRemainingMiles: Int,
) {
    val isComplete: Boolean get() = progress >= 1f
}

/** Phase of flight, derived from how far along the route the aircraft is. */
enum class InFlightPhase(val label: String) {
    BOARDING("At gate"),
    TAXI("Taxiing"),
    CLIMB("Climbing"),
    CRUISE("Cruising"),
    DESCENT("Descending"),
    LANDED("Landed"),
}
