package com.jetsetter.pro.feature.flightboard

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

@HiltViewModel
class FlightboardViewModel @Inject constructor(
    private val repository: FlightboardRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        FlightboardUiState(
            airportName = repository.airportName,
            airportCode = repository.airportCode,
            isLoading = true,
        ),
    )
    val ui: StateFlow<FlightboardUiState> = _ui.asStateFlow()

    // The direction / sort / status-filter selection is persisted as a small JSON object so a
    // tester's view survives app restarts (the search query is intentionally transient). Moshi
    // serialises the enums by name — the same idiom Converters.kt uses for nested model lists.
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val prefsAdapter = moshi.adapter(FlightBoardPrefs::class.java)

    init {
        // Load the mock board once, overlay the saved view controls, then drop the loading flag.
        viewModelScope.launch {
            val prefs = readPrefs()
            val flights = repository.getFlights()
            _ui.update {
                it.copy(
                    allFlights = flights,
                    direction = prefs.direction,
                    sort = prefs.sort,
                    statusFilter = prefs.statusFilter,
                    isLoading = false,
                )
            }
        }
    }

    /** Switch between the Departures and Arrivals views. */
    fun selectDirection(direction: FlightBoardDirection) {
        if (_ui.value.direction == direction) return
        _ui.update { it.copy(direction = direction) }
        persist()
    }

    /** Change the row ordering (by time or grouped by status). */
    fun selectSort(sort: FlightBoardSort) {
        if (_ui.value.sort == sort) return
        _ui.update { it.copy(sort = sort) }
        persist()
    }

    /** Toggle a status filter; tapping the already-active status clears it (back to "All"). */
    fun toggleStatusFilter(status: FlightBoardStatus) {
        _ui.update { it.copy(statusFilter = if (it.statusFilter == status) null else status) }
        persist()
    }

    /** Live search over flight number / city / airline. Not persisted. */
    fun updateQuery(query: String) {
        _ui.update { it.copy(query = query) }
    }

    /** Reset both the status filter and the search query. */
    fun clearFilters() {
        _ui.update { it.copy(statusFilter = null, query = "") }
        persist()
    }

    private fun persist() {
        val s = _ui.value
        val prefs = FlightBoardPrefs(
            direction = s.direction,
            sort = s.sort,
            statusFilter = s.statusFilter,
        )
        viewModelScope.launch { stateStore.save(PREFS_KEY, prefsAdapter.toJson(prefs)) }
    }

    /** Read the persisted view controls; falls back to defaults when absent or unreadable. */
    private suspend fun readPrefs(): FlightBoardPrefs {
        val json = stateStore.read(PREFS_KEY) ?: return FlightBoardPrefs()
        return runCatching { prefsAdapter.fromJson(json) }.getOrNull() ?: FlightBoardPrefs()
    }

    private companion object {
        const val PREFS_KEY = "flightboard_prefs"
    }
}

/** Persisted slice of [FlightboardUiState] — the user's view controls, serialised via Moshi. */
internal data class FlightBoardPrefs(
    val direction: FlightBoardDirection = FlightBoardDirection.DEPARTURES,
    val sort: FlightBoardSort = FlightBoardSort.TIME,
    val statusFilter: FlightBoardStatus? = null,
)
