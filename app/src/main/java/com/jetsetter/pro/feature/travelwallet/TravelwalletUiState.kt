package com.jetsetter.pro.feature.travelwallet

/**
 * Single immutable UI-state object for the Travel Wallet screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial. Every number the UI shows is
 * derived here from [passes], so the summary, chips, and list can never contradict each other.
 */
data class TravelWalletUiState(
    val passes: List<TravelWalletItem> = emptyList(),
    val isLoading: Boolean = false,
    /** Active type filter; `null` means "All". */
    val selectedFilter: TravelWalletPassType? = null,
    /** Whether the "Add to wallet" modal sheet is presented. */
    val showAddSheet: Boolean = false,
    /** True while a newly added pass is being persisted (drives the sheet's saving spinner). */
    val isSaving: Boolean = false,
) {
    /** Passes after the active filter is applied — the list actually rendered. */
    val visiblePasses: List<TravelWalletItem>
        get() = selectedFilter?.let { type -> passes.filter { it.type == type } } ?: passes

    /** Pinned passes, computed (never a static count). */
    val pinnedCount: Int get() = passes.count { it.isFavorite }

    /** Distinct pass types currently held — drives the summary "types" metric. */
    val typeCount: Int get() = passes.distinctBy { it.type }.size

    /** Types that have at least one pass, in canonical order — only these get a filter chip. */
    val presentTypes: List<TravelWalletPassType>
        get() = TravelWalletPassType.entries.filter { type -> passes.any { it.type == type } }

    /** How many passes of [type] are held. */
    fun countFor(type: TravelWalletPassType): Int = passes.count { it.type == type }

    /** Wallet has passes, but none match the active filter. */
    val isFilteredEmpty: Boolean get() = passes.isNotEmpty() && visiblePasses.isEmpty()
}
