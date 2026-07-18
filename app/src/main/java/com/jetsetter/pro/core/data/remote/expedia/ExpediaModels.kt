package com.jetsetter.pro.core.data.remote.expedia

import com.squareup.moshi.Json

/**
 * Expedia Rapid wire DTOs (§2.3) — the lean slice the Hotel Booking feature consumes: the OAuth2
 * token payload plus, per property, the id/name and the nested room→rate→occupancy pricing that
 * carries a bookable total + currency. Wire naming is per-field `@Json(name = "snake_case")`
 * (the §2.1 convention — no global naming strategy). Every field is nullable/defaulted — Rapid
 * omits what a rate plan doesn't carry.
 *
 * Property images and star/guest ratings are NOT in the availability response — they live in the
 * separate Rapid *content* API, a future enrichment step — so the booking cards keep their local
 * placeholder art for live results too.
 */

/** `POST /identity/oauth2/v3/token` response (client-credentials grant). */
data class ExpediaTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    /** Lifetime in seconds from issuance (Rapid typically returns 1799). */
    @Json(name = "expires_in") val expiresInSeconds: Long? = null,
    @Json(name = "token_type") val tokenType: String? = null,
)

/** One property in the `GET v3/properties/availability` response array. */
data class ExpediaProperty(
    @Json(name = "property_id") val propertyId: String? = null,
    /** Not guaranteed by availability alone (names canonically live in the content API). */
    val name: String? = null,
    /** Rapid's relevance score for the request — not a guest rating. */
    val score: Double? = null,
    val rooms: List<ExpediaRoom> = emptyList(),
)

/** A bookable room type with its rate plans. */
data class ExpediaRoom(
    val id: String? = null,
    @Json(name = "room_name") val roomName: String? = null,
    val rates: List<ExpediaRate> = emptyList(),
)

/** One rate plan; pricing is keyed by occupancy (e.g. `"2"` = two adults). */
data class ExpediaRate(
    val id: String? = null,
    val refundable: Boolean? = null,
    @Json(name = "occupancy_pricing") val occupancyPricing: Map<String, ExpediaOccupancyPricing> = emptyMap(),
)

/** Pricing for one occupancy — only the stay totals are modeled. */
data class ExpediaOccupancyPricing(
    val totals: ExpediaTotals? = null,
)

/** Stay totals; `inclusive` = base + taxes + fees (what the traveler pays). */
data class ExpediaTotals(
    val inclusive: ExpediaPricingByCurrency? = null,
)

/** An amount quoted in the currency the request asked for. */
data class ExpediaPricingByCurrency(
    @Json(name = "request_currency") val requestCurrency: ExpediaAmount? = null,
)

/** Rapid quotes money as a decimal string plus an ISO-4217 code. */
data class ExpediaAmount(
    val value: String? = null,
    val currency: String? = null,
)

// ── Pure derivation (unit-tested in ExpediaTokenLogicTest) ───────────────────

/** The cheapest usable all-in quote for a stay at one property. */
data class ExpediaStayPrice(
    /** Inclusive total for the whole stay (not per night). */
    val total: Double,
    /** Uppercased ISO-4217 code the total is quoted in. */
    val currency: String,
    val refundable: Boolean,
)

/**
 * The lowest inclusive stay total across every room/rate/occupancy of this property, or null when
 * no rate carries a usable quote. Junk amounts (missing, unparseable, non-positive, non-finite,
 * or without a currency code) are skipped so a malformed rate can never win the comparison.
 */
fun ExpediaProperty.lowestInclusiveTotal(): ExpediaStayPrice? =
    rooms.asSequence()
        .flatMap { room -> room.rates.asSequence() }
        .flatMap { rate ->
            rate.occupancyPricing.values.asSequence().mapNotNull { pricing ->
                val amount = pricing.totals?.inclusive?.requestCurrency ?: return@mapNotNull null
                val total = amount.value?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?: return@mapNotNull null
                val currency = amount.currency?.trim()?.uppercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                ExpediaStayPrice(total = total, currency = currency, refundable = rate.refundable == true)
            }
        }
        .minByOrNull { it.total }
