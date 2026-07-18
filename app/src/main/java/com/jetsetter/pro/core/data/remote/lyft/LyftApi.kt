package com.jetsetter.pro.core.data.remote.lyft

import com.squareup.moshi.Json
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Lyft Public API (§2.3) — two surfaces on the same host:
 *
 *  - [LyftAuthApi]: OAuth2 client-credentials token endpoint (`POST /oauth/token`, HTTP Basic
 *    `lyftClientId:lyftClientSecret`, body `grant_type=client_credentials&scope=public`) —
 *    called only by [LyftTokenProvider], which caches the bearer token.
 *  - [LyftApi]: cost estimates (`GET /v1/cost`), authorized per call with the cached
 *    `Bearer <token>` passed as a header parameter (no interceptor — token fetch is a suspend
 *    call, so the service resolves it before each request).
 *
 * Wire naming is per-field `@Json(name = "snake_case")` (the §2.1 convention — no global
 * naming strategy).
 *
 * PRIVACY INVARIANT (plan R10f): cost requests carry only bare coordinates — never preference
 * or profile-derived fields.
 */
interface LyftApi {
    @GET("v1/cost")
    suspend fun costEstimates(
        @Header("Authorization") authorization: String,
        @Query("start_lat") startLat: Double,
        @Query("start_lng") startLng: Double,
        @Query("end_lat") endLat: Double,
        @Query("end_lng") endLng: Double,
    ): LyftCostResponse
}

/** The OAuth2 token endpoint — see [LyftTokenProvider] for caching/refresh policy. */
interface LyftAuthApi {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun token(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
        @Field("scope") scope: String,
    ): LyftTokenResponse
}

data class LyftTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    /** Token lifetime in seconds (Lyft issues ~86 400 s tokens). */
    @Json(name = "expires_in") val expiresInSeconds: Long? = null,
    val scope: String? = null,
)

data class LyftCostResponse(
    @Json(name = "cost_estimates") val costEstimates: List<LyftCostEstimate> = emptyList(),
)

/**
 * One Lyft ride tier's quoted cost. Every field is nullable — Lyft omits the cents bounds when
 * a tier can't be quoted. Costs are integer CENTS (Lyft's unit — mapping code converts to
 * dollars); [primetimePercentage] is a display string like "25%" (0% == no surge).
 */
data class LyftCostEstimate(
    @Json(name = "ride_type") val rideType: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val currency: String? = null,
    @Json(name = "estimated_cost_cents_min") val estimatedCostCentsMin: Long? = null,
    @Json(name = "estimated_cost_cents_max") val estimatedCostCentsMax: Long? = null,
    @Json(name = "estimated_duration_seconds") val estimatedDurationSeconds: Long? = null,
    @Json(name = "estimated_distance_miles") val estimatedDistanceMiles: Double? = null,
    @Json(name = "primetime_percentage") val primetimePercentage: String? = null,
)
