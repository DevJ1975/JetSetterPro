package com.jetsetter.pro.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * [SessionManager] that persists the Supabase auth session (access + refresh JWTs) in
 * [EncryptedSharedPreferences] instead of supabase-kt's default plaintext store, so tokens are
 * encrypted at rest with an Android Keystore master key (plan B5). Installed via
 * `install(Auth) { sessionManager = ... }` in [com.jetsetter.pro.core.di.SupabaseModule].
 *
 * Doctrine notes:
 *  - Best-effort like every cloud seam: nothing here ever throws. If the Keystore/encrypted file
 *    is unusable (rare corruption, backup-restore across devices), the store degrades to
 *    process-memory only — the user just re-authenticates (anonymous sign-in recreates itself).
 *  - `androidx.security:security-crypto` is deprecated upstream (1.1.0 was its final release).
 *    Acceptable: the dependency is confined to this one class, so a future swap (e.g. to a
 *    Keystore+DataStore implementation) touches no other file.
 *  - One-time adoption: the very first [loadSession] also looks for a session left behind by the
 *    default `SettingsSessionManager` (plaintext `<package>_preferences`), migrates it into the
 *    encrypted store, and clears the plaintext copy — so upgrading users keep their uid.
 */
class EncryptedSessionManager(
    private val context: Context,
    supabaseUrl: String,
) : SessionManager {

    /**
     * Storage key, derived from the project URL exactly like supabase-kt's
     * `createDefaultSettingsKey(url) + "-session"` — same key in both stores keeps the adoption
     * mapping 1:1 and avoids collisions if a second Supabase project is ever added.
     */
    private val sessionKey: String =
        "sb-" + supabaseUrl.removeSuffix("/").replace('/', '-').replace('.', '-') + "-session"

    private val json = Json { ignoreUnknownKeys = true }

    /** Fallback when the encrypted store can't be created: session lives for this process only. */
    private var inMemorySession: UserSession? = null

    /**
     * Lazily-created encrypted prefs. Creation touches disk + the Android Keystore, so it only
     * happens inside the suspend members (on [Dispatchers.IO]), never at construction/DI time.
     * Null when creation failed — the in-memory fallback takes over.
     */
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            // security-crypto is deprecated upstream; this class deliberately owns the seam.
            @Suppress("DEPRECATION")
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            @Suppress("DEPRECATION")
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
    }

    override suspend fun saveSession(session: UserSession): Unit = withContext(Dispatchers.IO) {
        inMemorySession = session
        val store = prefs ?: return@withContext
        runCatching {
            store.edit().putString(sessionKey, json.encodeToString(UserSession.serializer(), session)).apply()
        }
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        val store = prefs ?: return@withContext inMemorySession
        val stored = runCatching { store.getString(sessionKey, null) }.getOrNull()
            ?.let { raw -> runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull() }
        stored ?: adoptLegacySession()
    }

    override suspend fun deleteSession(): Unit = withContext(Dispatchers.IO) {
        inMemorySession = null
        val store = prefs ?: return@withContext
        runCatching { store.edit().remove(sessionKey).apply() }
    }

    /**
     * One-time adoption of a session persisted by the previous default session manager.
     * supabase-kt's `SettingsSessionManager` writes the session JSON into the app's default
     * SharedPreferences file (`<package>_preferences`, via multiplatform-settings' no-arg
     * `Settings()`) under the same derived key — older library versions used the plain key
     * `"session"`. Both locations are checked; a decodable hit is migrated into the encrypted
     * store and the plaintext copy removed. Anything not trivially readable (absent, corrupt,
     * incompatible schema) is skipped — the user simply re-authenticates.
     */
    private suspend fun adoptLegacySession(): UserSession? {
        val legacy = runCatching {
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        }.getOrNull() ?: return null
        for (key in listOf(sessionKey, LEGACY_SETTINGS_KEY)) {
            val raw = runCatching { legacy.getString(key, null) }.getOrNull() ?: continue
            val session = runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
            if (session != null) {
                saveSession(session)
                runCatching { legacy.edit().remove(key).apply() }
                return session
            }
        }
        return null
    }

    private companion object {
        /** Dedicated encrypted prefs file — never shared with plaintext settings. */
        const val FILE_NAME = "supabase_secure_session"

        /** `SettingsSessionManager.SETTINGS_KEY` — the storage key of older supabase-kt versions. */
        const val LEGACY_SETTINGS_KEY = "session"
    }
}
