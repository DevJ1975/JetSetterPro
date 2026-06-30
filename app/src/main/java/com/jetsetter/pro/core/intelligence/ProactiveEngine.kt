package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.UserPreferences
import com.jetsetter.pro.feature.home.AlertSeverity
import com.jetsetter.pro.feature.home.HomeAlert
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure, on-device "what would IRIS proactively flag right now?" engine. Given the live trip and
 * expense ledgers (plus optional [UserPreferences]) it derives a list of **real, data-grounded**
 * [HomeAlert]s — an upcoming-trip nudge, a packing reminder for a near trip with an empty list, and
 * a top-spend heads-up — reusing the existing Home alert model so the dashboard renders unchanged.
 *
 * Stateless and deterministic: every input arrives as a parameter (including [today], so it stays
 * unit-testable and time-independent). No I/O, no Room, no network — it's a @Singleton only so Hilt
 * can inject it like the rest of the graph.
 */
@Singleton
class ProactiveEngine @Inject constructor() {

    /**
     * @return data-derived alerts ordered most-urgent-first; falls back to a pair of generic IRIS
     *   nudges when there's nothing to say so the dashboard is never empty.
     */
    fun computeAlerts(
        trips: List<Trip>,
        expenses: List<Expense>,
        prefs: UserPreferences? = null,
        today: LocalDate = LocalDate.now(),
    ): List<HomeAlert> {
        val alerts = mutableListOf<HomeAlert>()

        // Nearest not-yet-departed trip (by parseable ISO start date), if any.
        val nextTrip = trips
            .mapNotNull { trip -> parseDate(trip.startDate)?.let { trip to it } }
            .filter { (_, start) -> !start.isBefore(today) }
            .minByOrNull { (_, start) -> start }

        if (nextTrip != null) {
            val (trip, start) = nextTrip
            val daysOut = ChronoUnit.DAYS.between(today, start)
            if (daysOut <= UPCOMING_WINDOW_DAYS) {
                alerts += HomeAlert(
                    id = "upcoming-trip-${trip.id}",
                    title = "Trip coming up",
                    message = buildString {
                        append(trip.name)
                        append(" to ")
                        append(trip.destination)
                        append(
                            when (daysOut) {
                                0L -> " departs today — IRIS has your itinerary ready."
                                1L -> " departs tomorrow. Want IRIS to run a final check?"
                                else -> " is $daysOut days out. Want IRIS to prep your itinerary?"
                            }
                        )
                    },
                    severity = if (daysOut <= NEAR_WINDOW_DAYS) AlertSeverity.WARNING else AlertSeverity.INFO,
                )

                // Packing reminder only makes sense for a genuinely-near trip that has no list yet.
                if (daysOut <= NEAR_WINDOW_DAYS && trip.packingList.isEmpty()) {
                    alerts += HomeAlert(
                        id = "packing-${trip.id}",
                        title = "Nothing packed yet",
                        message = "Your ${trip.destination} packing list is empty and you leave " +
                            (if (daysOut == 0L) "today" else "in $daysOut day${if (daysOut == 1L) "" else "s"}") +
                            ". Tap to have IRIS draft one.",
                        severity = AlertSeverity.WARNING,
                    )
                }
            }
        }

        // Top single merchant across the ledger — the "Delta airfare is your largest expense" nudge.
        val topMerchant = expenses
            .groupBy { it.merchant }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .maxByOrNull { it.value }
        if (topMerchant != null && topMerchant.value > 0.0) {
            val currency = expenses.firstOrNull { it.merchant == topMerchant.key }?.currency ?: "USD"
            val category = expenses.firstOrNull { it.merchant == topMerchant.key }?.category?.label
            alerts += HomeAlert(
                id = "top-spend-${topMerchant.key}",
                title = "Biggest expense",
                message = buildString {
                    append(topMerchant.key)
                    if (category != null) append(" ($category)")
                    append(" is your largest expense at ")
                    append(formatAmount(topMerchant.value, currency))
                    append(" — export to Brex?")
                },
                severity = AlertSeverity.INFO,
            )
        }

        return alerts.ifEmpty { fallbackAlerts(prefs) }
    }

    /** Generic, data-free nudges shown when there are no trips or expenses to reason about. */
    private fun fallbackAlerts(prefs: UserPreferences?): List<HomeAlert> {
        val airport = prefs?.homeAirport?.takeIf { it.isNotBlank() }
        return listOf(
            HomeAlert(
                id = "no-trips-yet",
                title = "Plan your next trip",
                message = if (airport != null) {
                    "No trips on the radar. Tell IRIS where you're headed from $airport and it'll build the itinerary."
                } else {
                    "No trips on the radar yet. Add one and IRIS will start watching for gate changes and weather."
                },
                severity = AlertSeverity.INFO,
            ),
            HomeAlert(
                id = "track-spend",
                title = "Track your spend",
                message = "Log expenses as you go and IRIS will surface your biggest costs for easy export.",
                severity = AlertSeverity.INFO,
            ),
        )
    }

    private fun parseDate(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

    private fun formatAmount(amount: Double, currency: String): String {
        val symbol = when (currency.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> ""
        }
        val rounded = "%,.0f".format(amount)
        return if (symbol.isNotEmpty()) "$symbol$rounded" else "$rounded $currency"
    }

    private companion object {
        /** Surface an upcoming-trip nudge once the trip is within this many days. */
        const val UPCOMING_WINDOW_DAYS = 14L

        /** "Near" trips (escalated severity + packing reminder) are within this many days. */
        const val NEAR_WINDOW_DAYS = 7L
    }
}
