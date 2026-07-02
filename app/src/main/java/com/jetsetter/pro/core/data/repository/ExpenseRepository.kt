package com.jetsetter.pro.core.data.repository

import com.jetsetter.pro.core.auth.AuthRepository
import com.jetsetter.pro.core.data.local.ExpenseDao
import com.jetsetter.pro.core.data.local.toDomain
import com.jetsetter.pro.core.data.local.toEntity
import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.di.ApplicationScope
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.sync.SupabaseExpenseSync
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
 * Single source of truth for expenses — the expense analogue of [TripRepository]. Room is the
 * offline-first store; Supabase ([SupabaseExpenseSync]) provides cross-device sync when signed in.
 *
 * Sync model (see [initSync]): on sign-in we **merge** — adopt the cloud's expenses into Room and
 * push any local-only expenses up (non-destructive, no offline write queue yet). A Realtime
 * listener then propagates remote adds/edits *and deletions* into Room. Local writes mirror up
 * best-effort on [ApplicationScope] so the write returns immediately.
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val authRepository: AuthRepository,
    private val expenseSync: SupabaseExpenseSync,
    private val moduleState: ModuleStateStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val syncStarted = AtomicBoolean(false)
    private var syncJob: Job? = null

    private companion object {
        // bump the _vN suffix whenever the Room DB version changes — a destructive migration wipes the table but not this DataStore flag
        const val SEEDED_KEY = "expenses_seeded_v2"

        // Max ids per `DELETE … IN (:ids)` — keeps the expanded statement under SQLite's bound-variable limit.
        const val SYNC_DELETE_CHUNK = 500
    }

    fun observeExpenses(): Flow<List<Expense>> =
        expenseDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Seeds [MockData.expenses] exactly once per install. Gated behind a persisted flag so an
     * intentionally-emptied ledger stays empty across restarts instead of being re-seeded.
     */
    suspend fun seedIfEmpty() {
        if (moduleState.read(SEEDED_KEY) != null) return
        if (expenseDao.count() == 0) {
            expenseDao.upsertAll(MockData.expenses.map { it.toEntity() })
        }
        moduleState.save(SEEDED_KEY, "true")
    }

    /**
     * Demo-mode reset: wipes the ledger and re-inserts the pristine [MockData.expenses]. Writes go
     * straight to the DAO (not [add]) so seed rows are never mirrored up to Supabase, and the
     * seeded flag is re-armed so [seedIfEmpty] stays a no-op afterwards.
     */
    suspend fun resetToSeed() {
        expenseDao.deleteAll()
        expenseDao.upsertAll(MockData.expenses.map { it.toEntity() })
        moduleState.save(SEEDED_KEY, "true")
    }

    suspend fun add(expense: Expense) {
        expenseDao.upsert(expense.toEntity())
        val uid = authRepository.currentUid() ?: return
        appScope.launch { expenseSync.push(uid, expense) }
    }

    suspend fun delete(id: String) {
        expenseDao.delete(id)
        val uid = authRepository.currentUid() ?: return
        appScope.launch { expenseSync.delete(uid, id) }
    }

    /**
     * Idempotent (runs once per process). Signs in anonymously, merges Room ⇄ Supabase, then
     * starts the Realtime listener. Offline / sign-in failure falls back to local seed data.
     */
    suspend fun initSync() {
        if (!syncStarted.compareAndSet(false, true)) return

        val uid = runCatching { authRepository.ensureSignedIn() }.getOrNull()
        if (uid == null) {
            seedIfEmpty()
            return
        }

        // One-time, non-destructive merge: adopt remote, then push any local-only expenses up.
        runCatching {
            seedIfEmpty()
            val remote = expenseSync.getOnce(uid)
            val remoteIds = remote.map { it.id }.toSet()
            if (remote.isNotEmpty()) expenseDao.upsertAll(remote.map { it.toEntity() })
            expenseDao.getAll()
                .filter { it.id !in remoteIds && it.id !in MockData.seedExpenseIds }
                .forEach { expenseSync.push(uid, it.toDomain()) }
        }.onFailure { seedIfEmpty() }

        startLiveSync(uid)
    }

    /**
     * Pulls remote changes into Room so other devices' edits *and deletions* show up locally.
     * Writes go straight to the DAO (not [add]), so they don't re-trigger the Supabase mirror —
     * no sync loop. A server-confirmed empty result clears local expenses; transient fetch
     * failures are filtered out upstream in [SupabaseExpenseSync.observe] (never emitted as empty).
     */
    private fun startLiveSync(uid: String) {
        if (syncJob != null) return
        syncJob = appScope.launch {
            expenseSync.observe(uid)
                .catch { /* realtime error: keep local data; resync next launch */ }
                .collect { remote ->
                    if (remote.isEmpty()) {
                        expenseDao.deleteAll()
                    } else {
                        expenseDao.upsertAll(remote.map { it.toEntity() })
                        // Delete local rows the server no longer has. Computed in Kotlin and removed
                        // in bounded chunks — a single `WHERE id NOT IN (:ids)` over the whole remote
                        // set can exceed SQLite's bound-variable limit once a user has many rows.
                        val remoteIds = remote.mapTo(HashSet()) { it.id }
                        val stale = expenseDao.getAllIds().filterNot { it in remoteIds }
                        stale.chunked(SYNC_DELETE_CHUNK).forEach { expenseDao.deleteByIds(it) }
                    }
                }
        }
    }
}
