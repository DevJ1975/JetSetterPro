package com.jetsetter.pro.core.backend

import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CloudCollection] for tests: per-uid maps of documents keyed by [idOf], preserving
 * insertion order. Honors the seam's contract — upsert/delete never throw, [observe] re-emits the
 * full owner-scoped set on every change, ids are stable (re-upserting an id replaces in place).
 */
class FakeCloudCollection<T>(private val idOf: (T) -> String) : CloudCollection<T> {

    private val state = MutableStateFlow<Map<String, Map<String, T>>>(emptyMap())

    override suspend fun upsert(uid: String, item: T) {
        state.update { all -> all + (uid to ((all[uid] ?: emptyMap()) + (idOf(item) to item))) }
    }

    override suspend fun delete(uid: String, id: String) {
        state.update { all ->
            val docs = all[uid] ?: return@update all
            all + (uid to (docs - id))
        }
    }

    override suspend fun getOnce(uid: String): List<T> =
        state.value[uid]?.values?.toList() ?: emptyList()

    override fun observe(uid: String): Flow<List<T>> =
        state.map { it[uid]?.values?.toList() ?: emptyList() }.distinctUntilChanged()

    /** Test hook: drops every document belonging to [uid] (account deletion). */
    fun clear(uid: String) {
        state.update { it - uid }
    }
}

/**
 * Pure in-memory [CloudBackend] for unit tests — no network, no Android. Auth is simulated: a
 * single [fakeUid] account that [ensureSignedIn] creates anonymously and the email methods
 * upgrade, mirroring the live backend's anonymous-first flow. Construct with
 * `configured = false` to exercise the unconfigured degradation paths.
 */
class FakeCloudBackend(
    private val configured: Boolean = true,
    private val fakeUid: String = "fake-uid",
) : CloudBackend {

    override val isConfigured: Boolean get() = configured

    override val trips = FakeCloudCollection<Trip> { it.id }
    override val expenses = FakeCloudCollection<Expense> { it.id }
    override val travelSignals = FakeCloudCollection<TravelSignalDoc> { it.id }
    override val walletItems = FakeCloudCollection<TravelWalletItemDoc> { it.id }
    override val packingLists = FakeCloudCollection<PackingListDoc> { it.tripId }
    override val disruptionEvents = FakeCloudCollection<DisruptionEventDoc> { it.id }

    private val sessionState = MutableStateFlow<CloudSession?>(null)

    override val session: Flow<CloudSession?> = sessionState

    override fun currentSession(): CloudSession? = sessionState.value

    override suspend fun ensureSignedIn(): String? {
        if (!configured) return null
        val existing = sessionState.value
        if (existing != null) return existing.uid
        val anon = CloudSession(uid = fakeUid, email = null, isAnonymous = true)
        sessionState.value = anon
        return anon.uid
    }

    override suspend fun signUpWithEmail(email: String, password: String) {
        check(configured) { "Cloud backend not configured" }
        sessionState.value = CloudSession(uid = fakeUid, email = email, isAnonymous = false)
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        check(configured) { "Cloud backend not configured" }
        sessionState.value = CloudSession(uid = fakeUid, email = email, isAnonymous = false)
    }

    override suspend fun linkEmailToAnonymous(email: String, password: String) {
        check(configured) { "Cloud backend not configured" }
        val current = checkNotNull(sessionState.value) { "No session to link" }
        sessionState.value = current.copy(email = email, isAnonymous = false)
    }

    override suspend fun signOut() {
        sessionState.value = null
    }

    /**
     * Mirrors the live ordering contract: the "server-side" deletion must be possible first
     * (configured + signed in), otherwise the call fails WITHOUT wiping anything — exactly how
     * [SupabaseBackend] stops when the `delete-account` edge function is unreachable.
     */
    override suspend fun deleteAccount(): Result<Unit> {
        if (!configured) {
            return Result.failure(IllegalStateException("Cloud backend is not configured"))
        }
        val uid = sessionState.value?.uid
            ?: return Result.failure(IllegalStateException("No signed-in session to delete"))
        trips.clear(uid)
        expenses.clear(uid)
        travelSignals.clear(uid)
        walletItems.clear(uid)
        packingLists.clear(uid)
        disruptionEvents.clear(uid)
        signOut()
        return Result.success(Unit)
    }
}
