package com.jetsetter.pro.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.intelligence.TravelProfileStore
import com.jetsetter.pro.core.intelligence.TravelSignal
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val repository: CheckinRepository,
    private val stateStore: ModuleStateStore,
    private val travelProfileStore: TravelProfileStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(CheckinUiState(isLoading = true))
    val ui: StateFlow<CheckinUiState> = _ui.asStateFlow()

    // Persist the issued boarding assignments as a JSON List<PersistedCheckIn> so a tester's
    // check-ins (and the exact boarding group/position they were given) survive app restarts.
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val recordsType = Types.newParameterizedType(List::class.java, PersistedCheckIn::class.java)
    private val recordsAdapter = moshi.adapter<List<PersistedCheckIn>>(recordsType)

    init {
        // Seed from the mock repository (anchored to the real clock), then overlay any persisted
        // boarding passes on top so prior check-ins reappear exactly as issued.
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val flights = repository.loadUpcomingFlights(now)
            val records = readRecords().associateBy { it.flightId }
            _ui.update {
                it.copy(
                    flights = flights.map { flight -> flight.applyRecord(records[flight.id]) },
                    nowMillis = now,
                    isLoading = false,
                )
            }
        }
        // Live clock: advance "now" so countdowns and window states stay honest during a session
        // (a window can roll OPEN -> CLOSED while the screen is on). Cancelled when the VM clears.
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                _ui.update { it.copy(nowMillis = System.currentTimeMillis()) }
            }
        }
    }

    /**
     * Toggles a flight's check-in. Guarded: a check-in or a cancel is only honored while the window
     * is OPEN. Checking in issues a boarding assignment (zone from cabin, position from how early in
     * the window you acted); cancelling releases it. Persists the new set of boarding passes.
     */
    fun toggleCheckIn(flight: CheckInFlight) {
        val now = System.currentTimeMillis()
        _ui.update { state ->
            val target = state.flights.firstOrNull { it.id == flight.id }
                ?: return@update state.copy(nowMillis = now)
            // Guard closed / not-yet-open windows: only mutate while OPEN.
            if (!target.isWindowOpen(now)) return@update state.copy(nowMillis = now)
            val updated =
                if (target.isCheckedIn) target.copy(boarding = null)
                else target.copy(boarding = target.assignBoarding(now))
            state.copy(
                flights = state.flights.map { if (it.id == updated.id) updated else it },
                nowMillis = now,
            )
        }
        saveRecords()
        recordCheckInSignals(preToggle = flight)
    }

    /**
     * Learning seam (plan A4): a successful check-in emits seatChosen + flightFlown signals so the
     * travel profile learns real seat/airline behavior. Consent gating happens inside
     * [TravelProfileStore.record]; demo seed flights never record (plan R10e).
     */
    private fun recordCheckInSignals(preToggle: CheckInFlight) {
        if (preToggle.id in CheckinRepository.seedFlightIds) return
        val target = _ui.value.flights.firstOrNull { it.id == preToggle.id } ?: return
        val checkedInNow = !preToggle.isCheckedIn && target.isCheckedIn
        if (!checkedInNow) return
        val timestamp = Instant.now().toString()
        val ident = target.flightNumber.replace(" ", "").uppercase(Locale.US)
        viewModelScope.launch {
            travelProfileStore.record(
                TravelSignal(
                    kind = TravelSignal.Kind.SEAT_CHOSEN,
                    value = target.seat,
                    attributes = mapOf(
                        TravelSignal.Attr.AIRLINE to target.airline,
                        TravelSignal.Attr.CABIN_HINT to target.cabin.name.lowercase(Locale.US),
                    ),
                    timestamp = timestamp,
                    source = "checkin",
                ),
            )
            travelProfileStore.record(
                TravelSignal(
                    kind = TravelSignal.Kind.FLIGHT_FLOWN,
                    value = ident,
                    attributes = mapOf(TravelSignal.Attr.AIRLINE to target.airline),
                    timestamp = timestamp,
                    source = "checkin",
                ),
            )
        }
    }

    private fun CheckInFlight.applyRecord(record: PersistedCheckIn?): CheckInFlight =
        if (record == null) this
        else copy(
            boarding = BoardingAssignment(
                zone = record.zone,
                position = record.position,
                cabinLabel = record.cabinLabel,
                checkedInAtMillis = record.checkedInAtMillis,
            ),
        )

    /** Read the persisted boarding passes; empty when none or the stored JSON is invalid. */
    private suspend fun readRecords(): List<PersistedCheckIn> {
        val json = stateStore.read(RECORDS_KEY) ?: return emptyList()
        return runCatching { recordsAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    /** Persist the current set of issued boarding passes as a JSON List<PersistedCheckIn>. */
    private fun saveRecords() {
        val records = _ui.value.flights.mapNotNull { flight ->
            flight.boarding?.let {
                PersistedCheckIn(
                    flightId = flight.id,
                    zone = it.zone,
                    position = it.position,
                    cabinLabel = it.cabinLabel,
                    checkedInAtMillis = it.checkedInAtMillis,
                )
            }
        }
        viewModelScope.launch { stateStore.save(RECORDS_KEY, recordsAdapter.toJson(records)) }
    }

    private companion object {
        const val RECORDS_KEY = "checkin_records_v2"
        const val TICK_MILLIS = 30_000L
    }
}
