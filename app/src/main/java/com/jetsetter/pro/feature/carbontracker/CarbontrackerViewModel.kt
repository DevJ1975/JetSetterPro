package com.jetsetter.pro.feature.carbontracker

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
class CarbontrackerViewModel @Inject constructor(
    private val repository: CarbontrackerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CarbontrackerUiState(isLoading = true))
    val ui: StateFlow<CarbontrackerUiState> = _ui.asStateFlow()

    /** Distance bounds surfaced to the "add a leg" editor for client-side validation. */
    val minLegDistanceKm: Int = repository.minLegDistanceKm
    val maxLegDistanceKm: Int = repository.maxLegDistanceKm

    init {
        viewModelScope.launch {
            // Restore tester-entered state first so overrides/legs are reflected the moment data lands.
            val prefs = repository.loadPrefs()
            val flights = repository.loadFlights()
            val offsets = repository.loadOffsets()
            // Drop a stale selection if the restored programme no longer exists; default to the first.
            val selected = prefs.selectedOffsetId?.takeIf { id -> offsets.any { it.id == id } }
                ?: offsets.firstOrNull()?.id
            _ui.update {
                it.copy(
                    isLoading = false,
                    baseFlights = flights,
                    cabinOverrides = prefs.cabinOverrides.mapValues { (_, v) -> cabinClassOf(v) },
                    customLegs = prefs.customLegs.map { leg -> leg.toEstimate() },
                    offsets = offsets,
                    selectedOffsetId = selected,
                    offsetPurchased = prefs.offsetPurchased,
                )
            }
        }
    }

    /**
     * Changes a leg's cabin class. This recomputes that leg's CO2 and the trip total live, and
     * invalidates any prior offset purchase since the footprint to neutralize just changed.
     */
    fun onFlightCabinChanged(flightId: String, cabin: CarbonCabinClass) {
        _ui.update { state ->
            if (state.customLegs.any { it.id == flightId }) {
                state.copy(
                    customLegs = state.customLegs.map {
                        if (it.id == flightId) it.copy(cabinClass = cabin) else it
                    },
                    offsetPurchased = false,
                )
            } else {
                state.copy(
                    cabinOverrides = state.cabinOverrides + (flightId to cabin),
                    offsetPurchased = false,
                )
            }
        }
        persist()
    }

    /** Appends a tester-defined leg to the trip from the estimator inputs (distance clamped). */
    fun onAddLeg(routeLabel: String, distanceKm: Int, cabin: CarbonCabinClass) {
        val clamped = distanceKm.coerceIn(repository.minLegDistanceKm, repository.maxLegDistanceKm)
        val leg = CarbonFlightEstimate(
            flightNumber = "Added leg",
            origin = "",
            destination = "",
            distanceKm = clamped,
            cabinClass = cabin,
            isCustom = true,
            routeLabel = routeLabel.trim().ifEmpty { "Custom leg" },
        )
        _ui.update { it.copy(customLegs = it.customLegs + leg, offsetPurchased = false) }
        persist()
    }

    /** Removes a tester-added leg. Seeded sample legs can't be removed. */
    fun onRemoveLeg(flightId: String) {
        _ui.update {
            it.copy(
                customLegs = it.customLegs.filterNot { leg -> leg.id == flightId },
                offsetPurchased = false,
            )
        }
        persist()
    }

    /** Chooses an offset programme; switching programmes resets a prior purchase. */
    fun onOffsetSelected(offsetId: String) {
        _ui.update { state ->
            if (state.selectedOffsetId == offsetId) state
            else state.copy(selectedOffsetId = offsetId, offsetPurchased = false)
        }
        persist()
    }

    /** Toggles a (mock) purchase of the selected programme to neutralize the current total. */
    fun onToggleOffsetPurchased() {
        _ui.update { state ->
            if (state.selectedOffset == null || state.isEmpty) state
            else state.copy(offsetPurchased = !state.offsetPurchased)
        }
        persist()
    }

    /** Mirrors the current user-mutable state to disk so it survives a restart. */
    private fun persist() {
        val state = _ui.value
        viewModelScope.launch {
            repository.savePrefs(
                CarbonPersistedState(
                    cabinOverrides = state.cabinOverrides.mapValues { (_, v) -> v.name },
                    customLegs = state.customLegs.map {
                        CarbonPersistedLeg(it.id, it.routeLabel, it.distanceKm, it.cabinClass.name)
                    },
                    selectedOffsetId = state.selectedOffsetId,
                    offsetPurchased = state.offsetPurchased,
                ),
            )
        }
    }
}

/** Rehydrates a persisted leg into the runtime model used by the UI. */
private fun CarbonPersistedLeg.toEstimate(): CarbonFlightEstimate = CarbonFlightEstimate(
    id = id,
    flightNumber = "Added leg",
    origin = "",
    destination = "",
    distanceKm = distanceKm,
    cabinClass = cabinClassOf(cabin),
    isCustom = true,
    routeLabel = routeLabel,
)
