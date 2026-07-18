package com.jetsetter.pro.core.data.remote

import com.jetsetter.pro.core.secrets.Secrets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access to FlightAware AeroAPI (§2.3) behind the `isConfigured` doctrine: callers check
 * [isConfigured] and fall back to their mock data when the key is missing — the service never
 * throws for an unconfigured key (the request would just 401 into an [ApiError]).
 *
 * PRIVACY INVARIANT (plan R10f): requests carry only the flight ident — never preference or
 * profile-derived fields.
 *
 * Consumers: `FlighttrackerRepository` (live board lookups), `DisruptionCheck` /
 * `DisruptionMonitorWorker` (background disruption polling).
 */
@Singleton
class FlightAwareService @Inject constructor(
    private val api: FlightAwareApi,
) {
    /** True when a real AeroAPI key is present; false → callers use their mock fallback. */
    val isConfigured: Boolean get() = Secrets.isConfigured(Secrets.flightAware)

    /**
     * All AeroAPI flights matching [ident] (e.g. "DL1423") — typically several legs across days,
     * in AeroAPI's order (soonest first). Failures arrive as the typed [ApiResult.Failure];
     * an unknown ident is a success with an empty list.
     */
    suspend fun flightByIdent(ident: String): ApiResult<List<FlightAwareFlight>> =
        apiCall { api.getFlight(ident.trim()) }.map { it.flights }
}
