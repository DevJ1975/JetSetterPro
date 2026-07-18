package com.jetsetter.pro

import com.jetsetter.pro.core.ai.LiveContextBuilder
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.core.model.Trip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the pure live-context snapshot renderer (spec §1.7) via [LiveContextBuilder.format]:
 * 3-item itinerary cap, per-currency expense totals, next-flight extraction through the
 * canonical ident regex, and the empty state. Pure-JVM (no Android deps).
 */
class LiveContextBuilderTest {

    private val today: LocalDate = LocalDate.parse("2026-07-17")

    private fun trip(
        name: String,
        start: String,
        end: String,
        items: List<ItineraryItem> = emptyList(),
    ) = Trip(name = name, destination = name, startDate = start, endDate = end, items = items)

    private fun item(title: String, start: String) =
        ItineraryItem(title = title, type = ItineraryItemType.ACTIVITY, startDate = start)

    private fun expense(amount: Double, currency: String, merchant: String = "Nobu") = Expense(
        amount = amount,
        currency = currency,
        category = ExpenseCategory.FOOD,
        merchant = merchant,
        date = "2026-07-16",
    )

    // ---- itinerary items -----------------------------------------------------------------

    @Test
    fun upcomingItineraryCappedAtThreeItemsSortedByDate() {
        val t = trip(
            "Tokyo", "2026-07-16", "2026-07-25",
            items = listOf(
                // Past item — excluded even though it belongs to the active trip.
                item("Arrival dinner", "2026-07-16T19:00:00Z"),
                // Out of order on purpose: rendering must sort by start.
                item("Fourth thing", "2026-07-21T10:00:00Z"),
                item("First thing", "2026-07-18T10:00:00Z"),
                item("Third thing", "2026-07-20T10:00:00Z"),
                item("Second thing", "2026-07-19T10:00:00Z"),
            ),
        )

        val text = LiveContextBuilder.format(listOf(t), emptyList(), today)

        assertTrue(text.contains("- 2026-07-18: First thing"))
        assertTrue(text.contains("- 2026-07-19: Second thing"))
        assertTrue(text.contains("- 2026-07-20: Third thing"))
        // Capped at 3 — the fourth upcoming item never renders.
        assertFalse(text.contains("Fourth thing"))
        // Past items never render.
        assertFalse(text.contains("Arrival dinner"))
    }

    @Test
    fun tripHeaderShowsNameDestinationAndIsoRange() {
        val t = Trip(
            name = "Tokyo Adventure",
            destination = "Tokyo",
            startDate = "2026-07-18",
            endDate = "2026-07-25",
        )

        val text = LiveContextBuilder.format(listOf(t), emptyList(), today)

        assertTrue(text.contains("LIVE TRAVELER DATA:"))
        assertTrue(text.contains("Trip: Tokyo Adventure — Tokyo (2026-07-18 to 2026-07-25)"))
    }

    // ---- expenses ------------------------------------------------------------------------

    @Test
    fun expenseTotalsGroupedPerCurrency() {
        val expenses = listOf(
            expense(1000.06, "USD"),
            expense(234.50, "USD"),
            expense(89.00, "EUR"),
        )

        val text = LiveContextBuilder.format(emptyList(), expenses, today)

        // Count is across all currencies; totals per currency, thousands-separated,
        // largest total first.
        assertTrue(text.contains("Expenses: 3 expenses — 1,234.56 USD; 89.00 EUR"))
    }

    @Test
    fun singleExpenseUsesSingularNoun() {
        val text = LiveContextBuilder.format(emptyList(), listOf(expense(12.00, "USD")), today)
        assertTrue(text.contains("Expenses: 1 expense — 12.00 USD"))
    }

    // ---- next flight ---------------------------------------------------------------------

    @Test
    fun nextFlightLinePresentWhenTitleContainsIdent() {
        val t = trip(
            "Tokyo", "2026-07-18", "2026-07-25",
            items = listOf(item("Flight DL1423 to JFK", "2026-07-18T10:00:00Z")),
        )

        val text = LiveContextBuilder.format(listOf(t), emptyList(), today)

        assertTrue(text.contains("Next flight: DL1423"))
    }

    @Test
    fun nextFlightLineAbsentWhenNoTitleMatchesIdentRegex() {
        val t = trip(
            "Tokyo", "2026-07-18", "2026-07-25",
            // "NYC 2026" must NOT read as a flight ident (raw-title matching, no stripping).
            items = listOf(item("NYC 2026", "2026-07-18T10:00:00Z")),
        )

        val text = LiveContextBuilder.format(listOf(t), emptyList(), today)

        assertFalse(text.contains("Next flight:"))
    }

    // ---- empty state ---------------------------------------------------------------------

    @Test
    fun emptyInputsRenderEmptyStateWithClosingInstruction() {
        val text = LiveContextBuilder.format(emptyList(), emptyList(), today)

        assertTrue(text.contains("LIVE TRAVELER DATA:"))
        assertTrue(text.contains("No trips on file."))
        assertTrue(text.contains("Expenses: none logged."))
        assertTrue(text.contains("Do not invent trips, flights, or expenses"))
    }
}
