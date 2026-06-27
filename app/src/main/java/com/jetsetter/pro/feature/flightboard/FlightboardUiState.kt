package com.jetsetter.pro.feature.flightboard

import kotlin.math.roundToInt

/**
 * Single immutable UI-state object for the Flight Board screen — one object rather than
 * scattered flags, so previews stay trivial and impossible states can't arise.
 *
 * The screen holds the *whole* board ([allFlights]) plus the user's view controls (direction,
 * sort, status filter, search query). Everything shown on screen — counts, the on-time
 * percentage, and the rendered rows — is derived here so the numbers can never contradict the
 * list the user is looking at.
 */
data class FlightboardUiState(
    val airportName: String = "",
    val airportCode: String = "",
    val direction: FlightBoardDirection = FlightBoardDirection.DEPARTURES,
    val sort: FlightBoardSort = FlightBoardSort.TIME,
    /** Active status filter, or null for "All". */
    val statusFilter: FlightBoardStatus? = null,
    /** Free-text search over flight number / city / airline. Transient (not persisted). */
    val query: String = "",
    /** The full board for both directions; the screen never reads this directly. */
    val allFlights: List<FlightBoardItem> = emptyList(),
    val isLoading: Boolean = false,
) {
    /** Every flight on the currently selected side of the board — the basis for all counts. */
    val directionFlights: List<FlightBoardItem>
        get() = allFlights.filter { it.direction == direction }

    val totalCount: Int get() = directionFlights.size

    fun countFor(status: FlightBoardStatus): Int = directionFlights.count { it.status == status }

    val onTimeCount: Int get() = countFor(FlightBoardStatus.ON_TIME)
    val delayedCount: Int get() = countFor(FlightBoardStatus.DELAYED)

    /** Share of on-time flights for this direction (0–100). 0 when the board is empty. */
    val onTimePercent: Int
        get() = if (totalCount == 0) 0 else (onTimeCount * 100f / totalCount).roundToInt()

    /**
     * The rows actually rendered: the current direction, narrowed by the status filter and
     * search query, then ordered by the chosen [sort]. Times are "HH:mm", so a lexical sort is
     * already chronological.
     */
    val visibleFlights: List<FlightBoardItem>
        get() {
            val q = query.trim()
            return directionFlights
                .filter { statusFilter == null || it.status == statusFilter }
                .filter { q.isBlank() || it.matches(q) }
                .let { rows ->
                    when (sort) {
                        FlightBoardSort.TIME -> rows.sortedBy { it.time }
                        FlightBoardSort.STATUS ->
                            rows.sortedWith(compareBy({ it.status.sortPriority }, { it.time }))
                    }
                }
        }

    /** True when the visible list is being narrowed by a status filter or a search query. */
    val hasActiveFilter: Boolean get() = statusFilter != null || query.isNotBlank()

    /** No flights at all for this direction (distinct from "filtered everything out"). */
    val isBoardEmpty: Boolean get() = directionFlights.isEmpty()

    /** Board has flights, but the current filter/search hides them all. */
    val isFilteredEmpty: Boolean get() = directionFlights.isNotEmpty() && visibleFlights.isEmpty()
}

/** Case-insensitive match across the fields a traveller would actually search by. */
private fun FlightBoardItem.matches(query: String): Boolean {
    val q = query.lowercase()
    return flightNumber.lowercase().contains(q) ||
        city.lowercase().contains(q) ||
        airline.lowercase().contains(q)
}
