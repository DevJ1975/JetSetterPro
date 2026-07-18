package com.jetsetter.pro.core.data.remote.worldtracer

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * SITA WorldTracer baggage status by tag (§2.3): `GET /baggage/v1/baggage/{tag}` with the
 * `x-partner-key` header attached by an interceptor in `WorldTracerNetworkModule`.
 *
 * Wire naming is per-field `@Json(name = "snake_case")` (the §2.1 convention). DTOs are kept
 * lean — the slice the Luggage Tracker consumes: tag, status, last-seen station/time, and the
 * routing scan trail. Every field is nullable/defaulted so a sparse payload degrades at the
 * mapping site instead of failing the decode.
 */
interface WorldTracerApi {
    @GET("baggage/v1/baggage/{tag}")
    suspend fun bagByTag(@Path("tag") tag: String): WorldTracerBagDto
}

data class WorldTracerBagDto(
    @Json(name = "tag_number") val tagNumber: String? = null,
    val status: String? = null,
    @Json(name = "last_seen_station") val lastSeenStation: String? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null,
    val routing: List<WorldTracerRoutingDto> = emptyList(),
)

/** One entry in the bag's routing trail — a station scan with an optional timestamp/detail. */
data class WorldTracerRoutingDto(
    val station: String? = null,
    val description: String? = null,
    @Json(name = "scanned_at") val scannedAt: String? = null,
)
