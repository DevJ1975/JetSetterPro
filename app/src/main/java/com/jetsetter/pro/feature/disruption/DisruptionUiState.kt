package com.jetsetter.pro.feature.disruption

/**
 * Single immutable UI-state object for the Disruption Monitor screen — one object rather
 * than scattered flags, so impossible states can't arise and previews stay trivial.
 *
 * Derived views ([sortedAlternatives], [selectedAlternative], [completedStepCount]) are
 * computed here so the screen never has to re-derive or risk showing stale numbers.
 */
data class DisruptionUiState(
    val isLoading: Boolean = false,
    val monitoredFlight: DisruptionMonitoredFlight? = null,
    val alternatives: List<DisruptionAlternative> = emptyList(),
    val responseSteps: List<DisruptionResponseStep> = emptyList(),
    val selectedAlternativeId: String? = null,
    val recommendedAlternativeId: String? = null,
    val sortMode: DisruptionSortMode = DisruptionSortMode.RECOMMENDED,
    /** True while a confirm is in flight (button shows a spinner, taps are ignored). */
    val isRebooking: Boolean = false,
    val isRebooked: Boolean = false,
) {
    /** Alternatives ordered by the active [sortMode], with a stable secondary key. */
    val sortedAlternatives: List<DisruptionAlternative>
        get() = when (sortMode) {
            DisruptionSortMode.RECOMMENDED ->
                alternatives.sortedWith(
                    compareByDescending<DisruptionAlternative> { it.valueScore }.thenBy { it.price },
                )
            DisruptionSortMode.PRICE ->
                alternatives.sortedWith(compareBy({ it.price }, { it.arrivalMin }))
            DisruptionSortMode.ARRIVAL ->
                alternatives.sortedWith(compareBy({ it.arrivalMin }, { it.price }))
        }

    val selectedAlternative: DisruptionAlternative?
        get() = alternatives.firstOrNull { it.id == selectedAlternativeId }

    val completedStepCount: Int
        get() = responseSteps.count { it.status == DisruptionStepStatus.DONE }
}
