package com.jetsetter.pro.core.ai

import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.TripQueries
import com.jetsetter.pro.core.util.IsoDates
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the "live context snapshot" IRIS appends at session start (spec §1.7): the active or
 * next trip with its next three itinerary items, the next flight across all trips (via
 * [TripQueries.nextFlight] — the single canonical ident regex), and expense count + totals
 * grouped by currency. Plain text, closing with an instruction never to invent details beyond it.
 *
 * All selection/rendering logic lives in the pure [format] so tests can pin the contract without
 * Android or coroutine machinery; [build] only snapshots the repositories.
 */
@Singleton
class LiveContextBuilder @Inject constructor(
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
) {

    /** Snapshots Room through the repositories and renders the block. Failures degrade to empty. */
    suspend fun build(today: LocalDate = LocalDate.now()): String {
        val trips = runCatching { tripRepository.observeTrips().first() }.getOrDefault(emptyList())
        val expenses =
            runCatching { expenseRepository.observeExpenses().first() }.getOrDefault(emptyList())
        return format(trips, expenses, today)
    }

    companion object {
        private const val MAX_ITINERARY_ITEMS = 3

        private const val CLOSING_LINE =
            "This is the traveler's live data. Do not invent trips, flights, or expenses " +
                "beyond what is listed here."

        /**
         * Pure renderer — deterministic for a given input. "Now" is derived as [today]'s start of
         * day in UTC (matching [IsoDates]' zone-less interpretation), so today's itinerary items
         * still count as upcoming.
         */
        internal fun format(trips: List<Trip>, expenses: List<Expense>, today: LocalDate): String {
            val startOfToday = today.atStartOfDay().toInstant(ZoneOffset.UTC)

            val lines = buildList {
                add("LIVE TRAVELER DATA:")

                val trip = TripQueries.activeTrip(trips, today)
                if (trip == null) {
                    add("No trips on file.")
                } else {
                    add("Trip: ${trip.name} — ${trip.destination} (${trip.startDate} to ${trip.endDate})")
                    val upcoming = trip.items
                        .mapNotNull { item -> IsoDates.parseDateTime(item.startDate)?.let { it to item } }
                        .filterNot { (start, _) -> start.isBefore(startOfToday) }
                        .sortedBy { (start, _) -> start }
                        .take(MAX_ITINERARY_ITEMS)
                    if (upcoming.isNotEmpty()) {
                        add("Upcoming itinerary:")
                        upcoming.forEach { (start, item) ->
                            add("- ${start.atZone(ZoneOffset.UTC).toLocalDate()}: ${item.title}")
                        }
                    }
                }

                TripQueries.nextFlight(trips, startOfToday)?.let { next ->
                    add("Next flight: ${next.ident} — ${next.item.title} (${next.trip.name})")
                }

                add(expenseLine(expenses))
                add(CLOSING_LINE)
            }
            return lines.joinToString("\n")
        }

        /** e.g. `Expenses: 12 expenses — 1,234.56 USD; 89.00 EUR`. */
        private fun expenseLine(expenses: List<Expense>): String {
            if (expenses.isEmpty()) return "Expenses: none logged."
            val totals = expenses
                .groupBy { it.currency }
                .map { (currency, group) -> currency to group.sumOf { it.amount } }
                // Largest total first; currency code breaks ties — deterministic output.
                .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
                .joinToString("; ") { (currency, total) ->
                    "${"%,.2f".format(Locale.US, total)} $currency"
                }
            val noun = if (expenses.size == 1) "expense" else "expenses"
            return "Expenses: ${expenses.size} $noun — $totals"
        }
    }
}
