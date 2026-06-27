package com.jetsetter.pro.feature.intelligence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory source of truth for Travel Intelligence insights. Mock-first: no network, no
 * persistence — just realistic sample data the beta tester can interact with. Dismissing or
 * restoring an insight mutates the backing [MutableStateFlow] so the UI updates reactively.
 *
 * Numbers live in structured [InsightMetric]s, not in the prose, so every figure the UI shows is
 * computed from these inputs and stays self-consistent.
 */
@Singleton
class IntelligenceRepository @Inject constructor() {

    private val _insights = MutableStateFlow(sampleInsights())
    fun observeInsights(): Flow<List<TravelIntelligenceItem>> = _insights.asStateFlow()

    /** Move an insight into / out of the dismissed-history bucket. */
    fun setDismissed(id: String, dismissed: Boolean) {
        _insights.update { list ->
            list.map { if (it.id == id) it.copy(dismissed = dismissed) else it }
        }
    }

    /**
     * Reconcile every insight's dismissed flag to match the persisted set of ids: ids in [ids]
     * become dismissed, all others active. Used on launch to restore the tester's saved choices
     * (including ones they later restored) on top of the seed data.
     */
    fun applyDismissed(ids: Set<String>) {
        _insights.update { list -> list.map { it.copy(dismissed = it.id in ids) } }
    }

    /** Current ids in the dismissed-history bucket — snapshot used to persist after each change. */
    fun dismissedIds(): List<String> = _insights.value.filter { it.dismissed }.map { it.id }

    private fun sampleInsights(): List<TravelIntelligenceItem> = listOf(
        TravelIntelligenceItem(
            id = "weather-atl",
            category = IntelligenceCategory.WEATHER,
            priority = IntelligencePriority.ALERT,
            severity = IntelligenceSeverity.HIGH,
            title = "ATL flight may be delayed",
            detail = "2 hours of rain are forecast at Atlanta around your 9:40a arrival. DL 1423 has a " +
                "tight C22 connection — an earlier departure protects it.",
            actionLabel = "See alternates",
        ),
        TravelIntelligenceItem(
            id = "tsa-las",
            category = IntelligenceCategory.SECURITY,
            priority = IntelligencePriority.ALERT,
            severity = IntelligenceSeverity.HIGH,
            title = "TSA wait rising at LAS",
            detail = "Security at Harry Reid Terminal 1 is climbing. Leave by 6:05a to stay ahead of it.",
            actionLabel = "Set reminder",
            metric = InsightMetric.Wait(currentMinutes = 32, bufferMinutes = 30),
        ),
        TravelIntelligenceItem(
            id = "hotel-savings",
            category = IntelligenceCategory.HOTEL,
            priority = IntelligencePriority.OPPORTUNITY,
            severity = IntelligenceSeverity.MEDIUM,
            title = "Cheaper hotel found near the venue",
            detail = "The Loews Atlanta is 0.3 mi from your board meeting and fully refundable.",
            actionLabel = "View hotel",
            metric = InsightMetric.CostSaving(currentCents = 31_000, proposedCents = 21_400, cadence = "night"),
        ),
        TravelIntelligenceItem(
            id = "expense-policy",
            category = IntelligenceCategory.EXPENSE,
            priority = IntelligencePriority.INFO,
            severity = IntelligenceSeverity.MEDIUM,
            title = "Receipts missing for this trip",
            detail = "Your Delta airfare and an airport lunch don't have receipts attached. Add them " +
                "before Friday to stay inside the Brex reporting window.",
            actionLabel = "Attach receipts",
            metric = InsightMetric.Outstanding(amountsCents = listOf(129_000, 5_800)),
        ),
        TravelIntelligenceItem(
            id = "flight-upgrade",
            category = IntelligenceCategory.FLIGHT,
            priority = IntelligencePriority.OPPORTUNITY,
            severity = IntelligenceSeverity.LOW,
            title = "First-class upgrade available",
            detail = "Seat 2C just opened on DL 1423 — a 4h 45m flight in lie-flat comfort.",
            actionLabel = "Upgrade",
            metric = InsightMetric.PointsCost(costPoints = 14_500, balancePoints = 41_200),
        ),
        // Pre-seeded history so the dismissed section isn't empty on first launch.
        TravelIntelligenceItem(
            id = "weather-cleared",
            category = IntelligenceCategory.WEATHER,
            priority = IntelligencePriority.INFO,
            severity = IntelligenceSeverity.LOW,
            title = "Pack a light layer for Atlanta",
            detail = "Evenings dip to 61°F during your stay — a blazer or light jacket should cover it.",
            actionLabel = "Add to packing",
            dismissed = true,
        ),
        TravelIntelligenceItem(
            id = "lounge-cleared",
            category = IntelligenceCategory.SECURITY,
            priority = IntelligencePriority.OPPORTUNITY,
            severity = IntelligenceSeverity.LOW,
            title = "Sky Club near your gate",
            detail = "Delta Sky Club is a 3-minute walk from C22 and is included with your membership.",
            actionLabel = "Get directions",
            dismissed = true,
        ),
    )
}
