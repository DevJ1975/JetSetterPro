package com.jetsetter.pro.core.intelligence

import java.util.Locale
import kotlin.math.roundToInt

/**
 * A learned seat preference (spec §1.6): the recency-weighted mode of the traveler's chosen seat
 * [position] and cabin [zone], with [confidence] = the dominant position's share of total weight
 * (0..1) and [sampleSize] = number of parseable seat choices observed.
 */
data class SeatPreference(
    val position: String,   // "window" / "aisle" / "middle"
    val zone: String,       // "forward" / "middle" / "rear"
    val confidence: Double,
    val sampleSize: Int,
)

/**
 * Average spend per expense [category] + [currency] pair (spec §1.6), derived from
 * expenseLogged/receiptScanned signals. Mileage is never represented here.
 */
data class SpendStat(
    val category: String,   // lowercase spec category, e.g. "food"
    val currency: String,   // ISO code, e.g. "USD"
    val average: Double,
    val count: Int,
)

/**
 * The deterministic learned travel profile (spec §1.6) computed by [TravelProfileEngine] from
 * [TravelSignal]s + trips. Pure data — recomputed, never trained — and strictly on-device
 * (see [TravelSignal]'s privacy invariant). [summaryForPrompt] is the only sanctioned way to fold
 * it into an IRIS prompt.
 */
data class TravelProfileData(
    val typicalSeat: SeatPreference? = null,
    val topAirlines: List<WeightedValue> = emptyList(),
    val topHotelBrands: List<WeightedValue> = emptyList(),
    val frequentCities: List<WeightedValue> = emptyList(),
    val preferredCabin: String? = null,
    val spendByCategory: List<SpendStat> = emptyList(),
    val typicalBookingLeadDays: Int? = null,
    val typicalTripDurationDays: Int? = null,
    val travelCadenceDays: Int? = null,
    val peakTravelMonths: List<String> = emptyList(),   // English month names, e.g. "July"
    val generatedAt: String = "",                       // ISO-8601 timestamp
) {
    /** True when nothing has been learned — every derived aspect is absent. */
    val isEmpty: Boolean
        get() = typicalSeat == null &&
            topAirlines.isEmpty() &&
            topHotelBrands.isEmpty() &&
            frequentCities.isEmpty() &&
            preferredCabin == null &&
            spendByCategory.isEmpty() &&
            typicalBookingLeadDays == null &&
            typicalTripDurationDays == null &&
            travelCadenceDays == null &&
            peakTravelMonths.isEmpty()

    /**
     * Compact multi-line summary for the IRIS system prompt — one "- " bullet per learned aspect,
     * absent aspects omitted. Returns `""` when [isEmpty] (spec contract: empty profile contributes
     * nothing to the prompt).
     */
    fun summaryForPrompt(): String {
        if (isEmpty) return ""
        val lines = buildList {
            typicalSeat?.let { seat ->
                add(
                    "Typical seat: ${seat.position}, ${seat.zone} zone " +
                        "(confidence ${(seat.confidence * 100).roundToInt()}%, ${seat.sampleSize} flights)",
                )
            }
            preferredCabin?.let { add("Preferred cabin: $it") }
            if (topAirlines.isNotEmpty()) {
                add("Top airlines: ${topAirlines.joinToString(", ") { it.value }}")
            }
            if (topHotelBrands.isNotEmpty()) {
                add("Hotel brands: ${topHotelBrands.joinToString(", ") { it.value }}")
            }
            if (frequentCities.isNotEmpty()) {
                add("Frequent cities: ${frequentCities.joinToString(", ") { it.value }}")
            }
            if (spendByCategory.isNotEmpty()) {
                val stats = spendByCategory.joinToString("; ") { s ->
                    "${s.category} avg ${formatAmount(s.average)} ${s.currency} (${s.count} logged)"
                }
                add("Typical spend: $stats")
            }
            typicalBookingLeadDays?.let { add("Books about $it days ahead") }
            typicalTripDurationDays?.let { add("Typical trip length: $it days") }
            travelCadenceDays?.let { add("Travels about every $it days") }
            if (peakTravelMonths.isNotEmpty()) {
                add("Peak travel months: ${peakTravelMonths.joinToString(", ")}")
            }
        }
        return "Learned travel profile:\n" + lines.joinToString("\n") { "- $it" }
    }

    private fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)
}
