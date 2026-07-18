package com.jetsetter.pro.core.travel

/**
 * Static IATA → coordinates/city table for ~80 major airports — the single shared lookup for
 * the weather service (airport queries resolve here before falling back to geocoding), the
 * bag-claim surfaces, and the departure tool. Covers every [BagClaimEstimator.TIER1_HUBS] code
 * plus the remaining global majors. Coordinates are airport reference points at ~2-decimal
 * accuracy — plenty for a weather grid or distance sanity check, not for navigation.
 *
 * Privacy note (spec R10f): looking up here keeps bare IATA codes from ever being sent to the
 * external geocoding API; only the resulting latitude/longitude leave the device.
 */
object AirportCoordinates {

    data class Airport(
        val iata: String,
        val city: String,
        val latitude: Double,
        val longitude: Double,
    )

    /** Case-insensitive lookup (input trimmed). Null when the code isn't in the table. */
    fun lookup(iata: String): Airport? = BY_IATA[iata.trim().uppercase()]

    /** Every airport in the table (read-only; primarily for tests/diagnostics). */
    val all: Collection<Airport> get() = BY_IATA.values

    private val AIRPORTS: List<Airport> = listOf(
        // ── United States ────────────────────────────────────────────────────
        Airport("ATL", "Atlanta", 33.64, -84.43),
        Airport("DFW", "Dallas–Fort Worth", 32.90, -97.04),
        Airport("ORD", "Chicago", 41.97, -87.91),
        Airport("LAX", "Los Angeles", 33.94, -118.41),
        Airport("JFK", "New York", 40.64, -73.78),
        Airport("LGA", "New York", 40.78, -73.87),
        Airport("EWR", "Newark", 40.69, -74.17),
        Airport("DEN", "Denver", 39.86, -104.67),
        Airport("SFO", "San Francisco", 37.62, -122.38),
        Airport("OAK", "Oakland", 37.72, -122.22),
        Airport("SJC", "San Jose", 37.36, -121.93),
        Airport("SMF", "Sacramento", 38.70, -121.59),
        Airport("LAS", "Las Vegas", 36.08, -115.15),
        Airport("SEA", "Seattle", 47.45, -122.31),
        Airport("PDX", "Portland", 45.59, -122.60),
        Airport("MIA", "Miami", 25.79, -80.29),
        Airport("MCO", "Orlando", 28.43, -81.31),
        Airport("TPA", "Tampa", 27.98, -82.53),
        Airport("BOS", "Boston", 42.36, -71.01),
        Airport("CLT", "Charlotte", 35.21, -80.94),
        Airport("IAH", "Houston", 29.98, -95.34),
        Airport("HOU", "Houston", 29.65, -95.28),
        Airport("DAL", "Dallas", 32.85, -96.85),
        Airport("AUS", "Austin", 30.19, -97.67),
        Airport("PHX", "Phoenix", 33.44, -112.01),
        Airport("SLC", "Salt Lake City", 40.79, -111.98),
        Airport("DTW", "Detroit", 42.21, -83.35),
        Airport("MSP", "Minneapolis", 44.88, -93.22),
        Airport("PHL", "Philadelphia", 39.87, -75.24),
        Airport("DCA", "Washington", 38.85, -77.04),
        Airport("IAD", "Washington", 38.94, -77.46),
        Airport("BWI", "Baltimore", 39.18, -76.67),
        Airport("SAN", "San Diego", 32.73, -117.19),
        Airport("BNA", "Nashville", 36.12, -86.68),
        Airport("STL", "St. Louis", 38.75, -90.37),
        Airport("HNL", "Honolulu", 21.32, -157.92),
        Airport("ANC", "Anchorage", 61.17, -149.98),
        // ── Canada / Latin America ───────────────────────────────────────────
        Airport("YYZ", "Toronto", 43.68, -79.62),
        Airport("YVR", "Vancouver", 49.19, -123.18),
        Airport("MEX", "Mexico City", 19.44, -99.07),
        Airport("GRU", "São Paulo", -23.43, -46.47),
        Airport("EZE", "Buenos Aires", -34.82, -58.54),
        // ── Europe ───────────────────────────────────────────────────────────
        Airport("LHR", "London", 51.47, -0.45),
        Airport("MAN", "Manchester", 53.35, -2.28),
        Airport("EDI", "Edinburgh", 55.95, -3.37),
        Airport("DUB", "Dublin", 53.43, -6.24),
        Airport("CDG", "Paris", 49.01, 2.55),
        Airport("FRA", "Frankfurt", 50.03, 8.56),
        Airport("MUC", "Munich", 48.35, 11.79),
        Airport("BER", "Berlin", 52.36, 13.50),
        Airport("AMS", "Amsterdam", 52.31, 4.76),
        Airport("BRU", "Brussels", 50.90, 4.48),
        Airport("ZRH", "Zurich", 47.46, 8.55),
        Airport("VIE", "Vienna", 48.11, 16.57),
        Airport("MAD", "Madrid", 40.47, -3.56),
        Airport("BCN", "Barcelona", 41.30, 2.08),
        Airport("LIS", "Lisbon", 38.77, -9.13),
        Airport("FCO", "Rome", 41.80, 12.24),
        Airport("MXP", "Milan", 45.63, 8.72),
        Airport("CPH", "Copenhagen", 55.62, 12.66),
        Airport("ARN", "Stockholm", 59.65, 17.92),
        Airport("OSL", "Oslo", 60.19, 11.10),
        Airport("HEL", "Helsinki", 60.32, 24.96),
        Airport("IST", "Istanbul", 41.26, 28.74),
        Airport("ATH", "Athens", 37.94, 23.94),
        Airport("SVO", "Moscow", 55.97, 37.41),
        // ── Middle East / Africa ─────────────────────────────────────────────
        Airport("DXB", "Dubai", 25.25, 55.36),
        Airport("AUH", "Abu Dhabi", 24.43, 54.65),
        Airport("DOH", "Doha", 25.27, 51.61),
        Airport("CAI", "Cairo", 30.12, 31.41),
        Airport("JNB", "Johannesburg", -26.14, 28.25),
        // ── Asia-Pacific ─────────────────────────────────────────────────────
        Airport("HND", "Tokyo", 35.55, 139.78),
        Airport("NRT", "Tokyo", 35.77, 140.39),
        Airport("KIX", "Osaka", 34.43, 135.24),
        Airport("ITM", "Osaka", 34.79, 135.44),
        Airport("CTS", "Sapporo", 42.78, 141.69),
        Airport("FUK", "Fukuoka", 33.59, 130.45),
        Airport("ICN", "Seoul", 37.46, 126.44),
        Airport("GMP", "Seoul", 37.56, 126.79),
        Airport("PEK", "Beijing", 40.08, 116.58),
        Airport("PVG", "Shanghai", 31.14, 121.81),
        Airport("HKG", "Hong Kong", 22.31, 113.91),
        Airport("TPE", "Taipei", 25.08, 121.23),
        Airport("SIN", "Singapore", 1.36, 103.99),
        Airport("BKK", "Bangkok", 13.69, 100.75),
        Airport("KUL", "Kuala Lumpur", 2.75, 101.71),
        Airport("CGK", "Jakarta", -6.13, 106.66),
        Airport("MNL", "Manila", 14.51, 121.02),
        Airport("DEL", "Delhi", 28.57, 77.10),
        Airport("BOM", "Mumbai", 19.09, 72.87),
        Airport("MAA", "Chennai", 12.99, 80.17),
        Airport("BLR", "Bengaluru", 13.20, 77.71),
        Airport("SYD", "Sydney", -33.95, 151.18),
        Airport("MEL", "Melbourne", -37.67, 144.84),
    )

    private val BY_IATA: Map<String, Airport> = AIRPORTS.associateBy { it.iata }
}
