package com.jetsetter.pro.feature.inflight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class InflightViewModel @Inject constructor(
    private val repository: InflightRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(InflightUiState(isLoading = true))
    val ui: StateFlow<InflightUiState> = _ui.asStateFlow()

    /** Source of truth for the simulation position; progress is derived from it. */
    private var elapsedSeconds: Int = 0
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            val flights = repository.flights()
            val flight = repository.flightById(settings.selectedFlightId)
            elapsedSeconds = (flight.initialProgress * flight.totalSeconds).roundToInt()
            val rate = if (settings.playbackRate in InflightRepository.RATES) {
                settings.playbackRate
            } else {
                InflightRepository.DEFAULT_RATE
            }
            _ui.update {
                it.copy(
                    isLoading = false,
                    flights = flights,
                    selectedFlightId = flight.id,
                    flight = flight,
                    status = repository.statusAt(flight, flight.initialProgress),
                    useMetric = settings.useMetric,
                    playbackRate = rate,
                )
            }
        }
    }

    /** Start the live telemetry simulation (no-op if already running or flight has landed). */
    fun play() {
        val flight = _ui.value.flight ?: return
        if (_ui.value.isPlaying) return
        if (elapsedSeconds >= flight.totalSeconds) return
        _ui.update { it.copy(isPlaying = true) }
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                val f = _ui.value.flight ?: break
                elapsedSeconds = (elapsedSeconds + _ui.value.playbackRate).coerceAtMost(f.totalSeconds)
                publish(f)
                if (elapsedSeconds >= f.totalSeconds) {
                    pause()
                    break
                }
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        ticker = null
        if (_ui.value.isPlaying) _ui.update { it.copy(isPlaying = false) }
    }

    fun togglePlay() {
        if (_ui.value.isPlaying) pause() else play()
    }

    /** Scrub directly to a position (0f..1f) — used by the progress slider; updates live. */
    fun seekTo(fraction: Float) {
        val flight = _ui.value.flight ?: return
        elapsedSeconds = (fraction.coerceIn(0f, 1f) * flight.totalSeconds).roundToInt()
        publish(flight)
    }

    /** Rewind the simulation to where the flight was when first opened. */
    fun reset() {
        val flight = _ui.value.flight ?: return
        pause()
        elapsedSeconds = (flight.initialProgress * flight.totalSeconds).roundToInt()
        publish(flight)
    }

    /** Switch the tracked flight; resets the simulation to that flight's opening position. */
    fun selectFlight(id: String) {
        if (id == _ui.value.selectedFlightId) return
        val flight = repository.flightById(id)
        pause()
        elapsedSeconds = (flight.initialProgress * flight.totalSeconds).roundToInt()
        _ui.update {
            it.copy(
                selectedFlightId = flight.id,
                flight = flight,
                status = repository.statusAt(flight, flight.initialProgress),
            )
        }
        persist()
    }

    /** Toggle the metrics between imperial (ft / mph / mi) and metric (m / km·h / km). */
    fun toggleUnits() {
        _ui.update { it.copy(useMetric = !it.useMetric) }
        persist()
    }

    fun setPlaybackRate(rate: Int) {
        if (rate !in InflightRepository.RATES || rate == _ui.value.playbackRate) return
        _ui.update { it.copy(playbackRate = rate) }
        persist()
    }

    private fun publish(flight: InFlightFlight) {
        val progress = elapsedSeconds.toFloat() / flight.totalSeconds
        _ui.update { it.copy(status = repository.statusAt(flight, progress)) }
    }

    /** Persist only user-set preferences (never the per-tick position) to avoid write storms. */
    private fun persist() {
        val s = _ui.value
        viewModelScope.launch {
            repository.saveSettings(
                InflightSettings(
                    selectedFlightId = s.selectedFlightId,
                    useMetric = s.useMetric,
                    playbackRate = s.playbackRate,
                ),
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TICK_MS = 1000L
    }
}
