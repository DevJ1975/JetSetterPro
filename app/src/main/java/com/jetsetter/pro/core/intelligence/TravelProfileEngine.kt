package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.util.IsoDates
import java.time.Duration
import java.time.Instant
import java.time.Month
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** A ranked value with its recency-decayed [weight] sum and raw observation [count] (spec §1.6). */
data class WeightedValue(
    val value: String,
    val weight: Double,
    val count: Int,
)

/**
 * A structurally-parsed airplane seat: [position] from the column letter, [zone] from the row.
 * Labels are the cross-platform strings "window"/"aisle"/"middle" and "forward"/"middle"/"rear".
 */
data class ParsedSeat(
    val row: Int,
    val column: Char,
    val position: String,
    val zone: String,
)

/** Duration/cadence/peak-month aggregate of the trip ledger (spec §1.6 "rhythm"). */
data class TripRhythm(
    val typicalTripDurationDays: Int? = null,
    val travelCadenceDays: Int? = null,
    val peakTravelMonths: List<String> = emptyList(),
)

/**
 * The deterministic travel-profile learning engine (spec §1.6) — pure math over [TravelSignal]s and
 * the trip ledger, no I/O, no model, fully unit-testable. Every observation carries a recency
 * weight with a 365-day half-life ([weight]); aspects are recency-weighted modes/rankings so recent
 * behavior dominates without old habits vanishing overnight.
 *
 * This engine supersedes the deleted 45-day `PreferenceScoring` math; the iOS spec's 365-day
 * half-life is the shared contract. Consumers get a [TravelProfileData] from [compute].
 */
object TravelProfileEngine {

    /** Days for an observation's influence to halve — the §1.6 cross-platform constant. */
    const val HALF_LIFE_DAYS = 365.0

    /** Rankings keep at most this many values (spec §1.6). */
    const val TOP_N = 5

    private const val MILLIS_PER_DAY = 86_400_000.0

    private val FLIGHT_KINDS = setOf(TravelSignal.Kind.SEAT_CHOSEN, TravelSignal.Kind.FLIGHT_FLOWN)
    private val SPEND_KINDS = setOf(TravelSignal.Kind.EXPENSE_LOGGED, TravelSignal.Kind.RECEIPT_SCANNED)
    private val SEAT_REGEX = Regex("""(\d{1,3})([A-Z])""")

    /** Recency weight of an observation [ageDays] old: `0.5^(ageDays / 365)` (spec §1.6). */
    fun weight(ageDays: Double): Double = 0.5.pow(ageDays / HALF_LIFE_DAYS)

    /**
     * Aggregates `(value, weight)` observations by value — summing weights, counting occurrences —
     * and returns the top [topN] by total weight, descending. Blank values are dropped; ties break
     * by raw count then value for determinism.
     */
    fun rankings(entries: List<Pair<String, Double>>, topN: Int = TOP_N): List<WeightedValue> =
        entries
            .mapNotNull { (raw, w) -> raw.trim().takeIf { it.isNotEmpty() }?.let { it to w } }
            .groupBy({ it.first }, { it.second })
            .map { (value, weights) -> WeightedValue(value, weights.sum(), weights.size) }
            .sortedWith(
                compareByDescending<WeightedValue> { it.weight }
                    .thenByDescending { it.count }
                    .thenBy { it.value },
            )
            .take(topN)

    /**
     * Parses a seat designator like `"14F"` (spec §1.6): columns A/F/K/L = window, C/D/G/H = aisle,
     * B/E = middle; rows 1–10 = forward, 11–25 = middle, 26+ = rear. Case-insensitive and trimmed.
     * Returns `null` for anything unparsable (bad shape, row 0, unmapped column such as I/J).
     */
    fun parseSeat(seat: String): ParsedSeat? {
        val match = SEAT_REGEX.matchEntire(seat.trim().uppercase(Locale.US)) ?: return null
        val row = match.groupValues[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val column = match.groupValues[2].single()
        val position = when (column) {
            'A', 'F', 'K', 'L' -> "window"
            'C', 'D', 'G', 'H' -> "aisle"
            'B', 'E' -> "middle"
            else -> return null
        }
        val zone = when {
            row <= 10 -> "forward"
            row <= 25 -> "middle"
            else -> "rear"
        }
        return ParsedSeat(row, column, position, zone)
    }

    /**
     * Recency-weighted mode of the traveler's seat choices over `seatChosen` signals: dominant
     * position + dominant zone, confidence = the dominant position's share of total weight,
     * sampleSize = parseable observations. `null` when nothing parseable was observed.
     */
    fun seatPreference(signals: List<TravelSignal>, now: Instant): SeatPreference? {
        val observations = signals
            .filter { it.kind == TravelSignal.Kind.SEAT_CHOSEN }
            .mapNotNull { s ->
                val seat = parseSeat(s.value) ?: return@mapNotNull null
                val w = signalWeight(s, now) ?: return@mapNotNull null
                seat to w
            }
        if (observations.isEmpty()) return null
        val total = observations.sumOf { it.second }
        if (total <= 0.0) return null

        val dominantPosition = dominantKey(observations.map { it.first.position to it.second }) ?: return null
        val dominantZone = dominantKey(observations.map { it.first.zone to it.second }) ?: return null
        return SeatPreference(
            position = dominantPosition.first,
            zone = dominantZone.first,
            confidence = dominantPosition.second / total,
            sampleSize = observations.size,
        )
    }

    /** Recency-weighted mode of `attributes["cabinHint"]` over flight signals, or `null`. */
    fun preferredCabin(signals: List<TravelSignal>, now: Instant): String? =
        rankings(
            signals
                .filter { it.kind in FLIGHT_KINDS }
                .mapNotNull { s ->
                    val cabin = s.attributes[TravelSignal.Attr.CABIN_HINT] ?: return@mapNotNull null
                    signalWeight(s, now)?.let { cabin to it }
                },
            topN = 1,
        ).firstOrNull()?.value

    /**
     * Top cities from trip destinations + `placeVisited` signal values, both recency-weighted
     * (trips by start date, signals by timestamp).
     */
    fun frequentCities(signals: List<TravelSignal>, trips: List<Trip>, now: Instant): List<WeightedValue> {
        val fromTrips = trips.mapNotNull { trip ->
            instantWeight(IsoDates.parseDateTime(trip.startDate), now)?.let { trip.destination to it }
        }
        val fromSignals = signals
            .filter { it.kind == TravelSignal.Kind.PLACE_VISITED }
            .mapNotNull { s -> signalWeight(s, now)?.let { s.value to it } }
        return rankings(fromTrips + fromSignals)
    }

    /**
     * Average spend per category+currency from `expenseLogged`/`receiptScanned` signal attributes
     * (amount, currency defaulting to USD, category). Mileage is excluded — it's a reimbursement
     * artifact, not spending behavior. Signals lacking a parseable amount or a category are skipped.
     * Sorted by count descending (most-observed habits first), then category, then currency.
     */
    fun spendByCategory(signals: List<TravelSignal>): List<SpendStat> =
        signals
            .filter { it.kind in SPEND_KINDS }
            .mapNotNull { s ->
                val amount = s.attributes[TravelSignal.Attr.AMOUNT]?.toDoubleOrNull() ?: return@mapNotNull null
                val category = s.attributes[TravelSignal.Attr.CATEGORY]?.trim()?.lowercase(Locale.US)
                    ?.takeIf { it.isNotEmpty() && it != "mileage" } ?: return@mapNotNull null
                val currency = s.attributes[TravelSignal.Attr.CURRENCY]?.trim()
                    ?.takeIf { it.isNotEmpty() }?.uppercase(Locale.US) ?: "USD"
                Triple(category, currency, amount)
            }
            .groupBy({ it.first to it.second }, { it.third })
            .map { (key, amounts) ->
                SpendStat(category = key.first, currency = key.second, average = amounts.average(), count = amounts.size)
            }
            .sortedWith(
                compareByDescending<SpendStat> { it.count }
                    .thenBy { it.category }
                    .thenBy { it.currency },
            )

    /**
     * Trip rhythm (spec §1.6): mean trip duration in days, mean gap between consecutive trip starts
     * (cadence — needs ≥2 dated trips), and peak months = start months appearing ≥2× once ≥3 dated
     * trips exist (most-frequent first, then calendar order). Trips with unparsable dates are
     * skipped; durations are `end - start` and negatives are ignored.
     */
    fun tripRhythm(trips: List<Trip>): TripRhythm {
        val dated = trips
            .mapNotNull { trip -> IsoDates.parseDate(trip.startDate)?.let { it to IsoDates.parseDate(trip.endDate) } }
            .sortedBy { it.first }
        if (dated.isEmpty()) return TripRhythm()

        val durations = dated.mapNotNull { (start, end) ->
            end?.let { ChronoUnit.DAYS.between(start, it) }?.takeIf { it >= 0 }
        }
        val starts = dated.map { it.first }
        val gaps = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        val peakMonths = if (dated.size >= 3) {
            starts
                .groupingBy { it.month }
                .eachCount()
                .filterValues { it >= 2 }
                .entries
                .sortedWith(compareByDescending<Map.Entry<Month, Int>> { it.value }.thenBy { it.key })
                .map { it.key.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
        } else {
            emptyList()
        }
        return TripRhythm(
            typicalTripDurationDays = durations.meanOrNull(),
            travelCadenceDays = gaps.meanOrNull(),
            peakTravelMonths = peakMonths,
        )
    }

    /** Assembles the full [TravelProfileData] from the signal log + trip ledger as of [now]. */
    fun compute(
        signals: List<TravelSignal>,
        trips: List<Trip>,
        now: Instant = Instant.now(),
    ): TravelProfileData {
        val airlineEntries = signals
            .filter { it.kind in FLIGHT_KINDS }
            .mapNotNull { s ->
                val airline = s.attributes[TravelSignal.Attr.AIRLINE] ?: return@mapNotNull null
                signalWeight(s, now)?.let { airline to it }
            }
        val hotelEntries = signals
            .filter {
                it.kind == TravelSignal.Kind.LOYALTY_ADDED &&
                    it.attributes[TravelSignal.Attr.BRAND_KIND] == "hotel"
            }
            .mapNotNull { s -> signalWeight(s, now)?.let { s.value to it } }
        val leadDays = signals
            .mapNotNull { it.attributes[TravelSignal.Attr.LEAD_DAYS]?.toDoubleOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.average()?.roundToInt()
        val rhythm = tripRhythm(trips)

        return TravelProfileData(
            typicalSeat = seatPreference(signals, now),
            topAirlines = rankings(airlineEntries),
            topHotelBrands = rankings(hotelEntries),
            frequentCities = frequentCities(signals, trips, now),
            preferredCabin = preferredCabin(signals, now),
            spendByCategory = spendByCategory(signals),
            typicalBookingLeadDays = leadDays,
            typicalTripDurationDays = rhythm.typicalTripDurationDays,
            travelCadenceDays = rhythm.travelCadenceDays,
            peakTravelMonths = rhythm.peakTravelMonths,
            generatedAt = now.toString(),
        )
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────────

    /** Recency weight of [signal] as of [now], or `null` when its timestamp is unparsable. */
    private fun signalWeight(signal: TravelSignal, now: Instant): Double? =
        instantWeight(IsoDates.parseDateTime(signal.timestamp), now)

    /** Weight for an event at [instant]; future timestamps clamp to age 0 (weight 1.0). */
    private fun instantWeight(instant: Instant?, now: Instant): Double? {
        val at = instant ?: return null
        val ageDays = (Duration.between(at, now).toMillis() / MILLIS_PER_DAY).coerceAtLeast(0.0)
        return weight(ageDays)
    }

    /** Highest-total-weight key with deterministic ties (weight desc, then key). */
    private fun dominantKey(entries: List<Pair<String, Double>>): Pair<String, Double>? =
        entries
            .groupBy({ it.first }, { it.second })
            .map { (key, weights) -> key to weights.sum() }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .firstOrNull()

    private fun List<Long>.meanOrNull(): Int? =
        takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}
