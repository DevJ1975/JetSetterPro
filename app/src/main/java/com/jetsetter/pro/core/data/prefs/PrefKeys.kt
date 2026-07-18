package com.jetsetter.pro.core.data.prefs

/**
 * Canonical local-store keys shared with iOS (spec §2.5). These are the *cross-platform contract*
 * — the exact strings iOS uses for its persisted stores — so they must never be renamed. All are
 * persisted through [ModuleStateStore] (JSON payloads for collections, marker strings for flags).
 */
object PrefKeys {
    /** Loved Ones contact list (§3.3), JSON list of LovedOne. */
    const val LOVED_ONES = "jetsetter_loved_ones"

    /** Travel-signal log (§1.6), JSON list capped at 2000 FIFO. */
    const val TRAVEL_SIGNALS = "jetsetter_travel_signals"

    /** Cached generated traveler persona (§1.6), plain text. */
    const val TRAVEL_PERSONA = "jetsetter_travel_persona"

    /** IRIS preference memory (§1.5), JSON list of IrisPreference. */
    const val IRIS_MEMORY = "iris_memory"

    /** Flag: user booked a ride to the airport — suppresses the rideToAirport nudge (§1.9). */
    const val UBER_BOOKED = "uber_booked"

    /** Flag: user booked a ride on landing — suppresses the rideOnLanding nudge (§1.9). */
    const val RIDE_ON_LANDING_BOOKED = "ride_on_landing_booked"
}
