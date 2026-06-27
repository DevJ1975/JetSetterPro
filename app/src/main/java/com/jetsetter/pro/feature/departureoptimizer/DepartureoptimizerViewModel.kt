package com.jetsetter.pro.feature.departureoptimizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DepartureoptimizerViewModel @Inject constructor(
    private val repository: DepartureoptimizerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DepartureoptimizerUiState())
    val ui: StateFlow<DepartureoptimizerUiState> = _ui.asStateFlow()

    init {
        // Load persisted personal buffers before clearing the loading flag so the first real
        // render already reflects the traveler's saved preferences.
        viewModelScope.launch {
            val loaded = repository.load()
            _ui.update { it.copy(estimate = loaded, isLoading = false) }
        }
    }

    /** Re-roll the live drive + TSA estimates and fold the recomputed result into UI state. */
    fun refresh() {
        val state = _ui.value
        if (state.isRefreshing || state.isLoading) return
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true) }
            val next = repository.rollEstimate()
            _ui.update {
                it.copy(
                    estimate = next,
                    isRefreshing = false,
                    refreshCount = it.refreshCount + 1,
                    lastUpdatedLabel = "Updated just now",
                )
            }
        }
    }

    /** Presents the "Adjust buffers" sheet. */
    fun showAdjustSheet() {
        _ui.update { it.copy(showAdjustSheet = true) }
    }

    /** Dismisses the "Adjust buffers" sheet without persisting anything. */
    fun dismissAdjustSheet() {
        _ui.update { it.copy(showAdjustSheet = false) }
    }

    /**
     * Persists the traveler's edited personal buffers and folds the recomputed estimate back into
     * state. Callers pass already-validated, non-negative whole minutes. Closes the sheet on success.
     */
    fun updateBuffers(parkingMinutes: Int, gateMinutes: Int) {
        viewModelScope.launch {
            val next = repository.updateBuffers(parkingMinutes, gateMinutes)
            _ui.update { it.copy(estimate = next, showAdjustSheet = false) }
        }
    }
}
