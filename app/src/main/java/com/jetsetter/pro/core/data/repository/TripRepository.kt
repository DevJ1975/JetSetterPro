package com.jetsetter.pro.core.data.repository

import com.jetsetter.pro.core.backend.CloudBackend
import com.jetsetter.pro.core.data.local.TripDao
import com.jetsetter.pro.core.data.local.toDomain
import com.jetsetter.pro.core.data.local.toEntity
import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.di.ApplicationScope
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.TripQueries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for trips. Room is the offline-first store; the cloud seam
 * ([CloudBackend.trips]) provides cross-device sync when signed in.
 *
 * Sync model (see [initSync]): on sign-in we **merge** — adopt the cloud's trips into Room and
 * push any local trips the cloud doesn't have yet (covers a first device and any write that
 * didn't reach the server). The reconcile is intentionally non-destructive (no offline write
 * queue yet, so we never drop a local-only trip on launch). A Realtime listener then keeps Room
 * in sync, propagating remote edits *and deletions*. Local writes are mirrored up best-effort in
 * [upsert]/[delete], fired on [appScope] so the local write returns immediately.
 */
@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val backend: CloudBackend,
    private val moduleState: ModuleStateStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val syncStarted = AtomicBoolean(false)
    private var syncJob: Job? = null

    private companion object {
        // bump the _vN suffix whenever the Room DB version changes — a destructive migration wipes the table but not this DataStore flag
        const val SEEDED_KEY = "trips_seeded_v2"

        // Max ids per `DELETE … IN (:ids)` — keeps the expanded statement under SQLite's bound-variable limit.
        const val SYNC_DELETE_CHUNK = 500
    }

    fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Today's trip (date range contains today) or the next upcoming one — spec §2.5's "active
     * trip". Re-evaluates whenever Room's trip table changes; the clock is sampled per emission,
     * which is fine because any staleness heals on the next table change.
     */
    fun activeTrip(): Flow<Trip?> =
        observeTrips().map { trips -> TripQueries.activeTrip(trips, LocalDate.now()) }

    /** The next future flight across all trips (first itinerary title matching a flight ident). */
    fun nextFlight(): Flow<TripQueries.NextFlight?> =
        observeTrips().map { trips -> TripQueries.nextFlight(trips, Instant.now()) }

    /**
     * Seeds [MockData.trips] exactly once per install. Gated behind a persisted flag so that an
     * intentionally-emptied trip list stays empty across restarts instead of being re-seeded.
     */
    suspend fun seedIfEmpty() {
        if (moduleState.read(SEEDED_KEY) != null) return
        if (tripDao.count() == 0) {
            tripDao.upsertAll(MockData.trips.map { it.toEntity() })
        }
        moduleState.save(SEEDED_KEY, "true")
    }

    /**
     * Demo-mode reset: wipes the table and re-inserts the pristine [MockData.trips]. Writes go
     * straight to the DAO (not [upsert]) so seed rows are never mirrored up to Supabase, and the
     * seeded flag is re-armed so [seedIfEmpty] stays a no-op afterwards.
     */
    suspend fun resetToSeed() {
        tripDao.deleteAll()
        tripDao.upsertAll(MockData.trips.map { it.toEntity() })
        moduleState.save(SEEDED_KEY, "true")
    }

    suspend fun upsert(trip: Trip) {
        tripDao.upsert(trip.toEntity())
        val uid = backend.currentSession()?.uid ?: return
        appScope.launch { backend.trips.upsert(uid, trip) }
    }

    suspend fun delete(id: String) {
        tripDao.delete(id)
        val uid = backend.currentSession()?.uid ?: return
        appScope.launch { backend.trips.delete(uid, id) }
    }

    /**
     * Idempotent (runs once per process). Signs in anonymously, merges Room ⇄ cloud, then
     * starts the Realtime listener. Offline / sign-in failure falls back to local seed data so
     * the app stays usable ([CloudBackend.ensureSignedIn] returns null instead of throwing).
     */
    suspend fun initSync() {
        if (!syncStarted.compareAndSet(false, true)) return

        val uid = backend.ensureSignedIn()
        if (uid == null) {
            seedIfEmpty()
            return
        }

        // One-time, non-destructive merge: adopt remote, then push any local-only trips up.
        runCatching {
            seedIfEmpty()
            val remote = backend.trips.getOnce(uid)
            val remoteIds = remote.map { it.id }.toSet()
            if (remote.isNotEmpty()) tripDao.upsertAll(remote.map { it.toEntity() })
            tripDao.getAll()
                .filter { it.id !in remoteIds && it.id !in MockData.seedTripIds }
                .forEach { backend.trips.upsert(uid, it.toDomain()) }
        }.onFailure { seedIfEmpty() }

        startLiveSync(uid)
    }

    /**
     * Pulls remote changes into Room so other devices' edits *and deletions* show up locally.
     * Writes go straight to the DAO (not [upsert]), so they don't re-trigger the cloud
     * mirror — no sync loop. A server-confirmed empty result clears local trips; transient fetch
     * failures are filtered out upstream in [CloudBackend.trips]' observe (never emitted as empty).
     */
    private fun startLiveSync(uid: String) {
        if (syncJob != null) return
        syncJob = appScope.launch {
            backend.trips.observe(uid)
                .catch { /* snapshot error: keep local data; resync next launch */ }
                .collect { remote ->
                    if (remote.isEmpty()) {
                        tripDao.deleteAll()
                    } else {
                        tripDao.upsertAll(remote.map { it.toEntity() })
                        // Delete local rows the server no longer has. Computed in Kotlin and removed
                        // in bounded chunks — a single `WHERE id NOT IN (:ids)` over the whole remote
                        // set can exceed SQLite's bound-variable limit once a user has many rows.
                        val remoteIds = remote.mapTo(HashSet()) { it.id }
                        val stale = tripDao.getAllIds().filterNot { it in remoteIds }
                        stale.chunked(SYNC_DELETE_CHUNK).forEach { tripDao.deleteByIds(it) }
                    }
                }
        }
    }
}
