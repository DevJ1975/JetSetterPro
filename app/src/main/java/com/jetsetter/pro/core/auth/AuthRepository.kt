package com.jetsetter.pro.core.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over Supabase Auth. The app starts **anonymous** so cross-device sync works
 * without forcing sign-in; the anonymous account can later be upgraded to email/Google (which
 * links rather than forks, preserving the uid). The uid here is the Supabase user id that
 * Row Level Security keys off (`auth.uid()`), so every read/write is scoped to this user.
 *
 * [client] is nullable: when SUPABASE_URL / SUPABASE_ANON_KEY are unset the provider returns
 * null (see [com.jetsetter.pro.core.di.SupabaseModule]). In that "not configured" state
 * [ensureSignedIn] throws and callers fall back to local-only data — the same doctrine the
 * rest of the app follows.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val client: SupabaseClient?,
) {
    /** Current signed-in user id, or null. Re-emits on every auth state change. */
    val uid: Flow<String?> =
        client?.auth?.sessionStatus?.map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.id
        } ?: flowOf(null)

    /** Synchronous snapshot of the current uid (null if signed out or not configured). */
    fun currentUid(): String? = client?.auth?.currentUserOrNull()?.id

    /** Serializes the anonymous sign-in so concurrent callers don't each create a separate user. */
    private val signInMutex = Mutex()

    /**
     * Ensures a signed-in user exists (anonymous if needed) and returns the uid. Waits for the
     * persisted session to load first so a returning user keeps the same uid across launches.
     * Requires network on the very first run. Throws when Supabase is not configured.
     *
     * Multiple repositories call this concurrently on launch (trips + expenses sync). The mutex +
     * double-check ensures exactly one `signInAnonymously` runs, so a fresh install gets ONE
     * anonymous uid instead of one per caller.
     */
    suspend fun ensureSignedIn(): String {
        val c = client ?: error("Supabase not configured")
        c.auth.awaitInitialization()
        c.auth.currentUserOrNull()?.id?.let { return it }
        return signInMutex.withLock {
            c.auth.currentUserOrNull()?.id ?: run {
                c.auth.signInAnonymously()
                c.auth.currentUserOrNull()?.id ?: error("Anonymous sign-in returned no user")
            }
        }
    }
}