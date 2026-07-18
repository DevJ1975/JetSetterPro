package com.jetsetter.pro.core.travel

/**
 * Deterministic bag-delivery estimate (spec §3.2) — the Android counterpart of the iOS estimator.
 * Consumed by the rideOnLanding proactive trigger and the IRIS `flightActions(luggageStatus)` tool.
 * Pure JVM: fixed ranges keyed only on airport tier, no clocks or network.
 */
object BagClaimEstimator {

    /** Minutes from gate arrival until bags typically reach the carousel. */
    data class Estimate(
        val minMinutes: Int,
        val maxMinutes: Int,
        /** Midpoint of [minMinutes]..[maxMinutes]. */
        val expectedMinutes: Int,
        /** Short machine-ish reason for the range (which rule fired). */
        val basis: String,
        /** User-facing one-liner. */
        val display: String,
    )

    /** Tier-1 mega-hubs where sprawling bag rooms push delivery to the 20–35 min band. */
    val TIER1_HUBS: Set<String> = setOf(
        "ATL", "DFW", "ORD", "LAX", "JFK", "DEN", "SFO", "LAS", "SEA", "MIA",
        "EWR", "BOS", "MCO", "CLT", "IAH", "LHR", "CDG", "FRA", "AMS", "DXB",
        "HND", "NRT", "SIN", "HKG", "ICN", "PEK", "PVG",
    )

    /**
     * Estimates bag delivery at [airportIata] (case-insensitive). No checked bag → 0/0/0.
     * Tier-1 hub → 20–35 min; everywhere else → 12–25 min. `expectedMinutes` is the midpoint.
     */
    fun estimate(airportIata: String, hasCheckedBag: Boolean): Estimate {
        if (!hasCheckedBag) {
            return Estimate(
                minMinutes = 0,
                maxMinutes = 0,
                expectedMinutes = 0,
                basis = "No checked bag",
                display = "No checked bags — head straight out.",
            )
        }
        val code = airportIata.trim().uppercase()
        return if (code in TIER1_HUBS) {
            range(20, 35, code, basis = "Tier-1 hub ($code)")
        } else {
            range(12, 25, code, basis = "Standard airport ($code)")
        }
    }

    private fun range(min: Int, max: Int, code: String, basis: String) = Estimate(
        minMinutes = min,
        maxMinutes = max,
        expectedMinutes = (min + max) / 2,
        basis = basis,
        display = "Bags at $code usually take $min–$max min to reach the carousel.",
    )
}
