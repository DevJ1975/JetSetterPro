package com.jetsetter.pro.feature.irismemory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.intelligence.IrisMemory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the IRIS Memory & Privacy screen (plan A7): the inspect/forget UI over [IrisMemory]
 * (spec §1.5) plus the four learning-consent switches persisted on [UserPreferencesRepository]
 * (spec §1.6). The master toggle gates the three per-source switches — the screen renders them
 * disabled while it's off, and the store already treats them as no-ops.
 */
@HiltViewModel
class IrisMemoryViewModel @Inject constructor(
    private val irisMemory: IrisMemory,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(IrisMemoryUiState(isLoading = true))
    val ui: StateFlow<IrisMemoryUiState> = _ui.asStateFlow()

    init {
        // Fold the memory Flow + settings Flow into UI state — the canonical collect pattern
        // used across the app's ViewModels.
        viewModelScope.launch {
            combine(irisMemory.observe(), userPreferencesRepository.preferences) { prefs, settings ->
                prefs to settings
            }.collect { (prefs, settings) ->
                _ui.update { state ->
                    state.copy(
                        preferences = prefs,
                        learningEnabled = settings.learningEnabled,
                        learnFromReceipts = settings.learnFromReceipts,
                        learnFromCheckIns = settings.learnFromCheckIns,
                        learnFromTrips = settings.learnFromTrips,
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Remove a single remembered preference (per-row delete icon). */
    fun deletePreference(id: String) {
        viewModelScope.launch { irisMemory.delete(id) }
    }

    fun showForgetDialog() {
        _ui.update { it.copy(showForgetDialog = true) }
    }

    fun dismissForgetDialog() {
        _ui.update { it.copy(showForgetDialog = false) }
    }

    /** "Forget everything" — wipes the whole `iris_memory` store (confirmed via dialog). */
    fun forgetEverything() {
        _ui.update { it.copy(showForgetDialog = false) }
        viewModelScope.launch { irisMemory.forgetEverything() }
    }

    fun setLearningEnabled(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setLearningEnabled(value) }
    }

    fun setLearnFromReceipts(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setLearnFromReceipts(value) }
    }

    fun setLearnFromCheckIns(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setLearnFromCheckIns(value) }
    }

    fun setLearnFromTrips(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setLearnFromTrips(value) }
    }
}
