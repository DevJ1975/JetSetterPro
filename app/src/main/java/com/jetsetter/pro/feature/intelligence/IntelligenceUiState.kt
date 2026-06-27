package com.jetsetter.pro.feature.intelligence

/**
 * Single immutable UI-state object for the Travel Intelligence screen — active insights and the
 * dismissed-history bucket are kept as separate lists so the screen never has to filter inline.
 * [active] arrives pre-sorted highest-severity-first.
 */
data class IntelligenceUiState(
    val active: List<TravelIntelligenceItem> = emptyList(),
    val dismissed: List<TravelIntelligenceItem> = emptyList(),
    /** Ids of active insights whose action chip has been tapped — shown as confirmed, not hidden. */
    val acted: Set<String> = emptySet(),
    /** Selected severity filter for the active list; null means "All" (transient view preference). */
    val severityFilter: IntelligenceSeverity? = null,
    val isLoading: Boolean = false,
)
