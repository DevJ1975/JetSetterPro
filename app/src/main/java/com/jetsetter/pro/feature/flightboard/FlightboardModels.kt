package com.jetsetter.pro.feature.flightboard

/** Which side of the board is being viewed. */
enum class FlightBoardDirection(val label: String) {
    DEPARTURES("Departures"),
    ARRIVALS("Arrivals"),
}

/**
 * Live status for a board entry — mirrors the four states an airport board shows.
 *
 * [sortPriority] drives the "Sort by status" order from a traveller's point of view:
 * boarding now (act first) → delayed (needs attention) → on time → already departed.
 */
enum class FlightBoardStatus(val label: String, val sortPriority: Int) {
    ON_TIME("On Time", 2),
    BOARDING("Boarding", 0),
    DELAYED("Delayed", 1),
    DEPARTED("Departed", 3),
}

/** How the board rows are ordered. */
enum class FlightBoardSort(val label: String) {
    /** Chronological — earliest scheduled time first. */
    TIME("Time"),

    /** Grouped by [FlightBoardStatus.sortPriority], then by time within each group. */
    STATUS("Status"),
}

/**
 * One row on the departures/arrivals board. Module-prefixed name to avoid colliding with
 * generic model names elsewhere in the app.
 */
data class FlightBoardItem(
    val id: String,
    val time: String,
    val flightNumber: String,
    val airline: String,
    val city: String,
    val gate: String,
    val terminal: String,
    val status: FlightBoardStatus,
    val direction: FlightBoardDirection,
)
