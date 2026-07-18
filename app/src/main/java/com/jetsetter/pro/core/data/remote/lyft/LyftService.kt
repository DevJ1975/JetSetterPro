package com.jetsetter.pro.core.data.remote.lyft

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.apiCall
import com.jetsetter.pro.core.data.remote.map
import com.jetsetter.pro.core.secrets.Secrets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access to Lyft cost estimates (§2.3) behind the `isConfigured` doctrine: callers check
 * [isConfigured] and fall back to their mock data when the OAuth2 client pair is missing — the
 * service never throws for an unconfigured key. Bearer auth is resolved per call through
 * [LyftTokenProvider] (cached, mutex-serialized, early-refreshed); a 401 additionally drops the
 * cached token so the next call re-authenticates instead of replaying a revoked token.
 *
 * PRIVACY INVARIANT (plan R10f): cost requests carry only the four coordinates — never
 * preference or profile-derived fields.
 *
 * Consumer: `GroundtransportRepository` (live-over-rate-card merge).
 */
@Singleton
class LyftService @Inject constructor(
    private val api: LyftApi,
    private val tokenProvider: LyftTokenProvider,
) {
    /** True when a real Lyft client id + secret are present; false → callers use their mock fallback. */
    val isConfigured: Boolean
        get() = Secrets.isConfigured(Secrets.lyftClientId) && Secrets.isConfigured(Secrets.lyftClientSecret)

    /**
     * All Lyft ride tiers quoted for the start→end trip, in Lyft's order. Failures (including a
     * token-endpoint failure) arrive as the typed [ApiResult.Failure]; an unserviced area is a
     * success with an empty list.
     */
    suspend fun costEstimates(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): ApiResult<List<LyftCostEstimate>> {
        val result = apiCall {
            api.costEstimates(
                authorization = "Bearer ${tokenProvider.token()}",
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
            )
        }.map { it.costEstimates }
        if (result is ApiResult.Failure && result.error is ApiError.Unauthorized) {
            tokenProvider.invalidate()
        }
        return result
    }
}
