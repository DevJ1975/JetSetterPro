package com.jetsetter.pro.feature.departureoptimizer

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock-first source of "leave by" estimates. Holds an in-memory snapshot of the next flight and
 * re-rolls the variable parts (live drive time + TSA wait) on demand to mimic a traffic/wait-time
 * feed. No network — purely deterministic ranges seeded by [Random]. The traveler's personal
 * buffers are persisted as JSON via [ModuleStateStore] (Moshi) so they survive restarts. NO
 * @Provides here; Hilt constructs this via the @Inject constructor.
 */
@Singleton
class DepartureoptimizerRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    private val prefsAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(DepartureOptimizerPrefs::class.java)

    private var current = DepartureOptimizerEstimate()

    /** The most recently computed estimate (used to seed UI state synchronously). */
    fun currentEstimate(): DepartureOptimizerEstimate = current

    /**
     * Load any persisted personal buffers and fold them into the snapshot. Falls back to the
     * realistic defaults when nothing has been saved (or the blob is corrupt).
     */
    suspend fun load(): DepartureOptimizerEstimate {
        val prefs = readPrefs()
        current = current.copy(
            parkingBufferMinutes = prefs.parkingBufferMinutes,
            gateBufferMinutes = prefs.gateBufferMinutes,
        )
        return current
    }

    /**
     * Re-roll the live, variable estimates (drive, security wait, weather) and recompute risk
     * buckets. Personal buffers and the reference clock are preserved. The small [delay] simulates
     * a refresh round-trip so the UI can show a spinner.
     */
    suspend fun rollEstimate(): DepartureOptimizerEstimate {
        delay(550)
        val drive = Random.nextInt(22, 53)
        val tsa = Random.nextInt(8, 45)
        val weather = WEATHER_CONDITIONS[Random.nextInt(WEATHER_CONDITIONS.size)]
        current = current.copy(
            driveMinutes = drive,
            tsaWaitMinutes = tsa,
            trafficRisk = bucket(drive, lowMax = 30, moderateMax = 45),
            securityRisk = bucket(tsa, lowMax = 20, moderateMax = 35),
            weatherLabel = weather.label,
            weatherTempF = Random.nextInt(weather.tempRange.first, weather.tempRange.last + 1),
            weatherRisk = weather.risk,
        )
        return current
    }

    /** Persist the traveler's new personal buffers and fold them into the snapshot. */
    suspend fun updateBuffers(parkingMinutes: Int, gateMinutes: Int): DepartureOptimizerEstimate {
        store.save(PREFS_KEY, prefsAdapter.toJson(DepartureOptimizerPrefs(parkingMinutes, gateMinutes)))
        current = current.copy(
            parkingBufferMinutes = parkingMinutes,
            gateBufferMinutes = gateMinutes,
        )
        return current
    }

    private suspend fun readPrefs(): DepartureOptimizerPrefs =
        store.read(PREFS_KEY)
            ?.let { runCatching { prefsAdapter.fromJson(it) }.getOrNull() }
            ?: DepartureOptimizerPrefs(current.parkingBufferMinutes, current.gateBufferMinutes)

    private fun bucket(minutes: Int, lowMax: Int, moderateMax: Int): DepartureOptimizerRisk = when {
        minutes <= lowMax -> DepartureOptimizerRisk.LOW
        minutes <= moderateMax -> DepartureOptimizerRisk.MODERATE
        else -> DepartureOptimizerRisk.HIGH
    }

    private companion object {
        const val PREFS_KEY = "departureoptimizer_prefs"

        /** Realistic Las Vegas morning conditions the mock feed rolls between. */
        data class WeatherOption(val label: String, val tempRange: IntRange, val risk: DepartureOptimizerRisk)
        val WEATHER_CONDITIONS = listOf(
            WeatherOption("Clear skies", 68..92, DepartureOptimizerRisk.LOW),
            WeatherOption("Partly cloudy", 66..88, DepartureOptimizerRisk.LOW),
            WeatherOption("Gusty winds", 64..84, DepartureOptimizerRisk.MODERATE),
            WeatherOption("Haze", 70..90, DepartureOptimizerRisk.MODERATE),
            WeatherOption("Thunderstorms nearby", 62..78, DepartureOptimizerRisk.HIGH),
        )
    }
}
