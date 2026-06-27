package com.jetsetter.pro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.feature.expenses.ExpensesRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    tripRepository: TripRepository,
    expensesRepository: ExpensesRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    init {
        viewModelScope.launch { tripRepository.initSync() }
    }

    // Persist the set of dismissed alert ids as a JSON List<String> so a tester's dismissals
    // survive restarts — same idiom as CheckinViewModel.
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val idsType = Types.newParameterizedType(List::class.java, String::class.java)
    private val idsAdapter = moshi.adapter<List<String>>(idsType)

    // "This trip" spend rolled up from the live expense ledger: grand total + biggest single category.
    private fun summarize(expenses: List<Expense>): ExpenseSummary {
        val byCategory = expenses.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
        val top = byCategory.maxByOrNull { it.value }
        return ExpenseSummary(
            total = expenses.sumOf { it.amount },
            currency = expenses.firstOrNull()?.currency ?: "USD",
            topCategory = top?.key,
            topCategoryAmount = top?.value ?: 0.0,
        )
    }

    // Proactive heads-ups IRIS would raise for the next flight. Mocked but trip-aware.
    private val allAlerts: List<HomeAlert> = listOf(
        HomeAlert(
            id = "gate-change",
            title = "Gate change",
            message = "${MockData.nextFlight.ident} now departs from gate D14 " +
                "(was ${MockData.nextFlight.gateOrigin ?: "TBD"}).",
            severity = AlertSeverity.WARNING,
        ),
        HomeAlert(
            id = "weather",
            title = "Weather watch",
            message = "Afternoon thunderstorms forecast in ${MockData.nextFlight.destination.city}. " +
                "IRIS is watching for delays.",
            severity = AlertSeverity.INFO,
        ),
    )

    val uiState: StateFlow<HomeUiState> =
        combine(
            tripRepository.observeTrips(),
            expensesRepository.observeExpenses(),
            stateStore.observe(DISMISSED_ALERTS_KEY),
        ) { trips, expenses, dismissedJson ->
            val dismissed = dismissedJson?.let { json ->
                runCatching { idsAdapter.fromJson(json) }.getOrNull()
            }.orEmpty().toSet()
            HomeUiState(
                nextFlight = MockData.nextFlight,
                upcomingTrip = trips.firstOrNull(),
                expenseSummary = summarize(expenses),
                alerts = allAlerts.filterNot { it.id in dismissed },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Permanently dismiss an alert; the new id set is persisted so it stays gone after restart. */
    fun dismissAlert(id: String) {
        viewModelScope.launch {
            val current = stateStore.read(DISMISSED_ALERTS_KEY)?.let { json ->
                runCatching { idsAdapter.fromJson(json) }.getOrNull()
            }.orEmpty()
            if (id !in current) {
                stateStore.save(DISMISSED_ALERTS_KEY, idsAdapter.toJson(current + id))
            }
        }
    }

    private companion object {
        const val DISMISSED_ALERTS_KEY = "home_dismissed_alerts"
    }
}
