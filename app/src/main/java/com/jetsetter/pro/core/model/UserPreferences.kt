package com.jetsetter.pro.core.model

/** User-facing settings (subset of the iOS `UserPreferences`). Persisted via DataStore. */
data class UserPreferences(
    val displayName: String = "",
    val homeAirport: String = "",
    // Default to the premium dark theme (matches the navy splash/brand; user-switchable in More).
    val theme: ThemePreference = ThemePreference.DARK,
    /** First-run gate: false until the user finishes (or skips) onboarding. */
    val hasCompletedOnboarding: Boolean = false,
    /**
     * Presentation mode: the app runs on the curated demo traveler's data. Enabling it re-seeds
     * every module to a pristine, self-consistent dataset (see `core.data.demo.DemoSeeder`).
     */
    val demoMode: Boolean = false,
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }
