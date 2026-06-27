package com.jetsetter.pro.feature.flighttracker

/**
 * Lifecycle phase of a tracked flight. Always *derived* from the schedule and the current clock
 * (see [FlightTrackerFlight.snapshotAt]) — never a stored constant. Module-prefixed so it can't
 * collide with status enums elsewhere in the app.
 */
enum class FlightPhase(val label: String) {
    SCHEDULED("Scheduled"),
    BOARDING("Boarding"),
    IN_AIR("In Air"),
    LANDED("Landed"),
}

/**
 * Static schedule record for a flight — the *inputs* every rendered value is computed from.
 *
 * The schedule is stored as offsets (in minutes) from the moment the screen is opened rather than
 * as wall-clock strings, so each flight always resolves to a sensible phase regardless of the time
 * of day, and so the rendered progress, status, clock times and countdown all stay self-consistent
 * (they share one derivation).
 *
 * @param departureOffsetMin scheduled departure relative to "now"; negative means already departed.
 * @param durationMin scheduled gate-to-gate flight time, in minutes (must be > 0).
 * @param delayMin minutes the flight is running late (0 = on time); shifts departure *and* arrival.
 */
data class FlightTrackerFlight(
    val ident: String,
    val airline: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    val gate: String,
    val terminal: String,
    val departureOffsetMin: Long,
    val durationMin: Long,
    val delayMin: Long = 0L,
)

/**
 * Fully-derived, render-ready view of a flight at a specific clock minute. Everything here is a
 * pure function of a [FlightTrackerFlight] plus the current time, so the progress bar, status chip,
 * clock times and countdown can never contradict one another.
 */
data class FlightSnapshot(
    val ident: String,
    val airline: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    val gate: String,
    val terminal: String,
    val phase: FlightPhase,
    val isDelayed: Boolean,
    /** Real 0f..1f fraction of the route flown so far. */
    val progress: Float,
    /** Whole-percent form of [progress], e.g. 37. */
    val progressPercent: Int,
    val departureTime: String,
    val arrivalTime: String,
    /** Original scheduled departure ("HH:mm"); shown struck-through when [isDelayed]. */
    val scheduledDepartureTime: String,
    /** Chip text, e.g. "On Time", "Delayed 45m", "Landed". */
    val statusLabel: String,
    /** Countdown copy, e.g. "Departs in 1h 10m", "3h 12m to arrival", "Arrived". */
    val countdownLabel: String,
)

/** A flight is considered boarding within this many minutes of (actual) departure. */
private const val BOARDING_WINDOW_MIN = 45L
private const val MINUTES_PER_DAY = 1440L

/**
 * Pure derivation of a [FlightSnapshot] for [nowMinuteOfDay] (0..1439). Kept free of Android/time
 * APIs so it is trivially unit-testable and so previews render the same real values the app does.
 */
fun FlightTrackerFlight.snapshotAt(nowMinuteOfDay: Int): FlightSnapshot {
    val safeDuration = durationMin.coerceAtLeast(1L)
    // Actual (delay-adjusted) departure offset; positive = still to depart, negative = airborne.
    val actualDepartureOffset = departureOffsetMin + delayMin
    val elapsed = -actualDepartureOffset
    val fraction = (elapsed.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val phase = when {
        elapsed < 0L && actualDepartureOffset <= BOARDING_WINDOW_MIN -> FlightPhase.BOARDING
        elapsed < 0L -> FlightPhase.SCHEDULED
        elapsed >= safeDuration -> FlightPhase.LANDED
        else -> FlightPhase.IN_AIR
    }

    val delayed = delayMin > 0L
    val statusLabel = when {
        delayed -> "Delayed ${formatDuration(delayMin)}"
        phase == FlightPhase.LANDED -> "Landed"
        else -> "On Time"
    }

    val countdownLabel = when (phase) {
        FlightPhase.SCHEDULED, FlightPhase.BOARDING -> "Departs in ${formatDuration(actualDepartureOffset)}"
        FlightPhase.IN_AIR -> "${formatDuration(safeDuration - elapsed)} to arrival"
        FlightPhase.LANDED -> "Arrived"
    }

    return FlightSnapshot(
        ident = ident,
        airline = airline,
        originCode = originCode,
        originCity = originCity,
        destinationCode = destinationCode,
        destinationCity = destinationCity,
        gate = gate,
        terminal = terminal,
        phase = phase,
        isDelayed = delayed,
        progress = fraction,
        progressPercent = (fraction * 100f).toInt(),
        departureTime = formatClock(nowMinuteOfDay + actualDepartureOffset),
        arrivalTime = formatClock(nowMinuteOfDay + actualDepartureOffset + safeDuration),
        scheduledDepartureTime = formatClock(nowMinuteOfDay + departureOffsetMin),
        statusLabel = statusLabel,
        countdownLabel = countdownLabel,
    )
}

/** Formats an absolute minute-of-day (wraps across midnight, tolerates negatives) as "HH:mm". */
private fun formatClock(minuteOfDay: Long): String {
    val normalized = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    return "%02d:%02d".format(normalized / 60L, normalized % 60L)
}

/** Formats a positive minute span compactly, e.g. 75 -> "1h 15m", 40 -> "40m", 120 -> "2h". */
private fun formatDuration(totalMin: Long): String {
    val clamped = totalMin.coerceAtLeast(0L)
    val hours = clamped / 60L
    val minutes = clamped % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}
