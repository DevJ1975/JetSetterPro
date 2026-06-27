package com.jetsetter.pro.feature.carbontracker

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mock source for the Carbon Footprint feature. Mirrors the app's other
 * [Singleton] repositories: realistic sample data, no network, exposed via suspend funs,
 * with tester-mutable state persisted via [ModuleStateStore] (Moshi, the Converters idiom).
 *
 * Flights store only distance + cabin; every CO2 figure is derived from those, so the numbers
 * can never drift out of sync with the inputs. Stable ids let restored overrides keep matching.
 */
@Singleton
class CarbontrackerRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    private val flights = listOf(
        CarbonFlightEstimate(
            id = "dl1423",
            flightNumber = "DL 1423",
            origin = "LAS",
            destination = "ATL",
            distanceKm = 2780,
            cabinClass = CarbonCabinClass.BUSINESS,
        ),
        CarbonFlightEstimate(
            id = "aa218",
            flightNumber = "AA 218",
            origin = "ATL",
            destination = "JFK",
            distanceKm = 1224,
            cabinClass = CarbonCabinClass.ECONOMY,
        ),
        CarbonFlightEstimate(
            id = "ua90",
            flightNumber = "UA 90",
            origin = "JFK",
            destination = "LHR",
            distanceKm = 5541,
            cabinClass = CarbonCabinClass.BUSINESS,
        ),
    )

    private val offsets = listOf(
        CarbonOffsetSuggestion(
            id = "reforest",
            title = "Reforestation — Pará, Brazil",
            provider = "Gold Standard",
            description = "Native tree planting that restores degraded rainforest and local livelihoods.",
            pricePerTonneUsd = 18.0,
        ),
        CarbonOffsetSuggestion(
            id = "saf",
            title = "Sustainable Aviation Fuel credits",
            provider = "Verified Carbon Standard",
            description = "Funds blended SAF that cuts lifecycle jet-fuel emissions on partner routes.",
            pricePerTonneUsd = 45.0,
        ),
        CarbonOffsetSuggestion(
            id = "dac",
            title = "Direct Air Capture",
            provider = "Climeworks",
            description = "Permanent removal — CO2 is filtered from the air and mineralized underground.",
            pricePerTonneUsd = 120.0,
        ),
    )

    /** Distance bounds the "add a leg" editor accepts (km). */
    val minLegDistanceKm: Int = 50
    val maxLegDistanceKm: Int = 18_000

    /** Emits the seeded trip after a short simulated lookup so the loading state is real. */
    suspend fun loadFlights(): List<CarbonFlightEstimate> {
        delay(400)
        return flights
    }

    suspend fun loadOffsets(): List<CarbonOffsetSuggestion> = offsets

    // --- Persistence (Moshi, mirroring the Converters idiom) -------------------------------

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val prefsAdapter = moshi.adapter(CarbonPersistedState::class.java)

    /** Restores tester-entered state; falls back to defaults on first run or bad JSON. */
    suspend fun loadPrefs(): CarbonPersistedState =
        store.read(PREFS_KEY)
            ?.let { runCatching { prefsAdapter.fromJson(it) }.getOrNull() }
            ?: CarbonPersistedState()

    /** Persists tester-entered state so it survives an app restart. */
    suspend fun savePrefs(prefs: CarbonPersistedState) {
        store.save(PREFS_KEY, prefsAdapter.toJson(prefs))
    }

    private companion object {
        const val PREFS_KEY = "carbontracker_prefs"
    }
}
