package com.jetsetter.pro.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jetsetter.pro.core.model.ThemePreference
import com.jetsetter.pro.core.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/** Persists user settings via Jetpack DataStore (replaces the iOS `UserPreferences`/UserDefaults). */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val displayName = stringPreferencesKey("display_name")
        val homeAirport = stringPreferencesKey("home_airport")
        val theme = stringPreferencesKey("theme")
        val hasCompletedOnboarding = booleanPreferencesKey("has_completed_onboarding")
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val learningEnabled = booleanPreferencesKey("learning_enabled")
        val learnFromReceipts = booleanPreferencesKey("learn_from_receipts")
        val learnFromCheckIns = booleanPreferencesKey("learn_from_check_ins")
        val learnFromTrips = booleanPreferencesKey("learn_from_trips")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            displayName = prefs[Keys.displayName] ?: "",
            homeAirport = prefs[Keys.homeAirport] ?: "",
            theme = runCatching { ThemePreference.valueOf(prefs[Keys.theme] ?: ThemePreference.DARK.name) }
                .getOrDefault(ThemePreference.DARK),
            hasCompletedOnboarding = prefs[Keys.hasCompletedOnboarding] ?: false,
            ttsEnabled = prefs[Keys.ttsEnabled] ?: false,
            learningEnabled = prefs[Keys.learningEnabled] ?: true,
            learnFromReceipts = prefs[Keys.learnFromReceipts] ?: true,
            learnFromCheckIns = prefs[Keys.learnFromCheckIns] ?: true,
            learnFromTrips = prefs[Keys.learnFromTrips] ?: true,
        )
    }

    suspend fun setDisplayName(value: String) = context.dataStore.edit { it[Keys.displayName] = value }
    suspend fun setHomeAirport(value: String) = context.dataStore.edit { it[Keys.homeAirport] = value }
    suspend fun setTheme(value: ThemePreference) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setHasCompletedOnboarding(value: Boolean) =
        context.dataStore.edit { it[Keys.hasCompletedOnboarding] = value }
    suspend fun setTtsEnabled(value: Boolean) = context.dataStore.edit { it[Keys.ttsEnabled] = value }
    suspend fun setLearningEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.learningEnabled] = value }
    suspend fun setLearnFromReceipts(value: Boolean) =
        context.dataStore.edit { it[Keys.learnFromReceipts] = value }
    suspend fun setLearnFromCheckIns(value: Boolean) =
        context.dataStore.edit { it[Keys.learnFromCheckIns] = value }
    suspend fun setLearnFromTrips(value: Boolean) =
        context.dataStore.edit { it[Keys.learnFromTrips] = value }
}
