package com.jetsetter.pro.core.data.remote.expedia

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Expedia Rapid endpoints (§2.3, docs/API_REFERENCE.md). Hosts follow the iOS
 * `Endpoints.swift` contract: **auth always runs on the production host** (absolute URL below),
 * while the data host is Retrofit's baseUrl — `https://test.api.expediagroup.com` in debug
 * builds, production otherwise (see `ExpediaNetworkModule`).
 *
 * Auth headers are per-call parameters rather than an interceptor because the Bearer value comes
 * from a *suspending* token fetch (`ExpediaTokenProvider`) — OkHttp interceptors can't suspend.
 * The token POST is never replayed: `RetryInterceptor` retries GETs only.
 */
interface ExpediaApi {

    /** OAuth2 client-credentials grant; [authorization] is the Basic `clientId:clientSecret` pair. */
    @FormUrlEncoded
    @POST("https://api.expediagroup.com/identity/oauth2/v3/token")
    suspend fun token(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
    ): ExpediaTokenResponse

    /**
     * Property availability for a stay window; [authorization] is `Bearer <accessToken>`.
     * Rapid shops by explicit [propertyIds] (repeated `property_id` params) — there is no
     * free-text destination search on this endpoint.
     */
    @GET("v3/properties/availability")
    suspend fun availability(
        @Header("Authorization") authorization: String,
        @Query("checkin") checkin: String,
        @Query("checkout") checkout: String,
        @Query("currency") currency: String,
        @Query("language") language: String,
        @Query("country_code") countryCode: String,
        @Query("occupancy") occupancy: String,
        @Query("property_id") propertyIds: List<String>,
        @Query("sales_channel") salesChannel: String,
        @Query("sales_environment") salesEnvironment: String,
        @Query("rate_plan_count") ratePlanCount: Int,
    ): List<ExpediaProperty>
}
