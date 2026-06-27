package com.jetsetter.pro.feature.carbontracker

/**
 * Single immutable UI-state object for the Carbon Footprint screen — one object rather than
 * scattered flags, so impossible states can't arise and previews are trivial (guide §6).
 *
 * The seeded [baseFlights] plus tester-entered [cabinOverrides] / [customLegs] are the source
 * of truth; every figure on screen is derived from them, so a number can never contradict the
 * inputs that produced it.
 */
data class CarbontrackerUiState(
    val isLoading: Boolean = false,
    val baseFlights: List<CarbonFlightEstimate> = emptyList(),
    /** Per-flight cabin overrides applied on top of [baseFlights], keyed by flight id. */
    val cabinOverrides: Map<String, CarbonCabinClass> = emptyMap(),
    val customLegs: List<CarbonFlightEstimate> = emptyList(),
    val offsets: List<CarbonOffsetSuggestion> = emptyList(),
    val selectedOffsetId: String? = null,
    val offsetPurchased: Boolean = false,
) {
    /** Effective trip: seeded legs with any cabin override applied, plus tester-added legs. */
    val flights: List<CarbonFlightEstimate>
        get() = baseFlights.map { f -> cabinOverrides[f.id]?.let { f.copy(cabinClass = it) } ?: f } + customLegs

    val isEmpty: Boolean get() = flights.isEmpty()

    /** Total estimated CO2 across every flight in the trip, in kg. */
    val tripTotalCo2Kg: Double get() = flights.sumOf { it.co2Kg }

    val totalDistanceKm: Int get() = flights.sumOf { it.distanceKm }

    /** Trip-wide intensity band used for the headline status chip. */
    val emissionLevel: CarbonEmissionLevel get() = tripLevel(tripTotalCo2Kg)

    /** Blended kg CO2 per km across the whole trip (driven by the chosen cabins). */
    val averageKgPerKm: Double get() = if (totalDistanceKm > 0) tripTotalCo2Kg / totalDistanceKm else 0.0

    /** The currently chosen offset programme, if any. */
    val selectedOffset: CarbonOffsetSuggestion? get() = offsets.firstOrNull { it.id == selectedOffsetId }

    /** Live cost to neutralize the current total via the selected programme, in USD. */
    val offsetCostUsd: Double
        get() = selectedOffset?.let { it.pricePerTonneUsd * tripTotalCo2Kg / 1000.0 } ?: 0.0

    /** Share of the trip currently neutralized (mock): all-or-nothing once purchased. */
    val offsetCoverage: Float get() = if (offsetPurchased && !isEmpty) 1f else 0f
}
