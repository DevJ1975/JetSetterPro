package com.jetsetter.pro.feature.localexperience

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
class LocalexperienceViewModel @Inject constructor(
    private val repository: LocalexperienceRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LocalexperienceUiState(isLoading = true))
    val ui: StateFlow<LocalexperienceUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val experiences = repository.loadExperiences()
            _ui.update { it.copy(experiences = experiences, isLoading = false) }
        }
        // Fold the persisted saved-id set into UI state; toggles persist and flow back through here.
        viewModelScope.launch {
            repository.observeSavedIds().collect { saved ->
                _ui.update { it.copy(savedIds = saved) }
            }
        }
    }

    /** Pass `null` to clear the filter and show every experience ("All"). */
    fun selectCategory(category: LocalExperiencesCategory?) {
        _ui.update { it.copy(selectedCategory = category) }
    }

    fun setSort(sort: LocalExperienceSort) {
        _ui.update { it.copy(sort = sort) }
    }

    /** Toggles the "Saved only" view. */
    fun toggleSavedOnly() {
        _ui.update { it.copy(showSavedOnly = !it.showSavedOnly) }
    }

    /**
     * Bookmarks / un-bookmarks one experience and persists the new set. State is not mutated
     * directly here — the repository write re-emits via [LocalexperienceRepository.observeSavedIds]
     * so there's a single source of truth.
     */
    fun toggleSaved(id: String) {
        val current = _ui.value.savedIds
        val updated = if (id in current) current - id else current + id
        viewModelScope.launch { repository.setSavedIds(updated) }
    }
}
