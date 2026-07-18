package com.jetsetter.pro.feature.checkin

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock-first source of upcoming flights for the Check-In feature. Holds realistic in-memory sample
 * data only — no network, persistence, or DI providers. Departures are generated relative to a
 * supplied "now" so the demo reliably exercises every window state (open / opens-soon / closed)
 * while staying internally consistent with the real clock.
 */
@Singleton
class CheckinRepository @Inject constructor() {

    companion object {
        /**
         * Ids of the built-in demo flights below. Check-ins against these are demo interactions —
         * they must never feed the travel-profile learning loop (plan R10e: the profile never
         * learns from demo seats/flights). Keep in sync with [loadUpcomingFlights].
         */
        val seedFlightIds: Set<String> = setOf("dl1423", "aa88", "ua512")
    }

    /** Returns the traveler's upcoming flights, with departures anchored to [nowMillis]. */
    suspend fun loadUpcomingFlights(nowMillis: Long): List<CheckInFlight> {
        val hour = 60 * 60 * 1000L
        val minute = 60 * 1000L
        return listOf(
            // Window OPEN now — the primary "check in" interaction.
            CheckInFlight(
                id = "dl1423",
                airline = "Delta",
                flightNumber = "DL 1423",
                originCode = "LAS",
                originCity = "Las Vegas",
                destinationCode = "ATL",
                destinationCity = "Atlanta",
                gate = "C22",
                seat = "3A",
                cabin = Cabin.FIRST,
                departureAtMillis = nowMillis + 4 * hour,
                checkInClosesBeforeMinutes = 45,
            ),
            // Window opens in ~4h (24h before a departure 28h out) — guarded "opens soon".
            CheckInFlight(
                id = "aa88",
                airline = "American",
                flightNumber = "AA 88",
                originCode = "ATL",
                originCity = "Atlanta",
                destinationCode = "LHR",
                destinationCity = "London Heathrow",
                gate = "E15",
                // Seats sit inside their cabin's seat-map row range (see CheckinSeatMap.forFlight)
                // so the held seat always appears, free, on the map.
                seat = "16C",
                cabin = Cabin.MAIN,
                departureAtMillis = nowMillis + 28 * hour,
                checkInClosesBeforeMinutes = 90,
            ),
            // Departs in 30m, cutoff is 45m before — window is CLOSED. Guarded against check-in.
            CheckInFlight(
                id = "ua512",
                airline = "United",
                flightNumber = "UA 512",
                originCode = "SFO",
                originCity = "San Francisco",
                destinationCode = "EWR",
                destinationCity = "Newark",
                gate = "B7",
                seat = "9F",
                cabin = Cabin.PREMIUM,
                departureAtMillis = nowMillis + 30 * minute,
                checkInClosesBeforeMinutes = 45,
            ),
        )
    }
}
