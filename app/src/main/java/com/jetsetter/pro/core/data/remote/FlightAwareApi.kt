package com.jetsetter.pro.core.data.remote

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * FlightAware AeroAPI — flight status by identifier. The `x-apikey` header is attached
 * by an interceptor in `NetworkModule`. DTOs cover the §2.3 slice the Flight Tracker and
 * Disruption Monitor consume: airports, gate/terminal, the scheduled/estimated/actual
 * out+in times, status, cancellation, and delays (see docs/API_REFERENCE.md).
 *
 * Wire naming is per-field `@Json(name = "snake_case")` (the §2.1 convention — no global
 * naming strategy); timestamps stay ISO-8601 strings, parsed at the mapping site via
 * `IsoDates` so a malformed value degrades to null instead of failing the whole decode.
 */
interface FlightAwareApi {
    @GET("flights/{ident}")
    suspend fun getFlight(@Path("ident") ident: String): FlightAwareResponse
}

data class FlightAwareResponse(
    val flights: List<FlightAwareFlight> = emptyList(),
)

/** An airport reference on a flight (AeroAPI embeds a small airport object per endpoint). */
data class FlightAwareAirport(
    @Json(name = "code_iata") val codeIata: String? = null,
    val city: String? = null,
)

/**
 * One AeroAPI flight. Every field is nullable — AeroAPI omits what it doesn't know (e.g.
 * `actual_*` before the event happens, gates at airports that don't publish them).
 * [departureDelaySeconds]/[arrivalDelaySeconds] are SECONDS (AeroAPI's unit; negative =
 * running early) — mapping code converts to minutes.
 */
data class FlightAwareFlight(
    val ident: String? = null,
    val operator: String? = null,
    val status: String? = null,
    val cancelled: Boolean? = null,
    val origin: FlightAwareAirport? = null,
    val destination: FlightAwareAirport? = null,
    @Json(name = "scheduled_out") val scheduledOut: String? = null,
    @Json(name = "estimated_out") val estimatedOut: String? = null,
    @Json(name = "actual_out") val actualOut: String? = null,
    @Json(name = "scheduled_in") val scheduledIn: String? = null,
    @Json(name = "estimated_in") val estimatedIn: String? = null,
    @Json(name = "actual_in") val actualIn: String? = null,
    @Json(name = "gate_origin") val gateOrigin: String? = null,
    @Json(name = "gate_destination") val gateDestination: String? = null,
    @Json(name = "terminal_origin") val terminalOrigin: String? = null,
    @Json(name = "terminal_destination") val terminalDestination: String? = null,
    @Json(name = "departure_delay") val departureDelaySeconds: Long? = null,
    @Json(name = "arrival_delay") val arrivalDelaySeconds: Long? = null,
)
