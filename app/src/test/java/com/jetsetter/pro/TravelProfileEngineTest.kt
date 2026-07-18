package com.jetsetter.pro

import com.jetsetter.pro.core.intelligence.TravelProfileData
import com.jetsetter.pro.core.intelligence.TravelProfileEngine
import com.jetsetter.pro.core.intelligence.TravelSignal
import com.jetsetter.pro.core.intelligence.TravelSignalKindAdapter
import com.jetsetter.pro.core.model.Trip
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Pins the deterministic learning math of [TravelProfileEngine] (spec §1.6): the 365-day half-life,
 * seat parsing, top-5 recency-weighted rankings, spend stats (mileage never learned), trip rhythm,
 * seat-preference confidence, and the camelCase [TravelSignal.Kind] wire names. Pure-JVM.
 */
class TravelProfileEngineTest {

    private val now: Instant = Instant.parse("2026-07-17T12:00:00Z")

    private fun signal(
        kind: TravelSignal.Kind,
        value: String = "",
        attributes: Map<String, String> = emptyMap(),
        timestamp: String = now.toString(),
    ) = TravelSignal(kind = kind, value = value, attributes = attributes, timestamp = timestamp, source = "test")

    private fun daysAgo(days: Long): String = now.minus(days, ChronoUnit.DAYS).toString()

    // ── decay ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun weight_halvesAt365Days() {
        assertEquals(1.0, TravelProfileEngine.weight(0.0), 1e-9)
        assertEquals(0.5, TravelProfileEngine.weight(365.0), 1e-9)
        assertEquals(0.25, TravelProfileEngine.weight(730.0), 1e-9)
    }

    // ── seat parsing ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun parseSeat_mapsColumnAndRowPerSpecTable() {
        val cases = mapOf(
            "1A" to ("window" to "forward"),
            "14F" to ("window" to "middle"),
            "27C" to ("aisle" to "rear"),
            "12B" to ("middle" to "middle"),
            "10L" to ("window" to "forward"),
            "26E" to ("middle" to "rear"),
            "11G" to ("aisle" to "middle"),
        )
        for ((raw, expected) in cases) {
            val seat = TravelProfileEngine.parseSeat(raw)
            assertNotNull("expected $raw to parse", seat)
            assertEquals("position of $raw", expected.first, seat!!.position)
            assertEquals("zone of $raw", expected.second, seat.zone)
        }
    }

    @Test
    fun parseSeat_isCaseInsensitiveAndTrimmed() {
        val seat = TravelProfileEngine.parseSeat(" 14f ")
        assertEquals("window", seat?.position)
        assertEquals("middle", seat?.zone)
    }

    @Test
    fun parseSeat_returnsNullForUnparsable() {
        for (raw in listOf("", "garbage", "12", "F", "0A", "14I", "14J", "1 4F", "F14")) {
            assertNull("expected '$raw' to be null", TravelProfileEngine.parseSeat(raw))
        }
    }

    // ── rankings ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun rankings_aggregatesByValueAndTruncatesToTop5() {
        val entries = listOf(
            "Delta" to 1.0, "Delta" to 0.5,   // 1.5 total, count 2
            "United" to 1.2,
            "Alaska" to 1.0,
            "JetBlue" to 0.8,
            "American" to 0.6,
            "Spirit" to 0.1,                  // 6th — must be cut
        )
        val ranked = TravelProfileEngine.rankings(entries)
        assertEquals(5, ranked.size)
        assertEquals(listOf("Delta", "United", "Alaska", "JetBlue", "American"), ranked.map { it.value })
        assertEquals(1.5, ranked[0].weight, 1e-9)
        assertEquals(2, ranked[0].count)
        assertTrue(ranked.none { it.value == "Spirit" })
    }

    // ── spend ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun spendByCategory_groupsByCategoryAndCurrency_excludingMileage() {
        val signals = listOf(
            signal(TravelSignal.Kind.EXPENSE_LOGGED, attributes = mapOf("amount" to "20.0", "currency" to "USD", "category" to "food")),
            signal(TravelSignal.Kind.RECEIPT_SCANNED, attributes = mapOf("amount" to "40.0", "currency" to "USD", "category" to "food")),
            signal(TravelSignal.Kind.EXPENSE_LOGGED, attributes = mapOf("amount" to "100.0", "currency" to "EUR", "category" to "food")),
            signal(TravelSignal.Kind.EXPENSE_LOGGED, attributes = mapOf("amount" to "55.0", "currency" to "USD", "category" to "mileage")),
            // Non-spend kinds and unparsable amounts never contribute.
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "14F", attributes = mapOf("amount" to "9.0", "category" to "food")),
            signal(TravelSignal.Kind.EXPENSE_LOGGED, attributes = mapOf("amount" to "oops", "category" to "food")),
        )
        val stats = TravelProfileEngine.spendByCategory(signals)

        assertEquals(2, stats.size)
        val usdFood = stats.single { it.category == "food" && it.currency == "USD" }
        assertEquals(30.0, usdFood.average, 1e-9)
        assertEquals(2, usdFood.count)
        val eurFood = stats.single { it.category == "food" && it.currency == "EUR" }
        assertEquals(100.0, eurFood.average, 1e-9)
        assertEquals(1, eurFood.count)
        assertTrue("mileage must never be learned", stats.none { it.category == "mileage" })
    }

    // ── rhythm ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun tripRhythm_threeTrips_yieldsMeansAndPeakMonth() {
        val trips = listOf(
            Trip(name = "A", destination = "Tokyo", startDate = "2026-01-01", endDate = "2026-01-06"),   // 5d
            Trip(name = "B", destination = "Paris", startDate = "2026-03-01", endDate = "2026-03-04"),   // 3d
            Trip(name = "C", destination = "Lima", startDate = "2026-03-31", endDate = "2026-04-04"),    // 4d
        )
        val rhythm = TravelProfileEngine.tripRhythm(trips)
        assertEquals(4, rhythm.typicalTripDurationDays)          // mean(5, 3, 4)
        assertEquals(45, rhythm.travelCadenceDays)               // mean(59, 30) = 44.5 → 45
        assertEquals(listOf("March"), rhythm.peakTravelMonths)   // March ×2 with ≥3 trips
    }

    @Test
    fun tripRhythm_underThreeTrips_hasNoPeakMonths() {
        val trips = listOf(
            Trip(name = "A", destination = "Tokyo", startDate = "2026-03-01", endDate = "2026-03-04"),
            Trip(name = "B", destination = "Paris", startDate = "2026-03-20", endDate = "2026-03-22"),
        )
        val rhythm = TravelProfileEngine.tripRhythm(trips)
        assertTrue(rhythm.peakTravelMonths.isEmpty())
        assertEquals(3, rhythm.typicalTripDurationDays)   // mean(3, 2) = 2.5 → 3
        assertEquals(19, rhythm.travelCadenceDays)
    }

    // ── seat preference ──────────────────────────────────────────────────────────────────────────

    @Test
    fun seatPreference_confidenceIsDominantWeightedShare() {
        val signals = listOf(
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "12A"),
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "14F"),
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "2A"),
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "14C"),
        )
        val pref = TravelProfileEngine.seatPreference(signals, now)
        assertNotNull(pref)
        assertEquals("window", pref!!.position)   // 3 of 4 equal-weight picks
        assertEquals("middle", pref.zone)         // rows 12, 14, 14 vs one row-2 forward
        assertEquals(0.75, pref.confidence, 1e-9)
        assertEquals(4, pref.sampleSize)
    }

    @Test
    fun seatPreference_isRecencyWeighted_recentChoiceOutweighsStaleHabit() {
        val signals = listOf(
            // Two aisle picks from two years ago: weight 0.25 each = 0.5 total.
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "14C", timestamp = daysAgo(730)),
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "15D", timestamp = daysAgo(730)),
            // One window pick today: weight 1.0 — dominates.
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "14A"),
        )
        val pref = TravelProfileEngine.seatPreference(signals, now)
        assertEquals("window", pref?.position)
        assertEquals(1.0 / 1.5, pref!!.confidence, 1e-9)
        assertEquals(3, pref.sampleSize)
    }

    @Test
    fun seatPreference_nullWhenNothingParseable() {
        val signals = listOf(
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "garbage"),
            signal(TravelSignal.Kind.FLIGHT_FLOWN, value = "14F"),   // wrong kind
        )
        assertNull(TravelProfileEngine.seatPreference(signals, now))
    }

    // ── compute / profile assembly ───────────────────────────────────────────────────────────────

    @Test
    fun compute_assemblesAirlinesCabinAndHotelBrands() {
        val signals = listOf(
            signal(TravelSignal.Kind.SEAT_CHOSEN, value = "14F", attributes = mapOf("airline" to "Delta", "cabinHint" to "business")),
            signal(TravelSignal.Kind.FLIGHT_FLOWN, value = "DL1423", attributes = mapOf("airline" to "Delta", "cabinHint" to "business")),
            signal(TravelSignal.Kind.FLIGHT_FLOWN, value = "UA88", attributes = mapOf("airline" to "United", "cabinHint" to "economy")),
            signal(TravelSignal.Kind.LOYALTY_ADDED, value = "Marriott Bonvoy", attributes = mapOf("brandKind" to "hotel")),
            signal(TravelSignal.Kind.LOYALTY_ADDED, value = "Delta SkyMiles", attributes = mapOf("brandKind" to "airline")),
            signal(TravelSignal.Kind.PLACE_VISITED, value = "Kyoto"),
        )
        val trips = listOf(
            Trip(name = "T", destination = "Tokyo", startDate = "2026-07-01", endDate = "2026-07-05"),
        )
        val profile = TravelProfileEngine.compute(signals, trips, now)

        assertEquals(listOf("Delta", "United"), profile.topAirlines.map { it.value })
        assertEquals(2, profile.topAirlines[0].count)
        assertEquals("business", profile.preferredCabin)
        assertEquals(listOf("Marriott Bonvoy"), profile.topHotelBrands.map { it.value })
        assertEquals(setOf("Tokyo", "Kyoto"), profile.frequentCities.map { it.value }.toSet())
        assertEquals("window", profile.typicalSeat?.position)
        assertEquals(now.toString(), profile.generatedAt)
        assertTrue(!profile.isEmpty)
        assertTrue(profile.summaryForPrompt().isNotBlank())
    }

    @Test
    fun compute_leadDays_isMeanOfLeadDaysAttributes() {
        val signals = listOf(
            signal(TravelSignal.Kind.TRIP_COMPLETED, value = "Tokyo", attributes = mapOf("leadDays" to "10")),
            signal(TravelSignal.Kind.FLIGHT_FLOWN, value = "DL1423", attributes = mapOf("leadDays" to "21")),
        )
        val profile = TravelProfileEngine.compute(signals, emptyList(), now)
        assertEquals(16, profile.typicalBookingLeadDays)   // mean(10, 21) = 15.5 → 16
    }

    @Test
    fun emptyProfile_isEmptyAndSummarizesToEmptyString() {
        val profile = TravelProfileEngine.compute(emptyList(), emptyList(), now)
        assertTrue(profile.isEmpty)
        assertEquals("", profile.summaryForPrompt())
        // A hand-built default is empty too, whatever its generatedAt.
        assertTrue(TravelProfileData(generatedAt = now.toString()).isEmpty)
    }

    // ── wire contract ────────────────────────────────────────────────────────────────────────────

    @Test
    fun kindWireNames_areTheVerbatimCrossPlatformStrings() {
        val adapter = TravelSignalKindAdapter()
        val expected = mapOf(
            TravelSignal.Kind.SEAT_CHOSEN to "seatChosen",
            TravelSignal.Kind.FLIGHT_FLOWN to "flightFlown",
            TravelSignal.Kind.RECEIPT_SCANNED to "receiptScanned",
            TravelSignal.Kind.EXPENSE_LOGGED to "expenseLogged",
            TravelSignal.Kind.LOYALTY_ADDED to "loyaltyAdded",
            TravelSignal.Kind.TRIP_COMPLETED to "tripCompleted",
            TravelSignal.Kind.PLACE_VISITED to "placeVisited",
            TravelSignal.Kind.SUGGESTION_FEEDBACK to "suggestionFeedback",
        )
        assertEquals(expected.keys, TravelSignal.Kind.entries.toSet())
        for ((kind, wire) in expected) {
            assertEquals(wire, adapter.toJson(kind))
            assertEquals(kind, adapter.fromJson(wire))
        }
    }

    @Test
    fun travelSignal_roundTripsThroughMoshiWithCamelCaseKind() {
        val moshi = Moshi.Builder()
            .add(TravelSignalKindAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(TravelSignal::class.java)
        val original = TravelSignal(
            id = "abc",
            kind = TravelSignal.Kind.SEAT_CHOSEN,
            value = "14F",
            attributes = mapOf("airline" to "Delta"),
            timestamp = "2026-07-17T12:00:00Z",
            source = "checkin",
        )
        val json = adapter.toJson(original)
        assertTrue("kind must serialize as its wire name: $json", json.contains("\"kind\":\"seatChosen\""))
        assertEquals(original, adapter.fromJson(json))
    }
}
