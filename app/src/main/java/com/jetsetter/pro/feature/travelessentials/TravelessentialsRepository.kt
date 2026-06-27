package com.jetsetter.pro.feature.travelessentials

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory source of destination essentials. Mock-first: no network, no storage —
 * just a curated, realistic data set so the screen is fully usable for a beta tester.
 *
 * Zone ids and FX rates are stored as primitives so the screen's live clock and currency
 * converter compute from them directly (no hard-coded display strings that could drift).
 * The clock resolves each [TravelEssentialsDestination.zoneId]'s UTC offset DST-aware via
 * java.time, so summer/winter time is always correct.
 */
@Singleton
class TravelessentialsRepository @Inject constructor() {

    private val destinations: List<TravelEssentialsDestination> = listOf(
        TravelEssentialsDestination(
            id = "tokyo",
            city = "Tokyo",
            country = "Japan",
            flag = "🇯🇵",
            languages = "Japanese",
            timeZoneAbbrev = "JST",
            zoneId = "Asia/Tokyo",
            plugType = "Type A / Type B",
            voltage = "100 V · 50/60 Hz",
            currency = "Japanese Yen",
            currencyCode = "JPY",
            currencySymbol = "¥",
            currencyDecimals = 0,
            exchangeRatePerUsd = 157.0,
            emergencyGeneral = "110 / 119",
            emergencyPolice = "110",
            emergencyMedical = "119 (fire & ambulance)",
            tippingNorm = "Not customary",
            tipping = "Not customary — tipping can even be seen as rude.",
            tapWaterSafe = true,
            tapWaterNote = "Tap water is clean and safe to drink nationwide.",
            phrases = listOf(
                TravelEssentialsPhrase("Konnichiwa", "kon-nee-chee-wah", "Hello"),
                TravelEssentialsPhrase("Arigatou gozaimasu", "ah-ree-gah-toh go-zai-mas", "Thank you"),
                TravelEssentialsPhrase("Sumimasen", "soo-mee-mah-sen", "Excuse me / Sorry"),
                TravelEssentialsPhrase("Ikura desu ka?", "ee-koo-rah des-kah", "How much is it?"),
                TravelEssentialsPhrase("Eigo o hanasemasu ka?", "ay-go oh hah-nah-seh-mas-kah", "Do you speak English?"),
            ),
        ),
        TravelEssentialsDestination(
            id = "paris",
            city = "Paris",
            country = "France",
            flag = "🇫🇷",
            languages = "French",
            timeZoneAbbrev = "CET",
            zoneId = "Europe/Paris",
            plugType = "Type C / Type E",
            voltage = "230 V · 50 Hz",
            currency = "Euro",
            currencyCode = "EUR",
            currencySymbol = "€",
            currencyDecimals = 2,
            exchangeRatePerUsd = 0.92,
            emergencyGeneral = "112",
            emergencyPolice = "17",
            emergencyMedical = "15 (SAMU)",
            tippingNorm = "Optional",
            tipping = "Service is included; rounding up or ~5% is appreciated.",
            tapWaterSafe = true,
            tapWaterNote = "Tap water is safe — ask for 'une carafe d'eau' for free.",
            phrases = listOf(
                TravelEssentialsPhrase("Bonjour", "bon-zhoor", "Hello"),
                TravelEssentialsPhrase("Merci", "mair-see", "Thank you"),
                TravelEssentialsPhrase("S'il vous plaît", "seel-voo-play", "Please"),
                TravelEssentialsPhrase("Parlez-vous anglais?", "par-lay-voo on-glay", "Do you speak English?"),
                TravelEssentialsPhrase("L'addition, s'il vous plaît", "lah-dee-syon", "The bill, please"),
            ),
        ),
        TravelEssentialsDestination(
            id = "newyork",
            city = "New York",
            country = "United States",
            flag = "🇺🇸",
            languages = "English",
            timeZoneAbbrev = "EST",
            zoneId = "America/New_York",
            plugType = "Type A / Type B",
            voltage = "120 V · 60 Hz",
            currency = "US Dollar",
            currencyCode = "USD",
            currencySymbol = "$",
            currencyDecimals = 2,
            exchangeRatePerUsd = 1.0,
            emergencyGeneral = "911",
            emergencyPolice = "911",
            emergencyMedical = "911",
            tippingNorm = "Expected",
            tipping = "Expected: 15-20% at restaurants, \$1-2 per drink at bars.",
            tapWaterSafe = true,
            tapWaterNote = "NYC tap water is famously high quality and safe to drink.",
            phrases = listOf(
                TravelEssentialsPhrase("How's it going?", "—", "Casual hello"),
                TravelEssentialsPhrase("Can I get a check?", "—", "Asking for the bill"),
                TravelEssentialsPhrase("Which way to the subway?", "—", "Finding the metro"),
                TravelEssentialsPhrase("Keep the change", "—", "Leaving a tip"),
            ),
        ),
        TravelEssentialsDestination(
            id = "dubai",
            city = "Dubai",
            country = "United Arab Emirates",
            flag = "🇦🇪",
            languages = "Arabic · English",
            timeZoneAbbrev = "GST",
            zoneId = "Asia/Dubai",
            plugType = "Type G",
            voltage = "230 V · 50 Hz",
            currency = "UAE Dirham",
            currencyCode = "AED",
            currencySymbol = "د.إ",
            currencyDecimals = 2,
            exchangeRatePerUsd = 3.67,
            emergencyGeneral = "999",
            emergencyPolice = "999",
            emergencyMedical = "998 (ambulance)",
            tippingNorm = "Optional",
            tipping = "10-15% is appreciated; often a service charge is already added.",
            tapWaterSafe = true,
            tapWaterNote = "Desalinated tap water is safe; most locals still prefer bottled.",
            phrases = listOf(
                TravelEssentialsPhrase("Marhaba", "mar-ha-ba", "Hello"),
                TravelEssentialsPhrase("Shukran", "shoo-kran", "Thank you"),
                TravelEssentialsPhrase("Min fadlak", "min fad-lak", "Please"),
                TravelEssentialsPhrase("Kam howa?", "kam hoo-wa", "How much is it?"),
            ),
        ),
        TravelEssentialsDestination(
            id = "mexicocity",
            city = "Mexico City",
            country = "Mexico",
            flag = "🇲🇽",
            languages = "Spanish",
            timeZoneAbbrev = "CST",
            zoneId = "America/Mexico_City",
            plugType = "Type A / Type B",
            voltage = "127 V · 60 Hz",
            currency = "Mexican Peso",
            currencyCode = "MXN",
            currencySymbol = "$",
            currencyDecimals = 2,
            exchangeRatePerUsd = 17.2,
            emergencyGeneral = "911",
            emergencyPolice = "911",
            emergencyMedical = "911",
            tippingNorm = "Expected",
            tipping = "10-15% at restaurants; small change for porters and drivers.",
            tapWaterSafe = false,
            tapWaterNote = "Don't drink the tap water — stick to bottled or filtered.",
            phrases = listOf(
                TravelEssentialsPhrase("Hola", "oh-lah", "Hello"),
                TravelEssentialsPhrase("Gracias", "grah-syas", "Thank you"),
                TravelEssentialsPhrase("Por favor", "por fah-vor", "Please"),
                TravelEssentialsPhrase("¿Cuánto cuesta?", "kwan-toh kwes-tah", "How much is it?"),
                TravelEssentialsPhrase("La cuenta, por favor", "lah kwen-tah", "The bill, please"),
            ),
        ),
    )

    /** Streams the available destinations. Mock data, emitted once. */
    fun observeDestinations(): Flow<List<TravelEssentialsDestination>> = flowOf(destinations)

    /** Suspend accessor for the same mock data set. */
    suspend fun getDestinations(): List<TravelEssentialsDestination> = destinations
}
