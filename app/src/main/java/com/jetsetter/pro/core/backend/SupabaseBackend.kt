package com.jetsetter.pro.core.backend

import com.jetsetter.pro.core.data.local.ExpenseDao
import com.jetsetter.pro.core.data.local.TripDao
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.sync.SupabaseDisruptionEventSync
import com.jetsetter.pro.core.sync.SupabaseExpenseSync
import com.jetsetter.pro.core.sync.SupabaseTripSync
import com.jetsetter.pro.core.sync.SupabaseTravelSignalSync
import com.jetsetter.pro.core.sync.SupabaseWalletItemSync
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only live [CloudBackend] — Supabase Postgres + Auth + Realtime, the same project the iOS
 * app talks to (RLS owner-scopes every row on `user_id = auth.uid()`).
 *
 * Collection wiring:
 *  - [trips] / [expenses] delegate to the existing [SupabaseTripSync] / [SupabaseExpenseSync]
 *    untouched — their offline-first, merge, and realtime behavior is the proven contract.
 *  - [packingLists] is a *view* over `trips.packing_list` jsonb (doc id = tripId, the live iOS
 *    contract): upsert rewrites that one column via a targeted update, reads derive from trips.
 *  - [travelSignals] / [walletItems] / [disruptionEvents] delegate to the dedicated
 *    [SupabaseTravelSignalSync] / [SupabaseWalletItemSync] / [SupabaseDisruptionEventSync]
 *    classes over the `travel_signals` / `wallet_items` / `disruption_events` tables from
 *    migration 0002. Until that migration is applied remotely every call is silently absorbed by
 *    their best-effort guards (missing table → no-op / empty), so shipping the client ahead of
 *    the schema is safe.
 *
 * Auth is anonymous-first (see [ensureSignedIn]); the sign-in mutex lives here so exactly one
 * anonymous user is created no matter how many repositories race on launch. [client] is nullable:
 * when Supabase is unconfigured every method no-ops (or returns null/empty) and the app runs
 * purely on local data.
 */
@Singleton
class SupabaseBackend @Inject constructor(
    private val client: SupabaseClient?,
    tripSync: SupabaseTripSync,
    expenseSync: SupabaseExpenseSync,
    travelSignalSync: SupabaseTravelSignalSync,
    walletItemSync: SupabaseWalletItemSync,
    disruptionEventSync: SupabaseDisruptionEventSync,
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao,
    private val moduleState: ModuleStateStore,
) : CloudBackend {

    override val isConfigured: Boolean get() = client != null

    // ── Collections ──────────────────────────────────────────────────────────

    override val trips: CloudCollection<Trip> = object : CloudCollection<Trip> {
        override suspend fun upsert(uid: String, item: Trip) = tripSync.push(uid, item)
        override suspend fun delete(uid: String, id: String) = tripSync.delete(uid, id)
        override suspend fun getOnce(uid: String): List<Trip> = tripSync.getOnce(uid)
        override fun observe(uid: String): Flow<List<Trip>> = tripSync.observe(uid)
    }

    override val expenses: CloudCollection<Expense> = object : CloudCollection<Expense> {
        override suspend fun upsert(uid: String, item: Expense) = expenseSync.push(uid, item)
        override suspend fun delete(uid: String, id: String) = expenseSync.delete(uid, id)
        override suspend fun getOnce(uid: String): List<Expense> = expenseSync.getOnce(uid)
        override fun observe(uid: String): Flow<List<Expense>> = expenseSync.observe(uid)
    }

    /**
     * Packing lists as a logical collection over `trips.packing_list` jsonb. Upsert rewrites only
     * that column on the owner's trip row (no-op when the trip doesn't exist remotely — the trip
     * is the parent document); delete resets the column to `[]` (it never deletes the trip).
     * Reads derive from the trips sync, so item shape stays byte-identical to what
     * [SupabaseTripSync] writes (camelCase `isPacked` — the iOS-shared jsonb encoding).
     */
    override val packingLists: CloudCollection<PackingListDoc> = object : CloudCollection<PackingListDoc> {
        override suspend fun upsert(uid: String, item: PackingListDoc) {
            setPackingColumn(uid, item.tripId, item.items.map { PackingItemRow(it.id, it.name, it.isPacked) })
        }

        override suspend fun delete(uid: String, id: String) {
            setPackingColumn(uid, tripId = id, rows = emptyList())
        }

        override suspend fun getOnce(uid: String): List<PackingListDoc> =
            tripSync.getOnce(uid).map { PackingListDoc(it.id, it.packingList) }

        override fun observe(uid: String): Flow<List<PackingListDoc>> =
            tripSync.observe(uid).map { trips -> trips.map { PackingListDoc(it.id, it.packingList) } }
    }

    /** Best-effort targeted update of one trip's `packing_list` column. */
    private suspend fun setPackingColumn(uid: String, tripId: String, rows: List<PackingItemRow>) {
        val c = client ?: return
        runCatching {
            c.from("trips").update({ set("packing_list", rows) }) {
                filter {
                    eq("id", tripId)
                    eq("user_id", uid)
                }
            }
        }
    }

    override val travelSignals: CloudCollection<TravelSignalDoc> = object : CloudCollection<TravelSignalDoc> {
        override suspend fun upsert(uid: String, item: TravelSignalDoc) = travelSignalSync.push(uid, item)
        override suspend fun delete(uid: String, id: String) = travelSignalSync.delete(uid, id)
        override suspend fun getOnce(uid: String): List<TravelSignalDoc> = travelSignalSync.getOnce(uid)
        override fun observe(uid: String): Flow<List<TravelSignalDoc>> = travelSignalSync.observe(uid)
    }

    override val walletItems: CloudCollection<TravelWalletItemDoc> = object : CloudCollection<TravelWalletItemDoc> {
        override suspend fun upsert(uid: String, item: TravelWalletItemDoc) = walletItemSync.push(uid, item)
        override suspend fun delete(uid: String, id: String) = walletItemSync.delete(uid, id)
        override suspend fun getOnce(uid: String): List<TravelWalletItemDoc> = walletItemSync.getOnce(uid)
        override fun observe(uid: String): Flow<List<TravelWalletItemDoc>> = walletItemSync.observe(uid)
    }

    override val disruptionEvents: CloudCollection<DisruptionEventDoc> = object : CloudCollection<DisruptionEventDoc> {
        override suspend fun upsert(uid: String, item: DisruptionEventDoc) = disruptionEventSync.push(uid, item)
        override suspend fun delete(uid: String, id: String) = disruptionEventSync.delete(uid, id)
        override suspend fun getOnce(uid: String): List<DisruptionEventDoc> = disruptionEventSync.getOnce(uid)
        override fun observe(uid: String): Flow<List<DisruptionEventDoc>> = disruptionEventSync.observe(uid)
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    override val session: Flow<CloudSession?> =
        client?.auth?.sessionStatus?.map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.toCloudSession()
        } ?: flowOf(null)

    override fun currentSession(): CloudSession? =
        client?.auth?.currentUserOrNull()?.toCloudSession()

    /** Serializes the anonymous sign-in so concurrent callers don't each create a separate user. */
    private val signInMutex = Mutex()

    /**
     * Ensures a signed-in user exists (anonymous if needed) and returns the uid, or null when
     * unconfigured or when sign-in fails (offline, anonymous sign-ins disabled server-side —
     * currently the live project's state). Waits for the persisted session to load first so a
     * returning user keeps the same uid across launches.
     *
     * Multiple repositories call this concurrently on launch (trips + expenses sync). The mutex +
     * double-check ensures exactly one `signInAnonymously` runs, so a fresh install gets ONE
     * anonymous uid instead of one per caller.
     */
    override suspend fun ensureSignedIn(): String? {
        val c = client ?: return null
        return runCatching {
            c.auth.awaitInitialization()
            c.auth.currentUserOrNull()?.id ?: signInMutex.withLock {
                c.auth.currentUserOrNull()?.id ?: run {
                    c.auth.signInAnonymously()
                    c.auth.currentUserOrNull()?.id
                }
            }
        }.getOrNull()
    }

    override suspend fun signUpWithEmail(email: String, password: String) {
        val c = requireClient()
        c.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        val c = requireClient()
        c.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Upgrades the current anonymous user in place via `auth.updateUser` (supabase-kt's
     * `linkIdentity` is OAuth-only). The uid — and therefore every RLS-scoped row — is preserved.
     *
     * Caveat: with the project's "Confirm email" setting ON, Supabase sends a confirmation link
     * and only attaches the email once it's clicked; until then [CloudSession.isAnonymous] stays
     * true. With it OFF the upgrade is immediate.
     */
    override suspend fun linkEmailToAnonymous(email: String, password: String) {
        val c = requireClient()
        c.auth.updateUser {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        val c = client ?: return
        runCatching { c.auth.signOut() }
    }

    /**
     * Full account deletion. Clients cannot delete their own Supabase auth user, so step 1
     * invokes the `delete-account` edge function (`supabase/functions/delete-account/index.ts`:
     * validates our JWT, admin-deletes the auth user; the 0001/0002 FK `on delete cascade`
     * chains wipe every owned row). Only after that succeeds does step 2 tear down locally —
     * sign out + wipe Room trips/expenses + clear the module-state DataStore — returning the app
     * to fresh-install behavior.
     *
     * If the invoke fails (function NOT DEPLOYED yet — the current live state — offline, or no
     * session) we STOP and return the failure without touching local data: wiping locally around
     * a half-deleted cloud account would strand the user's rows with no way to retry.
     */
    override suspend fun deleteAccount(): Result<Unit> {
        val c = client
            ?: return Result.failure(IllegalStateException("Cloud backend is not configured"))
        runCatching { c.auth.awaitInitialization() }
        if (c.auth.currentSessionOrNull() == null) {
            return Result.failure(IllegalStateException("No signed-in session to delete"))
        }
        // Step 1 — server-side deletion. functions-kt attaches the current session's JWT as the
        // Authorization bearer automatically; any non-2xx (404 while undeployed) throws here.
        runCatching { c.functions.invoke(DELETE_ACCOUNT_FUNCTION) }
            .onFailure { return Result.failure(it) }
        // Step 2 — local teardown. The auth user is gone; every guard below is best-effort
        // (the account is already deleted, so local cleanup must not fail the operation).
        signOut()
        runCatching { c.auth.clearSession() }
        runCatching {
            tripDao.deleteAll()
            expenseDao.deleteAll()
            moduleState.clearAll()
        }
        return Result.success(Unit)
    }

    private fun requireClient(): SupabaseClient =
        client ?: error("Supabase is not configured (missing SUPABASE_URL / SUPABASE_ANON_KEY)")

    private companion object {
        /** Edge-function slug — `supabase/functions/delete-account/` (deployed separately). */
        const val DELETE_ACCOUNT_FUNCTION = "delete-account"
    }
}

private fun UserInfo.toCloudSession() = CloudSession(
    uid = id,
    email = email,
    // Anonymous users are the only accounts without an email (see CloudSession KDoc).
    isAnonymous = email.isNullOrEmpty(),
)

// ── Wire DTOs ────────────────────────────────────────────────────────────────
// The table-backed collections' DTOs live with their sync classes (core/sync). Only the packing
// jsonb element shape remains here, because packingLists is a view over trips.packing_list.

/** Element shape of `trips.packing_list` jsonb — camelCase, matching [SupabaseTripSync]'s DTO. */
@Serializable
private data class PackingItemRow(
    val id: String,
    val name: String,
    val isPacked: Boolean = false,
)
