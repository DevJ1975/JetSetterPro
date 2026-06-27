package com.jetsetter.pro.feature.flightboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mock source for the Flight Board feature (Harry Reid International, LAS).
 *
 * No network — realistic sample departures/arrivals are held inline so a beta tester can
 * exercise the screen offline. Mirrors the project's `@Singleton @Inject` repo convention.
 */
@Singleton
class FlightboardRepository @Inject constructor() {

    /** The airport this mock board represents. */
    val airportName: String = "Harry Reid International"
    val airportCode: String = "LAS"

    private val flights: List<FlightBoardItem> = listOf(
        // Departures
        FlightBoardItem("d1", "06:15", "DL 1423", "Delta", "Atlanta", "C22", "T1", FlightBoardStatus.BOARDING, FlightBoardDirection.DEPARTURES),
        FlightBoardItem("d2", "06:50", "AA 188", "American", "Dallas/Fort Worth", "B14", "T1", FlightBoardStatus.ON_TIME, FlightBoardDirection.DEPARTURES),
        FlightBoardItem("d3", "07:30", "UA 642", "United", "Chicago O'Hare", "D55", "T3", FlightBoardStatus.ON_TIME, FlightBoardDirection.DEPARTURES),
        FlightBoardItem("d4", "08:05", "WN 2210", "Southwest", "Denver", "B22", "T1", FlightBoardStatus.DELAYED, FlightBoardDirection.DEPARTURES),
        FlightBoardItem("d5", "09:40", "AS 615", "Alaska", "Seattle", "C8", "T1", FlightBoardStatus.ON_TIME, FlightBoardDirection.DEPARTURES),
        FlightBoardItem("d6", "10:15", "B6 431", "JetBlue", "New York JFK", "E2", "T3", FlightBoardStatus.ON_TIME, FlightBoardDirection.DEPARTURES),
        FlightBoardItem("d7", "11:00", "NK 877", "Spirit", "Orlando", "C19", "T1", FlightBoardStatus.DEPARTED, FlightBoardDirection.DEPARTURES),
        FlightBoardItem("d8", "12:25", "F9 1503", "Frontier", "Phoenix", "B6", "T1", FlightBoardStatus.DELAYED, FlightBoardDirection.DEPARTURES),
        // Arrivals
        FlightBoardItem("a1", "06:05", "DL 980", "Delta", "Minneapolis", "C20", "T1", FlightBoardStatus.ON_TIME, FlightBoardDirection.ARRIVALS),
        FlightBoardItem("a2", "06:40", "AA 233", "American", "Miami", "B11", "T1", FlightBoardStatus.DELAYED, FlightBoardDirection.ARRIVALS),
        FlightBoardItem("a3", "07:15", "UA 1198", "United", "San Francisco", "D44", "T3", FlightBoardStatus.ON_TIME, FlightBoardDirection.ARRIVALS),
        FlightBoardItem("a4", "08:30", "WN 1764", "Southwest", "Phoenix", "B18", "T1", FlightBoardStatus.ON_TIME, FlightBoardDirection.ARRIVALS),
        FlightBoardItem("a5", "09:10", "AS 304", "Alaska", "Portland", "C6", "T1", FlightBoardStatus.DEPARTED, FlightBoardDirection.ARRIVALS),
        FlightBoardItem("a6", "10:05", "B6 188", "JetBlue", "Boston", "E4", "T3", FlightBoardStatus.ON_TIME, FlightBoardDirection.ARRIVALS),
        FlightBoardItem("a7", "11:20", "NK 622", "Spirit", "Houston", "C17", "T1", FlightBoardStatus.DELAYED, FlightBoardDirection.ARRIVALS),
        FlightBoardItem("a8", "12:00", "F9 410", "Frontier", "Denver", "B4", "T1", FlightBoardStatus.ON_TIME, FlightBoardDirection.ARRIVALS),
    )

    /** Emits the full mock board; callers filter by direction. */
    fun observeFlights(): Flow<List<FlightBoardItem>> = flowOf(flights)

    /** One-shot accessor for the full mock board. */
    suspend fun getFlights(): List<FlightBoardItem> = flights
}
