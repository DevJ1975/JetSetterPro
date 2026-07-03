package com.jetsetter.pro.feature.departureoptimizer

/**
 * Confidence/severity bucket for a single travel factor (traffic or security).
 * Module-prefixed so it never collides with another feature's status enum.
 */
enum class DepartureOptimizerRisk(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
}

/**
 * Overall readiness for the whole trip, derived from how much *slack* remains between now and the
 * moment the traveler must leave. The screen colors its headline by this — green when there's room,
 * amber when it's tight, red when it's go-time. See [DepartureOptimizerEstimate.status].
 */
enum class DepartureOptimizerStatus(val label: String) {
    ON_TRACK("On track"),
    TIGHT("Cutting it close"),
    LEAVE_NOW("Leave now"),
}

/**
 * Immutable snapshot of everything the optimizer needs to compute a "leave by" time.
 * Times of day are stored as minutes-since-midnight so the math stays dependency-free; the
 * UI layer formats them into a 12-hour clock. All durations are in whole minutes.
 *
 * Every derived value below is a pure function of these inputs, so the screen can never display a
 * number that contradicts its own breakdown. Defaults describe a realistic morning departure so
 * previews and first render look complete.
 */
data class DepartureOptimizerEstimate(
    val flightNumber: String = "DL 1423",
    val route: String = "LAS → ATL",
    val airport: String = "Harry Reid Intl (LAS)",
    val terminal: String = "Terminal 3 · Gate C22",
    val departureMinuteOfDay: Int = 7 * 60,
    /** Simulated "current" wall-clock time (mock-first), used to compute remaining slack. */
    val nowMinuteOfDay: Int = 4 * 60 + 48,
    val driveMinutes: Int = 34,
    val parkingBufferMinutes: Int = 15,
    val tsaWaitMinutes: Int = 22,
    val gateBufferMinutes: Int = 30,
    val trafficRisk: DepartureOptimizerRisk = DepartureOptimizerRisk.MODERATE,
    val securityRisk: DepartureOptimizerRisk = DepartureOptimizerRisk.MODERATE,
    /** Current conditions along the drive, e.g. "Clear skies". Rolled with the live estimates. */
    val weatherLabel: String = "Clear skies",
    val weatherTempF: Int = 74,
    val weatherRisk: DepartureOptimizerRisk = DepartureOptimizerRisk.LOW,
) {
    /** "Clear skies · 74°F" — the single string every surface (screen, IRIS) shows for weather. */
    val weatherSummary: String
        get() = "$weatherLabel · $weatherTempF°F"

    /** Total lead time the traveler needs before wheels-up, in minutes. */
    val totalLeadMinutes: Int
        get() = driveMinutes + parkingBufferMinutes + tsaWaitMinutes + gateBufferMinutes

    /** Recommended departure-from-home time as minutes-since-midnight. */
    val leaveByMinuteOfDay: Int
        get() = departureMinuteOfDay - totalLeadMinutes

    /** Minutes of cushion between *now* and when the traveler must leave. Negative = already late. */
    val slackMinutes: Int
        get() = leaveByMinuteOfDay - nowMinuteOfDay

    /** Overall readiness bucket derived purely from [slackMinutes]. */
    val status: DepartureOptimizerStatus
        get() = when {
            slackMinutes >= 20 -> DepartureOptimizerStatus.ON_TRACK
            slackMinutes >= 5 -> DepartureOptimizerStatus.TIGHT
            else -> DepartureOptimizerStatus.LEAVE_NOW
        }
}
