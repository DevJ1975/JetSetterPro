package com.jetsetter.pro.feature.itinerary

import com.jetsetter.pro.core.model.Trip

/**
 * Single immutable UI-state object for the Itinerary screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial (guide §6).
 */
data class ItineraryUiState(
    val trips: List<Trip> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Whether the "Add Trip" modal bottom sheet is currently presented. */
    val showAddSheet: Boolean = false,
)