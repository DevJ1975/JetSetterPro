package com.jetsetter.pro.core.data.remote.uber

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Uber Riders API — price estimates between two points (§2.3). The
 * `Authorization: Token <serverToken>` header is attached by an interceptor in
 * `RideNetworkModule`. DTOs cover the slice the Ground Transport screen consumes:
 * product name, quoted fare range + currency, trip duration and distance.
 *
 * Wire naming is per-field `@Json(name = "snake_case")` (the §2.1 convention — no global
 * naming strategy).
 *
 * PRIVACY INVARIANT (plan R10f): requests carry only bare coordinates — never preference
 * or profile-derived fields.
 */
interface UberApi {
    @GET("v1.2/estimates/price")
    suspend fun priceEstimates(
        @Query("start_latitude") startLatitude: Double,
        @Query("start_longitude") startLongitude: Double,
        @Query("end_latitude") endLatitude: Double,
        @Query("end_longitude") endLongitude: Double,
    ): UberPriceEstimatesResponse
}

data class UberPriceEstimatesResponse(
    val prices: List<UberPriceEstimate> = emptyList(),
)

/**
 * One Uber product's quoted fare. Every field is nullable — metered products (e.g. TAXI)
 * omit the numeric bounds and only carry the display [estimate]. [duration] is trip time in
 * SECONDS and [distance] is trip length in miles (Uber's units); note this endpoint carries
 * no driver-pickup ETA (that lives on `/estimates/time`).
 */
data class UberPriceEstimate(
    @Json(name = "product_id") val productId: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "localized_display_name") val localizedDisplayName: String? = null,
    /** Display string like "$23-29"; the only quote metered products get. */
    val estimate: String? = null,
    @Json(name = "low_estimate") val lowEstimate: Double? = null,
    @Json(name = "high_estimate") val highEstimate: Double? = null,
    @Json(name = "currency_code") val currencyCode: String? = null,
    @Json(name = "surge_multiplier") val surgeMultiplier: Double? = null,
    val duration: Long? = null,
    val distance: Double? = null,
)
