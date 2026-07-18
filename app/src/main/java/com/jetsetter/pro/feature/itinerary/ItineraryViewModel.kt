package com.jetsetter.pro.feature.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ItineraryUiState(isLoading = true))
    val ui: StateFlow<ItineraryUiState> = _ui.asStateFlow()

    init {
        // Seed before collecting so the empty state doesn't flash on first run, then fold the
        // repository Flow into UI state — the canonical guide §6 collect pattern.
        viewModelScope.launch {
            tripRepository.initSync()
            tripRepository.observeTrips()
                .catch { e -> _ui.update { it.copy(isLoading = false, errorMessage = e.message) } }
                .collect { trips -> _ui.update { it.copy(trips = trips, isLoading = false) } }
        }
    }

    fun togglePacked(trip: Trip, item: PackingItem) {
        val updated = trip.copy(
            packingList = trip.packingList.map {
                if (it.id == item.id) it.copy(isPacked = !it.isPacked) else it
            },
        )
        viewModelScope.launch { tripRepository.upsert(updated) }
    }

    /** Presents the "Add Trip" sheet. */
    fun showAddSheet() {
        _ui.update { it.copy(showAddSheet = true) }
    }

    /** Dismisses the "Add Trip" sheet without persisting anything. */
    fun dismissAddSheet() {
        _ui.update { it.copy(showAddSheet = false) }
    }

    /**
     * Builds a [Trip] from the sheet inputs and persists it via the repository (Room +
     * Supabase mirror). A blank name is ignored so we never create an unnamed trip; the
     * remaining fields fall back to sensible placeholders. Closes the sheet on success.
     */
    fun addTrip(name: String, destination: String, startDate: String, endDate: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            tripRepository.upsert(
                Trip(
                    name = trimmedName,
                    destination = destination.trim().ifEmpty { "TBD" },
                    startDate = startDate.trim(),
                    endDate = endDate.trim(),
                ),
            )
            _ui.update { it.copy(showAddSheet = false) }
        }
    }

    /** Deletes a trip by id (also removes it from the Supabase mirror when signed in). */
    fun delete(id: String) {
        viewModelScope.launch { tripRepository.delete(id) }
    }
}