package com.jetsetter.pro.core.model

/** User-facing settings (subset of the iOS `UserPreferences`). Persisted via DataStore. */
data class UserPreferences(
    val displayName: String = "",
    val homeAirport: String = "",
    // Default to the premium dark theme (matches the navy splash/brand; user-switchable in More).
    val theme: ThemePreference = ThemePreference.DARK,
    /** First-run gate: false until the user finishes (or skips) onboarding. */
    val hasCompletedOnboarding: Boolean = false,
    /** Opt-in: speak IRIS replies aloud (TTS). Off by default. */
    val ttsEnabled: Boolean = false,
    /** Master learning consent (spec §1.6): off = no travel signal is ever recorded. */
    val learningEnabled: Boolean = true,
    /** Gates receiptScanned/expenseLogged signals (only meaningful while [learningEnabled]). */
    val learnFromReceipts: Boolean = true,
    /** Gates seatChosen signals (only meaningful while [learningEnabled]). */
    val learnFromCheckIns: Boolean = true,
    /** Gates flightFlown/tripCompleted/placeVisited signals (only meaningful while [learningEnabled]). */
    val learnFromTrips: Boolean = true,
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }
