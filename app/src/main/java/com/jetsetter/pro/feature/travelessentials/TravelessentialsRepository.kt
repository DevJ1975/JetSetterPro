package com.jetsetter.pro.feature.travelessentials

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of destination essentials, backed by [TravelEssentialsCatalog] — a bundled, offline
 * reference table (no network, no storage, no API key) of real-world figures for the destinations
 * a traveller is most likely to look up, with a safe default for anything not covered.
 *
 * Zone ids and FX rates are stored as primitives so the screen's live clock and currency
 * converter compute from them directly (no hard-coded display strings that could drift).
 * The clock resolves each [TravelEssentialsDestination.zoneId]'s UTC offset DST-aware via
 * java.time, so summer/winter time is always correct.
 */
@Singleton
class TravelessentialsRepository @Inject constructor() {

    private val destinations: List<TravelEssentialsDestination> = TravelEssentialsCatalog.destinations

    /** Streams the available destinations from the bundled catalog. */
    fun observeDestinations(): Flow<List<TravelEssentialsDestination>> = flowOf(destinations)

    /** Suspend accessor for the same bundled catalog. */
    suspend fun getDestinations(): List<TravelEssentialsDestination> = destinations

    /**
     * Looks up the essentials for the [query] destination the UI provides (city, country, id,
     * currency code or language), falling back to a sensible default for unknown destinations so
     * the caller always gets a usable result. A blank query returns the first destination.
     */
    fun essentialsFor(query: String?): TravelEssentialsDestination = TravelEssentialsCatalog.lookup(query)
}
