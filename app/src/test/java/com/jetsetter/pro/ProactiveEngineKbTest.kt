package com.jetsetter.pro

import com.jetsetter.pro.core.intelligence.KbFacts
import com.jetsetter.pro.core.intelligence.LearnedPreferences
import com.jetsetter.pro.core.intelligence.ProactiveEngine
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.Trip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Deterministic checks on the KB/learned-aware [ProactiveEngine] upgrades: anticipatory visa/packing
 * alerts from KB facts, learned loyalty blending, and suppression of repeatedly-dismissed categories.
 * Pure-JVM (the engine takes everything as parameters, including `today`).
 */
class ProactiveEngineKbTest {

    private val engine = ProactiveEngine()
    private val today = LocalDate.of(2026, 7, 1)

    private fun upcomingTrip() = Trip(
        id = "t1",
        name = "Paris Summit",
        destination = "Paris",
        startDate = today.plusDays(3).toString(),
        endDate = today.plusDays(6).toString(),
    )

    @Test
    fun kbFacts_produceVisaAndPackingAlerts() {
        val alerts = engine.computeAlerts(
            trips = listOf(upcomingTrip()),
            expenses = emptyList(),
            kbFacts = KbFacts(
                destination = "Paris",
                visaNote = "Schengen: passport valid 3+ months beyond departure; ETIAS required.",
                packingNote = "Summer in Paris is warm; pack light layers and a compact rain shell.",
            ),
            today = today,
        )
        assertTrue("expected a visa-entry alert", alerts.any { it.category == "visa-entry" })
        assertTrue("expected a packing-weather alert", alerts.any { it.category == "packing-weather" })
    }

    @Test
    fun loyaltyTip_blendsLearnedAirline() {
        val alerts = engine.computeAlerts(
            trips = emptyList(),
            expenses = emptyList(),
            learned = LearnedPreferences(topAirlines = listOf("Delta")),
            kbFacts = KbFacts(loyaltyTip = "Concentrate flying within one alliance to reach the next tier."),
            today = today,
        )
        val loyalty = alerts.firstOrNull { it.category == "loyalty-tip" }
        assertTrue("expected a loyalty-tip alert", loyalty != null)
        assertTrue("loyalty tip should reference the learned airline", loyalty!!.message.contains("Delta"))
    }

    @Test
    fun dismissedCategory_isSuppressed() {
        val expenses = listOf(
            Expense(amount = 1290.0, category = ExpenseCategory.BUSINESS, merchant = "Delta", date = "2026-06-20"),
        )
        val withTopSpend = engine.computeAlerts(trips = emptyList(), expenses = expenses, today = today)
        assertTrue("baseline should surface a top-spend alert", withTopSpend.any { it.category == "top-spend" })

        val suppressed = engine.computeAlerts(
            trips = emptyList(),
            expenses = expenses,
            learned = LearnedPreferences(dismissedCategories = setOf("top-spend")),
            today = today,
        )
        assertFalse("top-spend must be suppressed once repeatedly dismissed", suppressed.any { it.category == "top-spend" })
    }
}
