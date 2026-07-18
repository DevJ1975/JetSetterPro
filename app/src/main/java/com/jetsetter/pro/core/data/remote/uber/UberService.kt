package com.jetsetter.pro.core.data.remote.uber

import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.apiCall
import com.jetsetter.pro.core.data.remote.map
import com.jetsetter.pro.core.secrets.Secrets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access to Uber price estimates (§2.3) behind the `isConfigured` doctrine: callers check
 * [isConfigured] and fall back to their mock data when the server token is missing — the service
 * never throws for an unconfigured key (the request would just 401 into an
 * [com.jetsetter.pro.core.data.remote.ApiError]).
 *
 * PRIVACY INVARIANT (plan R10f): requests carry only the four coordinates — never preference
 * or profile-derived fields.
 *
 * Consumer: `GroundtransportRepository` (live-over-rate-card merge).
 */
@Singleton
class UberService @Inject constructor(
    private val api: UberApi,
) {
    /** True when a real Uber server token is present; false → callers use their mock fallback. */
    val isConfigured: Boolean get() = Secrets.isConfigured(Secrets.uberServerToken)

    /**
     * All Uber products quoted for the start→end trip, in Uber's order. Failures arrive as the
     * typed [ApiResult.Failure]; a serviced area with no products is a success with an empty list.
     */
    suspend fun priceEstimates(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): ApiResult<List<UberPriceEstimate>> = apiCall {
        api.priceEstimates(
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            endLatitude = endLatitude,
            endLongitude = endLongitude,
        )
    }.map { it.prices }
}
