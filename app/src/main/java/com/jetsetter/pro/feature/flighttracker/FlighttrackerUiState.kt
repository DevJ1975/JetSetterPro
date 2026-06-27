package com.jetsetter.pro.feature.flighttracker

/**
 * Single immutable UI-state object for the Flight Tracker screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial.
 *
 * @param query the current ident search text.
 * @param selected the flight rendered in the live-status card; null only while loading.
 * @param results the board filtered by [query] (the full board when [query] is blank).
 * @param recentIdents persisted recent-search idents (newest first).
 * @param totalCount size of the full board, used in the result-count label.
 */
data class FlighttrackerUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val selected: FlightSnapshot? = null,
    val results: List<FlightSnapshot> = emptyList(),
    val recentIdents: List<String> = emptyList(),
    val totalCount: Int = 0,
) {
    /** True when the tester has typed something but no flight matches — drives the not-found state. */
    val isNotFound: Boolean get() = !isLoading && query.isNotBlank() && results.isEmpty()

    /** True when an ident filter is active (vs. browsing the whole board). */
    val isFiltering: Boolean get() = query.isNotBlank()
}
