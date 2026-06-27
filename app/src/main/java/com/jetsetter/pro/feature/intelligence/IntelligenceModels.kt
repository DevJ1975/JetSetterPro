package com.jetsetter.pro.feature.intelligence

/**
 * Domain models for the Travel Intelligence feature. Names are module-prefixed to avoid
 * collisions with other feature packages. Kept free of Compose/Android types so the
 * repository and view-model stay pure and unit-testable.
 */

/** What part of the trip an insight is about — drives the icon + accent on screen. */
enum class IntelligenceCategory(val label: String) {
    FLIGHT("Flight"),
    HOTEL("Hotel"),
    SECURITY("Security"),
    EXPENSE("Expense"),
    WEATHER("Weather"),
}

/** What KIND of attention an insight wants — drives the type tag next to the action chip. */
enum class IntelligencePriority(val label: String) {
    ALERT("Action needed"),
    OPPORTUNITY("Opportunity"),
    INFO("Heads up"),
}

/**
 * Graduated urgency, independent of [IntelligencePriority] type. An opportunity can be high
 * severity (big savings) or low (nice-to-have). Drives the severity chip, the highest-first
 * sort order and the severity filter. [weight] is used for sorting (higher = more urgent).
 */
enum class IntelligenceSeverity(val label: String, val weight: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1),
}

/**
 * A quantified, self-consistent impact for an insight. Every figure a card surfaces is DERIVED
 * here from raw inputs (never hard-coded into prose), so the headline number can never contradict
 * the values it is computed from.
 */
sealed interface InsightMetric {

    /** Re-booking / re-pricing saving. [savedCents] is derived from current vs proposed. */
    data class CostSaving(
        val currentCents: Int,
        val proposedCents: Int,
        /** Per-unit label, e.g. "night". */
        val cadence: String,
    ) : InsightMetric {
        val savedCents: Int get() = (currentCents - proposedCents).coerceAtLeast(0)
    }

    /** Points/miles redemption. [remainingPoints] and [affordable] are derived from the balance. */
    data class PointsCost(
        val costPoints: Int,
        val balancePoints: Int,
    ) : InsightMetric {
        val remainingPoints: Int get() = balancePoints - costPoints
        val affordable: Boolean get() = balancePoints >= costPoints
    }

    /** Outstanding money items (e.g. receipts). [count] and [totalCents] are derived from the list. */
    data class Outstanding(
        val amountsCents: List<Int>,
    ) : InsightMetric {
        val count: Int get() = amountsCents.size
        val totalCents: Int get() = amountsCents.sum()
    }

    /** A wait reading measured against a buffer. [overBufferMinutes] is derived. */
    data class Wait(
        val currentMinutes: Int,
        val bufferMinutes: Int,
    ) : InsightMetric {
        /** Positive → over the buffer (tight); negative/zero → spare time. */
        val overBufferMinutes: Int get() = currentMinutes - bufferMinutes
    }
}

/**
 * A single proactive insight surfaced to the traveler.
 *
 * @param severity graduated urgency that drives the severity chip, sort order and the filter.
 * @param metric optional quantified impact whose displayed figures are all derived (see [InsightMetric]).
 * @param actionLabel short verb for the action chip, e.g. "Rebook" / "View hotel".
 * @param dismissed whether the user has cleared it into history (can be restored).
 */
data class TravelIntelligenceItem(
    val id: String,
    val category: IntelligenceCategory,
    val priority: IntelligencePriority,
    val severity: IntelligenceSeverity,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val metric: InsightMetric? = null,
    val dismissed: Boolean = false,
)
