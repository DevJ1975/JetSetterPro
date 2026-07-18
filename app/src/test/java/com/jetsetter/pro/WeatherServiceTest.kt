package com.jetsetter.pro

import com.jetsetter.pro.core.travel.AirportCoordinates
import com.jetsetter.pro.core.travel.BagClaimEstimator
import com.jetsetter.pro.core.weather.OpenMeteoApi
import com.jetsetter.pro.core.weather.OpenMeteoCurrent
import com.jetsetter.pro.core.weather.OpenMeteoForecastResponse
import com.jetsetter.pro.core.weather.OpenMeteoGeocodingResponse
import com.jetsetter.pro.core.weather.OpenMeteoGeocodingResult
import com.jetsetter.pro.core.weather.OpenMeteoWeatherService
import com.jetsetter.pro.core.weather.WmoWeatherCodes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pins the weather seam (spec §1.3 getWeather): the WMO weather-code → condition-string table,
 * the [AirportCoordinates] roster (every [BagClaimEstimator.TIER1_HUBS] code must resolve), and
 * [OpenMeteoWeatherService] resolution/failure behavior against a fake API. Pure JVM.
 */
class WeatherServiceTest {

    // ── WMO code table ───────────────────────────────────────────────────────

    @Test
    fun wmoTable_mapsWellKnownCodes() {
        assertEquals("Clear sky", WmoWeatherCodes.describe(0))
        assertEquals("Mainly clear", WmoWeatherCodes.describe(1))
        assertEquals("Partly cloudy", WmoWeatherCodes.describe(2))
        assertEquals("Overcast", WmoWeatherCodes.describe(3))
        assertEquals("Fog", WmoWeatherCodes.describe(45))
        assertEquals("Light drizzle", WmoWeatherCodes.describe(51))
        assertEquals("Light rain", WmoWeatherCodes.describe(61))
        assertEquals("Rain", WmoWeatherCodes.describe(63))
        assertEquals("Heavy rain", WmoWeatherCodes.describe(65))
        assertEquals("Light snow", WmoWeatherCodes.describe(71))
        assertEquals("Heavy snow", WmoWeatherCodes.describe(75))
        assertEquals("Rain showers", WmoWeatherCodes.describe(81))
        assertEquals("Thunderstorm", WmoWeatherCodes.describe(95))
        assertEquals("Thunderstorm with heavy hail", WmoWeatherCodes.describe(99))
    }

    @Test
    fun wmoTable_unknownOrMissingCode_readsUnknown() {
        assertEquals("Unknown", WmoWeatherCodes.describe(null))
        assertEquals("Unknown", WmoWeatherCodes.describe(42))
        assertEquals("Unknown", WmoWeatherCodes.describe(-1))
    }

    @Test
    fun wmoTable_everyKnownCode_hasANonUnknownDescription() {
        for (code in WmoWeatherCodes.knownCodes) {
            val text = WmoWeatherCodes.describe(code)
            assertTrue("blank description for $code", text.isNotBlank())
            assertTrue("code $code reads Unknown", text != "Unknown")
        }
    }

    // ── Airport coordinate table ─────────────────────────────────────────────

    @Test
    fun airportTable_containsEveryTier1Hub() {
        for (hub in BagClaimEstimator.TIER1_HUBS) {
            assertNotNull("missing tier-1 hub $hub", AirportCoordinates.lookup(hub))
        }
    }

    @Test
    fun airportTable_hasAtLeastEightyMajors_withValidEntries() {
        val all = AirportCoordinates.all
        assertTrue("expected ~80 airports, got ${all.size}", all.size >= 80)
        for (airport in all) {
            assertTrue("bad IATA ${airport.iata}", airport.iata.matches(Regex("[A-Z]{3}")))
            assertTrue("blank city for ${airport.iata}", airport.city.isNotBlank())
            assertTrue("latitude out of range for ${airport.iata}", airport.latitude in -90.0..90.0)
            assertTrue("longitude out of range for ${airport.iata}", airport.longitude in -180.0..180.0)
            // Round-trips through lookup under its own code.
            assertEquals(airport, AirportCoordinates.lookup(airport.iata))
        }
    }

    @Test
    fun airportTable_spotChecksLandNearTheRealAirports() {
        fun assertNear(iata: String, lat: Double, lon: Double) {
            val a = AirportCoordinates.lookup(iata)
            assertNotNull("missing $iata", a)
            assertEquals("lat for $iata", lat, a!!.latitude, 0.5)
            assertEquals("lon for $iata", lon, a.longitude, 0.5)
        }
        assertNear("ATL", 33.64, -84.43)
        assertNear("LHR", 51.47, -0.45)
        assertNear("SYD", -33.95, 151.18)  // southern hemisphere
        assertNear("GRU", -23.43, -46.47)  // south + west
        assertNear("HND", 35.55, 139.78)
    }

    @Test
    fun airportLookup_isCaseInsensitiveAndTrimmed_andNullForUnknown() {
        assertNotNull(AirportCoordinates.lookup(" atl "))
        assertEquals(AirportCoordinates.lookup("ATL"), AirportCoordinates.lookup("atl"))
        assertNull(AirportCoordinates.lookup("ZZZ"))
        assertNull(AirportCoordinates.lookup(""))
    }

    // ── OpenMeteoWeatherService against a fake API ───────────────────────────

    /** Scriptable fake; records calls so tests can pin what goes over the wire. */
    private class FakeOpenMeteoApi(
        var geocodeResult: OpenMeteoGeocodingResponse? = null,
        var forecastResult: OpenMeteoForecastResponse? = null,
    ) : OpenMeteoApi {
        var geocodeCalls = 0
        var lastGeocodeName: String? = null
        var lastLatitude: Double? = null
        var lastLongitude: Double? = null
        var lastCurrentFields: String? = null
        var lastTemperatureUnit: String? = null
        var lastWindSpeedUnit: String? = null

        override suspend fun geocode(name: String, count: Int): OpenMeteoGeocodingResponse {
            geocodeCalls++
            lastGeocodeName = name
            return geocodeResult ?: throw IOException("geocoding down")
        }

        override suspend fun forecast(
            latitude: Double,
            longitude: Double,
            current: String,
            temperatureUnit: String,
            windSpeedUnit: String,
        ): OpenMeteoForecastResponse {
            lastLatitude = latitude
            lastLongitude = longitude
            lastCurrentFields = current
            lastTemperatureUnit = temperatureUnit
            lastWindSpeedUnit = windSpeedUnit
            return forecastResult ?: throw IOException("forecast down")
        }
    }

    private fun forecast(tempF: Double? = 72.5, code: Int? = 2, windMph: Double? = 8.1) =
        OpenMeteoForecastResponse(OpenMeteoCurrent(tempF, code, windMph))

    @Test
    fun iataQuery_resolvesFromStaticTable_withoutGeocoding() = runBlocking {
        val api = FakeOpenMeteoApi(forecastResult = forecast())
        val report = OpenMeteoWeatherService(api).current("atl")

        assertNotNull(report)
        assertEquals("Atlanta (ATL)", report!!.location)
        assertEquals(72.5, report.tempF, 0.0)
        assertEquals("Partly cloudy", report.conditions)
        assertEquals(8.1, report.windMph, 0.0)
        // Privacy contract (R10f): the airport code never hits geocoding — only coordinates go out.
        assertEquals(0, api.geocodeCalls)
        assertEquals(33.64, api.lastLatitude!!, 0.001)
        assertEquals(-84.43, api.lastLongitude!!, 0.001)
        // Wire contract: fahrenheit/mph and the three current fields.
        assertEquals("temperature_2m,weather_code,wind_speed_10m", api.lastCurrentFields)
        assertEquals("fahrenheit", api.lastTemperatureUnit)
        assertEquals("mph", api.lastWindSpeedUnit)
    }

    @Test
    fun cityQuery_fallsBackToGeocoding() = runBlocking {
        val api = FakeOpenMeteoApi(
            geocodeResult = OpenMeteoGeocodingResponse(
                listOf(OpenMeteoGeocodingResult("Paris", "France", 48.86, 2.35)),
            ),
            forecastResult = forecast(tempF = 61.0, code = 61, windMph = 5.0),
        )
        val report = OpenMeteoWeatherService(api).current("Paris")

        assertNotNull(report)
        assertEquals("Paris, France", report!!.location)
        assertEquals("Light rain", report.conditions)
        assertEquals(1, api.geocodeCalls)
        assertEquals("Paris", api.lastGeocodeName)
        assertEquals(48.86, api.lastLatitude!!, 0.001)
    }

    @Test
    fun unknownThreeLetterQuery_stillTriesGeocoding() = runBlocking {
        val api = FakeOpenMeteoApi(
            geocodeResult = OpenMeteoGeocodingResponse(
                listOf(OpenMeteoGeocodingResult("Rio de Janeiro", "Brazil", -22.91, -43.17)),
            ),
            forecastResult = forecast(),
        )
        // "RIO" is IATA-shaped but not in the table → falls through to geocoding.
        val report = OpenMeteoWeatherService(api).current("RIO")

        assertNotNull(report)
        assertEquals(1, api.geocodeCalls)
        assertEquals("Rio de Janeiro, Brazil", report!!.location)
    }

    @Test
    fun anyFailure_returnsNull() = runBlocking {
        // Geocoding throws.
        assertNull(OpenMeteoWeatherService(FakeOpenMeteoApi()).current("Paris"))

        // Geocoding empty.
        assertNull(
            OpenMeteoWeatherService(
                FakeOpenMeteoApi(geocodeResult = OpenMeteoGeocodingResponse(emptyList())),
            ).current("Nowhereville"),
        )

        // Forecast throws (IATA path).
        assertNull(OpenMeteoWeatherService(FakeOpenMeteoApi()).current("ATL"))

        // Forecast payload missing the current block or required fields.
        assertNull(
            OpenMeteoWeatherService(
                FakeOpenMeteoApi(forecastResult = OpenMeteoForecastResponse(null)),
            ).current("ATL"),
        )
        assertNull(
            OpenMeteoWeatherService(
                FakeOpenMeteoApi(forecastResult = forecast(tempF = null)),
            ).current("ATL"),
        )
        assertNull(
            OpenMeteoWeatherService(
                FakeOpenMeteoApi(forecastResult = forecast(windMph = null)),
            ).current("ATL"),
        )

        // Blank query short-circuits without any network call.
        val api = FakeOpenMeteoApi(forecastResult = forecast())
        assertNull(OpenMeteoWeatherService(api).current("   "))
        assertEquals(0, api.geocodeCalls)
        assertNull(api.lastLatitude)
    }

    @Test
    fun missingWeatherCode_stillReports_withUnknownConditions() = runBlocking {
        val api = FakeOpenMeteoApi(forecastResult = forecast(code = null))
        val report = OpenMeteoWeatherService(api).current("ATL")
        assertNotNull(report)
        assertEquals("Unknown", report!!.conditions)
    }
}
