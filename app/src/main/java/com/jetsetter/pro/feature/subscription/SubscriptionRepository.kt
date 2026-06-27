package com.jetsetter.pro.feature.subscription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory source of the Pro paywall catalog. Mock-first: there is no billing client or network
 * here — purchases are simulated in the ViewModel. Mirrors the iOS `StoreKit` config that ships
 * with the production build.
 */
@Singleton
class SubscriptionRepository @Inject constructor() {

    private val features = listOf(
        SubscriptionFeature(
            id = "tracking",
            title = "Unlimited flight tracking",
            description = "Follow every leg of every trip in real time — no cap on watched flights.",
        ),
        SubscriptionFeature(
            id = "disruption",
            title = "Disruption auto-response",
            description = "IRIS rebooks, finds lounges, and drafts the airline message the moment a flight slips.",
        ),
        SubscriptionFeature(
            id = "vaults",
            title = "Document vaults",
            description = "Encrypted vaults for passports, visas, loyalty cards, and trip receipts.",
        ),
        SubscriptionFeature(
            id = "exports",
            title = "Expense exports",
            description = "One-tap exports to Brex, Ramp, Divvy, and Expensify with receipt OCR.",
        ),
    )

    // Prices only — every derived figure (per-month equivalent, savings %, renewal date) is
    // computed by SubscriptionMath so the cards can't drift out of sync with the catalog.
    private val plans = listOf(
        SubscriptionPlan(
            id = "monthly",
            cycle = SubscriptionBillingCycle.MONTHLY,
            title = "Monthly",
            price = 9.99,
            tagline = "Flexible — cancel anytime.",
        ),
        SubscriptionPlan(
            id = "annual",
            cycle = SubscriptionBillingCycle.ANNUAL,
            title = "Annual",
            price = 79.99,
            tagline = "Billed once a year — best for frequent flyers.",
            isBestValue = true,
        ),
    )

    /** The Pro capabilities to advertise on the paywall. */
    fun observeFeatures(): Flow<List<SubscriptionFeature>> = flowOf(features)

    suspend fun loadFeatures(): List<SubscriptionFeature> = features

    /** The purchasable plans, best-value last. */
    suspend fun loadPlans(): List<SubscriptionPlan> = plans
}
