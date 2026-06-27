package com.jetsetter.pro.feature.groundtransport

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
class GroundtransportViewModel @Inject constructor(
    private val repository: GroundtransportRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(GroundtransportUiState(isLoading = true))
    val ui: StateFlow<GroundtransportUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val persisted = repository.loadState()
            val direction = runCatching { GroundTransportDirection.valueOf(persisted.directionName) }
                .getOrDefault(GroundTransportDirection.TO_AIRPORT)
            val sort = runCatching { GroundTransportSort.valueOf(persisted.sortName) }
                .getOrDefault(GroundTransportSort.PRICE)
            _ui.update {
                it.copy(
                    direction = direction,
                    passengers = persisted.passengers.coerceIn(1, it.maxPassengers),
                    sort = sort,
                    booking = persisted.booking,
                    pickup = repository.pickupLabel(direction),
                    dropoff = repository.dropoffLabel(direction),
                    airportName = repository.airportName,
                )
            }
            loadQuote(direction)
        }
    }

    /** Flip between the to-airport and from-airport quotes; re-fetches estimates for the new route. */
    fun selectDirection(direction: GroundTransportDirection) {
        if (direction == _ui.value.direction && !_ui.value.isLoading) return
        _ui.update {
            it.copy(
                direction = direction,
                pickup = repository.pickupLabel(direction),
                dropoff = repository.dropoffLabel(direction),
            )
        }
        loadQuote(direction)
        persist()
    }

    /** Set the party size; clamped to the supported range. Options re-derive their fit purely in state. */
    fun setPassengers(count: Int) {
        val clamped = count.coerceIn(1, _ui.value.maxPassengers)
        if (clamped == _ui.value.passengers) return
        _ui.update { it.copy(passengers = clamped) }
        persist()
    }

    /** Choose the list ordering (price vs. arrival). */
    fun setSort(sort: GroundTransportSort) {
        if (sort == _ui.value.sort) return
        _ui.update { it.copy(sort = sort) }
        persist()
    }

    /**
     * Mock "Book": shows the per-row spinner, then locks the *currently quoted* fare into an active
     * booking and persists it. Locking the quoted values keeps the confirmation self-consistent even
     * if the rider later changes direction or party size.
     */
    fun book(option: GroundTransportOption) {
        if (_ui.value.bookingInProgressId != null) return
        _ui.update { it.copy(bookingInProgressId = option.id) }
        viewModelScope.launch {
            delay(600)
            val booking = GroundTransportBooking(
                directionName = _ui.value.direction.name,
                optionId = option.id,
                service = option.service,
                priceLow = option.priceLow,
                priceHigh = option.priceHigh,
                etaMinutes = option.etaMinutes,
            )
            _ui.update { it.copy(booking = booking, bookingInProgressId = null) }
            persist()
        }
    }

    /** Cancels the active booking. */
    fun cancelBooking() {
        if (_ui.value.booking == null) return
        _ui.update { it.copy(booking = null) }
        persist()
    }

    private fun loadQuote(direction: GroundTransportDirection) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            val quote = repository.quote(direction)
            _ui.update {
                it.copy(
                    direction = direction,
                    options = quote.options,
                    distanceMiles = quote.distanceMiles,
                    rideMinutes = quote.rideMinutes,
                    pickup = repository.pickupLabel(direction),
                    dropoff = repository.dropoffLabel(direction),
                    airportName = repository.airportName,
                    isLoading = false,
                )
            }
        }
    }

    private fun persist() {
        viewModelScope.launch {
            val s = _ui.value
            repository.saveState(
                GroundTransportPersisted(
                    directionName = s.direction.name,
                    passengers = s.passengers,
                    sortName = s.sort.name,
                    booking = s.booking,
                ),
            )
        }
    }
}
