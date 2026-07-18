package com.jetsetter.pro.core.data.remote.expedia

import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.apiCall
import com.jetsetter.pro.core.secrets.Secrets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access to Expedia Rapid availability (§2.3) behind the `isConfigured` doctrine: callers
 * check [isConfigured] and fall back to their mock data when the client-credentials pair is
 * missing — the service never throws for unconfigured keys (the token grant would just 401 into
 * a typed `ApiError`). The Bearer token is fetched/cached by [ExpediaTokenProvider].
 *
 * PRIVACY INVARIANT (plan R10f): requests carry only property ids, stay dates, an occupancy
 * count, and currency/language/country codes — never preference or profile-derived fields.
 *
 * Consumer: `BookingRepository` (live hotel search with mock fallback).
 */
@Singleton
class ExpediaService @Inject constructor(
    private val api: ExpediaApi,
    private val tokenProvider: ExpediaTokenProvider,
) {

    /** True when the full OAuth2 pair is present; false → callers use their mock fallback. */
    val isConfigured: Boolean
        get() = Secrets.isConfigured(Secrets.expediaClientId) &&
            Secrets.isConfigured(Secrets.expediaClientSecret)

    /**
     * Availability for [propertyIds] over the [checkin]→[checkout] window (yyyy-MM-dd), priced
     * for [occupancy] adults in [currency]. An empty id list short-circuits to an empty success
     * (Rapid rejects a shop with no `property_id`). Token-grant and transport failures both
     * arrive as the typed [ApiResult.Failure]; an unknown property is simply absent from the
     * response array.
     */
    suspend fun availability(
        checkin: String,
        checkout: String,
        propertyIds: List<String>,
        occupancy: String = "2",
        currency: String = "USD",
    ): ApiResult<List<ExpediaProperty>> {
        if (propertyIds.isEmpty()) return ApiResult.Success(emptyList())
        return apiCall {
            api.availability(
                authorization = "Bearer ${tokenProvider.accessToken()}",
                checkin = checkin,
                checkout = checkout,
                currency = currency,
                language = LANGUAGE,
                countryCode = COUNTRY_CODE,
                occupancy = occupancy,
                propertyIds = propertyIds,
                salesChannel = SALES_CHANNEL,
                salesEnvironment = SALES_ENVIRONMENT,
                ratePlanCount = RATE_PLAN_COUNT,
            )
        }
    }

    private companion object {
        // Fixed shopping context for a consumer app selling stand-alone hotel stays.
        const val LANGUAGE = "en-US"
        const val COUNTRY_CODE = "US"
        const val SALES_CHANNEL = "website"
        const val SALES_ENVIRONMENT = "hotel_only"
        const val RATE_PLAN_COUNT = 1
    }
}
