package com.jetsetter.pro.core.weather

/**
 * Current-conditions weather seam (spec §1.3 `getWeather`, §2.3). The live implementation is
 * [OpenMeteoWeatherService] — keyless, so there is no `isConfigured` gate and no mock fallback;
 * failures simply surface as null and callers degrade gracefully. Consumers: the IRIS getWeather
 * tool, the generatePackingList commit, and the weatherWatch proactive trigger.
 */
interface WeatherService {

    /**
     * Current conditions for [query] — a city name ("Paris") or IATA airport code ("CDG").
     * Null when the location can't be resolved or the fetch fails for any reason.
     */
    suspend fun current(query: String): WeatherReport?
}

/** Snapshot of current conditions at a resolved location. */
data class WeatherReport(
    /** Human-readable resolved place, e.g. "Atlanta (ATL)" or "Paris, France". */
    val location: String,
    val tempF: Double,
    /** Short condition string from the WMO weather-code table, e.g. "Partly cloudy". */
    val conditions: String,
    val windMph: Double,
)
