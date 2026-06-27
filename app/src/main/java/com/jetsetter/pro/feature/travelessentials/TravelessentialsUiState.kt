package com.jetsetter.pro.feature.travelessentials

/**
 * Single immutable UI-state object for the Travel Essentials screen — one object rather than
 * scattered flags, so impossible states can't arise and previews stay trivial.
 */
data class TravelessentialsUiState(
    val isLoading: Boolean = false,
    val destinations: List<TravelEssentialsDestination> = emptyList(),
    val selectedDestinationId: String? = null,
    val phrasesExpanded: Boolean = false,
) {
    /** The destination currently in focus, or null while loading. */
    val selectedDestination: TravelEssentialsDestination?
        get() = destinations.firstOrNull { it.id == selectedDestinationId }
            ?: destinations.firstOrNull()
}
