package com.jetsetter.pro.feature.lovedones

import com.jetsetter.pro.core.data.lovedones.LovedOne

/**
 * Single immutable UI-state object for the Loved Ones screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial.
 */
data class LovedonesUiState(
    val contacts: List<LovedOne> = emptyList(),
    val isLoading: Boolean = false,
    /** Add/edit bottom sheet visibility; [editing] decides which mode it opens in. */
    val showEditSheet: Boolean = false,
    /** The contact being edited, or null when the sheet is adding a new one. */
    val editing: LovedOne? = null,
    val isSaving: Boolean = false,
) {
    /** True only when there are no contacts at all (first run / everything deleted). */
    val isEmpty: Boolean get() = contacts.isEmpty()
}
