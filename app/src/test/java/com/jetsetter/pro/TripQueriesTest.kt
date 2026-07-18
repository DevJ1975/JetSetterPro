package com.jetsetter.pro

import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.TripQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Pins the pure trip-selection contract in [TripQueries] (spec §2.5): active-trip boundary
 * inclusivity, upcoming fallback, and the flight-ident regex applied to RAW itinerary titles
 * (no whitespace stripping). Pure-JVM (no Android deps).
 */
class TripQueriesTest {

    private fun trip(
        name: String,
        start: String,
        end: String,
        items: List<ItineraryItem> = emptyList(),
    ) = Trip(name = name, destination = name, startDate = start, endDate = end, items = items)

    private fun flightItem(title: String, start: String) =
        ItineraryItem(title = title, type = ItineraryItemType.FLIGHT, startDate = start)

    // ---- activeTrip ----------------------------------------------------------------------

    @Test
    fun activeTrip_startAndEndDatesAreInclusive() {
        val t = trip("Tokyo", "2026-07-10", "2026-07-20")

        assertEquals(t, TripQueries.activeTrip(listOf(t), LocalDate.parse("2026-07-10")))
        assertEquals(t, TripQueries.activeTrip(listOf(t), LocalDate.parse("2026-07-20")))
        assertEquals(t, TripQueries.activeTrip(listOf(t), LocalDate.parse("2026-07-15")))
    }

    @Test
    fun activeTrip_dayBeforeStartIsUpcomingNotActive_dayAfterEndIsNeither() {
        val t = trip("Tokyo", "2026-07-10", "2026-07-20")

        // The day before the start it's still returned — as the next upcoming trip.
        assertEquals(t, TripQueries.activeTrip(listOf(t), LocalDate.parse("2026-07-09")))
        // The day after the end there is nothing active or upcoming.
        assertNull(TripQueries.activeTrip(listOf(t), LocalDate.parse("2026-07-21")))
    }

    @Test
    fun activeTrip_currentTripWinsOverUpcoming() {
        val current = trip("Paris", "2026-07-01", "2026-07-31")
        val upcoming = trip("Rome", "2026-08-05", "2026-08-10")

        val result = TripQueries.activeTrip(listOf(upcoming, current), LocalDate.parse("2026-07-17"))
        assertEquals(current, result)
    }

    @Test
    fun activeTrip_picksEarliestUpcomingByStartDate() {
        val later = trip("Rome", "2026-09-01", "2026-09-05")
        val sooner = trip("Lima", "2026-08-01", "2026-08-05")

        val result = TripQueries.activeTrip(listOf(later, sooner), LocalDate.parse("2026-07-17"))
        assertEquals(sooner, result)
    }

    @Test
    fun activeTrip_skipsUnparseableDates_emptyListReturnsNull() {
        val garbage = trip("Bad", "sometime", "later")
        val good = trip("Lima", "2026-08-01", "2026-08-05")

        assertEquals(good, TripQueries.activeTrip(listOf(garbage, good), LocalDate.parse("2026-07-17")))
        assertNull(TripQueries.activeTrip(emptyList(), LocalDate.parse("2026-07-17")))
    }

    // ---- flight-ident regex --------------------------------------------------------------

    @Test
    fun flightRegex_acceptsRealIdentsInsideRawTitles() {
        assertTrue(TripQueries.FLIGHT_IDENT_REGEX.containsMatchIn("DL1423"))
        assertTrue(TripQueries.FLIGHT_IDENT_REGEX.containsMatchIn("AA88"))
        assertTrue(TripQueries.FLIGHT_IDENT_REGEX.containsMatchIn("SWA123"))
        // Idents embedded in longer raw titles still resolve.
        assertEquals("DL1423", TripQueries.FLIGHT_IDENT_REGEX.find("Flight DL1423 to JFK")?.value)
    }

    @Test
    fun flightRegex_rejectsNonIdents() {
        // Single letter prefix.
        assertFalse(TripQueries.FLIGHT_IDENT_REGEX.containsMatchIn("A1"))
        // Lowercase carrier code.
        assertFalse(TripQueries.FLIGHT_IDENT_REGEX.containsMatchIn("dl1423"))
        // Raw-title matching: the space between letters and digits is NOT stripped.
        assertFalse(TripQueries.FLIGHT_IDENT_REGEX.containsMatchIn("NYC 2026"))
    }

    // ---- nextFlight ----------------------------------------------------------------------

    @Test
    fun nextFlight_returnsFirstFutureMatchWithExtractedIdent() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val t = trip(
            "Tokyo", "2026-07-16", "2026-07-25",
            items = listOf(
                // Past flight — skipped even though it matches.
                flightItem("UA100 to SFO", "2026-07-16T09:00:00Z"),
                // Non-flight title — no ident match.
                flightItem("Dinner reservation", "2026-07-17T19:00:00Z"),
                flightItem("Flight DL1423 to JFK", "2026-07-18T10:00:00Z"),
            ),
        )

        val next = TripQueries.nextFlight(listOf(t), now)
        assertEquals("DL1423", next?.ident)
        assertEquals("Flight DL1423 to JFK", next?.item?.title)
        assertEquals(t, next?.trip)
    }

    @Test
    fun nextFlight_scansTripsInStartDateOrder() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val laterTrip = trip(
            "Rome", "2026-09-01", "2026-09-05",
            items = listOf(flightItem("AZ611", "2026-09-01T08:00:00Z")),
        )
        val soonerTrip = trip(
            "Lima", "2026-08-01", "2026-08-05",
            items = listOf(flightItem("LA2513", "2026-08-01T08:00:00Z")),
        )

        val next = TripQueries.nextFlight(listOf(laterTrip, soonerTrip), now)
        assertEquals("LA2513", next?.ident)
        assertEquals(soonerTrip, next?.trip)
    }

    @Test
    fun nextFlight_noMatches_returnsNull() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val t = trip(
            "Tokyo", "2026-07-16", "2026-07-25",
            items = listOf(
                flightItem("NYC 2026", "2026-07-18T10:00:00Z"),
                flightItem("dl1423", "2026-07-19T10:00:00Z"),
                flightItem("Broken clock flight DL1", "not-a-date"),
            ),
        )
        assertNull(TripQueries.nextFlight(listOf(t), now))
        assertNull(TripQueries.nextFlight(emptyList(), now))
    }
}
