package com.jetsetter.pro.feature.home

import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.Flight
import com.jetsetter.pro.core.model.Trip

/**
 * Single immutable UI-state object for the Home dashboard (guide §6). Lives in its own file per
 * the `<Name>UiState.kt` convention in docs/FEATURE_PARITY.md §2.
 */
data class HomeUiState(
    val nextFlight: Flight = MockData.nextFlight,
    val upcomingTrip: Trip? = null,
    /** Aggregated "this trip" spend, summed from the mock expense ledger in [HomeViewModel]. */
    val expenseSummary: ExpenseSummary? = null,
    /** Proactive alerts (gate changes, weather…) the tester hasn't dismissed yet. */
    val alerts: List<HomeAlert> = emptyList(),
    /**
     * Mirrors [com.jetsetter.pro.core.model.UserPreferences.demoMode]. Drives the alpha-only
     * DEMO chip in the Home header so demo mode can be flipped without leaving the dashboard.
     */
    val demoMode: Boolean = false,
)

/** Rolled-up spend for the upcoming trip: grand total plus the single biggest category. */
data class ExpenseSummary(
    val total: Double,
    val currency: String,
    val topCategory: ExpenseCategory?,
    val topCategoryAmount: Double,
)

/** A proactive, dismissible heads-up surfaced at the top of the dashboard. */
data class HomeAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    /** Stable suggestion kind ([IrisSuggestionKind.name]) for learned accept/dismiss tracking. */
    val category: String = "",
    /** Natural-language request deep-linked into IRIS chat when the alert is tapped, or null. */
    val promptToIris: String? = null,
)

enum class AlertSeverity { WARNING, INFO }
