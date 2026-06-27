package com.jetsetter.pro.core.data.repository

import com.jetsetter.pro.core.auth.AuthRepository
import com.jetsetter.pro.core.data.local.TripDao
import com.jetsetter.pro.core.data.local.toDomain
import com.jetsetter.pro.core.data.local.toEntity
import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.di.ApplicationScope
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.sync.TripSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for trips. Room is the offline-first store; Cloud Firestore
 * ([TripSyncRepository]) provides cross-device sync when signed in.
 *
 * Sync model (see [initSync]): on first sign-in, if the cloud already has trips the **cloud is
 * authoritative** (mirror down + drop local trips no longer present); if the cloud is empty,
 * the device **seeds the cloud** from its local trips. A live listener then keeps Room in sync,
 * propagating remote edits *and deletions*. Local writes are mirrored up in [upsert]/[delete].
 */
@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val authRepository: AuthRepository,
    private val tripSync: TripSyncRepository,
    private val moduleState: ModuleStateStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val syncStarted = AtomicBoolean(false)
    private var syncJob: Job? = null

    private companion object {
        const val SEEDED_KEY = "trips_seeded"
    }

    fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeAll().map { entities -> entities.map { it.toDomain() } }

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

    suspend fun upsert(trip: Trip) {
        tripDao.upsert(trip.toEntity())
        authRepository.currentUid()?.let { uid -> tripSync.push(uid, trip) }
    }

    suspend fun delete(id: String) {
        tripDao.delete(id)
        authRepository.currentUid()?.let { uid -> tripSync.delete(uid, id) }
    }

    /**
     * Idempotent (runs once per process). Signs in anonymously, reconciles Room ⇄ Firestore,
     * then starts the live listener. Offline / sign-in failure falls back to local seed data so
     * the app stays usable.
     */
    suspend fun initSync() {
        if (!syncStarted.compareAndSet(false, true)) return

        val uid = runCatching { authRepository.ensureSignedIn() }.getOrNull()
        if (uid == null) {
            seedIfEmpty()
            return
        }

        // One-time reconcile.
        runCatching {
            val remote = tripSync.getOnce(uid)
            if (remote.isNotEmpty()) {
                // Cloud is authoritative: adopt it, dropping local trips it no longer has.
                tripDao.upsertAll(remote.map { it.toEntity() })
                tripDao.deleteNotIn(remote.map { it.id })
            } else {
                // First device for this account: seed locally, then push the seed to the cloud.
                seedIfEmpty()
                tripDao.getAll().forEach { tripSync.push(uid, it.toDomain()) }
            }
        }.onFailure { seedIfEmpty() }

        startLiveSync(uid)
    }

    /**
     * Pulls remote changes into Room so other devices' edits *and deletions* show up locally.
     * Writes go straight to the DAO (not [upsert]), so they don't re-trigger the Firestore
     * mirror — no sync loop. A server-confirmed empty result clears local trips; cache-only
     * empties are filtered out upstream in [TripSyncRepository.observe].
     */
    private fun startLiveSync(uid: String) {
        if (syncJob != null) return
        syncJob = appScope.launch {
            tripSync.observe(uid)
                .catch { /* snapshot error: keep local data; resync next launch */ }
                .collect { remote ->
                    if (remote.isEmpty()) {
                        tripDao.deleteAll()
                    } else {
                        tripDao.upsertAll(remote.map { it.toEntity() })
                        tripDao.deleteNotIn(remote.map { it.id })
                    }
                }
        }
    }
}
