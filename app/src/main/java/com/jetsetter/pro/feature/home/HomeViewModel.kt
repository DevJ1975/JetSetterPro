package com.jetsetter.pro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.ai.ActionRouter
import com.jetsetter.pro.core.ai.IrisNavEvent
import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.PrefKeys
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.intelligence.IrisSuggestion
import com.jetsetter.pro.core.intelligence.IrisSuggestionEngine
import com.jetsetter.pro.core.intelligence.IrisSuggestionKind
import com.jetsetter.pro.core.intelligence.KbFacts
import com.jetsetter.pro.core.intelligence.KbFactsResolver
import com.jetsetter.pro.core.intelligence.SuggestionInputs
import com.jetsetter.pro.core.intelligence.TravelProfileStore
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.TripQueries
import com.jetsetter.pro.core.rag.UserDataIndexer
import com.jetsetter.pro.core.util.IsoDates
import com.jetsetter.pro.feature.checkin.PersistedCheckIn
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    tripRepository: TripRepository,
    expensesRepository: ExpenseRepository,
    private val stateStore: ModuleStateStore,
    private val travelProfileStore: TravelProfileStore,
    private val actionRouter: ActionRouter,
    private val userDataIndexer: UserDataIndexer,
    private val kbFactsResolver: KbFactsResolver,
) : ViewModel() {

    init {
        // Kick off cross-device sync for both ledgers (idempotent; shares the one anon sign-in).
        viewModelScope.launch { tripRepository.initSync() }
        viewModelScope.launch { expensesRepository.initSync() }
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // Persist the set of dismissed alert ids as a JSON List<String> so a tester's dismissals
    // survive restarts — same idiom as CheckinViewModel.
    private val idsAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java),
    )

    // Read-only view of the Check-In feature's persisted boarding passes (shared key/JSON contract
    // with feature/checkin — see RepositoryCheckinStateSource in core/di/ToolDataModule).
    private val checkinAdapter = moshi.adapter<List<PersistedCheckIn>>(
        Types.newParameterizedType(List::class.java, PersistedCheckIn::class.java),
    )

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

    /** Everything HomeViewModel reads from [ModuleStateStore], folded into one combine source. */
    private data class StoreState(
        val dismissedIds: Set<String>,
        val checkedInFlightIds: Set<String>,
        val uberBooked: Boolean,
        val rideOnLandingBooked: Boolean,
        val dailyBriefingShownFor: LocalDate?,
    )

    private val storeState = combine(
        stateStore.observe(DISMISSED_ALERTS_KEY),
        stateStore.observe(CHECKIN_RECORDS_KEY),
        stateStore.observe(PrefKeys.UBER_BOOKED),
        stateStore.observe(PrefKeys.RIDE_ON_LANDING_BOOKED),
        stateStore.observe(DAILY_BRIEFING_KEY),
    ) { dismissedJson, checkinJson, uber, ride, briefing ->
        StoreState(
            dismissedIds = dismissedJson
                ?.let { runCatching { idsAdapter.fromJson(it) }.getOrNull() }
                .orEmpty()
                .toSet(),
            checkedInFlightIds = checkinJson
                ?.let { runCatching { checkinAdapter.fromJson(it) }.getOrNull() }
                .orEmpty()
                .map { it.flightId.lowercase(Locale.US) }
                .toSet(),
            uberBooked = uber == "true",
            rideOnLandingBooked = ride == "true",
            dailyBriefingShownFor = briefing?.let(IsoDates::parseDate),
        )
    }

    /** Re-evaluates the proactive triggers every 15 minutes even when nothing else changes. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(tripRepository.observeTrips(), expensesRepository.observeExpenses()) { t, e -> t to e },
            storeState,
            travelProfileStore.observeProfile(),
            ticker,
        ) { (trips, expenses), store, profile, _ ->
            // Keep the personal RAG index current (no-op on devices without the embedder).
            // Fire-and-forget: it feeds the on-device tier's grounding, not this emission.
            viewModelScope.launch { userDataIndexer.reindex(trips, expenses) }

            val now = Instant.now()
            val today = LocalDate.now()

            // Next flight across all trips → departure instant, arrival guess, checked-in state.
            val next = TripQueries.nextFlight(trips, now)
            val departure = next?.item?.startDate?.let(IsoDates::parseDateTime)
            val checkedIn = next != null &&
                next.ident.lowercase(Locale.US) in store.checkedInFlightIds

            // KB enrichment for the nearest upcoming trip's destination (empty facts on devices
            // without the embedder) — the visa + packing-weather lookups the old engine made.
            val nextDestination = trips
                .filter { it.startDate >= today.toString() }
                .minByOrNull { it.startDate }
                ?.destination
            val kbFacts =
                if (nextDestination != null) kbFactsResolver.resolve(nextDestination) else KbFacts()

            // Per-kind dismissal counts back the "preference nudges back off after 3" rule.
            val dismissedCounts = IrisSuggestionKind.entries
                .filter { it.isPreferenceNudge }
                .associate { it.name to travelProfileStore.dismissedCount(it.name) }

            val suggestions = IrisSuggestionEngine.evaluate(
                SuggestionInputs(
                    now = now,
                    today = today,
                    trips = trips,
                    expenses = expenses,
                    nextFlightDeparture = departure,
                    checkedIn = checkedIn,
                    // LoyaltyVaultAccount carries no expiration dates yet — nothing to feed
                    // tierAtRisk until the vault model grows expiry fields.
                    loyaltyExpirations = emptyList(),
                    profile = profile,
                    uberBooked = store.uberBooked,
                    rideOnLandingBooked = store.rideOnLandingBooked,
                    arrivalIata = next?.item?.let(::guessArrivalIata),
                    hasCheckedBag = true,
                    visaRequirement = kbFacts.visaNote?.let(::trimNote),
                    kbWeatherNote = kbFacts.packingNote?.let(::trimNote),
                    dismissedCounts = dismissedCounts,
                    dailyBriefingShownFor = store.dailyBriefingShownFor,
                ),
            )

            // Per-id dismissal only silences suppressible kinds — safety/operational kinds
            // (checkInWindow, visaCheck, weatherWatch) always re-surface (spec §1.9).
            val alerts = suggestions
                .filter { it.kind.neverSuppressed || it.dismissalKey !in store.dismissedIds }
                .map { it.toHomeAlert() }

            HomeUiState(
                nextFlight = MockData.nextFlight,
                upcomingTrip = trips.firstOrNull(),
                expenseSummary = summarize(expenses),
                alerts = alerts,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Dismiss an alert: the id joins the persisted set (suppressible kinds stay gone; never-
     * suppressed kinds re-surface on the next evaluation) and the rejection is recorded as
     * suggestion feedback so preference nudges can back off.
     */
    fun dismissAlert(id: String) {
        val alert = uiState.value.alerts.firstOrNull { it.id == id }
        viewModelScope.launch {
            val current = stateStore.read(DISMISSED_ALERTS_KEY)?.let { json ->
                runCatching { idsAdapter.fromJson(json) }.getOrNull()
            }.orEmpty()
            if (id !in current) {
                stateStore.save(DISMISSED_ALERTS_KEY, idsAdapter.toJson(current + id))
            }
            markDailyBriefingHandled(alert)
            alert?.category?.takeIf { it.isNotBlank() }?.let {
                travelProfileStore.recordSuggestionFeedback(it, accepted = false)
            }
        }
    }

    /**
     * The user tapped an alert: deep-link its prompt into IRIS chat (the app shell collects the
     * router's nav events) and record the engagement as accepted suggestion feedback.
     */
    fun onAlertClick(alert: HomeAlert) {
        val prompt = alert.promptToIris ?: return
        actionRouter.navigate(IrisNavEvent.OpenIrisWithPrompt(prompt))
        viewModelScope.launch {
            markDailyBriefingHandled(alert)
            alert.category.takeIf { it.isNotBlank() }?.let {
                travelProfileStore.recordSuggestionFeedback(it, accepted = true)
            }
        }
    }

    /** Acting on (or dismissing) today's briefing gates it until tomorrow (date persisted). */
    private suspend fun markDailyBriefingHandled(alert: HomeAlert?) {
        if (alert?.category == IrisSuggestionKind.DAILY_BRIEFING.name) {
            stateStore.save(DAILY_BRIEFING_KEY, LocalDate.now().toString())
        }
    }

    private companion object {
        const val DISMISSED_ALERTS_KEY = "home_dismissed_alerts"

        /** Same key `feature/checkin` persists under — the shared read-only contract. */
        const val CHECKIN_RECORDS_KEY = "checkin_records_v2"

        /** ISO date the daily briefing was last shown/handled (gates it to once per day). */
        const val DAILY_BRIEFING_KEY = "iris_daily_briefing_shown"

        /** Proactive-trigger re-evaluation cadence (plan A5). */
        const val TICK_MILLIS = 15L * 60_000L
    }
}

/**
 * Best-effort arrival-airport guess from a flight itinerary title like `"DL1423 LAS → ATL"`:
 * the last 3-letter IATA token after an arrow/dash separator, else null.
 */
internal fun guessArrivalIata(item: ItineraryItem): String? =
    Regex("(?:→|->|–|-)\\s*([A-Z]{3})\\b").findAll(item.title).lastOrNull()?.groupValues?.get(1)

/** Trims a KB chunk down to a single readable sentence/line for an alert body. */
internal fun trimNote(note: String, max: Int = 160): String {
    val firstLine = note.substringBefore('\n').trim()
    return if (firstLine.length <= max) firstLine else firstLine.take(max).trimEnd() + "…"
}

/**
 * The [IrisSuggestion] → [HomeAlert] projection: id = dismissalKey (stable per flight/trip/day),
 * category = kind name (feedback contract), warning severity for the act-now kinds.
 */
internal fun IrisSuggestion.toHomeAlert(): HomeAlert = HomeAlert(
    id = dismissalKey,
    title = title,
    message = body,
    severity = when (kind) {
        IrisSuggestionKind.CHECK_IN_WINDOW,
        IrisSuggestionKind.VISA_CHECK,
        IrisSuggestionKind.TIER_AT_RISK,
        -> AlertSeverity.WARNING

        else -> AlertSeverity.INFO
    },
    category = kind.name,
    promptToIris = promptToIris,
)
