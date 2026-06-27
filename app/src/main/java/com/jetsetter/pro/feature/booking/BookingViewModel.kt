package com.jetsetter.pro.feature.booking

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
class BookingViewModel @Inject constructor(
    private val repository: BookingRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(BookingUiState(isLoading = true))
    val ui: StateFlow<BookingUiState> = _ui.asStateFlow()

    init {
        // Restore persisted prefs first, then fold them onto the freshly loaded results so the
        // header, favorites and selection render in their saved state on first frame.
        viewModelScope.launch {
            val prefs = repository.loadPreferences()
            val favorites = prefs.favoriteIds.toSet()
            val results = repository.searchHotels()
                .map { it.copy(isFavorite = it.id in favorites) }
            val sortMode = runCatching { SortMode.valueOf(prefs.sortMode) }
                .getOrDefault(SortMode.RECOMMENDED)
            val selectedId = prefs.selectedHotelId?.takeIf { id -> results.any { it.id == id } }

            _ui.update {
                it.copy(
                    summary = repository.searchSummary(),
                    hotels = results,
                    sortMode = sortMode,
                    selectedHotelId = selectedId,
                    isLoading = false,
                )
            }
        }
    }

    /** Selects a stay (tap again to deselect). Persisted so the choice survives a restart. */
    fun selectHotel(item: HotelBookingItem) {
        _ui.update { state ->
            state.copy(selectedHotelId = if (state.selectedHotelId == item.id) null else item.id)
        }
        persist()
    }

    /** Opens the detail bottom sheet for a result. */
    fun showDetail(item: HotelBookingItem) {
        _ui.update { it.copy(detailHotelId = item.id) }
    }

    /** Dismisses the detail bottom sheet. */
    fun dismissDetail() {
        _ui.update { it.copy(detailHotelId = null) }
    }

    /** Changes the result ordering (persisted). */
    fun setSortMode(mode: SortMode) {
        _ui.update { it.copy(sortMode = mode) }
        persist()
    }

    /** Updates the raw budget-filter text; validation/derivation lives in [BookingUiState]. */
    fun setBudget(text: String) {
        _ui.update { it.copy(maxNightlyBudget = text) }
    }

    /** Toggles the favorites-only filter (session-only, not persisted). */
    fun toggleFavoritesOnly() {
        _ui.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    /** Clears the transient session filters, leaving favorites/selection intact. */
    fun clearFilters() {
        _ui.update { it.copy(maxNightlyBudget = "", showFavoritesOnly = false) }
    }

    /** Toggles the saved/favorite state of a result (persisted). */
    fun toggleFavorite(item: HotelBookingItem) {
        _ui.update { state ->
            state.copy(
                hotels = state.hotels.map {
                    if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
                },
            )
        }
        persist()
    }

    /** Writes the current favorites / selection / sort to disk via the repository (Moshi). */
    private fun persist() {
        val state = _ui.value
        viewModelScope.launch {
            repository.savePreferences(
                BookingPreferences(
                    favoriteIds = state.hotels.filter { it.isFavorite }.map { it.id },
                    selectedHotelId = state.selectedHotelId,
                    sortMode = state.sortMode.name,
                ),
            )
        }
    }
}
