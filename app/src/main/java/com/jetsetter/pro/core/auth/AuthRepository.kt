package com.jetsetter.pro.core.auth

import com.jetsetter.pro.core.backend.CloudBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade over the cloud seam's auth surface, kept for call-site stability — the actual
 * implementation (anonymous-first sign-in, the sign-in mutex, email upgrade, sign-out, account
 * deletion) lives behind [CloudBackend] (see
 * [com.jetsetter.pro.core.backend.SupabaseBackend]). New code should inject [CloudBackend]
 * directly; this class only preserves the original `uid`/`currentUid`/`ensureSignedIn` contract.
 *
 * The uid is the backend user id that row-level security keys off, so every cloud read/write is
 * scoped to this user.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val backend: CloudBackend,
) {
    /** Current signed-in user id, or null. Re-emits on every auth state change. */
    val uid: Flow<String?> = backend.session.map { it?.uid }

    /** Synchronous snapshot of the current uid (null if signed out or not configured). */
    fun currentUid(): String? = backend.currentSession()?.uid

    /**
     * Ensures a signed-in user exists (anonymous if needed) and returns the uid. Throws when the
     * backend is unconfigured or sign-in fails — the original contract, preserved for existing
     * callers who wrap it in `runCatching`. Prefer [CloudBackend.ensureSignedIn], which returns
     * null instead.
     */
    suspend fun ensureSignedIn(): String =
        backend.ensureSignedIn() ?: error("Cloud backend not configured or sign-in failed")
}
