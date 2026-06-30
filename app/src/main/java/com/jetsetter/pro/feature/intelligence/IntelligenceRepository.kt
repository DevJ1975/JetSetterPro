package com.jetsetter.pro.feature.intelligence

import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Derives the Travel Intelligence feed from the **live** trip and expense ledgers — no mock data.
 * Insights are recomputed reactively whenever trips or expenses change (the same offline-first Room
 * data that drives Home and Expenses). Every figure a card shows is computed here from the raw
 * inputs (carried in a structured [InsightMetric] where one applies), never baked into prose.
 *
 * Dismiss/restore is handled by [IntelligenceViewModel] as an overlay of persisted ids on top of
 * this derived set; insight ids are **stable** (keyed by trip id / merchant) so a dismissal sticks
 * across re-derivations. This stays a @Singleton with no mutable state so Hilt injects it like the
 * rest of the graph.
 */
@Singleton
class IntelligenceRepository @Inject constructor(
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
) {
    /** The current active insight set (before the dismissal overlay), derived from live data. */
    fun observeInsights(): Flow<List<TravelIntelligenceItem>> =
        combine(tripRepository.observeTrips(), expenseRepository.observeExpenses()) { trips, expenses ->
            deriveInsights(trips, expenses)
        }

    private fun deriveInsights(
        trips: List<Trip>,
        expenses: List<Expense>,
        today: LocalDate = LocalDate.now(),
    ): List<TravelIntelligenceItem> {
        val items = mutableListOf<TravelIntelligenceItem>()

        // ── Trip-derived ────────────────────────────────────────────────────────────────────
        val nextTrip = trips
            .mapNotNull { trip -> parseDate(trip.startDate)?.let { trip to it } }
            .filter { (_, start) -> !start.isBefore(today) }
            .minByOrNull { (_, start) -> start }

        if (nextTrip != null) {
            val (trip, start) = nextTrip
            val days = ChronoUnit.DAYS.between(today, start)
            items += TravelIntelligenceItem(
                id = "trip-${trip.id}",
                category = IntelligenceCategory.FLIGHT,
                priority = if (days <= 7) IntelligencePriority.ALERT else IntelligencePriority.INFO,
                severity = when {
                    days <= 3 -> IntelligenceSeverity.HIGH
                    days <= 7 -> IntelligenceSeverity.MEDIUM
                    else -> IntelligenceSeverity.LOW
                },
                title = "${trip.destination} ${whenPhrase(days)}",
                detail = "${trip.name}: ${trip.startDate} → ${trip.endDate}. IRIS can prep the itinerary " +
                    "and watch for gate changes and weather.",
                actionLabel = "Prep itinerary",
            )

            val unpacked = trip.packingList.count { !it.isPacked }
            if (trip.packingList.isNotEmpty() && unpacked > 0 && days <= 14) {
                items += TravelIntelligenceItem(
                    id = "packing-${trip.id}",
                    category = IntelligenceCategory.FLIGHT,
                    priority = if (days <= 3) IntelligencePriority.ALERT else IntelligencePriority.INFO,
                    severity = if (days <= 3) IntelligenceSeverity.HIGH else IntelligenceSeverity.MEDIUM,
                    title = "$unpacked of ${trip.packingList.size} items still to pack",
                    detail = "Your ${trip.destination} packing list is " +
                        "${trip.packingList.size - unpacked}/${trip.packingList.size} done.",
                    actionLabel = "Open packing",
                )
            }
        }

        // ── Expense-derived ─────────────────────────────────────────────────────────────────
        if (expenses.isNotEmpty()) {
            val currency = expenses.first().currency
            val total = expenses.sumOf { it.amount }
            val topCategory = expenses.groupBy { it.category }
                .mapValues { (_, v) -> v.sumOf { it.amount } }
                .maxByOrNull { it.value }

            items += TravelIntelligenceItem(
                id = "spend-total",
                category = IntelligenceCategory.EXPENSE,
                priority = IntelligencePriority.INFO,
                severity = IntelligenceSeverity.LOW,
                title = "Trip spend: ${money(total, currency)}",
                detail = buildString {
                    append("Across ${expenses.size} item${if (expenses.size == 1) "" else "s"}")
                    topCategory?.let { append(" — ${it.key.label} is the largest at ${money(it.value, currency)}") }
                    append(".")
                },
                actionLabel = "View expenses",
            )

            val topMerchant = expenses.groupBy { it.merchant }
                .mapValues { (_, v) -> v.sumOf { it.amount } }
                .maxByOrNull { it.value }
            if (topMerchant != null && topMerchant.value > 0.0) {
                items += TravelIntelligenceItem(
                    id = "export-${topMerchant.key}",
                    category = IntelligenceCategory.EXPENSE,
                    priority = IntelligencePriority.OPPORTUNITY,
                    severity = IntelligenceSeverity.MEDIUM,
                    title = "Export ${topMerchant.key} to Brex",
                    detail = "${topMerchant.key} is your largest single expense at " +
                        "${money(topMerchant.value, currency)} — export it for reimbursement.",
                    actionLabel = "Export",
                )
            }

            val missing = expenses.filter { it.notes.isNullOrBlank() }
            if (missing.isNotEmpty()) {
                items += TravelIntelligenceItem(
                    id = "receipts-outstanding",
                    category = IntelligenceCategory.EXPENSE,
                    priority = IntelligencePriority.ALERT,
                    severity = if (missing.size >= 3) IntelligenceSeverity.HIGH else IntelligenceSeverity.MEDIUM,
                    title = "${missing.size} expense${if (missing.size == 1) "" else "s"} need receipts",
                    detail = "Attach receipts before your reporting window closes so nothing is rejected.",
                    actionLabel = "Attach receipts",
                    metric = InsightMetric.Outstanding(amountsCents = missing.map { (it.amount * 100).roundToInt() }),
                )
            }
        }

        if (items.isEmpty()) {
            items += TravelIntelligenceItem(
                id = "empty",
                category = IntelligenceCategory.FLIGHT,
                priority = IntelligencePriority.INFO,
                severity = IntelligenceSeverity.LOW,
                title = "No active insights yet",
                detail = "Add a trip or log an expense and IRIS will start surfacing proactive insights here.",
                actionLabel = "Add a trip",
            )
        }
        return items
    }

    private fun whenPhrase(days: Long): String = when (days) {
        0L -> "trip is today"
        1L -> "trip is tomorrow"
        else -> "trip is in $days days"
    }

    private fun parseDate(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

    private fun money(amount: Double, currency: String): String {
        val symbol = when (currency.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> ""
        }
        val rounded = "%,.0f".format(amount)
        return if (symbol.isNotEmpty()) "$symbol$rounded" else "$rounded $currency"
    }
}
