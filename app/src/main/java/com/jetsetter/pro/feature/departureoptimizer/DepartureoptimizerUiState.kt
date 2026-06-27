package com.jetsetter.pro.feature.departureoptimizer

/**
 * Single immutable UI-state object for the Departure Optimizer screen — one object rather than
 * scattered flags, so impossible states can't arise and previews are trivial.
 */
data class DepartureoptimizerUiState(
    val estimate: DepartureOptimizerEstimate = DepartureOptimizerEstimate(),
    /** True only during the initial load of persisted buffers (first render). */
    val isLoading: Boolean = true,
    /** True while a live-feed re-roll round-trip is in flight. */
    val isRefreshing: Boolean = false,
    /** Whether the "Adjust buffers" modal bottom sheet is currently presented. */
    val showAdjustSheet: Boolean = false,
    /** How many times the live feed has been re-rolled this session. */
    val refreshCount: Int = 0,
    val lastUpdatedLabel: String = "Live feed ready",
)
