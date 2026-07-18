package com.jetsetter.pro.core.data.remote.fx

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Frankfurter — keyless foreign-exchange rates with ECB provenance (spec §2.3 "live FX";
 * plan B7). No API key, so there is no `isConfigured` gate; the request carries only currency
 * codes (privacy contract R10f — nothing preference- or profile-derived ever goes out).
 *
 * Host choice: `https://api.frankfurter.dev/v1/` is the canonical maintained host (the project
 * moved from frankfurter.app to frankfurter.dev; the old `api.frankfurter.app` still resolves as
 * a legacy alias). Recorded as the proposed shared iOS contract per plan R10(i).
 *
 * The Retrofit instance is built in `NetworkModule` off `@Named("baseHttp")`, so the §2.1 policy
 * applies: 30 s timeouts + GET-only retry with backoff.
 */
interface FrankfurterApi {

    /**
     * Latest reference rates. Both parameters are optional on the wire — Retrofit omits null
     * queries — and the API defaults to base EUR, all symbols. The app always passes its USD
     * reference base explicitly.
     */
    @GET("latest")
    suspend fun latest(
        @Query("base") base: String? = null,
        @Query("symbols") symbols: String? = null,
    ): FrankfurterLatestResponse
}

/**
 * Wire DTO for `GET /v1/latest`, e.g.
 * `{"amount":1.0,"base":"USD","date":"2026-07-17","rates":{"EUR":0.9187,...}}`.
 * Every field is nullable so a partial payload degrades in mapping rather than throwing.
 */
data class FrankfurterLatestResponse(
    @Json(name = "amount") val amount: Double?,
    @Json(name = "base") val base: String?,
    @Json(name = "date") val date: String?,
    @Json(name = "rates") val rates: Map<String, Double>?,
)
