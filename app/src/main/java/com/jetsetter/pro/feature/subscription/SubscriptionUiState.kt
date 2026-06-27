package com.jetsetter.pro.feature.subscription

import java.time.LocalDate

/**
 * Single immutable UI-state object for the Subscription paywall — one object rather than scattered
 * flags so impossible states can't arise and previews stay trivial (guide §6). All money/date
 * figures are derived from [plans] + [todayIso] via [SubscriptionMath], never stored statically.
 */
data class SubscriptionUiState(
    val isLoading: Boolean = false,
    /** A mock billing round-trip is in flight (CTA shows a spinner, taps are ignored). */
    val isProcessing: Boolean = false,
    val isProActive: Boolean = false,
    /** For the closed beta everything is unlocked regardless of [isProActive]. */
    val betaUnlocked: Boolean = true,
    val features: List<SubscriptionFeature> = emptyList(),
    val plans: List<SubscriptionPlan> = emptyList(),
    val selectedPlanId: String? = null,
    /** Plan the active subscription was purchased on (drives the Pro banner). */
    val activePlanId: String? = null,
    /** ISO date the active subscription began — base for the renewal calculation. */
    val subscribedOnIso: String? = null,
    /** Reference "today" the ViewModel stamped at load so quotes stay deterministic. */
    val todayIso: String = LocalDate.now().toString(),
) {
    val selectedPlan: SubscriptionPlan?
        get() = plans.firstOrNull { it.id == selectedPlanId }

    /** The plan backing the currently active subscription. */
    val activePlan: SubscriptionPlan?
        get() = plans.firstOrNull { it.id == activePlanId }

    /** Self-consistent breakdown (per-month, savings, renewal date) for the selected plan. */
    val selectedQuote: SubscriptionQuote?
        get() {
            val plan = selectedPlan ?: return null
            val today = runCatching { LocalDate.parse(todayIso) }.getOrElse { LocalDate.now() }
            return SubscriptionMath.quote(plan, plans, today)
        }

    /** Renewal date for the *active* subscription = its start date + one billing cycle. */
    val activeRenewalDateIso: String?
        get() {
            val plan = activePlan ?: return null
            val start = subscribedOnIso ?: return null
            return runCatching {
                LocalDate.parse(start).plusMonths(plan.cycle.months.toLong()).toString()
            }.getOrNull()
        }
}
