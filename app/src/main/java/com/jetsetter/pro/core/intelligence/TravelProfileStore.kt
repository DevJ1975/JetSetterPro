package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.PrefKeys
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.di.ApplicationScope
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.UserPreferences
import com.jetsetter.pro.core.util.IsoDates
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The travel-signal store + learned-profile front door (spec §1.6, plan A4).
 *
 * Signals persist as a Moshi JSON list under the cross-platform key [PrefKeys.TRAVEL_SIGNALS],
 * FIFO-capped at [TravelProfileStoreLogic.MAX_SIGNALS] (oldest dropped). [record] is **consent-
 * gated and silent**: the master `learningEnabled` switch gates every kind, and the per-surface
 * switches gate their kinds (see [TravelProfileStoreLogic.allows]) — a disallowed signal is simply
 * not recorded, never an error.
 *
 * [profile] recomputes the deterministic [TravelProfileData] via [TravelProfileEngine] (cached
 * in-memory; invalidated on [record] and on consent changes) and returns the empty profile while
 * learning is disabled. [persona] serves the generated traveler persona cached under
 * [PrefKeys.TRAVEL_PERSONA], keyed by the profile summary's hash, regenerating through
 * [TravelPersonaGenerator] when stale and clearing it when learning is off or nothing is learned.
 *
 * A trip-completion watcher on [TripRepository.observeTrips] records an idempotent `tripCompleted`
 * signal for each trip whose end date has passed (seed/demo trips excluded — the profile never
 * learns from demo data, plan R10e).
 *
 * PRIVACY INVARIANT (see [TravelSignal]): signals and everything derived from them stay on-device;
 * they are never sent to third-party APIs.
 */
@Singleton
class TravelProfileStore @Inject constructor(
    private val store: ModuleStateStore,
    private val userPreferences: UserPreferencesRepository,
    private val tripRepository: TripRepository,
    private val personaGenerator: TravelPersonaGenerator,
    @ApplicationScope appScope: CoroutineScope,
) {
    private val moshi = Moshi.Builder()
        .add(TravelSignalKindAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()
    private val signalsAdapter = moshi.adapter<List<TravelSignal>>(
        Types.newParameterizedType(List::class.java, TravelSignal::class.java),
    )
    private val idsAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java),
    )

    private val writeMutex = Mutex()

    @Volatile
    private var cachedProfile: TravelProfileData? = null

    init {
        // Consent changes invalidate the cached profile; turning the master switch off also clears
        // the stored persona (spec: persona is "cleared if off/empty").
        appScope.launch {
            userPreferences.preferences
                .map { LearningConsent(it.learningEnabled, it.learnFromReceipts, it.learnFromCheckIns, it.learnFromTrips) }
                .distinctUntilChanged()
                .collect { consent ->
                    cachedProfile = null
                    if (!consent.learningEnabled) clearPersona()
                }
        }
        // Trip-completion watcher: whenever the trip table changes, record tripCompleted signals
        // for newly-ended trips (idempotent via the recorded-ids side key).
        appScope.launch {
            tripRepository.observeTrips().collect { trips ->
                runCatching { recordCompletedTrips(trips) }
            }
        }
        // One-time cleanup: drop the legacy UserMemory/TravelProfile blobs this store supersedes
        // (their keys are abandoned — nothing reads them anymore). remove() is idempotent.
        appScope.launch {
            runCatching {
                store.remove(LEGACY_USER_MEMORY_KEY)
                store.remove(LEGACY_TRAVEL_PROFILE_KEY)
            }
        }
    }

    // ── Signals ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Appends [signal] to the log (FIFO cap 2000) — a SILENT no-op when the consent mapping
     * disallows its kind (spec §1.6). Invalidates the cached profile on a real write.
     */
    suspend fun record(signal: TravelSignal) {
        val prefs = userPreferences.preferences.first()
        if (!TravelProfileStoreLogic.allows(signal.kind, prefs)) return
        writeMutex.withLock {
            val updated = TravelProfileStoreLogic.appendCapped(signals(), signal)
            store.save(PrefKeys.TRAVEL_SIGNALS, signalsAdapter.toJson(updated))
        }
        cachedProfile = null
    }

    /** Feedback on a proactive suggestion: value = suggestion-kind name, accepted flag attribute. */
    suspend fun recordSuggestionFeedback(kind: String, accepted: Boolean) {
        record(
            TravelSignal(
                kind = TravelSignal.Kind.SUGGESTION_FEEDBACK,
                value = kind,
                attributes = mapOf(TravelSignal.Attr.ACCEPTED to accepted.toString()),
                timestamp = Instant.now().toString(),
                source = "suggestions",
            ),
        )
    }

    /** How many times the suggestion [kind] was dismissed (feedback with accepted=false). */
    suspend fun dismissedCount(kind: String): Int =
        TravelProfileStoreLogic.dismissedCount(signals(), kind)

    // ── Trip completion ─────────────────────────────────────────────────────────────────────────

    /**
     * Records a `tripCompleted` signal for every trip in [trips] that has ended (end date before
     * today) and wasn't recorded yet. Seed/demo trips are skipped. Idempotent: recorded trip ids
     * are tracked in a side key. Consent-blocked trips stay unmarked so they record once consent
     * returns.
     */
    suspend fun recordCompletedTrips(trips: List<Trip>) {
        val toRecord = TravelProfileStoreLogic.completedTripsToRecord(
            trips = trips,
            today = LocalDate.now(),
            recordedIds = recordedTripIds(),
            seedIds = MockData.seedTripIds,
        )
        toRecord.forEach { recordCompletedTrip(it, source = "trips") }
    }

    /**
     * Records one trip's `tripCompleted` signal (value = destination, attributes durationDays)
     * from [source], only for trips that have actually ended. Idempotent + seed-guarded; consent
     * rules apply as in [record]. Used by the watcher (source "trips") and the IRIS `addTrip`
     * commit for retroactively-added past trips (source "iris").
     */
    suspend fun recordCompletedTrip(trip: Trip, source: String) {
        if (isSeedTrip(trip.id)) return
        val ended = IsoDates.parseDate(trip.endDate)?.isBefore(LocalDate.now()) == true
        if (!ended) return
        if (trip.id in recordedTripIds()) return
        val prefs = userPreferences.preferences.first()
        if (!TravelProfileStoreLogic.allows(TravelSignal.Kind.TRIP_COMPLETED, prefs)) return
        record(
            TravelSignal(
                kind = TravelSignal.Kind.TRIP_COMPLETED,
                value = trip.destination,
                attributes = buildMap {
                    TravelProfileStoreLogic.durationDays(trip)?.let { put(ATTR_DURATION_DAYS, it.toString()) }
                },
                timestamp = Instant.now().toString(),
                source = source,
            ),
        )
        markTripRecorded(trip.id)
    }

    // ── Profile ─────────────────────────────────────────────────────────────────────────────────

    /**
     * The learned profile as of now — recomputed from the signal log + trip ledger and cached
     * in-memory until the next [record] or consent change. Empty while learning is disabled.
     */
    suspend fun profile(): TravelProfileData {
        val prefs = userPreferences.preferences.first()
        if (!prefs.learningEnabled) return TravelProfileData()
        cachedProfile?.let { return it }
        val computed = TravelProfileEngine.compute(
            signals = signals(),
            trips = tripRepository.observeTrips().first(),
            now = Instant.now(),
        )
        cachedProfile = computed
        return computed
    }

    /** Live profile view: re-emits on signal, trip, or consent changes. Empty while disabled. */
    fun observeProfile(): Flow<TravelProfileData> =
        combine(
            userPreferences.preferences,
            store.observe(PrefKeys.TRAVEL_SIGNALS),
            tripRepository.observeTrips(),
        ) { prefs, json, trips ->
            if (!prefs.learningEnabled) {
                TravelProfileData()
            } else {
                TravelProfileEngine.compute(parseSignals(json), trips, Instant.now())
            }
        }

    // ── Persona ─────────────────────────────────────────────────────────────────────────────────

    /**
     * The cached traveler persona for the current profile, regenerated via [TravelPersonaGenerator]
     * when the profile summary's hash changed. `""` (and the stored persona cleared) when learning
     * is off or the profile is empty.
     */
    suspend fun persona(): String {
        val prefs = userPreferences.preferences.first()
        if (!prefs.learningEnabled) {
            clearPersona()
            return ""
        }
        val profile = profile()
        if (profile.isEmpty) {
            clearPersona()
            return ""
        }
        val hash = profile.summaryForPrompt().hashCode().toString()
        val cachedText = store.read(PrefKeys.TRAVEL_PERSONA)
        if (store.read(PERSONA_HASH_KEY) == hash && !cachedText.isNullOrBlank()) return cachedText
        val generated = runCatching { personaGenerator.generate(profile) }.getOrDefault("")
        if (generated.isBlank()) return cachedText.orEmpty() // keep the last good persona
        store.save(PrefKeys.TRAVEL_PERSONA, generated)
        store.save(PERSONA_HASH_KEY, hash)
        return generated
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────────

    private suspend fun signals(): List<TravelSignal> =
        parseSignals(store.read(PrefKeys.TRAVEL_SIGNALS))

    private fun parseSignals(json: String?): List<TravelSignal> =
        json?.let { runCatching { signalsAdapter.fromJson(it) }.getOrNull() }.orEmpty()

    private suspend fun recordedTripIds(): Set<String> =
        store.read(RECORDED_TRIP_IDS_KEY)
            ?.let { runCatching { idsAdapter.fromJson(it) }.getOrNull() }
            .orEmpty()
            .toSet()

    private suspend fun markTripRecorded(tripId: String) {
        writeMutex.withLock {
            val updated = recordedTripIds() + tripId
            store.save(RECORDED_TRIP_IDS_KEY, idsAdapter.toJson(updated.toList()))
        }
    }

    private suspend fun clearPersona() {
        store.remove(PrefKeys.TRAVEL_PERSONA)
        store.remove(PERSONA_HASH_KEY)
    }

    /** The 4 consent switches — the only preference changes that invalidate the profile cache. */
    private data class LearningConsent(
        val learningEnabled: Boolean,
        val learnFromReceipts: Boolean,
        val learnFromCheckIns: Boolean,
        val learnFromTrips: Boolean,
    )

    companion object {
        /** Local (non-cross-platform) side key: hash the stored persona was generated from. */
        private const val PERSONA_HASH_KEY = "travel_persona_hash"

        /** Local side key: trip ids whose tripCompleted signal was already recorded. */
        private const val RECORDED_TRIP_IDS_KEY = "travel_profile_recorded_trip_ids"

        /** Abandoned key of the deleted legacy `UserMemory` store (cleaned up once in init). */
        private const val LEGACY_USER_MEMORY_KEY = "user_memory"

        /** Abandoned key of the deleted legacy `TravelProfile` store (cleaned up once in init). */
        private const val LEGACY_TRAVEL_PROFILE_KEY = "travel_profile"

        /** Attribute key for a completed trip's length in days (not in [TravelSignal.Attr]). */
        private const val ATTR_DURATION_DAYS = "durationDays"

        /** Seed-data guard (plan R10e): signals must never learn from demo trips. */
        fun isSeedTrip(tripId: String): Boolean = tripId in MockData.seedTripIds

        /** Seed-data guard (plan R10e): signals must never learn from demo expenses. */
        fun isSeedExpense(expenseId: String): Boolean = expenseId in MockData.seedExpenseIds
    }
}

/**
 * The pure rules of [TravelProfileStore] — consent mapping, FIFO cap, feedback counting, and
 * completed-trip selection — extracted so unit tests can pin them without DataStore/Room.
 */
internal object TravelProfileStoreLogic {

    /** Signal-log FIFO cap (spec §1.6): oldest entries drop past this size. */
    const val MAX_SIGNALS = 2000

    /**
     * The spec §1.6 consent mapping: master `learningEnabled` gates everything; learnFromReceipts
     * gates receiptScanned+expenseLogged; learnFromCheckIns gates seatChosen; learnFromTrips gates
     * flightFlown+tripCompleted+placeVisited; loyaltyAdded + suggestionFeedback are master-only.
     */
    fun allows(kind: TravelSignal.Kind, prefs: UserPreferences): Boolean {
        if (!prefs.learningEnabled) return false
        return when (kind) {
            TravelSignal.Kind.RECEIPT_SCANNED,
            TravelSignal.Kind.EXPENSE_LOGGED,
            -> prefs.learnFromReceipts

            TravelSignal.Kind.SEAT_CHOSEN -> prefs.learnFromCheckIns

            TravelSignal.Kind.FLIGHT_FLOWN,
            TravelSignal.Kind.TRIP_COMPLETED,
            TravelSignal.Kind.PLACE_VISITED,
            -> prefs.learnFromTrips

            TravelSignal.Kind.LOYALTY_ADDED,
            TravelSignal.Kind.SUGGESTION_FEEDBACK,
            -> true
        }
    }

    /** [existing] + [signal], keeping only the newest [cap] entries (drop-oldest FIFO). */
    fun appendCapped(
        existing: List<TravelSignal>,
        signal: TravelSignal,
        cap: Int = MAX_SIGNALS,
    ): List<TravelSignal> = (existing + signal).takeLast(cap)

    /** Count of suggestionFeedback signals for [kind] with accepted=false. */
    fun dismissedCount(signals: List<TravelSignal>, kind: String): Int =
        signals.count {
            it.kind == TravelSignal.Kind.SUGGESTION_FEEDBACK &&
                it.value == kind &&
                it.attributes[TravelSignal.Attr.ACCEPTED] == "false"
        }

    /** Trips ended before [today] that are neither seed data nor already recorded. */
    fun completedTripsToRecord(
        trips: List<Trip>,
        today: LocalDate,
        recordedIds: Set<String>,
        seedIds: Set<String>,
    ): List<Trip> = trips.filter { trip ->
        trip.id !in recordedIds &&
            trip.id !in seedIds &&
            IsoDates.parseDate(trip.endDate)?.isBefore(today) == true
    }

    /** Trip length in days (`end - start`), or null when unparsable/negative. */
    fun durationDays(trip: Trip): Long? {
        val start = IsoDates.parseDate(trip.startDate) ?: return null
        val end = IsoDates.parseDate(trip.endDate) ?: return null
        return ChronoUnit.DAYS.between(start, end).takeIf { it >= 0 }
    }
}
