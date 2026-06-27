package com.jetsetter.pro.feature.carbontracker

import java.util.UUID

/**
 * Economy baseline emission factor: ~0.09 kg CO2 per passenger-km on a typical jet — the
 * mock-friendly equivalent of the factor real calculators (ICAO / DEFRA) start from.
 */
const val ECONOMY_KG_PER_KM = 0.09

/**
 * Cabin class with a per-passenger emissions multiplier — premium seats take more space, so
 * each passenger is allocated a larger share of the aircraft's fuel burn.
 */
enum class CarbonCabinClass(val label: String, val emissionMultiplier: Double) {
    ECONOMY("Economy", 1.0),
    PREMIUM("Premium", 1.6),
    BUSINESS("Business", 2.9),
    FIRST("First", 4.0);

    /** This cabin's per-passenger emission factor, in kg CO2 per km. */
    val kgPerKm: Double get() = ECONOMY_KG_PER_KM * emissionMultiplier
}

/** Relative intensity band for a CO2 figure — drives the status chip + colour. */
enum class CarbonEmissionLevel(val label: String) {
    LOW("Low impact"),
    MODERATE("Moderate"),
    HIGH("High impact"),
}

/** A single flight leg with its estimated per-passenger CO2 output. */
data class CarbonFlightEstimate(
    val id: String = UUID.randomUUID().toString(),
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val distanceKm: Int,
    val cabinClass: CarbonCabinClass,
    /** True for tester-added legs (removable); false for the seeded sample trip. */
    val isCustom: Boolean = false,
    /** Optional display label for custom legs; mock legs derive their route from the codes. */
    val routeLabel: String = "",
) {
    val route: String get() = routeLabel.ifBlank { "$origin → $destination" }

    /** Real, self-consistent figure: distance × this cabin's emission factor. */
    val co2Kg: Double get() = carbonEstimateKg(distanceKm.toDouble(), cabinClass)

    val level: CarbonEmissionLevel get() = flightLevel(co2Kg)
}

/** A purchasable offset programme that neutralizes emissions at a given price per tonne. */
data class CarbonOffsetSuggestion(
    val id: String,
    val title: String,
    val provider: String,
    val description: String,
    val pricePerTonneUsd: Double,
)

/**
 * Tester-mutable state persisted between launches via
 * [com.jetsetter.pro.core.data.prefs.ModuleStateStore] (serialized with Moshi, mirroring the
 * Converters idiom). Enum values are stored as names so a future enum change can't crash a
 * restore. Flight ids are stable in the repository, so a restored override still matches.
 */
data class CarbonPersistedState(
    /** Per-flight cabin overrides, keyed by flight id (cabin stored as enum name). */
    val cabinOverrides: Map<String, String> = emptyMap(),
    val customLegs: List<CarbonPersistedLeg> = emptyList(),
    val selectedOffsetId: String? = null,
    val offsetPurchased: Boolean = false,
)

/** Persisted form of a tester-added leg. */
data class CarbonPersistedLeg(
    val id: String,
    val routeLabel: String,
    val distanceKm: Int,
    val cabin: String,
)

/** Per-passenger flight emissions estimate, in kg CO2: distance × cabin factor. */
fun carbonEstimateKg(distanceKm: Double, cabin: CarbonCabinClass): Double =
    distanceKm * cabin.kgPerKm

/** Safe enum parse for a restored cabin name; falls back to Economy on anything unexpected. */
fun cabinClassOf(name: String?): CarbonCabinClass =
    name?.let { runCatching { CarbonCabinClass.valueOf(it) }.getOrNull() } ?: CarbonCabinClass.ECONOMY

/** Per-flight intensity bands (kg CO2 per passenger, single leg). */
fun flightLevel(kg: Double): CarbonEmissionLevel = when {
    kg < 150.0 -> CarbonEmissionLevel.LOW
    kg < 400.0 -> CarbonEmissionLevel.MODERATE
    else -> CarbonEmissionLevel.HIGH
}

/** Whole-trip intensity bands (summed kg CO2 across every leg). */
fun tripLevel(kg: Double): CarbonEmissionLevel = when {
    kg < 500.0 -> CarbonEmissionLevel.LOW
    kg < 1500.0 -> CarbonEmissionLevel.MODERATE
    else -> CarbonEmissionLevel.HIGH
}

/** A mature tree sequesters roughly 21 kg CO2 per year. */
fun treeYearsFor(kg: Double): Int = (kg / 21.0).toInt().coerceAtLeast(1)

/** An average petrol car emits roughly 0.17 kg CO2 per km driven. */
fun carKmFor(kg: Double): Int = (kg / 0.17).toInt()
