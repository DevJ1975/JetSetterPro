package com.jetsetter.pro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.intelligence.KbFacts
import com.jetsetter.pro.core.intelligence.KbFactsResolver
import com.jetsetter.pro.core.intelligence.ProactiveEngine
import com.jetsetter.pro.core.intelligence.TravelProfile
import com.jetsetter.pro.core.intelligence.UserMemory
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.rag.UserDataIndexer
import java.time.LocalDate
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
    private val userMemory: UserMemory,
    private val userDataIndexer: UserDataIndexer,
    private val kbFactsResolver: KbFactsResolver,
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
        ) { trips, expenses, dismissedJson ->
            val dismissed = dismissedJson?.let { json ->
                runCatching { idsAdapter.fromJson(json) }.getOrNull()
            }.orEmpty().toSet()

            // Keep the on-device learned signals current. Strictly local — never sent to Claude.
            // Fire-and-forget: these feed the *next* emission's preferences/grounding.
            viewModelScope.launch { travelProfile.update(trips, expenses) }
            viewModelScope.launch { userMemory.refreshFromLedgers(trips, expenses) }
            viewModelScope.launch { userDataIndexer.reindex(trips, expenses) }

            // Resolve learned prefs + KB facts for the nearest upcoming trip so the engine can
            // anticipate (visa/packing/loyalty). KB facts are empty on devices without the embedder.
            val nextDestination = trips
                .filter { it.startDate >= LocalDate.now().toString() }
                .minByOrNull { it.startDate }
                ?.destination
            val learned = userMemory.topPreferences()
            val kbFacts = if (nextDestination != null) kbFactsResolver.resolve(nextDestination) else KbFacts()

            // Real, data-derived proactive nudges replace the old hardcoded gate/weather pair.
            val alerts = proactiveEngine.computeAlerts(trips, expenses, learned = learned, kbFacts = kbFacts)
            HomeUiState(
                nextFlight = MockData.nextFlight,
                upcomingTrip = trips.firstOrNull(),
                expenseSummary = summarize(expenses),
                alerts = alerts.filterNot { it.id in dismissed },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Permanently dismiss an alert; the new id set is persisted so it stays gone after restart. */
    fun dismissAlert(id: String) {
        val category = uiState.value.alerts.firstOrNull { it.id == id }?.category
        viewModelScope.launch {
            val current = stateStore.read(DISMISSED_ALERTS_KEY)?.let { json ->
                runCatching { idsAdapter.fromJson(json) }.getOrNull()
            }.orEmpty()
            if (id !in current) {
                stateStore.save(DISMISSED_ALERTS_KEY, idsAdapter.toJson(current + id))
            }
            // Learn that the user rejects this kind of nudge (on-device only).
            category?.takeIf { it.isNotBlank() }?.let { userMemory.recordSuggestion(it, accepted = false) }
        }
    }

    /** The user engaged with an alert (tapped/acted) — reinforces this kind of nudge. */
    fun onAlertClick(id: String) {
        val category = uiState.value.alerts.firstOrNull { it.id == id }?.category ?: return
        if (category.isBlank()) return
        viewModelScope.launch { userMemory.recordSuggestion(category, accepted = true) }
    }

    private companion object {
        const val DISMISSED_ALERTS_KEY = "home_dismissed_alerts"
    }
}
