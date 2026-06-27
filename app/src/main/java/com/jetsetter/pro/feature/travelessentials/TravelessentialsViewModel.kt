package com.jetsetter.pro.feature.travelessentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Persisted slice of this module's state. The chosen city is a real user preference, so we keep
 * it across restarts via [ModuleStateStore] using the same Moshi idiom Room uses in
 * `core/data/local/Converters.kt`.
 */
data class TravelEssentialsPrefs(
    val selectedDestinationId: String? = null,
)

@HiltViewModel
class TravelessentialsViewModel @Inject constructor(
    private val repository: TravelessentialsRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val prefsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(TravelEssentialsPrefs::class.java)

    private val _ui = MutableStateFlow(TravelessentialsUiState(isLoading = true))
    val ui: StateFlow<TravelessentialsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // Restore the last-viewed city (if still valid) before folding in the data set, so the
            // tester returns to where they left off rather than always snapping back to the first.
            val savedId = runCatching {
                stateStore.read(KEY)?.let { prefsAdapter.fromJson(it)?.selectedDestinationId }
            }.getOrNull()

            repository.observeDestinations().collect { list ->
                _ui.update { state ->
                    val resolved = state.selectedDestinationId
                        ?: savedId?.takeIf { id -> list.any { it.id == id } }
                        ?: list.firstOrNull()?.id
                    state.copy(
                        isLoading = false,
                        destinations = list,
                        selectedDestinationId = resolved,
                    )
                }
            }
        }
    }

    /** Switch the destination whose essentials are shown. Collapses phrases for a clean reset. */
    fun selectDestination(id: String) {
        _ui.update { it.copy(selectedDestinationId = id, phrasesExpanded = false) }
        viewModelScope.launch {
            runCatching { stateStore.save(KEY, prefsAdapter.toJson(TravelEssentialsPrefs(id))) }
        }
    }

    /** Expand / collapse the useful-phrases list. */
    fun togglePhrases() {
        _ui.update { it.copy(phrasesExpanded = !it.phrasesExpanded) }
    }

    private companion object {
        const val KEY = "travel_essentials_prefs"
    }
}
