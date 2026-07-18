package com.jetsetter.pro.core.data.remote.expedia

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.secrets.Secrets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** A cached Expedia OAuth2 access token with its absolute expiry. */
data class ExpediaToken(
    val accessToken: String,
    val expiresAtMillis: Long,
)

/**
 * The pure, clock-free rules of the token cache (the ExpediaTokenLogicTest target):
 * payload→token mapping, the refresh-early freshness window, and the Basic-auth header.
 */
object ExpediaTokenLogic {

    /** Tokens are refreshed this long *before* their nominal expiry, per plan B7. */
    const val REFRESH_EARLY_MILLIS: Long = 60_000L

    /** True while [token] can still be used: strictly inside the refresh-early window. */
    fun isFresh(token: ExpediaToken?, nowMillis: Long): Boolean =
        token != null && nowMillis < token.expiresAtMillis - REFRESH_EARLY_MILLIS

    /**
     * Maps the wire payload to a cacheable token as of [nowMillis]; null when the payload is
     * unusable (blank/missing `access_token` or a missing/non-positive `expires_in`).
     */
    fun toToken(response: ExpediaTokenResponse, nowMillis: Long): ExpediaToken? {
        val access = response.accessToken?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val expiresInSeconds = response.expiresInSeconds?.takeIf { it > 0L } ?: return null
        return ExpediaToken(
            accessToken = access,
            expiresAtMillis = nowMillis + expiresInSeconds * 1000L,
        )
    }

    /** `Basic base64(clientId:clientSecret)` for the token endpoint. */
    fun basicAuth(clientId: String, clientSecret: String): String {
        val credentials = "$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(credentials)
    }
}

/**
 * Caches the Expedia Rapid OAuth2 client-credentials token. All reads go through one [Mutex], so
 * concurrent callers single-flight: the first fetches, the rest wait and reuse the cached token.
 * Tokens refresh [ExpediaTokenLogic.REFRESH_EARLY_MILLIS] (~60 s) before expiry so an in-flight
 * availability call never rides a token that dies mid-request. A failed or unusable fetch caches
 * nothing — the next call retries.
 *
 * The primary constructor is `internal` for tests (injected fetch + clock); production wiring
 * uses the `@Inject` secondary constructor over [ExpediaApi] + [Secrets].
 */
@Singleton
class ExpediaTokenProvider internal constructor(
    private val fetchToken: suspend () -> ExpediaTokenResponse,
    private val clock: () -> Long,
) {

    @Inject
    constructor(api: ExpediaApi) : this(
        fetchToken = {
            api.token(
                authorization = ExpediaTokenLogic.basicAuth(
                    clientId = Secrets.expediaClientId,
                    clientSecret = Secrets.expediaClientSecret,
                ),
                grantType = "client_credentials",
            )
        },
        clock = System::currentTimeMillis,
    )

    private val mutex = Mutex()
    private var cached: ExpediaToken? = null

    /**
     * A currently-fresh access token, fetching (and caching) a new one when needed.
     * Throws [ApiError.DecodingFailed] on an unusable token payload and propagates transport
     * failures — callers run inside `apiCall {}`, which maps both onto the typed `ApiResult`.
     */
    suspend fun accessToken(): String = mutex.withLock {
        val fresh = cached?.takeIf { ExpediaTokenLogic.isFresh(it, nowMillis = clock()) }
        if (fresh != null) return@withLock fresh.accessToken

        val fetched = ExpediaTokenLogic.toToken(fetchToken(), nowMillis = clock())
            ?: throw ApiError.DecodingFailed(
                IllegalStateException("Expedia token payload missing access_token/expires_in"),
            )
        cached = fetched
        fetched.accessToken
    }
}
