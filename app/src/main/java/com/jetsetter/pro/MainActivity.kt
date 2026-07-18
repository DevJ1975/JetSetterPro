package com.jetsetter.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.core.model.ThemePreference
import com.jetsetter.pro.core.model.UserPreferences
import com.jetsetter.pro.feature.more.SettingsViewModel
import com.jetsetter.pro.feature.more.StartupState
import com.jetsetter.pro.feature.onboarding.OnboardingScreen
import com.jetsetter.pro.ui.JetSetterApp
import com.jetsetter.pro.ui.theme.JetSetterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Activity-scoped so the splash keep-condition can read its startup state in onCreate.
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until DataStore yields the first value, so a returning user never
        // sees a flash of onboarding before their saved preferences load.
        splash.setKeepOnScreenCondition { settingsViewModel.startup.value is StartupState.Loading }
        enableEdgeToEdge()
        setContent {
            val startup by settingsViewModel.startup.collectAsStateWithLifecycle()

            // Nav-reset guard: once onboarding is complete and the main shell has been chosen,
            // LATCH that decision (and the prefs backing it). A transient re-emission of
            // Loading — or a default-prefs flash with hasCompletedOnboarding=false — must
            // never swap back to splash/onboarding, because that tears down JetSetterApp and
            // its NavController, silently collapsing the back stack to Home. Genuine first-run
            // behavior is preserved: the latch only engages after onboarding completes, so a
            // fresh install still sees OnboardingScreen and enters the app on completion.
            var latchedPrefs by remember { mutableStateOf<UserPreferences?>(null) }
            (startup as? StartupState.Loaded)?.preferences
                ?.takeIf { it.hasCompletedOnboarding }
                ?.let { latchedPrefs = it }

            val prefs = latchedPrefs ?: (startup as? StartupState.Loaded)?.preferences
            if (prefs != null) {
                val darkTheme = when (prefs.theme) {
                    ThemePreference.DARK -> true
                    ThemePreference.LIGHT -> false
                    ThemePreference.SYSTEM -> isSystemInDarkTheme()
                }
                JetSetterTheme(darkTheme = darkTheme) {
                    if (latchedPrefs != null) {
                        JetSetterApp()
                    } else {
                        OnboardingScreen()
                    }
                }
            } // else StartupState.Loading and nothing latched — splash is still on screen
        }
    }
}
