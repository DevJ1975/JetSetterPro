package com.jetsetter.pro.feature.disruption

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.remote.FlightAwareFlight
import com.jetsetter.pro.core.util.IsoDates
import com.jetsetter.pro.core.work.DisruptionCheck
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of truth for the Disruption Monitor. Live-when-configured (plan B6): the monitored
 * flight comes from the user's next upcoming flight (`TripRepository.nextFlight`) resolved
 * against FlightAware through the shared [DisruptionCheck] — the same run the background
 * [com.jetsetter.pro.core.work.DisruptionMonitorWorker] executes, so opening the screen also
 * performs the snapshot comparison and mirrors any newly-detected disruption through
 * `CloudBackend.disruptionEvents`. When FlightAware isn't configured (or the live lookup fails)
 * the realistic in-memory demo flight is returned instead, and the alternatives / timeline copy
 * stay demo data throughout (rebooking has no live backend).
 *
 * The one piece of *user* state — the committed rebooking [DisruptionDecision] — is persisted as
 * a Moshi-serialized JSON blob via [ModuleStateStore] (key [KEY]) so a confirmed rebooking
 * survives an app restart, mirroring the Moshi idiom in `core/data/local/Converters.kt`.
 */
@Singleton
class DisruptionRepository @Inject constructor(
    private val store: ModuleStateStore,
    private val disruptionCheck: DisruptionCheck,
) {
    private val decisionAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(DisruptionDecision::class.java)

    /**
     * The flight under monitoring: live (next flight × FlightAware) when configured, demo
     * otherwise. Running the check here also persists the last-seen snapshot and pushes any
     * newly-detected disruption to the cloud — see [DisruptionCheck].
     */
    suspend fun monitoredFlight(): DisruptionMonitoredFlight {
        val outcome = runCatching { disruptionCheck.run() }.getOrNull()
        return outcome?.flight
            ?.toMonitoredFlight(zone = ZoneId.systemDefault(), fare = DEMO_FARE)
            ?: demoMonitoredFlight()
    }

    private fun demoMonitoredFlight(): DisruptionMonitoredFlight = DisruptionMonitoredFlight(
        flightNumber = "DL 1423",
        route = "LAS → ATL",
        origin = "Las Vegas (LAS)",
        destination = "Atlanta (ATL)",
        scheduledDepartureMin = 7 * 60,        // 7:00 AM
        revisedDepartureMin = 8 * 60 + 35,     // 8:35 AM  -> 95 min departure delay
        scheduledArrivalMin = 14 * 60 + 15,    // 2:15 PM
        revisedArrivalMin = 17 * 60 + 15,      // 5:15 PM  -> 3h arrival delay (weather hold)
        gate = "C22",
        fare = DEMO_FARE,
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

        // AeroAPI carries no fare data, and the rebooking alternatives (whose prices the UI
        // compares against this) are demo data either way — so the demo fare anchors both paths.
        const val DEMO_FARE = 342.0
    }
}

// ── Live mapping (pure, unit-tested in FlightAwareMappingTest) ───────────────

/**
 * Maps an AeroAPI flight onto the screen's minutes-since-midnight model, resolving clock times
 * in [zone]. Null when the ident or either scheduled time is missing/unparseable — callers fall
 * back to the demo flight. Revised times prefer actual → estimated → scheduled+reported-delay,
 * so the derived `delayMinutes` (revised − scheduled) matches what FlightAware is reporting.
 */
internal fun FlightAwareFlight.toMonitoredFlight(zone: ZoneId, fare: Double): DisruptionMonitoredFlight? {
    val flightIdent = ident?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val scheduledOutAt = IsoDates.parseDateTime(scheduledOut) ?: return null
    val scheduledInAt = IsoDates.parseDateTime(scheduledIn) ?: return null
    val revisedOutAt = IsoDates.parseDateTime(actualOut)
        ?: IsoDates.parseDateTime(estimatedOut)
        ?: scheduledOutAt.plusSeconds((departureDelaySeconds ?: 0L).coerceAtLeast(0L))
    val revisedInAt = IsoDates.parseDateTime(actualIn)
        ?: IsoDates.parseDateTime(estimatedIn)
        ?: scheduledInAt.plusSeconds((arrivalDelaySeconds ?: 0L).coerceAtLeast(0L))

    val originCode = origin?.codeIata?.takeIf { it.isNotBlank() }
    val destinationCode = destination?.codeIata?.takeIf { it.isNotBlank() }
    val departureDelayMin = Duration.between(scheduledOutAt, revisedOutAt).toMinutes().coerceAtLeast(0L)

    val liveStatus = when {
        cancelled == true -> DisruptionFlightStatus.CANCELLED
        departureDelayMin > 0L -> DisruptionFlightStatus.DELAYED
        else -> DisruptionFlightStatus.ON_TIME
    }
    val liveReason = status?.trim()?.takeIf { it.isNotEmpty() } ?: when (liveStatus) {
        DisruptionFlightStatus.CANCELLED -> "Cancelled by the airline"
        DisruptionFlightStatus.DELAYED -> "Running ${durationLabel(departureDelayMin.toInt())} late"
        else -> "On schedule"
    }

    return DisruptionMonitoredFlight(
        flightNumber = flightIdent,
        route = if (originCode != null && destinationCode != null) "$originCode → $destinationCode" else flightIdent,
        origin = placeLabel(origin?.city, originCode),
        destination = placeLabel(destination?.city, destinationCode),
        scheduledDepartureMin = scheduledOutAt.minuteOfDay(zone),
        revisedDepartureMin = revisedOutAt.minuteOfDay(zone),
        scheduledArrivalMin = scheduledInAt.minuteOfDay(zone),
        revisedArrivalMin = revisedInAt.minuteOfDay(zone),
        gate = gateOrigin?.takeIf { it.isNotBlank() } ?: "—",
        fare = fare,
        status = liveStatus,
        reason = liveReason,
    )
}

/** "Las Vegas (LAS)" / "Las Vegas" / "LAS" / "—" depending on what AeroAPI supplied. */
private fun placeLabel(city: String?, code: String?): String {
    val cleanCity = city?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        cleanCity != null && code != null -> "$cleanCity ($code)"
        cleanCity != null -> cleanCity
        code != null -> code
        else -> "—"
    }
}

private fun Instant.minuteOfDay(zone: ZoneId): Int =
    atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
