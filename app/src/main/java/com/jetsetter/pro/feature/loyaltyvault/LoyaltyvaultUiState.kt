package com.jetsetter.pro.feature.loyaltyvault

/**
 * Single immutable UI-state object for the Loyalty Vault screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial.
 *
 * Everything visible (totals, breakdown, visible list) is derived here from [accounts] + [filter]
 * + [sort], so the summary can never drift out of sync with the cards below it.
 */
data class LoyaltyvaultUiState(
    val accounts: List<LoyaltyVaultAccount> = emptyList(),
    val filter: LoyaltyVaultFilter = LoyaltyVaultFilter.ALL,
    val sort: LoyaltyVaultSort = LoyaltyVaultSort.VALUE,
    /** Membership numbers the user has tapped to reveal (transient — never persisted). */
    val revealedIds: Set<String> = emptySet(),
    /** Privacy mode: masks every balance / value / number on screen until toggled off. */
    val hideBalances: Boolean = false,
    val isLoading: Boolean = false,
    val showAddSheet: Boolean = false,
    val isSaving: Boolean = false,
) {
    /** Accounts matching the active [filter], pinned first, then ordered by [sort]. */
    val visibleAccounts: List<LoyaltyVaultAccount>
        get() {
            val matched = when (filter) {
                LoyaltyVaultFilter.ALL -> accounts
                LoyaltyVaultFilter.AIRLINE -> accounts.filter { it.type == LoyaltyVaultProgramType.AIRLINE }
                LoyaltyVaultFilter.HOTEL -> accounts.filter { it.type == LoyaltyVaultProgramType.HOTEL }
            }
            val comparator: Comparator<LoyaltyVaultAccount> = when (sort) {
                LoyaltyVaultSort.VALUE -> compareByDescending { it.estimatedValueUsd }
                LoyaltyVaultSort.BALANCE -> compareByDescending { it.balance }
                LoyaltyVaultSort.NAME -> compareBy { it.programName.lowercase() }
            }
            return matched.sortedWith(compareByDescending<LoyaltyVaultAccount> { it.isPinned }.then(comparator))
        }

    /** True only when there are no accounts at all (first run / everything deleted). */
    val isEmptyVault: Boolean get() = accounts.isEmpty()

    /** Summary scoped to whatever the filter currently shows, so the numbers match the list. */
    val summary: LoyaltyVaultSummary
        get() {
            val scope = visibleAccounts
            return LoyaltyVaultSummary(
                scopeLabel = filter.scopeLabel,
                programCount = scope.size,
                airlineMiles = scope.filter { it.type == LoyaltyVaultProgramType.AIRLINE }.sumOf { it.balance },
                hotelPoints = scope.filter { it.type == LoyaltyVaultProgramType.HOTEL }.sumOf { it.balance },
                airlineValueUsd = scope.filter { it.type == LoyaltyVaultProgramType.AIRLINE }.sumOf { it.estimatedValueUsd },
                hotelValueUsd = scope.filter { it.type == LoyaltyVaultProgramType.HOTEL }.sumOf { it.estimatedValueUsd },
            )
        }
}

/** Pre-computed totals for the summary card — pure function of the (filtered) account set. */
data class LoyaltyVaultSummary(
    val scopeLabel: String,
    val programCount: Int,
    val airlineMiles: Long,
    val hotelPoints: Long,
    val airlineValueUsd: Long,
    val hotelValueUsd: Long,
) {
    val totalValueUsd: Long get() = airlineValueUsd + hotelValueUsd

    /** Airline share of total value as 0f..1f (used to size the breakdown bar). */
    val airlineValueShare: Float
        get() = if (totalValueUsd <= 0L) 0f else airlineValueUsd.toFloat() / totalValueUsd
}
