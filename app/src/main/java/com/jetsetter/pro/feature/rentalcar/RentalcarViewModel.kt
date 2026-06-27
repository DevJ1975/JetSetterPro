package com.jetsetter.pro.feature.rentalcar

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
class RentalcarViewModel @Inject constructor(
    private val repository: RentalcarRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(RentalcarUiState(isLoading = true))
    val ui: StateFlow<RentalcarUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // Restore tester-entered state first so the editor reflects it the moment offers land.
            val prefs = repository.loadPrefs()
            _ui.update {
                it.copy(
                    rentalDays = prefs.rentalDays,
                    daysInput = prefs.rentalDays.toString(),
                    selectedOfferId = prefs.selectedOfferId,
                    sort = prefs.sort,
                )
            }
            repository.observeOffers().collect { offers ->
                _ui.update { state ->
                    // Drop a stale selection if the restored id is no longer offered.
                    val validSelection = state.selectedOfferId?.takeIf { id -> offers.any { it.id == id } }
                    state.copy(offers = offers, selectedOfferId = validSelection, isLoading = false)
                }
            }
        }
    }

    /** Toggles selection of a rental offer; tapping the selected offer again clears it. */
    fun selectOffer(offer: RentalCarsOffer) {
        _ui.update {
            it.copy(selectedOfferId = if (it.selectedOfferId == offer.id) null else offer.id)
        }
        persist()
    }

    /** Re-orders the offer list. */
    fun setSort(sort: RentalCarsSort) {
        _ui.update { it.copy(sort = sort) }
        persist()
    }

    /**
     * Handles typing in the day editor. Keeps only digits and validates the range so every
     * displayed total stays driven by a clean integer; on an invalid entry we surface a
     * message and leave the last valid [RentalcarUiState.rentalDays] (and totals) untouched.
     */
    fun onDaysChange(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(3)
        val parsed = digits.toIntOrNull()
        _ui.update { state ->
            when {
                digits.isEmpty() ->
                    state.copy(daysInput = digits, daysError = "Enter 1–${repository.maxRentalDays} days")
                parsed == null || parsed < repository.minRentalDays ->
                    state.copy(daysInput = digits, daysError = "Minimum ${repository.minRentalDays} day")
                parsed > repository.maxRentalDays ->
                    state.copy(daysInput = digits, daysError = "Maximum ${repository.maxRentalDays} days")
                else ->
                    state.copy(daysInput = digits, rentalDays = parsed, daysError = null)
            }
        }
        persist()
    }

    /** Stepper: +1 day, clamped to the allowed range. */
    fun incrementDays() = stepDays(+1)

    /** Stepper: −1 day, clamped to the allowed range. */
    fun decrementDays() = stepDays(-1)

    private fun stepDays(delta: Int) {
        _ui.update { state ->
            val next = (state.rentalDays + delta)
                .coerceIn(repository.minRentalDays, repository.maxRentalDays)
            state.copy(rentalDays = next, daysInput = next.toString(), daysError = null)
        }
        persist()
    }

    /** Mirrors the current user-mutable state to disk so it survives a restart. */
    private fun persist() {
        val state = _ui.value
        viewModelScope.launch {
            repository.savePrefs(
                RentalcarPersistedState(
                    rentalDays = state.rentalDays,
                    selectedOfferId = state.selectedOfferId,
                    sort = state.sort,
                ),
            )
        }
    }
}
