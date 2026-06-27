package com.jetsetter.pro.feature.inflight

/**
 * Single immutable UI-state object for the In-Flight tracker — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial.
 */
data class InflightUiState(
    val isLoading: Boolean = true,
    /** Flights that can be tracked; drives the selector chips. */
    val flights: List<InFlightFlight> = emptyList(),
    val selectedFlightId: String? = null,
    val flight: InFlightFlight? = null,
    val status: InFlightStatus? = null,
    val useMetric: Boolean = false,
    /** Whether the live simulation is auto-advancing. */
    val isPlaying: Boolean = false,
    /** Playback rate in simulated seconds per real second (see [InflightRepository.RATES]). */
    val playbackRate: Int = InflightRepository.DEFAULT_RATE,
) {
    val isComplete: Boolean get() = status?.isComplete == true
}
