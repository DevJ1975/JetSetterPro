package com.jetsetter.pro.core.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [FirebaseAuth]. The app starts anonymous so cross-device sync works
 * without forcing sign-in; an anonymous account can later be upgraded to email/Google.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) {
    /** Current signed-in user id, or null. Re-emits on every auth state change. */
    val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Synchronous snapshot of the current uid (null if signed out). */
    fun currentUid(): String? = auth.currentUser?.uid

    /** Ensures a signed-in user exists (anonymous if needed). Returns the uid. Requires network on first run. */
    suspend fun ensureSignedIn(): String {
        auth.currentUser?.uid?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in returned no user")
    }
}
