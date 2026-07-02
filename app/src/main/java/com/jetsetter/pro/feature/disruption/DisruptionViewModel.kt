package com.jetsetter.pro.feature.disruption

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.notifications.JetNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisruptionViewModel @Inject constructor(
    private val repository: DisruptionRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(DisruptionUiState(isLoading = true))
    val ui: StateFlow<DisruptionUiState> = _ui.asStateFlow()

    private var progressJob: Job? = null

    init {
        viewModelScope.launch {
            val flight = repository.monitoredFlight()
            val alternatives = scoreAlternatives(repository.alternatives())
            val seeds = repository.responseStepSeeds(flight, alternatives)
            val recommendedId = alternatives.firstOrNull { it.isRecommended }?.id
            val decision = repository.readDecision()

            if (decision?.isRebooked == true) {
                // A rebooking was already committed in a previous session: restore the finished
                // state directly, no animation replay.
                val chosenId = alternatives.firstOrNull { it.id == decision.selectedAlternativeId }?.id
                    ?: recommendedId
                val chosen = alternatives.firstOrNull { it.id == chosenId }
                _ui.update {
                    it.copy(
                        isLoading = false,
                        monitoredFlight = flight.copy(status = DisruptionFlightStatus.REBOOKED),
                        alternatives = alternatives,
                        responseSteps = completedSteps(seeds, chosen),
                        selectedAlternativeId = chosenId,
                        recommendedAlternativeId = recommendedId,
                        isRebooked = true,
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        monitoredFlight = flight,
                        alternatives = alternatives,
                        responseSteps = seeds,
                        selectedAlternativeId = recommendedId,
                        recommendedAlternativeId = recommendedId,
                    )
                }
                startProgress()
            }
        }
    }

    /** Animates the four automated timeline steps to DONE, then parks the 5th step ACTIVE. */
    private fun startProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val autoCount = (_ui.value.responseSteps.size - 1).coerceAtLeast(0)
            for (index in 0 until autoCount) {
                setStepStatus(index, DisruptionStepStatus.ACTIVE)
                delay(STEP_DELAY_MS)
                setStepStatus(index, DisruptionStepStatus.DONE)
                // "Traveler notified" is real, not just timeline copy: a push lands in the shade
                // (silently skipped when the notification permission hasn't been granted).
                if (_ui.value.responseSteps.getOrNull(index)?.id == "step-notified") {
                    postDisruptionNotification()
                }
            }
            // The confirmation step is "active" while it waits on the traveler.
            val confirmIndex = _ui.value.responseSteps.lastIndex
            if (confirmIndex >= 0) setStepStatus(confirmIndex, DisruptionStepStatus.ACTIVE)
        }
    }

    private fun setStepStatus(index: Int, status: DisruptionStepStatus) {
        _ui.update { state ->
            if (index !in state.responseSteps.indices) {
                state
            } else {
                state.copy(
                    responseSteps = state.responseSteps.mapIndexed { i, step ->
                        if (i == index) step.copy(status = status) else step
                    },
                )
            }
        }
    }

    /** Re-orders the alternative list. Ignored once locked by a confirmed rebooking. */
    fun setSortMode(mode: DisruptionSortMode) {
        _ui.update { it.copy(sortMode = mode) }
    }

    /** Highlight a candidate flight; ignored once a rebooking is confirmed or in flight. */
    fun selectAlternative(id: String) {
        val state = _ui.value
        if (state.isRebooked || state.isRebooking) return
        _ui.update { it.copy(selectedAlternativeId = id) }
    }

    /**
     * Confirm the auto-rebooking for the selected alternative: show a brief in-flight state, then
     * mark every response step done, flip the monitored flight to REBOOKED, lock the selection,
     * and persist the decision so it survives a restart.
     */
    fun confirmRebooking() {
        val state = _ui.value
        if (state.isRebooked || state.isRebooking || state.selectedAlternativeId == null) return
        progressJob?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(isRebooking = true) }
            delay(REBOOK_DELAY_MS)
            val chosen = _ui.value.selectedAlternative
            _ui.update { current ->
                current.copy(
                    isRebooking = false,
                    isRebooked = true,
                    monitoredFlight = current.monitoredFlight?.copy(status = DisruptionFlightStatus.REBOOKED),
                    responseSteps = completedSteps(current.responseSteps, chosen),
                )
            }
            repository.saveDecision(
                DisruptionDecision(selectedAlternativeId = chosen?.id, isRebooked = true),
            )
        }
    }

    /** Builds the shade alert from the monitored flight so it can't contradict the screen. */
    private fun postDisruptionNotification() {
        val flight = _ui.value.monitoredFlight ?: return
        val alternatives = _ui.value.alternatives
        JetNotifier.postDisruptionAlert(
            context = appContext,
            title = "${flight.flightNumber} delayed ${durationLabel(flight.delayMinutes)}",
            text = "${flight.origin} → ${flight.destination} now departs ${flight.revisedDeparture}. " +
                "IRIS found ${alternatives.size} rebooking options — open Trip Disruption to confirm one.",
        )
    }

    /** Marks all steps DONE and rewrites the final step into the confirmation receipt. */
    private fun completedSteps(
        steps: List<DisruptionResponseStep>,
        chosen: DisruptionAlternative?,
    ): List<DisruptionResponseStep> = steps.mapIndexed { index, step ->
        if (index == steps.lastIndex) {
            step.copy(
                title = "Rebooking confirmed",
                detail = chosen?.let {
                    "Booked ${it.carrier} · ${it.cabin}, departs ${it.departureTime}"
                } ?: "Itinerary updated automatically",
                status = DisruptionStepStatus.DONE,
            )
        } else {
            step.copy(status = DisruptionStepStatus.DONE)
        }
    }

    /**
     * Assigns each alternative a normalized 0..1 "best value" score (cheaper + earlier arrival =
     * higher) and flags the single highest scorer as the AI recommendation. The score is real and
     * derived from the list itself, so it stays consistent with the prices/times shown.
     */
    private fun scoreAlternatives(raw: List<DisruptionAlternative>): List<DisruptionAlternative> {
        if (raw.isEmpty()) return raw
        val minPrice = raw.minOf { it.price }
        val maxPrice = raw.maxOf { it.price }
        val minArrival = raw.minOf { it.arrivalMin }
        val maxArrival = raw.maxOf { it.arrivalMin }
        val priceSpan = (maxPrice - minPrice).takeIf { it > 0.0 }
        val arrivalSpan = (maxArrival - minArrival).takeIf { it > 0 }

        val scored = raw.map { alt ->
            val priceScore = priceSpan?.let { (maxPrice - alt.price) / it } ?: 1.0
            val arrivalScore = arrivalSpan?.let { (maxArrival - alt.arrivalMin).toDouble() / it } ?: 1.0
            alt.copy(valueScore = PRICE_WEIGHT * priceScore + ARRIVAL_WEIGHT * arrivalScore)
        }
        val bestId = scored.maxByOrNull { it.valueScore }?.id
        return scored.map { it.copy(isRecommended = it.id == bestId) }
    }

    private companion object {
        const val STEP_DELAY_MS = 650L
        const val REBOOK_DELAY_MS = 900L
        const val PRICE_WEIGHT = 0.55
        const val ARRIVAL_WEIGHT = 0.45
    }
}
