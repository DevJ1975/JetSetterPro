package com.jetsetter.pro.feature.disruption

import java.util.Locale

/**
 * Domain models for the Disruption Monitor feature. Names are module-prefixed
 * (`Disruption…`) so they never collide with models from other feature modules.
 *
 * Times are stored as minutes-since-midnight ([Int]) — the single source of truth — and
 * every displayed clock string / duration / delay is *derived* from them, so no shown
 * number can contradict another (e.g. the delay is always `revised - scheduled`).
 */

/** Live status of the flight IRIS is actively watching. */
enum class DisruptionFlightStatus(val label: String) {
    ON_TIME("On time"),
    DELAYED("Delayed"),
    CANCELLED("Cancelled"),
    REBOOKED("Rebooked"),
}

/** Lifecycle of a single node in the auto-response timeline. */
enum class DisruptionStepStatus { PENDING, ACTIVE, DONE }

/** How the alternative list is ordered. The label is shown on the sort chips. */
enum class DisruptionSortMode(val label: String) {
    RECOMMENDED("Best value"),
    PRICE("Cheapest"),
    ARRIVAL("Earliest"),
}

/** The single flight under active AI monitoring. */
data class DisruptionMonitoredFlight(
    val flightNumber: String,
    val route: String,
    val origin: String,
    val destination: String,
    val scheduledDepartureMin: Int,
    val revisedDepartureMin: Int,
    val scheduledArrivalMin: Int,
    val revisedArrivalMin: Int,
    val gate: String,
    val fare: Double,
    val status: DisruptionFlightStatus,
    val reason: String,
) {
    val delayMinutes: Int get() = (revisedDepartureMin - scheduledDepartureMin).coerceAtLeast(0)
    val arrivalDelayMinutes: Int get() = (revisedArrivalMin - scheduledArrivalMin).coerceAtLeast(0)
    val scheduledDeparture: String get() = clockLabel(scheduledDepartureMin)
    val revisedDeparture: String get() = clockLabel(revisedDepartureMin)
    val scheduledArrival: String get() = clockLabel(scheduledArrivalMin)
    val revisedArrival: String get() = clockLabel(revisedArrivalMin)
}

/** A candidate re-booking the AI surfaced as an alternative. */
data class DisruptionAlternative(
    val id: String,
    val carrier: String,
    val route: String,
    val departureMin: Int,
    val arrivalMin: Int,
    val cabin: String,
    val seatsLeft: Int,
    val price: Double,
    /** Normalized 0..1 "best value" score (higher = better); assigned by the ViewModel. */
    val valueScore: Double = 0.0,
    val isRecommended: Boolean = false,
) {
    val durationMinutes: Int get() = (arrivalMin - departureMin).coerceAtLeast(0)
    val departureTime: String get() = clockLabel(departureMin)
    val arrivalTime: String get() = clockLabel(arrivalMin)
}

/** One node in the 5-step auto-response timeline. */
data class DisruptionResponseStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: DisruptionStepStatus = DisruptionStepStatus.PENDING,
)

/**
 * The traveler's committed rebooking decision. This is the only user-mutable state worth
 * surviving a restart, so it is the single object persisted as JSON via [com.jetsetter.pro.core.data.prefs.ModuleStateStore]
 * (Moshi). Only constructor properties are serialized.
 */
data class DisruptionDecision(
    val selectedAlternativeId: String? = null,
    val isRebooked: Boolean = false,
)

/** Formats minutes-since-midnight as a 12-hour clock, e.g. `515` -> "8:35 AM". */
internal fun clockLabel(minutesSinceMidnight: Int): String {
    val m = ((minutesSinceMidnight % 1440) + 1440) % 1440
    val hour24 = m / 60
    val minute = m % 60
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
}

/** Formats a duration in minutes, e.g. `95` -> "1h 35m", `60` -> "1h", `40` -> "40m". */
internal fun durationLabel(totalMinutes: Int): String {
    val m = totalMinutes.coerceAtLeast(0)
    val hours = m / 60
    val minutes = m % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
