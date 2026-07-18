package com.jetsetter.pro.core.model

import com.jetsetter.pro.core.util.IsoDates
import java.time.Instant
import java.time.LocalDate

/**
 * Pure trip/flight selection helpers (spec §2.5) — shared by the IRIS live-context snapshot,
 * the proactive suggestion engine, and [com.jetsetter.pro.core.data.repository.TripRepository]'s
 * Flow wrappers. Pure JVM: all inputs explicit, no clocks, no Android deps.
 */
object TripQueries {

    /**
     * Flight-ident pattern, e.g. `DL1423`, `AA88`, `SWA123`. Matched against RAW itinerary item
     * titles (per the cross-platform contract — no whitespace stripping), so `"DL 1423"` does NOT
     * resolve; seed/demo titles must use unspaced idents.
     */
    val FLIGHT_IDENT_REGEX = Regex("[A-Z]{2,3}\\d{1,4}")

    /** The next flight found across the user's trips: extracted ident + its item + owning trip. */
    data class NextFlight(
        val ident: String,
        val item: ItineraryItem,
        val trip: Trip,
    )

    /**
     * The trip whose `[startDate, endDate]` range contains [today] (inclusive on both ends);
     * otherwise the next upcoming trip by start date; otherwise `null`. Trips with unparseable
     * dates are skipped. Ties resolve to the earliest start date (stable for equal starts).
     */
    fun activeTrip(trips: List<Trip>, today: LocalDate): Trip? {
        val dated = trips.mapNotNull { trip ->
            val start = IsoDates.parseDate(trip.startDate) ?: return@mapNotNull null
            val end = IsoDates.parseDate(trip.endDate) ?: return@mapNotNull null
            Triple(trip, start, end)
        }.sortedBy { it.second }

        val current = dated.firstOrNull { (_, start, end) ->
            !today.isBefore(start) && !today.isAfter(end)
        }
        if (current != null) return current.first

        return dated.firstOrNull { (_, start, _) -> start.isAfter(today) }?.first
    }

    /**
     * The first future itinerary item whose title contains a flight ident, scanning trips in
     * start-date order and each trip's items in start-time order. "Future" = item start strictly
     * after [now]; items with unparseable start times are skipped.
     */
    fun nextFlight(trips: List<Trip>, now: Instant): NextFlight? {
        val ordered = trips.sortedBy { IsoDates.parseDate(it.startDate) ?: LocalDate.MAX }
        for (trip in ordered) {
            for (item in trip.sortedItems) {
                val start = IsoDates.parseDateTime(item.startDate) ?: continue
                if (!start.isAfter(now)) continue
                val ident = FLIGHT_IDENT_REGEX.find(item.title)?.value ?: continue
                return NextFlight(ident = ident, item = item, trip = trip)
            }
        }
        return null
    }
}
