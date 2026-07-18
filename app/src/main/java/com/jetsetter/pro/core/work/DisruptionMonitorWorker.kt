package com.jetsetter.pro.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jetsetter.pro.core.backend.CloudBackend
import com.jetsetter.pro.core.backend.DisruptionEventDoc
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.remote.FlightAwareFlight
import com.jetsetter.pro.core.data.remote.FlightAwareService
import com.jetsetter.pro.core.data.remote.getOrNull
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.notifications.JetNotifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background flight-disruption monitor — the Android counterpart of the iOS
 * `DisruptionMonitorService` (which used BGTaskScheduler). Scheduled from
 * [com.jetsetter.pro.JetSetterApplication] as a 15-minute `PeriodicWorkRequest` (unique, KEEP)
 * only when FlightAware is configured; the actual check lives in [DisruptionCheck] so the
 * Disruption screen can run the identical logic on open. A newly-detected disruption raises a
 * shade alert through [JetNotifier] (cabin-chime channel).
 *
 * The worker doubles as demo mode's scripted alert: [DemoSeeder][com.jetsetter.pro.core.data.demo.DemoSeeder]
 * enqueues a one-shot (under [DEMO_ALERT_UNIQUE_NAME], so it never displaces the periodic
 * monitor) carrying [KEY_TITLE]/[KEY_TEXT] input data; when input data is present the worker
 * posts that copy directly instead of running a live check. The defaults mirror the seeded
 * DisruptionRepository flight (DL 1423 delayed 1h 35m, 3 alternatives) so the shade alert and
 * the Trip Disruption screen tell the same story.
 *
 * Always returns [Result.success]: the check is best-effort by doctrine (no key, no next flight,
 * offline, cloud down are all normal states), so there is nothing WorkManager should retry.
 */
@HiltWorker
class DisruptionMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val disruptionCheck: DisruptionCheck,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Scripted (demo) branch: input data present → post the supplied copy and stop.
        if (inputData.getString(KEY_TITLE) != null || inputData.getString(KEY_TEXT) != null) {
            JetNotifier.postDisruptionAlert(
                applicationContext,
                inputData.getString(KEY_TITLE) ?: DEFAULT_TITLE,
                inputData.getString(KEY_TEXT) ?: DEFAULT_TEXT,
            )
            return Result.success()
        }

        // Live branch: run the real check; a newly-detected disruption lands in the shade.
        runCatching { disruptionCheck.run() }.getOrNull()?.let { outcome ->
            outcome.detected?.let { detected ->
                JetNotifier.postDisruptionAlert(
                    applicationContext,
                    "${outcome.ident}: ${detected.reason}",
                    "Open Trip Disruption to review your rebooking options.",
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "disruption_monitor"
        const val INTERVAL_MINUTES = 15L

        /** Unique name for demo mode's one-shot scripted alert (kept separate from [UNIQUE_NAME] —
         * WorkManager unique names span one-time and periodic work, so sharing it would cancel or
         * displace the live periodic monitor). */
        const val DEMO_ALERT_UNIQUE_NAME = "disruption_demo_alert"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"

        // Mirrors the seeded DisruptionRepository flight (DL 1423 delayed 1h 35m, 3 alternatives)
        // so the shade alert and the Trip Disruption screen tell the same story.
        const val DEFAULT_TITLE = "DL 1423 delayed 1h 35m"
        const val DEFAULT_TEXT =
            "Las Vegas → Atlanta now departs 8:35 AM. IRIS found 3 rebooking options — " +
                "open Trip Disruption to confirm one."
    }
}

/**
 * One end-to-end disruption check (plan B6), shared by [DisruptionMonitorWorker] and
 * `feature/disruption`'s repository:
 *  1. Resolve the user's next flight ([TripRepository.nextFlight]) — nothing upcoming → no-op.
 *  2. Fetch its live status from FlightAware (only when [FlightAwareService.isConfigured]).
 *  3. Compare against the last-seen [FlightStatusSnapshot] persisted in [ModuleStateStore]
 *     ([SNAPSHOT_KEY]) via the pure [DisruptionDetection]; persist the new snapshot.
 *  4. On a newly-detected disruption, mirror a [DisruptionEventDoc] best-effort through
 *     [CloudBackend.disruptionEvents] (no session / missing table → silently dropped, the
 *     established sync doctrine).
 *
 * Returns the fetched flight + detection outcome so the on-screen caller can render the same
 * data without a second fetch; null whenever the live path isn't available (unconfigured key,
 * no next flight, fetch failure) — callers fall back to mock data.
 */
@Singleton
class DisruptionCheck @Inject constructor(
    private val flightAware: FlightAwareService,
    private val tripRepository: TripRepository,
    private val backend: CloudBackend,
    private val moduleState: ModuleStateStore,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val snapshotAdapter = moshi.adapter(FlightStatusSnapshot::class.java)
    private val flightAdapter = moshi.adapter(FlightAwareFlight::class.java)

    /** The live status this check observed, plus the disruption it newly detected (if any). */
    data class Outcome(
        val ident: String,
        val flight: FlightAwareFlight,
        val detected: DetectedDisruption?,
    )

    suspend fun run(): Outcome? {
        if (!flightAware.isConfigured) return null
        val next = tripRepository.nextFlight().first() ?: return null
        val flight = flightAware.flightByIdent(next.ident).getOrNull()?.firstOrNull() ?: return null
        val current = DisruptionDetection.snapshot(flight) ?: return null

        val previous = moduleState.read(SNAPSHOT_KEY)
            ?.let { json -> runCatching { snapshotAdapter.fromJson(json) }.getOrNull() }
        val detected = DisruptionDetection.detect(current, previous)
        moduleState.save(SNAPSHOT_KEY, snapshotAdapter.toJson(current))

        if (detected != null) pushEvent(current, flight, detected)
        return Outcome(ident = current.ident, flight = flight, detected = detected)
    }

    /** Best-effort cloud mirror of a detected disruption. No session → silent no-op. */
    private suspend fun pushEvent(
        snapshot: FlightStatusSnapshot,
        flight: FlightAwareFlight,
        detected: DetectedDisruption,
    ) {
        val uid = backend.currentSession()?.uid ?: return
        val origin = flight.origin?.codeIata
        val destination = flight.destination?.codeIata
        val route = if (origin != null && destination != null) "$origin → $destination" else snapshot.ident
        backend.disruptionEvents.upsert(
            uid,
            DisruptionEventDoc(
                id = UUID.randomUUID().toString(),
                flightNumber = snapshot.ident,
                route = route,
                status = detected.status,
                reason = detected.reason,
                detectedAt = Instant.now().toString(),
                // Full provider payload for forward-compatible detail (CloudModels contract).
                payloadJson = flightAdapter.toJson(flight),
            ),
        )
    }

    private companion object {
        /** ModuleStateStore key for the last-seen snapshot of the monitored flight. */
        const val SNAPSHOT_KEY = "disruption_last_snapshot"
    }
}

/**
 * The last-seen state of the monitored flight — the minimal slice disruption detection compares
 * between polls. Persisted as Moshi JSON in [ModuleStateStore] between checks.
 */
data class FlightStatusSnapshot(
    val ident: String,
    val cancelled: Boolean = false,
    /** Departure delay in MINUTES (AeroAPI's seconds ÷ 60), never negative. */
    val departureDelayMinutes: Long = 0L,
    val departureGate: String? = null,
)

/** A disruption newly observed by one check. [status] matches `DisruptionEventDoc.status`. */
data class DetectedDisruption(
    val status: String,
    val reason: String,
) {
    companion object {
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_DELAYED = "DELAYED"
        const val STATUS_GATE_CHANGED = "GATE_CHANGED"
    }
}

/**
 * Pure disruption-detection logic (plan B6) — a disruption is cancellation, departure delay
 * > [DELAY_THRESHOLD_MIN] minutes, or a gate change versus the last-seen snapshot. Pure JVM,
 * unit-tested in `DisruptionDetectionTest`.
 */
object DisruptionDetection {

    /** Spec threshold: a delay strictly greater than 45 minutes counts as a disruption. */
    const val DELAY_THRESHOLD_MIN = 45L

    /** Condenses an AeroAPI flight to the comparable snapshot; null without a usable ident. */
    fun snapshot(flight: FlightAwareFlight): FlightStatusSnapshot? {
        val ident = flight.ident?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return FlightStatusSnapshot(
            ident = ident,
            cancelled = flight.cancelled == true,
            departureDelayMinutes = ((flight.departureDelaySeconds ?: 0L) / 60L).coerceAtLeast(0L),
            departureGate = flight.gateOrigin?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * The disruption newly present in [current] that [previous] hadn't seen, or null. Transition-
     * based so each disruption raises exactly one event across polls: an already-seen
     * cancellation, an already-past-threshold delay, or an unchanged gate never re-fires. A
     * [previous] snapshot for a *different* ident is ignored (the monitored flight changed).
     * Severity order when several apply at once: cancelled > delayed > gate change.
     */
    fun detect(current: FlightStatusSnapshot, previous: FlightStatusSnapshot?): DetectedDisruption? {
        val prior = previous?.takeIf { it.ident == current.ident }
        return when {
            current.cancelled -> if (prior?.cancelled == true) {
                null
            } else {
                DetectedDisruption(
                    status = DetectedDisruption.STATUS_CANCELLED,
                    reason = "Flight ${current.ident} was cancelled",
                )
            }

            current.departureDelayMinutes > DELAY_THRESHOLD_MIN &&
                (prior == null || prior.departureDelayMinutes <= DELAY_THRESHOLD_MIN) ->
                DetectedDisruption(
                    status = DetectedDisruption.STATUS_DELAYED,
                    reason = "Departure delayed ${formatMinutes(current.departureDelayMinutes)}",
                )

            prior?.departureGate != null && current.departureGate != null &&
                prior.departureGate != current.departureGate ->
                DetectedDisruption(
                    status = DetectedDisruption.STATUS_GATE_CHANGED,
                    reason = "Departure gate changed ${prior.departureGate} → ${current.departureGate}",
                )

            else -> null
        }
    }

    /** Formats a minute span compactly, e.g. 95 → "1h 35m", 60 → "1h", 40 → "40m". */
    fun formatMinutes(totalMin: Long): String {
        val clamped = totalMin.coerceAtLeast(0L)
        val hours = clamped / 60L
        val minutes = clamped % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
            hours > 0L -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
