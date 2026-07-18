package com.jetsetter.pro.core.backend

import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import kotlinx.coroutines.flow.Flow

/**
 * One per-user cloud collection of documents with stable string ids (spec §2.4). This is the
 * backend-neutral seam of the dual-seam decision: call sites program against this contract, and a
 * different provider (e.g. Firebase) can be swapped in later without touching them. The only live
 * implementation is [SupabaseBackend].
 *
 * BEST-EFFORT SEMANTICS (the doctrine every implementation must follow — cloned from
 * [com.jetsetter.pro.core.sync.SupabaseTripSync]):
 *  - [upsert] / [delete] never throw. The local store already holds the write; a failed mirror
 *    (offline, auth failure, missing remote table) is silently dropped.
 *  - [getOnce] MAY throw on transient failure so callers can tell "cloud is empty" apart from
 *    "cloud unreachable" — callers wrap it in `runCatching` and must never destroy local state on
 *    failure. Implementations for optional collections may instead degrade to an empty list; both
 *    behaviors satisfy the contract because callers treat failure as "no data".
 *  - [observe] never throws and never emits a failure as an empty list (a transient blip must not
 *    wipe local data). When the backend is unconfigured it is an empty flow.
 */
interface CloudCollection<T> {

    /** Best-effort create-or-replace of [item] under its stable id. Never throws. */
    suspend fun upsert(uid: String, item: T)

    /** Best-effort delete of the document with [id]. Never throws. */
    suspend fun delete(uid: String, id: String)

    /**
     * One-shot read of the user's remote documents (the sign-in reconcile). May throw on
     * transient failure — wrap in `runCatching` and treat failure as "no data".
     */
    suspend fun getOnce(uid: String): List<T>

    /**
     * Live remote documents for [uid]. Emits the full owner-scoped set on every remote change so
     * collectors can mirror edits *and deletions* locally. Transient fetch failures are dropped,
     * never emitted as empty. Empty flow when unconfigured.
     */
    fun observe(uid: String): Flow<List<T>>
}

/**
 * The app's cloud backend: per-user collections plus the auth surface that scopes them
 * (spec §2.4). Anonymous-first — [ensureSignedIn] creates an anonymous user on first run so
 * cross-device sync works without a sign-up wall; the account can later be upgraded to
 * email/password via [linkEmailToAnonymous] (preserving the uid, so no data migrates).
 *
 * Everything here tolerates the "not configured" state (missing keys) and remote failure
 * (anonymous auth disabled, migration not applied): sync paths degrade to local-only, they never
 * crash. Only the explicit user-initiated auth methods ([signUpWithEmail] & co.) throw, so a
 * future settings UI can surface the error.
 */
interface CloudBackend {

    /** False when the backend's keys are missing — every cloud call is then a silent no-op. */
    val isConfigured: Boolean

    /** Trips, id = [Trip.id]. */
    val trips: CloudCollection<Trip>

    /** Expenses, id = [Expense.id]. */
    val expenses: CloudCollection<Expense>

    /** Travel-behavior signals (spec §1.6), id = [TravelSignalDoc.id]. */
    val travelSignals: CloudCollection<TravelSignalDoc>

    /** Travel-wallet passes, id = [TravelWalletItemDoc.id]. */
    val walletItems: CloudCollection<TravelWalletItemDoc>

    /**
     * Packing lists keyed by trip: doc id = [PackingListDoc.tripId]. The spec models these as
     * their own collection; physically they may live inside the trip document/row (Supabase
     * stores them in `trips.packing_list` jsonb — the live iOS contract), so upserting a packing
     * list for an unknown trip is a silent no-op: the trip is the parent, upsert it first.
     */
    val packingLists: CloudCollection<PackingListDoc>

    /** Flight-disruption events, id = [DisruptionEventDoc.id]. */
    val disruptionEvents: CloudCollection<DisruptionEventDoc>

    /** Current session, re-emitted on every auth state change; null when signed out/unconfigured. */
    val session: Flow<CloudSession?>

    /** Synchronous snapshot of [session] (null when signed out or unconfigured). */
    fun currentSession(): CloudSession?

    /**
     * Ensures a signed-in user exists (anonymous if needed) and returns the uid, or **null** when
     * the backend is unconfigured or sign-in fails (offline, anonymous auth disabled server-side).
     * Never throws — callers fall back to local-only data.
     */
    suspend fun ensureSignedIn(): String?

    /**
     * Creates a new email/password account. Throws on failure (invalid email, weak password,
     * network, unconfigured backend) so the caller can surface the error. With "Confirm email"
     * enabled on the project the session starts unconfirmed until the user clicks the link.
     */
    suspend fun signUpWithEmail(email: String, password: String)

    /** Signs into an existing email/password account. Throws on failure (see [signUpWithEmail]). */
    suspend fun signInWithEmail(email: String, password: String)

    /**
     * Upgrades the current anonymous user to email/password **in place**, preserving the uid so
     * all synced data stays attached. Throws on failure.
     *
     * Caveat: when the project's "Confirm email" setting is ON, the email is only attached after
     * the user clicks the confirmation link — until then the account still behaves as anonymous.
     */
    suspend fun linkEmailToAnonymous(email: String, password: String)

    /** Signs out locally. Best-effort — never throws. */
    suspend fun signOut()

    /**
     * Deletes the user's account: server-side first (clients cannot delete auth users, so the
     * backend's privileged deletion path — Supabase's `delete-account` edge function — must
     * succeed), THEN signs out and wipes local stores, returning the app to fresh-install
     * behavior. Never throws; returns a failure [Result] instead.
     *
     * Ordering is the contract: when the server-side deletion fails (function not deployed yet,
     * offline, no session) the whole operation STOPS with a failure and **nothing local is
     * wiped** — a half-deleted account must keep its local data so the user can retry. The UI
     * surfaces the failure (e.g. "couldn't reach the account-deletion service").
     */
    suspend fun deleteAccount(): Result<Unit>
}
