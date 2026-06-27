package com.jetsetter.pro.feature.checkin

/**
 * Single immutable UI-state object for the Check-In screen — one object rather than scattered flags,
 * so impossible states can't arise and previews are trivial. [nowMillis] is the clock the whole UI
 * derives window state and countdowns from; the ViewModel advances it so the screen stays honest.
 */
data class CheckinUiState(
    val flights: List<CheckInFlight> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val checkedInCount: Int get() = flights.count { it.isCheckedIn }

    /** Flights whose window is open right now (regardless of whether they're checked in). */
    val openCount: Int get() = flights.count { it.windowState(nowMillis) == CheckInWindowState.OPEN }

    /** Flights the traveler can act on now: window open and not yet checked in. */
    val actionableCount: Int
        get() = flights.count { !it.isCheckedIn && it.windowState(nowMillis) == CheckInWindowState.OPEN }

    /** Soonest-closing window still needing a check-in — drives the urgency hint. */
    val nextDeadlineFlight: CheckInFlight?
        get() = flights
            .filter { !it.isCheckedIn && it.windowState(nowMillis) == CheckInWindowState.OPEN }
            .minByOrNull { it.checkInClosesAtMillis }

    val allCheckedIn: Boolean get() = flights.isNotEmpty() && flights.all { it.isCheckedIn }
}
