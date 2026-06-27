package com.jetsetter.pro.feature.travelessentials

/**
 * A short phrase a traveller can use on the ground, with a rough phonetic hint
 * and its English meaning. Names are module-prefixed to avoid collisions.
 */
data class TravelEssentialsPhrase(
    val phrase: String,
    val pronunciation: String,
    val meaning: String,
)

/**
 * All the at-a-glance "before you land" facts for a single destination.
 * Pure data only (no Compose / icon types) so it can live in the repository layer.
 *
 * Time-zone and currency are stored as the *primitives* every calculation derives from
 * ([zoneId], [exchangeRatePerUsd]) rather than as pre-baked display strings, so the
 * live clock, time-difference badge and currency converter can never contradict the data.
 */
data class TravelEssentialsDestination(
    val id: String,
    val city: String,
    val country: String,
    val flag: String,
    val languages: String,
    /** Short zone abbreviation, e.g. "JST". The "UTC±h" suffix is derived from [zoneId]. */
    val timeZoneAbbrev: String,
    /**
     * IANA time-zone id (e.g. "Asia/Tokyo", "Europe/Paris"). Drives the live clock and the
     * "ahead/behind you" badge, with the current UTC offset resolved DST-aware via java.time
     * so it stays correct year-round (no hardcoded standard-time offset to drift in summer).
     */
    val zoneId: String,
    val plugType: String,
    val voltage: String,
    val currency: String,
    val currencyCode: String,
    /** Symbol shown beside converted amounts, e.g. "¥". */
    val currencySymbol: String,
    /** Fraction digits to render for this currency (JPY = 0, EUR/USD = 2). */
    val currencyDecimals: Int,
    /** How many local units one US dollar buys — the single source of truth for the converter. */
    val exchangeRatePerUsd: Double,
    val emergencyGeneral: String,
    val emergencyPolice: String,
    val emergencyMedical: String,
    /** One-word tipping norm used as a status chip, e.g. "Expected" / "Optional" / "Not customary". */
    val tippingNorm: String,
    val tipping: String,
    val tapWaterSafe: Boolean,
    val tapWaterNote: String,
    val phrases: List<TravelEssentialsPhrase>,
)
