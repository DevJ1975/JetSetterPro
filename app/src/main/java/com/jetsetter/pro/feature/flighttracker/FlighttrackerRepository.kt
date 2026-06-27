package com.jetsetter.pro.feature.flighttracker

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mock backing for the Flight Tracker feature. Mirrors the shape of a real flight-status
 * API (search / filter by ident) without any network — the live integration (FlightAware AeroAPI)
 * is documented in the feature parity guide.
 *
 * Schedules are expressed as offsets from "now" (see [FlightTrackerFlight]) so the demo board always
 * shows a believable mix of boarding / airborne / landed flights. The tester's recent searches are
 * the only mutable state, persisted as a Moshi-serialized JSON list via [ModuleStateStore]
 * (key [RECENTS_KEY]) — the same Moshi idiom Room uses in `core/data/local/Converters.kt` — so they
 * survive app restarts.
 */
@Singleton
class FlighttrackerRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val recentsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

    private val flights: List<FlightTrackerFlight> = listOf(
        // Airborne, on time — roughly a third of the way through a transcon hop.
        FlightTrackerFlight(
            ident = "DL1423",
            airline = "Delta Air Lines",
            originCode = "LAS",
            originCity = "Las Vegas",
            destinationCode = "ATL",
            destinationCity = "Atlanta",
            gate = "C22",
            terminal = "3",
            departureOffsetMin = -95L,
            durationMin = 258L,
        ),
        // Still on the ground, running late.
        FlightTrackerFlight(
            ident = "UA512",
            airline = "United Airlines",
            originCode = "SFO",
            originCity = "San Francisco",
            destinationCode = "EWR",
            destinationCity = "Newark",
            gate = "F8",
            terminal = "3",
            departureOffsetMin = 30L,
            durationMin = 325L,
            delayMin = 45L,
        ),
        // Long-haul, nearly arrived.
        FlightTrackerFlight(
            ident = "AA88",
            airline = "American Airlines",
            originCode = "JFK",
            originCity = "New York",
            destinationCode = "LHR",
            destinationCity = "London",
            gate = "B44",
            terminal = "8",
            departureOffsetMin = -360L,
            durationMin = 415L,
        ),
        // Boarding now, slightly delayed.
        FlightTrackerFlight(
            ident = "WN2299",
            airline = "Southwest Airlines",
            originCode = "DAL",
            originCity = "Dallas",
            destinationCode = "DEN",
            destinationCity = "Denver",
            gate = "A17",
            terminal = "1",
            departureOffsetMin = 15L,
            durationMin = 110L,
            delayMin = 25L,
        ),
        // Already on the ground at the destination.
        FlightTrackerFlight(
            ident = "B6701",
            airline = "JetBlue Airways",
            originCode = "BOS",
            originCity = "Boston",
            destinationCode = "FLL",
            destinationCity = "Fort Lauderdale",
            gate = "C6",
            terminal = "C",
            departureOffsetMin = -210L,
            durationMin = 185L,
        ),
        // Comfortably scheduled, on time.
        FlightTrackerFlight(
            ident = "AS640",
            airline = "Alaska Airlines",
            originCode = "SEA",
            originCity = "Seattle",
            destinationCode = "SFO",
            destinationCity = "San Francisco",
            gate = "N12",
            terminal = "N",
            departureOffsetMin = 180L,
            durationMin = 125L,
        ),
    )

    /** Every flight on the board, in schedule-board order. */
    fun allFlights(): List<FlightTrackerFlight> = flights

    /** The flight surfaced on first open, before the tester searches. */
    fun featuredFlight(): FlightTrackerFlight = flights.first()

    /** Exact, case- and space-insensitive lookup by ident; null when nothing matches. */
    fun findByIdent(ident: String): FlightTrackerFlight? {
        val needle = ident.normalizeIdent()
        if (needle.isEmpty()) return null
        return flights.firstOrNull { it.ident.normalizeIdent() == needle }
    }

    /**
     * Substring filter by ident (case- and space-insensitive). An empty/blank query returns the
     * full board, so the list never appears empty just because the field is cleared.
     */
    fun filterByIdent(query: String): List<FlightTrackerFlight> {
        val needle = query.normalizeIdent()
        if (needle.isEmpty()) return flights
        return flights.filter { it.ident.normalizeIdent().contains(needle) }
    }

    /** Persisted recent-search idents, newest first; empty (and tolerant of bad JSON) on first run. */
    suspend fun recentIdents(): List<String> {
        val json = store.read(RECENTS_KEY) ?: return emptyList()
        return runCatching { recentsAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    /** Records [ident] as the most-recent search (de-duplicated, capped) and persists it. */
    suspend fun pushRecent(ident: String) {
        val updated = (listOf(ident) + recentIdents())
            .distinctBy { it.normalizeIdent() }
            .take(MAX_RECENTS)
        store.save(RECENTS_KEY, recentsAdapter.toJson(updated))
    }

    private fun String.normalizeIdent(): String = replace(" ", "").uppercase()

    private companion object {
        const val RECENTS_KEY = "flighttracker_recent_idents"
        const val MAX_RECENTS = 6
    }
}
