package com.jetsetter.pro.feature.checkin

/**
 * Seat-map model for online check-in. Mock-first like every module: the cabin layout is derived
 * from the flight's [Cabin] and the occupancy is a deterministic function of the flight id + seat
 * id, so the same flight always renders the same map (stable across restarts, screenshots, and
 * demo runs) without storing anything. The traveler's currently-held seat is always kept free.
 */
data class SeatMapSeat(
    val id: String,
    val row: Int,
    val letter: Char,
    val isOccupied: Boolean,
)

/** One physical cabin row: the seats left of the aisle, then right of the aisle. */
data class SeatMapRow(
    val row: Int,
    val left: List<SeatMapSeat>,
    val right: List<SeatMapSeat>,
) {
    val seats: List<SeatMapSeat> get() = left + right
}

object CheckinSeatMap {

    /** Percentage of seats that read as already taken — full enough to feel real, never sold out. */
    private const val OCCUPANCY_PERCENT = 58

    /**
     * Builds the seat map for [flight]'s cabin:
     *  - First: rows 1–4 in a 2-2 layout (A B · C D)
     *  - Premium Economy: rows 7–12 in a 3-3 layout
     *  - Business / Main: rows 15–26 in a 3-3 layout
     */
    fun forFlight(flight: CheckInFlight): List<SeatMapRow> = when (flight.cabin) {
        Cabin.FIRST -> buildRows(flight, rows = 1..4, left = "AB", right = "CD")
        Cabin.BUSINESS -> buildRows(flight, rows = 5..8, left = "ABC", right = "DEF")
        Cabin.PREMIUM -> buildRows(flight, rows = 7..12, left = "ABC", right = "DEF")
        Cabin.MAIN -> buildRows(flight, rows = 15..26, left = "ABC", right = "DEF")
    }

    private fun buildRows(
        flight: CheckInFlight,
        rows: IntRange,
        left: String,
        right: String,
    ): List<SeatMapRow> = rows.map { row ->
        SeatMapRow(
            row = row,
            left = left.map { letter -> seat(flight, row, letter) },
            right = right.map { letter -> seat(flight, row, letter) },
        )
    }

    private fun seat(flight: CheckInFlight, row: Int, letter: Char): SeatMapSeat {
        val id = "$row$letter"
        return SeatMapSeat(
            id = id,
            row = row,
            letter = letter,
            isOccupied = id != flight.seat && isOccupied(flight.id, id),
        )
    }

    /** Deterministic pseudo-occupancy: stable per (flight, seat), roughly [OCCUPANCY_PERCENT]% full. */
    private fun isOccupied(flightId: String, seatId: String): Boolean {
        // Simple positive hash — Kotlin's hashCode() is stable for String across runs/devices.
        val hash = ("$flightId#$seatId".hashCode() and Int.MAX_VALUE)
        return hash % 100 < OCCUPANCY_PERCENT
    }
}
