package com.jetsetter.pro

import com.jetsetter.pro.core.model.ThemePreference
import com.jetsetter.pro.core.model.UserPreferences
import com.jetsetter.pro.feature.more.StartupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the defaults the onboarding gate relies on: a brand-new user must start with onboarding
 * incomplete and the premium dark theme (brand default), and [StartupState.Loaded] must round-trip
 * the resolved preferences the root reads. Pure-JVM (no Android deps), like `MockDataTest`.
 */
class UserPreferencesDefaultsTest {

    @Test
    fun defaults_showOnboardingAndDarkTheme() {
        val prefs = UserPreferences()
        assertFalse("First run must show onboarding", prefs.hasCompletedOnboarding)
        assertEquals(ThemePreference.DARK, prefs.theme)
        assertEquals("", prefs.displayName)
        assertEquals("", prefs.homeAirport)
    }

    @Test
    fun startupLoaded_carriesResolvedPreferences() {
        val prefs = UserPreferences(hasCompletedOnboarding = true, displayName = "Jamil")
        val state = StartupState.Loaded(prefs)
        assertEquals(prefs, state.preferences)
    }
}
