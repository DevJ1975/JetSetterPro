package com.jetsetter.pro.feature.inflight

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** User-tunable, persisted preferences for the In-Flight tracker (survives app restarts). */
data class InflightSettings(
    val selectedFlightId: String? = null,
    val useMetric: Boolean = false,
    /** Playback rate in simulated seconds advanced per real second. */
    val playbackRate: Int = InflightRepository.DEFAULT_RATE,
)

/**
 * Mock-first source of live in-flight telemetry. No network — the roster of flights is hard-coded
 * sample data, and [statusAt] deterministically derives altitude / speed / vertical-speed / phase /
 * remaining figures from a single progress fraction so the screen can simulate the flight advancing
 * while every number stays internally consistent.
 *
 * Mirrors the iOS in-flight tracker, which streamed from the aircraft's ACARS feed.
 */
@Singleton
class InflightRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    private val settingsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(InflightSettings::class.java)

    /** All flights the traveller could be tracking (first is the default active leg). */
    fun flights(): List<InFlightFlight> = SampleFlights

    fun flightById(id: String?): InFlightFlight =
        SampleFlights.firstOrNull { it.id == id } ?: SampleFlights.first()

    /** Loads persisted preferences, falling back to sensible defaults on first run / bad JSON. */
    suspend fun loadSettings(): InflightSettings =
        runCatching { store.read(KEY)?.let { settingsAdapter.fromJson(it) } }
            .getOrNull() ?: InflightSettings()

    suspend fun saveSettings(settings: InflightSettings) {
        runCatching { store.save(KEY, settingsAdapter.toJson(settings)) }
    }

    /**
     * Pure projection of [flight] at the given [progress] (clamped to 0f..1f). Altitude is linear
     * within climb / descent, and the reported vertical speed is exactly that line's slope — so the
     * altitude readout and the climb/descent rate can never disagree. Ground speed ramps from
     * rotation speed up to cruise and back down to an approach speed; distance / time-remaining are
     * straight proportions of the planned totals.
     */
    fun statusAt(flight: InFlightFlight, progress: Float): InFlightStatus {
        val p = progress.coerceIn(0f, 1f)
        val total = flight.totalFlightMinutes.toFloat()
        val climbFrac = (CLIMB_MINUTES / total).coerceIn(0f, 0.45f)
        val descentFrac = (DESCENT_MINUTES / total).coerceIn(0f, 0.45f)
        val descentStart = 1f - descentFrac

        val phase = when {
            p <= 0f -> InFlightPhase.BOARDING
            p < climbFrac -> InFlightPhase.CLIMB
            p < descentStart -> InFlightPhase.CRUISE
            p < 1f -> InFlightPhase.DESCENT
            else -> InFlightPhase.LANDED
        }

        val altitude = when (phase) {
            InFlightPhase.BOARDING, InFlightPhase.TAXI, InFlightPhase.LANDED -> 0f
            InFlightPhase.CLIMB -> flight.cruiseAltitudeFeet * (p / climbFrac)
            InFlightPhase.CRUISE -> flight.cruiseAltitudeFeet.toFloat()
            InFlightPhase.DESCENT -> flight.cruiseAltitudeFeet * ((1f - p) / descentFrac)
        }.coerceIn(0f, flight.cruiseAltitudeFeet.toFloat())

        val verticalSpeed = when (phase) {
            InFlightPhase.CLIMB -> flight.cruiseAltitudeFeet / CLIMB_MINUTES
            InFlightPhase.DESCENT -> -flight.cruiseAltitudeFeet / DESCENT_MINUTES
            else -> 0f
        }

        val groundSpeed = when (phase) {
            InFlightPhase.BOARDING, InFlightPhase.LANDED -> 0f
            InFlightPhase.TAXI -> TAXI_SPEED
            InFlightPhase.CLIMB ->
                lerp(ROTATION_SPEED, flight.cruiseSpeedMph.toFloat(), (p / climbFrac).coerceIn(0f, 1f))
            InFlightPhase.CRUISE -> flight.cruiseSpeedMph.toFloat()
            InFlightPhase.DESCENT ->
                lerp(APPROACH_SPEED, flight.cruiseSpeedMph.toFloat(), ((1f - p) / descentFrac).coerceIn(0f, 1f))
        }

        val secondsElapsed = (p * flight.totalSeconds).roundToInt()
        return InFlightStatus(
            progress = p,
            phase = phase,
            altitudeFeet = altitude.roundToInt(),
            groundSpeedMph = groundSpeed.roundToInt(),
            verticalSpeedFpm = verticalSpeed.roundToInt(),
            secondsElapsed = secondsElapsed,
            secondsRemaining = flight.totalSeconds - secondsElapsed,
            distanceCoveredMiles = (p * flight.totalDistanceMiles).roundToInt(),
            distanceRemainingMiles = ((1f - p) * flight.totalDistanceMiles).roundToInt(),
        )
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    companion object {
        const val DEFAULT_RATE = 1
        /** Playback rates offered in the UI (simulated seconds per real second). */
        val RATES = listOf(1, 10, 60)

        private const val KEY = "inflight_settings"
        private const val CLIMB_MINUTES = 18f
        private const val DESCENT_MINUTES = 24f
        private const val ROTATION_SPEED = 184f
        private const val APPROACH_SPEED = 161f
        private const val TAXI_SPEED = 14f

        private val SampleFlights = listOf(
            InFlightFlight(
                id = "dl1423",
                flightNumber = "DL 1423",
                airline = "Delta Air Lines",
                originCode = "LAS",
                originCity = "Las Vegas",
                destinationCode = "ATL",
                destinationCity = "Atlanta",
                aircraftType = "Airbus A321neo",
                tailNumber = "N512DN",
                departedLocal = "7:05 AM PDT",
                scheduledArrivalLocal = "2:10 PM EDT",
                totalDistanceMiles = 1946,
                totalFlightMinutes = 245,
                cruiseAltitudeFeet = 38000,
                cruiseSpeedMph = 521,
                initialProgress = 0.62f,
            ),
            InFlightFlight(
                id = "ua88",
                flightNumber = "UA 88",
                airline = "United Airlines",
                originCode = "EWR",
                originCity = "Newark",
                destinationCode = "LHR",
                destinationCity = "London",
                aircraftType = "Boeing 767-300ER",
                tailNumber = "N676UA",
                departedLocal = "8:40 PM EDT",
                scheduledArrivalLocal = "8:25 AM BST",
                totalDistanceMiles = 3454,
                totalFlightMinutes = 405,
                cruiseAltitudeFeet = 36000,
                cruiseSpeedMph = 560,
                initialProgress = 0.18f,
            ),
            InFlightFlight(
                id = "as1011",
                flightNumber = "AS 1011",
                airline = "Alaska Airlines",
                originCode = "SEA",
                originCity = "Seattle",
                destinationCode = "SFO",
                destinationCity = "San Francisco",
                aircraftType = "Boeing 737 MAX 9",
                tailNumber = "N932AK",
                departedLocal = "10:15 AM PDT",
                scheduledArrivalLocal = "12:25 PM PDT",
                totalDistanceMiles = 679,
                totalFlightMinutes = 130,
                cruiseAltitudeFeet = 34000,
                cruiseSpeedMph = 489,
                initialProgress = 0.86f,
            ),
        )
    }
}
