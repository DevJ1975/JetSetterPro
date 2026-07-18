package com.jetsetter.pro.core.intelligence

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import java.util.UUID

/**
 * One observed travel-behavior event (spec §1.6) — the raw material [TravelProfileEngine] distills
 * into a [TravelProfileData]. Signals are recorded on-device (seat picked at check-in, expense
 * logged, loyalty program added, …) and persisted as JSON; the JSON shape — field names, camelCase
 * [Kind] wire names, ISO-8601 [timestamp] — is a cross-platform contract shared with iOS.
 *
 * PRIVACY INVARIANT: signals and everything derived from them stay on-device (plus the user's own
 * cloud sync). They are never sent to third-party APIs.
 */
data class TravelSignal(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind,
    val value: String,
    val attributes: Map<String, String> = emptyMap(),
    val timestamp: String,   // ISO-8601, e.g. "2026-07-17T12:30:00Z"
    val source: String,      // emitting surface, e.g. "checkin", "expenses", "iris"
) {
    /**
     * What was observed. Declaration order is stable; [wireName] is the verbatim iOS-shared
     * serialization name — never rename either side independently.
     */
    enum class Kind(val wireName: String) {
        SEAT_CHOSEN("seatChosen"),
        FLIGHT_FLOWN("flightFlown"),
        RECEIPT_SCANNED("receiptScanned"),
        EXPENSE_LOGGED("expenseLogged"),
        LOYALTY_ADDED("loyaltyAdded"),
        TRIP_COMPLETED("tripCompleted"),
        PLACE_VISITED("placeVisited"),
        SUGGESTION_FEEDBACK("suggestionFeedback"),
        ;

        companion object {
            /** Resolves a camelCase wire name back to its [Kind], or `null` when unknown. */
            fun fromWireName(name: String): Kind? = entries.firstOrNull { it.wireName == name }
        }
    }

    /** Common attribute keys (spec §1.6) — use these constants, not ad-hoc strings. */
    object Attr {
        const val AIRLINE = "airline"
        const val CABIN_HINT = "cabinHint"
        const val CATEGORY = "category"
        const val AMOUNT = "amount"
        const val CURRENCY = "currency"
        const val CITY = "city"
        const val LEAD_DAYS = "leadDays"
        const val BRAND_KIND = "brandKind"
        const val ACCEPTED = "accepted"
    }
}

/**
 * Moshi adapter pinning [TravelSignal.Kind] to its camelCase wire name (spec §1.6) so persisted
 * JSON matches iOS byte-for-byte. Unknown names throw [JsonDataException]; store readers wrap
 * decoding in `runCatching` so a corrupt entry degrades to "no data", never a crash.
 */
class TravelSignalKindAdapter {

    @ToJson
    fun toJson(kind: TravelSignal.Kind): String = kind.wireName

    @FromJson
    fun fromJson(value: String): TravelSignal.Kind =
        TravelSignal.Kind.fromWireName(value)
            ?: throw JsonDataException("Unknown TravelSignal kind: '$value'")
}
