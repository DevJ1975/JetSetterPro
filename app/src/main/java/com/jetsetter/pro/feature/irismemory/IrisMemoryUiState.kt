package com.jetsetter.pro.feature.irismemory

import com.jetsetter.pro.core.intelligence.IrisPreference
import com.jetsetter.pro.core.intelligence.IrisPreferenceCategory

/**
 * Single immutable UI-state object for the IRIS Memory & Privacy screen — one object rather than
 * scattered flags, so impossible states can't arise and previews are trivial.
 *
 * The grouped list is derived here from [preferences], so the rendered sections can never drift
 * out of sync with the underlying store.
 */
data class IrisMemoryUiState(
    /** Everything IRIS currently remembers (raw, unsorted — see [grouped]). */
    val preferences: List<IrisPreference> = emptyList(),
    /** Master learning consent (spec §1.6): off = no travel signal is ever recorded. */
    val learningEnabled: Boolean = true,
    /** Gates receiptScanned/expenseLogged signals (only meaningful while [learningEnabled]). */
    val learnFromReceipts: Boolean = true,
    /** Gates seatChosen signals (only meaningful while [learningEnabled]). */
    val learnFromCheckIns: Boolean = true,
    /** Gates flightFlown/tripCompleted/placeVisited signals (only meaningful while [learningEnabled]). */
    val learnFromTrips: Boolean = true,
    val isLoading: Boolean = false,
    /** "Forget everything" destructive confirmation dialog visibility. */
    val showForgetDialog: Boolean = false,
) {
    /**
     * Preferences grouped by category — categories in enum declaration order (the same order
     * IRIS's prompt summary uses), rows within a category by confidence descending.
     */
    val grouped: List<Pair<IrisPreferenceCategory, List<IrisPreference>>>
        get() = IrisPreferenceCategory.entries.mapNotNull { category ->
            val rows = preferences
                .filter { it.category == category }
                .sortedByDescending { it.confidence }
            if (rows.isEmpty()) null else category to rows
        }

    /** True only when IRIS has remembered nothing (first run / after "forget everything"). */
    val isEmpty: Boolean get() = preferences.isEmpty()
}
