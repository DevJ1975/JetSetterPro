package com.jetsetter.pro.feature.subscription

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Billing cadence for a [SubscriptionPlan]. Module-prefixed names avoid collisions with other
 * feature modules (guide: never plain `Status`/`Item`). [months] and [unitSuffix] let every price
 * figure (per-month equivalent, savings, renewal date) be derived rather than hard-coded.
 */
enum class SubscriptionBillingCycle(val label: String, val months: Int, val unitSuffix: String) {
    MONTHLY("Monthly", 1, "/mo"),
    ANNUAL("Annual", 12, "/yr"),
}

/** A purchasable plan rendered as a selectable card on the paywall. */
data class SubscriptionPlan(
    val id: String,
    val cycle: SubscriptionBillingCycle,
    val title: String,
    /** Amount billed once per [cycle]. */
    val price: Double,
    val tagline: String,
    val isBestValue: Boolean = false,
) {
    /** Equivalent cost per month (annual ÷ 12) — the apples-to-apples comparison figure. */
    val pricePerMonth: Double get() = price / cycle.months

    /** Suffix shown next to the headline price, derived from the cycle (e.g. "/mo", "/yr"). */
    val pricePeriod: String get() = cycle.unitSuffix
}

/** One Pro capability shown in the feature list. */
data class SubscriptionFeature(
    val id: String,
    val title: String,
    val description: String,
)

/**
 * Self-consistent breakdown for a single plan. Every figure is *derived* from the catalog prices
 * and a reference date, so the order summary, savings chip, and renewal line can never contradict
 * the plan cards (the previous build hard-coded "Save 33%").
 */
data class SubscriptionQuote(
    val plan: SubscriptionPlan,
    /** Charged immediately on subscribe. */
    val dueToday: Double,
    /** Effective monthly cost for this plan. */
    val pricePerMonth: Double,
    /** Money saved over a year vs. paying the monthly plan for 12 months. */
    val savingsPerYear: Double,
    /** [savingsPerYear] as a whole-number percentage of the monthly-billed baseline. */
    val savingsPercent: Int,
    /** ISO date this plan would next renew (reference date + one cycle). */
    val renewalDateIso: String,
) {
    val hasSavings: Boolean get() = savingsPercent > 0 && savingsPerYear > 0.0
}

/**
 * Persisted Pro entitlement (Moshi-serialized via `ModuleStateStore`). Replaces the old bare
 * boolean so the Pro banner can show the real plan and a computed renewal date after a restart.
 */
data class SubscriptionEntitlement(
    val isProActive: Boolean = false,
    val planId: String? = null,
    val startedOnIso: String? = null,
)

/** Pure pricing helpers — kept off the UI thread-aware types so they're trivially unit-testable. */
internal object SubscriptionMath {

    /**
     * Builds a [SubscriptionQuote] for [plan], measuring savings against the cheapest monthly
     * baseline in [allPlans] (annualized) and dating the renewal as [referenceDate] + one cycle.
     */
    fun quote(
        plan: SubscriptionPlan,
        allPlans: List<SubscriptionPlan>,
        referenceDate: LocalDate,
    ): SubscriptionQuote {
        val baselinePerMonth = allPlans
            .filter { it.cycle == SubscriptionBillingCycle.MONTHLY }
            .minOfOrNull { it.pricePerMonth }
            ?: plan.pricePerMonth
        val baselinePerYear = baselinePerMonth * 12
        val planPerYear = plan.pricePerMonth * 12
        val savings = (baselinePerYear - planPerYear).coerceAtLeast(0.0)
        val percent = if (baselinePerYear > 0.0) Math.round(savings / baselinePerYear * 100).toInt() else 0
        val renewal = referenceDate.plusMonths(plan.cycle.months.toLong())
        return SubscriptionQuote(
            plan = plan,
            dueToday = plan.price,
            pricePerMonth = plan.pricePerMonth,
            savingsPerYear = savings,
            savingsPercent = percent,
            renewalDateIso = renewal.format(DateTimeFormatter.ISO_LOCAL_DATE),
        )
    }
}
