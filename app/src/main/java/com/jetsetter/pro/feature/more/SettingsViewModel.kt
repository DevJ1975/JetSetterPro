package com.jetsetter.pro.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.demo.DemoSeeder
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.model.ThemePreference
import com.jetsetter.pro.core.model.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-startup gate. [Loading] until the first DataStore emission arrives, then [Loaded]
 * with the resolved preferences. The root reads this to choose theme + onboarding-vs-app
 * without flashing onboarding at returning users on the first frame.
 */
sealed interface StartupState {
    data object Loading : StartupState
    data class Loaded(val preferences: UserPreferences) : StartupState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val stateStore: ModuleStateStore,
    private val demoSeeder: DemoSeeder,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> =
        prefsRepository.preferences
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    /** Eagerly collected so the launch splash can be held until prefs are ready. */
    val startup: StateFlow<StartupState> =
        prefsRepository.preferences
            .map<UserPreferences, StartupState> { StartupState.Loaded(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, StartupState.Loading)

    // Live Features-menu filter. Persisted as JSON via ModuleStateStore (Moshi) — the same
    // idiom as Converters.kt / TraveljournalViewModel — so the last filter survives a restart.
    private val filterAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(MoreFilterState::class.java)

    private val _searchQuery = MutableStateFlow("")

    /** Combined screen state collected by [MoreScreen] via collectAsStateWithLifecycle. */
    val ui: StateFlow<MoreUiState> =
        combine(preferences, _searchQuery) { prefs, query ->
            MoreUiState(preferences = prefs, searchQuery = query)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    init {
        // Restore the last-used filter (if any) before the user starts typing.
        viewModelScope.launch {
            stateStore.read(KEY_FILTER)
                ?.let { json -> runCatching { filterAdapter.fromJson(json) }.getOrNull() }
                ?.let { saved -> _searchQuery.value = saved.searchQuery }
        }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { prefsRepository.setTheme(theme) }
    fun setDisplayName(value: String) = viewModelScope.launch { prefsRepository.setDisplayName(value) }
    fun setHomeAirport(value: String) = viewModelScope.launch { prefsRepository.setHomeAirport(value) }

    /** Demo mode on/off. Enabling re-seeds everything and arms the scripted disruption push. */
    fun setDemoMode(enabled: Boolean) = viewModelScope.launch {
        if (enabled) demoSeeder.enableDemoMode() else demoSeeder.disableDemoMode()
    }

    /** Restores the pristine demo dataset without touching the demo-mode switch. */
    fun resetDemoData() = viewModelScope.launch { demoSeeder.resetDemoData() }

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
        viewModelScope.launch { stateStore.save(KEY_FILTER, filterAdapter.toJson(MoreFilterState(value))) }
    }

    private companion object {
        const val KEY_FILTER = "more_feature_filter"
    }
}
