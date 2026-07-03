package com.jetsetter.pro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.demo.DemoSeeder
import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.intelligence.ProactiveEngine
import com.jetsetter.pro.core.intelligence.TravelProfile
import com.jetsetter.pro.core.model.Expense
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
    expensesRepository: ExpenseRepository,
    private val stateStore: ModuleStateStore,
    private val proactiveEngine: ProactiveEngine,
    private val travelProfile: TravelProfile,
    private val demoSeeder: DemoSeeder,
    prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    init {
        // Kick off cross-device sync for both ledgers (idempotent; shares the one anon sign-in).
        viewModelScope.launch { tripRepository.initSync() }
        viewModelScope.launch { expensesRepository.initSync() }
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

    val uiState: StateFlow<HomeUiState> =
        combine(
            tripRepository.observeTrips(),
            expensesRepository.observeExpenses(),
            stateStore.observe(DISMISSED_ALERTS_KEY),
            prefsRepository.preferences,
        ) { trips, expenses, dismissedJson, prefs ->
            val dismissed = dismissedJson?.let { json ->
                runCatching { idsAdapter.fromJson(json) }.getOrNull()
            }.orEmpty().toSet()

            // Keep the on-device learned profile current. Strictly local — never sent to IRIS.
            viewModelScope.launch { travelProfile.update(trips, expenses) }

            // Real, data-derived proactive nudges replace the old hardcoded gate/weather pair.
            val alerts = proactiveEngine.computeAlerts(trips, expenses)
            HomeUiState(
                nextFlight = MockData.nextFlight,
                upcomingTrip = trips.firstOrNull(),
                expenseSummary = summarize(expenses),
                alerts = alerts.filterNot { it.id in dismissed },
                demoMode = prefs.demoMode,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Alpha convenience for on-device walkthroughs: the Home-header DEMO chip flips demo mode
     * without leaving the dashboard. Same seeder path as More → Presentation — enabling re-seeds
     * the curated dataset and arms the scripted disruption push; disabling leaves data as-is.
     */
    fun setDemoMode(enabled: Boolean) = viewModelScope.launch {
        if (enabled) demoSeeder.enableDemoMode() else demoSeeder.disableDemoMode()
    }

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
