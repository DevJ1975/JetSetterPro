package com.jetsetter.pro.feature.visalookup

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
class VisalookupViewModel @Inject constructor(
    private val repository: VisalookupRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(VisalookupUiState(isLoading = true))
    val ui: StateFlow<VisalookupUiState> = _ui.asStateFlow()

    // Moshi adapter for the persisted planner slice, matching the Converters.kt / ModuleStateStore idiom.
    private val plannerAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(VisaPlannerState::class.java)

    init {
        // Load the bundled destinations, then overlay any persisted planner state so the tester's
        // last selection, trip inputs and ticked documents survive a restart. We deliberately do
        // NOT auto-select a destination on first run — the screen opens on the clear "select a
        // destination" empty state.
        viewModelScope.launch {
            val destinations = repository.getDestinations()
            val saved = stateStore.read(KEY_PLANNER)?.let { json ->
                runCatching { plannerAdapter.fromJson(json) }.getOrNull()
            }
            _ui.update {
                it.copy(
                    destinations = destinations,
                    selectedId = saved?.selectedId?.takeIf { id -> destinations.any { d -> d.id == id } },
                    tripLengthDays = saved?.tripLengthDays.orEmpty(),
                    passportExpiry = saved?.passportExpiry.orEmpty(),
                    checkedDocs = saved?.checkedDocs.orEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    /** User tapped a destination row — swap the selection and persist it. */
    fun selectDestination(id: String) {
        _ui.update { it.copy(selectedId = id) }
        persist()
    }

    /** Returns to the empty "select a destination" state. */
    fun clearSelection() {
        _ui.update { it.copy(selectedId = null) }
        persist()
    }

    /** Live search over the destination list (not persisted). */
    fun onQueryChange(value: String) {
        _ui.update { it.copy(query = value) }
    }

    /** Trip length input — digits only, capped so a stray value can't break the layout. */
    fun onTripLengthChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(3)
        _ui.update { it.copy(tripLengthDays = digits) }
        persist()
    }

    /** Passport expiry input — restricted to date characters. */
    fun onPassportExpiryChange(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '-' }.take(10)
        _ui.update { it.copy(passportExpiry = cleaned) }
        persist()
    }

    /** Toggle a document on the current destination's readiness checklist, then persist. */
    fun toggleDocument(label: String) {
        val id = _ui.value.selectedId ?: return
        _ui.update { state ->
            val current = state.checkedDocs[id].orEmpty()
            val updated = if (label in current) current - label else current + label
            state.copy(checkedDocs = state.checkedDocs + (id to updated))
        }
        persist()
    }

    /** Snapshot the persistable slice of state and write it as JSON. */
    private fun persist() {
        val s = _ui.value
        val snapshot = VisaPlannerState(
            selectedId = s.selectedId,
            tripLengthDays = s.tripLengthDays,
            passportExpiry = s.passportExpiry,
            checkedDocs = s.checkedDocs,
        )
        viewModelScope.launch {
            stateStore.save(KEY_PLANNER, plannerAdapter.toJson(snapshot))
        }
    }

    private companion object {
        const val KEY_PLANNER = "visalookup_planner"
    }
}
