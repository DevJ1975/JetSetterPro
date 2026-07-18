package com.jetsetter.pro.core.data.remote.lyft

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.secrets.Secrets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OAuth2 client-credentials token cache for the Lyft Public API (§2.3) — the same shape as the
 * Expedia Rapid provider: one token fetched with HTTP Basic (`lyftClientId:lyftClientSecret`,
 * body `grant_type=client_credentials&scope=public`), cached until shortly before expiry, and
 * all refreshes serialized behind a [Mutex] so concurrent callers never race N token requests.
 *
 * Early refresh: the cached token is considered dead [EARLY_REFRESH_SECONDS] before Lyft's
 * `expires_in`, so a request never departs with a token that expires mid-flight. A 401 on the
 * cost call means Lyft revoked the token early — [invalidate] drops the cache so the next call
 * re-authenticates.
 *
 * Failures are thrown ([ApiError] or the raw transport error) and mapped by the caller's
 * `apiCall {}` wrapper — this class never returns a stale or blank token.
 */
@Singleton
class LyftTokenProvider @Inject constructor(
    private val authApi: LyftAuthApi,
) {
    private data class CachedToken(val token: String, val expiresAtMillis: Long)

    private val mutex = Mutex()
    private var cached: CachedToken? = null

    /**
     * A currently-valid bearer token (the raw value, without the "Bearer " prefix), fetching a
     * fresh one when none is cached or the cached one is inside the early-refresh window.
     */
    suspend fun token(nowMillis: Long = System.currentTimeMillis()): String = mutex.withLock {
        cached?.takeIf { nowMillis < it.expiresAtMillis - EARLY_REFRESH_SECONDS * 1000 }
            ?.let { return it.token }

        val response = authApi.token(
            authorization = Credentials.basic(Secrets.lyftClientId, Secrets.lyftClientSecret),
            grantType = "client_credentials",
            scope = "public",
        )
        val token = response.accessToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiError.DecodingFailed(IllegalStateException("Lyft token response missing access_token"))
        val ttlSeconds = (response.expiresInSeconds ?: DEFAULT_TTL_SECONDS).coerceAtLeast(MIN_TTL_SECONDS)
        cached = CachedToken(token = token, expiresAtMillis = nowMillis + ttlSeconds * 1000)
        token
    }

    /** Drops the cached token (called after a 401 on the cost endpoint) so the next call re-auths. */
    suspend fun invalidate() = mutex.withLock { cached = null }

    private companion object {
        /** Refresh this many seconds before Lyft's stated expiry. */
        const val EARLY_REFRESH_SECONDS = 60L

        /** Assumed lifetime when Lyft omits `expires_in` (it documents 86 400 s tokens). */
        const val DEFAULT_TTL_SECONDS = 3600L

        /** Floor so a hostile/broken `expires_in` (0, negative) can't force a fetch-per-call loop. */
        const val MIN_TTL_SECONDS = EARLY_REFRESH_SECONDS + 60L
    }
}
