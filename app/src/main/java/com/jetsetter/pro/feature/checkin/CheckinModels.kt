package com.jetsetter.pro.feature.checkin

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Per-zone boarding capacity used to turn check-in timing into a real position number. */
private const val ZONE_CAPACITY = 60

/**
 * Cabin / fare class. Determines the base boarding zone assigned at check-in — lower zone boards
 * first. Real airlines map premium cabins to early zones; we mirror that deterministically.
 */
enum class Cabin(val label: String, val zone: Int) {
    FIRST("First · SkyPriority", 1),
    BUSINESS("Business", 2),
    PREMIUM("Premium Economy", 3),
    MAIN("Main Cabin", 4),
}

/** Where the check-in window sits relative to "now". Always derived, never stored. */
enum class CheckInWindowState { NOT_OPEN_YET, OPEN, CLOSED }

/**
 * Boarding assignment issued at the exact moment of check-in. Persisted alongside the flight so the
 * boarding pass stays stable across restarts — recomputing it later would drift as "now" advances.
 */
data class BoardingAssignment(
    val zone: Int,
    val position: Int,
    val cabinLabel: String,
    val checkedInAtMillis: Long,
)

/**
 * A single upcoming flight eligible for online check-in. Times are real epoch millis so every
 * derived value (window open/closed, countdowns, boarding position) is computed and self-consistent
 * rather than hard-coded. Module-prefixed name to avoid collisions with other features' models.
 */
data class CheckInFlight(
    val id: String,
    val airline: String,
    val flightNumber: String,
    val originCode: String,
    val originCity: String,
    val destinationCode: String,
    val destinationCity: String,
    val gate: String,
    val seat: String,
    val cabin: Cabin,
    /** Scheduled departure as epoch millis — the single source of truth for all timing. */
    val departureAtMillis: Long,
    /** Online check-in opens this many minutes before departure (airline standard: 24h). */
    val checkInOpensBeforeMinutes: Int = 24 * 60,
    /** Check-in cutoff — closes this many minutes before departure (domestic 45 / intl 90). */
    val checkInClosesBeforeMinutes: Int,
    /** Present once the traveler has checked in; null otherwise. Persisted via ModuleStateStore. */
    val boarding: BoardingAssignment? = null,
) {
    val isCheckedIn: Boolean get() = boarding != null

    val checkInOpensAtMillis: Long get() = departureAtMillis - checkInOpensBeforeMinutes * 60_000L
    val checkInClosesAtMillis: Long get() = departureAtMillis - checkInClosesBeforeMinutes * 60_000L

    fun windowState(nowMillis: Long): CheckInWindowState = when {
        nowMillis < checkInOpensAtMillis -> CheckInWindowState.NOT_OPEN_YET
        nowMillis > checkInClosesAtMillis -> CheckInWindowState.CLOSED
        else -> CheckInWindowState.OPEN
    }

    fun isWindowOpen(nowMillis: Long): Boolean = windowState(nowMillis) == CheckInWindowState.OPEN
    fun hasDeparted(nowMillis: Long): Boolean = nowMillis >= departureAtMillis

    /** "Fri, Jun 26 · 7:00 AM" — formatted from the real departure timestamp. */
    fun departureLabel(zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(departureAtMillis).atZone(zone).format(DEPARTURE_FORMAT)

    /** Human label for the time until departure, e.g. "Departs in 4h 12m" / "Departed". */
    fun departsInLabel(nowMillis: Long): String =
        if (hasDeparted(nowMillis)) "Departed" else "in ${formatCountdown(departureAtMillis - nowMillis)}"

    /** Status of the check-in window phrased for the traveler, derived from [nowMillis]. */
    fun windowLabel(nowMillis: Long): String = when (windowState(nowMillis)) {
        CheckInWindowState.NOT_OPEN_YET -> "Opens in ${formatCountdown(checkInOpensAtMillis - nowMillis)}"
        CheckInWindowState.OPEN -> "Closes in ${formatCountdown(checkInClosesAtMillis - nowMillis)}"
        CheckInWindowState.CLOSED ->
            if (hasDeparted(nowMillis)) "Flight departed" else "Closed ${checkInClosesBeforeMinutes}m before departure"
    }

    /**
     * Issues a boarding assignment for a check-in happening at [nowMillis]. The zone comes from the
     * cabin; the position within that zone reflects how early in the window the traveler checked in —
     * earlier check-in earns a lower (better) position. Deterministic and self-consistent.
     */
    fun assignBoarding(nowMillis: Long): BoardingAssignment {
        val span = (checkInClosesAtMillis - checkInOpensAtMillis).coerceAtLeast(1L)
        val elapsed = (nowMillis - checkInOpensAtMillis).coerceIn(0L, span)
        val fractionElapsed = elapsed.toDouble() / span.toDouble()
        val position = 1 + Math.round(fractionElapsed * (ZONE_CAPACITY - 1)).toInt()
        return BoardingAssignment(
            zone = cabin.zone,
            position = position,
            cabinLabel = cabin.label,
            checkedInAtMillis = nowMillis,
        )
    }

    private companion object {
        val DEPARTURE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US)
    }
}

/**
 * Compact, persistable record of one check-in. Mirrors [BoardingAssignment] plus the flight id, so
 * the boarding pass survives restarts without re-deriving it from a moved "now".
 */
data class PersistedCheckIn(
    val flightId: String,
    val zone: Int,
    val position: Int,
    val cabinLabel: String,
    val checkedInAtMillis: Long,
    /** Seat held at check-in (seat-map selection). Blank in pre-seat-map records → keep the default. */
    val seat: String = "",
)

/** "2d 4h", "4h 12m", "45m", or "0m" for non-positive deltas. */
fun formatCountdown(deltaMillis: Long): String {
    val totalMinutes = deltaMillis / 60_000L
    if (totalMinutes <= 0) return "0m"
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
