package com.jetsetter.pro.feature.travelwallet

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
class TravelWalletViewModel @Inject constructor(
    private val repository: TravelWalletRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TravelWalletUiState(isLoading = true))
    val ui: StateFlow<TravelWalletUiState> = _ui.asStateFlow()

    init {
        // Seed before collecting so the empty state doesn't flash on first run, then fold the
        // repository Flow into UI state — the canonical collect pattern used across the app.
        viewModelScope.launch {
            repository.seedIfEmpty()
            repository.observePasses().collect { passes ->
                _ui.update { state ->
                    // Drop a filter whose type no longer has any passes (e.g. last one deleted).
                    val filter = state.selectedFilter?.takeIf { type -> passes.any { it.type == type } }
                    state.copy(passes = passes, isLoading = false, selectedFilter = filter)
                }
            }
        }
    }

    /** Toggle the type filter; tapping the active chip clears it back to "All". */
    fun selectFilter(type: TravelWalletPassType?) {
        _ui.update { it.copy(selectedFilter = if (it.selectedFilter == type) null else type) }
    }

    fun showAddSheet() {
        _ui.update { it.copy(showAddSheet = true) }
    }

    fun dismissAddSheet() {
        _ui.update { it.copy(showAddSheet = false) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun deletePass(id: String) {
        viewModelScope.launch { repository.removePass(id) }
    }

    /**
     * Builds a [TravelWalletItem] from the sheet inputs and persists it. A blank title is ignored
     * so we never store an unnamed pass; the remaining fields trim and fall back to sensible
     * defaults (the type's own detail label / a dash). Flips [TravelWalletUiState.isSaving] for the
     * brief persist so the sheet can show progress, then closes the sheet.
     */
    fun addPass(
        type: TravelWalletPassType,
        title: String,
        subtitle: String,
        detailLabel: String,
        detailValue: String,
        date: String,
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return
        _ui.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.addPass(
                TravelWalletItem(
                    id = "wallet-user-${System.currentTimeMillis()}",
                    type = type,
                    title = trimmedTitle,
                    subtitle = subtitle.trim(),
                    keyDetailLabel = detailLabel.trim().ifEmpty { type.defaultDetailLabel },
                    keyDetailValue = detailValue.trim().ifEmpty { "—" },
                    date = date.trim().ifEmpty { "—" },
                ),
            )
            _ui.update { it.copy(isSaving = false, showAddSheet = false) }
        }
    }
}
