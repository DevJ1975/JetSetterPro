package com.jetsetter.pro.core.weather

import com.jetsetter.pro.core.data.remote.apiCall
import com.jetsetter.pro.core.data.remote.getOrNull
import com.jetsetter.pro.core.travel.AirportCoordinates
import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live [WeatherService] backed by Open-Meteo — keyless (no API key, no `isConfigured` gate).
 *
 * Resolution order: an IATA-shaped query hits the static [AirportCoordinates] table first
 * (privacy: the airport code never leaves the device — only its coordinates do); anything else,
 * or an unknown code, falls back to Open-Meteo geocoding. Any failure anywhere → null.
 */
@Singleton
class OpenMeteoWeatherService @Inject constructor(
    private val api: OpenMeteoApi,
) : WeatherService {

    override suspend fun current(query: String): WeatherReport? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val place = resolve(trimmed) ?: return null
        val current = apiCall {
            api.forecast(
                latitude = place.latitude,
                longitude = place.longitude,
                current = CURRENT_FIELDS,
                temperatureUnit = "fahrenheit",
                windSpeedUnit = "mph",
            )
        }.getOrNull()?.current ?: return null
        val tempF = current.temperatureF ?: return null
        val windMph = current.windMph ?: return null
        return WeatherReport(
            location = place.label,
            tempF = tempF,
            conditions = WmoWeatherCodes.describe(current.weatherCode),
            windMph = windMph,
        )
    }

    private data class Place(val label: String, val latitude: Double, val longitude: Double)

    private suspend fun resolve(query: String): Place? {
        if (IATA_SHAPE.matches(query)) {
            AirportCoordinates.lookup(query)?.let {
                return Place("${it.city} (${it.iata})", it.latitude, it.longitude)
            }
        }
        val hit = apiCall { api.geocode(name = query, count = 1) }
            .getOrNull()?.results?.firstOrNull() ?: return null
        val latitude = hit.latitude ?: return null
        val longitude = hit.longitude ?: return null
        val label = listOfNotNull(hit.name, hit.country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { query }
        return Place(label, latitude, longitude)
    }

    private companion object {
        val IATA_SHAPE = Regex("[A-Za-z]{3}")
        const val CURRENT_FIELDS = "temperature_2m,weather_code,wind_speed_10m"
    }
}

/**
 * Open-Meteo endpoints. Geocoding and forecast live on different hosts, so each method carries
 * an absolute URL and they share one Retrofit (client derived from `@Named("baseHttp")` in
 * `NetworkModule` — 30 s timeouts + GET retry policy apply).
 */
interface OpenMeteoApi {

    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun geocode(
        @Query("name") name: String,
        @Query("count") count: Int,
    ): OpenMeteoGeocodingResponse

    @GET("https://api.open-meteo.com/v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("temperature_unit") temperatureUnit: String,
        @Query("wind_speed_unit") windSpeedUnit: String,
    ): OpenMeteoForecastResponse
}

data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoGeocodingResult> = emptyList(),
)

data class OpenMeteoGeocodingResult(
    val name: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class OpenMeteoForecastResponse(
    val current: OpenMeteoCurrent?,
)

data class OpenMeteoCurrent(
    @Json(name = "temperature_2m") val temperatureF: Double?,
    @Json(name = "weather_code") val weatherCode: Int?,
    @Json(name = "wind_speed_10m") val windMph: Double?,
)

/**
 * WMO 4677-derived weather interpretation codes (Open-Meteo's `weather_code` field) → short
 * condition strings. Unknown or absent codes read "Unknown".
 */
object WmoWeatherCodes {

    private val DESCRIPTIONS: Map<Int, String> = mapOf(
        0 to "Clear sky",
        1 to "Mainly clear",
        2 to "Partly cloudy",
        3 to "Overcast",
        45 to "Fog",
        48 to "Depositing rime fog",
        51 to "Light drizzle",
        53 to "Drizzle",
        55 to "Dense drizzle",
        56 to "Light freezing drizzle",
        57 to "Freezing drizzle",
        61 to "Light rain",
        63 to "Rain",
        65 to "Heavy rain",
        66 to "Light freezing rain",
        67 to "Freezing rain",
        71 to "Light snow",
        73 to "Snow",
        75 to "Heavy snow",
        77 to "Snow grains",
        80 to "Light rain showers",
        81 to "Rain showers",
        82 to "Violent rain showers",
        85 to "Light snow showers",
        86 to "Snow showers",
        95 to "Thunderstorm",
        96 to "Thunderstorm with light hail",
        99 to "Thunderstorm with heavy hail",
    )

    fun describe(code: Int?): String = code?.let { DESCRIPTIONS[it] } ?: "Unknown"

    /** Codes with a known description (exposed so tests can pin the table). */
    val knownCodes: Set<Int> get() = DESCRIPTIONS.keys
}
