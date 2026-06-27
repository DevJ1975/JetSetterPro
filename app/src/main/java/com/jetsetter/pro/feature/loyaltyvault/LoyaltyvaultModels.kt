package com.jetsetter.pro.feature.loyaltyvault

/** Whether a loyalty program is an airline frequent-flyer or a hotel rewards program. */
enum class LoyaltyVaultProgramType(
    val label: String,
    /** Default redemption value for a tester-added program (real-world ballpark cents per unit). */
    val defaultCentsPerUnit: Double,
    /** Unit a balance is measured in for this kind of program. */
    val defaultUnitLabel: String,
) {
    AIRLINE("Airline", defaultCentsPerUnit = 1.3, defaultUnitLabel = "miles"),
    HOTEL("Hotel", defaultCentsPerUnit = 0.7, defaultUnitLabel = "points"),
}

/** Segmented filter applied to the account list (and to the summary scope). */
enum class LoyaltyVaultFilter(val label: String, val scopeLabel: String) {
    ALL("All", "All programs"),
    AIRLINE("Airlines", "Airline programs"),
    HOTEL("Hotels", "Hotel programs"),
}

/** How the account list is ordered. Pinned accounts always float to the top within each order. */
enum class LoyaltyVaultSort(val label: String) {
    VALUE("Value"),
    BALANCE("Balance"),
    NAME("Name"),
}

/**
 * A single frequent-flyer or hotel loyalty account.
 *
 * [membershipNumber] is the full number; the screen masks it (••••1234) until the user taps to
 * reveal it. [balance] is in the program's own unit ([unitLabel]); [centsPerUnit] turns that into a
 * real, self-consistent cash estimate. Tier-chip progress is driven by [tierProgress]/[tierTarget]
 * toward [nextTier] (null = already top tier).
 *
 * Computed values live as body `val`s so Moshi (constructor-only) ignores them on serialize.
 */
data class LoyaltyVaultAccount(
    val id: String,
    val programName: String,
    val membershipNumber: String,
    val tier: String,
    val balance: Long,
    val unitLabel: String,
    val type: LoyaltyVaultProgramType,
    val centsPerUnit: Double = type.defaultCentsPerUnit,
    val nextTier: String? = null,
    val tierProgress: Int = 0,
    val tierTarget: Int = 0,
    val tierUnit: String = "",
    val isPinned: Boolean = false,
) {
    /** Real cash estimate for this balance, in whole US dollars (rounded). */
    val estimatedValueUsd: Long get() = Math.round(balance * centsPerUnit / 100.0)

    /** Whether the member is already at the program's top published tier. */
    val isTopTier: Boolean get() = nextTier == null || tierTarget <= 0

    /** Progress toward [nextTier] as 0f..1f (full when already top tier). */
    val tierFraction: Float
        get() = if (isTopTier) 1f else (tierProgress.toFloat() / tierTarget).coerceIn(0f, 1f)

    /** Remaining qualifying activity needed to reach [nextTier] (0 when top tier). */
    val tierRemaining: Int get() = if (isTopTier) 0 else (tierTarget - tierProgress).coerceAtLeast(0)
}
