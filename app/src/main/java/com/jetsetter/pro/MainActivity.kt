package com.jetsetter.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.core.model.ThemePreference
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
            when (val state = startup) {
                StartupState.Loading -> Unit // splash is still on screen
                is StartupState.Loaded -> {
                    val prefs = state.preferences
                    val darkTheme = when (prefs.theme) {
                        ThemePreference.DARK -> true
                        ThemePreference.LIGHT -> false
                        ThemePreference.SYSTEM -> isSystemInDarkTheme()
                    }
                    JetSetterTheme(darkTheme = darkTheme) {
                        if (prefs.hasCompletedOnboarding) {
                            JetSetterApp()
                        } else {
                            OnboardingScreen()
                        }
                    }
                }
            }
        }
    }
}
