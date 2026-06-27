package com.jetsetter.pro.feature.flighttracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class FlighttrackerViewModel @Inject constructor(
    private val repository: FlighttrackerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FlighttrackerUiState(isLoading = true))
    val ui: StateFlow<FlighttrackerUiState> = _ui.asStateFlow()

    // Captured once when the screen opens so every flight's offset-based schedule resolves against
    // the same "now"; this keeps the whole board internally consistent for the session.
    private val nowMinuteOfDay: Int = LocalTime.now().let { it.hour * 60 + it.minute }

    init {
        // Seed the featured flight + full board before the tester interacts, so the live card never
        // flashes empty on first open. Recents are loaded from disk.
        viewModelScope.launch {
            val recents = repository.recentIdents()
            _ui.update {
                it.copy(
                    isLoading = false,
                    selected = repository.featuredFlight().snapshot(),
                    results = repository.allFlights().map { flight -> flight.snapshot() },
                    recentIdents = recents,
                    totalCount = repository.allFlights().size,
                )
            }
        }
    }

    /** Live ident filter — runs on every keystroke so the board narrows as the tester types. */
    fun onQueryChange(query: String) {
        _ui.update {
            it.copy(
                query = query,
                results = repository.filterByIdent(query).map { flight -> flight.snapshot() },
            )
        }
    }

    /** Clears the search field and restores the full board. */
    fun onClearQuery() {
        _ui.update {
            it.copy(query = "", results = repository.allFlights().map { flight -> flight.snapshot() })
        }
    }

    /** IME "search" action: track the exact match, else the first filtered result. No-op if none. */
    fun onSearch() {
        val query = _ui.value.query.trim()
        if (query.isEmpty()) return
        val match = repository.findByIdent(query) ?: repository.filterByIdent(query).firstOrNull() ?: return
        track(match)
    }

    /** Re-runs a persisted recent search: refills the field, re-filters, and tracks the flight. */
    fun onSelectRecent(ident: String) {
        val match = repository.findByIdent(ident) ?: return
        _ui.update {
            it.copy(
                query = match.ident,
                results = repository.filterByIdent(match.ident).map { flight -> flight.snapshot() },
            )
        }
        track(match)
    }

    /** Promotes a board row to the live-status card (and records it as a recent search). */
    fun onSelectFlight(snapshot: FlightSnapshot) {
        val match = repository.findByIdent(snapshot.ident) ?: return
        track(match)
    }

    /** Sets the live card and persists the flight as the newest recent search. */
    private fun track(flight: FlightTrackerFlight) {
        _ui.update { it.copy(selected = flight.snapshot()) }
        viewModelScope.launch {
            repository.pushRecent(flight.ident)
            _ui.update { it.copy(recentIdents = repository.recentIdents()) }
        }
    }

    private fun FlightTrackerFlight.snapshot(): FlightSnapshot = snapshotAt(nowMinuteOfDay)
}
