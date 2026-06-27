package com.jetsetter.pro.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AssistantUiState(isLoading = true))
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    init {
        // Fold the repository Flows into UI state — the canonical collect pattern used across the app.
        viewModelScope.launch {
            repository.observePrompts().collect { prompts ->
                _ui.update { it.copy(prompts = prompts, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.observeRecentIds().collect { ids ->
                _ui.update { it.copy(recentIds = ids) }
            }
        }
    }

    /** Tap a quick action: record it, show a brief "thinking" beat, then reveal the answer. */
    fun selectPrompt(id: String) {
        viewModelScope.launch {
            _ui.update { it.copy(selectedPromptId = id, response = null, isResponding = true) }
            repository.recordRecent(id)
            delay(420)
            // Ignore if the user dismissed/changed selection mid-think.
            if (_ui.value.selectedPromptId != id) return@launch
            _ui.update { it.copy(isResponding = false, response = repository.responseFor(id)) }
        }
    }

    /** Live-update the search/filter text as the user types. */
    fun onQueryChange(query: String) {
        _ui.update { it.copy(query = query) }
    }

    /** Dismiss the current answer and return to the quick-action grid. */
    fun clearResponse() {
        _ui.update { it.copy(selectedPromptId = null, response = null, isResponding = false) }
    }

    /** Clears the persisted "recently asked" list. */
    fun clearRecents() {
        viewModelScope.launch { repository.clearRecents() }
    }
}
