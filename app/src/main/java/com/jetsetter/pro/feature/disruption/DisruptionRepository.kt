package com.jetsetter.pro.feature.disruption

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock-first source of truth for the Disruption Monitor. The disrupted flight, the
 * alternatives, and the timeline copy are realistic in-memory sample data (no network /
 * Room). The one piece of *user* state — the committed rebooking [DisruptionDecision] — is
 * persisted as a Moshi-serialized JSON blob via [ModuleStateStore] (key [KEY]) so a confirmed
 * rebooking survives an app restart, mirroring the Moshi idiom in `core/data/local/Converters.kt`.
 */
@Singleton
class DisruptionRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val decisionAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(DisruptionDecision::class.java)

    fun monitoredFlight(): DisruptionMonitoredFlight = DisruptionMonitoredFlight(
        flightNumber = "DL 1423",
        route = "LAS → ATL",
        origin = "Las Vegas (LAS)",
        destination = "Atlanta (ATL)",
        scheduledDepartureMin = 7 * 60,        // 7:00 AM
        revisedDepartureMin = 8 * 60 + 35,     // 8:35 AM  -> 95 min departure delay
        scheduledArrivalMin = 14 * 60 + 15,    // 2:15 PM
        revisedArrivalMin = 17 * 60 + 15,      // 5:15 PM  -> 3h arrival delay (weather hold)
        gate = "C22",
        fare = 342.0,
        status = DisruptionFlightStatus.DELAYED,
        reason = "Late inbound aircraft · weather hold at ATL",
    )

    fun alternatives(): List<DisruptionAlternative> = listOf(
        DisruptionAlternative(
            id = "alt-aa218",
            carrier = "AA 218",
            route = "LAS → ATL",
            departureMin = 8 * 60 + 10,    // 8:10 AM
            arrivalMin = 15 * 60 + 25,     // 3:25 PM
            cabin = "First",
            seatsLeft = 2,
            price = 412.0,
        ),
        DisruptionAlternative(
            id = "alt-dl2207",
            carrier = "DL 2207",
            route = "LAS → ATL",
            departureMin = 9 * 60 + 45,    // 9:45 AM
            arrivalMin = 16 * 60 + 58,     // 4:58 PM
            cabin = "Comfort+",
            seatsLeft = 6,
            price = 289.0,
        ),
        DisruptionAlternative(
            id = "alt-wn1190",
            carrier = "WN 1190",
            route = "LAS → ATL",
            departureMin = 11 * 60 + 20,   // 11:20 AM
            arrivalMin = 18 * 60 + 40,     // 6:40 PM
            cabin = "Main",
            seatsLeft = 14,
            price = 198.0,
        ),
    )

    /**
     * The five timeline steps, all seeded [DisruptionStepStatus.PENDING]; the ViewModel
     * animates them to DONE. Detail copy is derived from the real flight / alternatives so the
     * narrative can't drift from what's shown elsewhere on screen.
     */
    fun responseStepSeeds(
        flight: DisruptionMonitoredFlight,
        alternatives: List<DisruptionAlternative>,
    ): List<DisruptionResponseStep> {
        val city = flight.destination.substringBefore(" (")
        return listOf(
            DisruptionResponseStep(
                id = "step-detected",
                title = "Disruption detected",
                detail = "${flight.flightNumber} flagged ${durationLabel(flight.delayMinutes)} late",
            ),
            DisruptionResponseStep(
                id = "step-options",
                title = "Rebooking options found",
                detail = "${alternatives.size} same-day alternatives located",
            ),
            DisruptionResponseStep(
                id = "step-hotel",
                title = "Backup hotel held",
                detail = "Refundable room in $city held until 6:00 PM",
            ),
            DisruptionResponseStep(
                id = "step-notified",
                title = "Traveler notified",
                detail = "Push + SMS sent with the top recommendation",
            ),
            DisruptionResponseStep(
                id = "step-confirm",
                title = "Awaiting your confirmation",
                detail = "Pick an alternative to auto-rebook in one tap",
            ),
        )
    }

    /** Loads the persisted rebooking decision, or null if the traveler hasn't rebooked yet. */
    suspend fun readDecision(): DisruptionDecision? =
        store.read(KEY)?.let { json -> runCatching { decisionAdapter.fromJson(json) }.getOrNull() }

    /** Persists the committed rebooking so it survives a restart. */
    suspend fun saveDecision(decision: DisruptionDecision) {
        store.save(KEY, decisionAdapter.toJson(decision))
    }

    private companion object {
        const val KEY = "disruption_decision"
    }
}
