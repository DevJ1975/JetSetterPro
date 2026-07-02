package com.jetsetter.pro

import com.jetsetter.pro.feature.checkin.Cabin
import com.jetsetter.pro.feature.checkin.CheckInFlight
import com.jetsetter.pro.feature.checkin.CheckinSeatMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM unit tests over the check-in seat map — runs in CI via `testDebugUnitTest`. */
class SeatMapTest {

    private fun flight(id: String, seat: String, cabin: Cabin) = CheckInFlight(
        id = id,
        airline = "Delta",
        flightNumber = "DL 1423",
        originCode = "LAS",
        originCity = "Las Vegas",
        destinationCode = "ATL",
        destinationCity = "Atlanta",
        gate = "C22",
        seat = seat,
        cabin = cabin,
        departureAtMillis = 1_800_000_000_000L,
        checkInClosesBeforeMinutes = 45,
    )

    @Test
    fun heldSeatIsAlwaysFree() {
        for (cabin in Cabin.entries) {
            val f = flight("dl1423", seatFor(cabin), cabin)
            val held = CheckinSeatMap.forFlight(f).flatMap { it.seats }.first { it.id == f.seat }
            assertFalse("Held seat ${f.seat} must be selectable in $cabin", held.isOccupied)
        }
    }

    @Test
    fun mapIsDeterministic() {
        val f = flight("dl1423", "3A", Cabin.FIRST)
        assertEquals(CheckinSeatMap.forFlight(f), CheckinSeatMap.forFlight(f))
    }

    @Test
    fun everyCabinHasFreeAndOccupiedSeats() {
        for (cabin in Cabin.entries) {
            val seats = CheckinSeatMap.forFlight(flight("ua512", seatFor(cabin), cabin))
                .flatMap { it.seats }
            assertTrue("$cabin should have open seats", seats.any { !it.isOccupied })
            assertTrue("$cabin should have occupied seats", seats.any { it.isOccupied })
        }
    }

    @Test
    fun firstCabinIsTwoByTwo() {
        val rows = CheckinSeatMap.forFlight(flight("dl1423", "3A", Cabin.FIRST))
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.left.size == 2 && it.right.size == 2 })
    }

    @Test
    fun seatIdsMatchRowAndLetter() {
        val rows = CheckinSeatMap.forFlight(flight("aa88", "12C", Cabin.MAIN))
        rows.flatMap { it.seats }.forEach { seat ->
            assertEquals("${seat.row}${seat.letter}", seat.id)
        }
    }

    private fun seatFor(cabin: Cabin): String = when (cabin) {
        Cabin.FIRST -> "3A"
        Cabin.BUSINESS -> "6D"
        Cabin.PREMIUM -> "9B"
        Cabin.MAIN -> "22F"
    }
}
