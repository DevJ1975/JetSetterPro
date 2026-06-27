package com.jetsetter.pro.feature.luggagetracker

/**
 * Single immutable UI-state object for the Luggage Tracker screen — one object rather than
 * scattered flags, so impossible states can't arise and previews are trivial.
 *
 * Everything visible is *derived* from a small set of inputs:
 *  - [visibleBags] applies the live [query] filter.
 *  - [selectedBag] resolves [selectedTagId] against the visible set, so the selection can
 *    never drift out of sync (or point at a bag the search has hidden).
 *  - [statusCounts] are real counts over [bags], so the summary chips always add up.
 */
data class LuggagetrackerUiState(
    val bags: List<LuggageTrackerBag> = emptyList(),
    val selectedTagId: String? = null,
    /** Live search text; filters the registered-bag list as the tester types. */
    val query: String = "",
    /** Clock the relative ("x min ago") labels are measured against; captured once at load. */
    val nowMillis: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Tag id of the bag whose rename sheet is open, or null when the sheet is dismissed. */
    val renamingTagId: String? = null,
) {
    /** Bags matching the current [query] (by tag id, description, or nickname). */
    val visibleBags: List<LuggageTrackerBag>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return bags
            return bags.filter { bag ->
                bag.tagId.contains(q, ignoreCase = true) ||
                    bag.description.contains(q, ignoreCase = true) ||
                    (bag.nickname?.contains(q, ignoreCase = true) == true)
            }
        }

    /** The selected bag, constrained to the visible set so it can never point off-list. */
    val selectedBag: LuggageTrackerBag?
        get() = visibleBags.firstOrNull { it.tagId == selectedTagId } ?: visibleBags.firstOrNull()

    /** The bag whose rename sheet is currently open, if any. */
    val renamingBag: LuggageTrackerBag?
        get() = bags.firstOrNull { it.tagId == renamingTagId }

    /** Real per-status counts over every registered bag, in enum order. */
    val statusCounts: List<Pair<LuggageTrackerStatus, Int>>
        get() = LuggageTrackerStatus.entries
            .map { status -> status to bags.count { it.status == status } }
            .filter { it.second > 0 }

    /** Timestamp of the most recent scan across all bags, for the header "last seen" line. */
    val mostRecentScanMillis: Long?
        get() = bags.mapNotNull { it.lastScan?.timestampMillis }.maxOrNull()

    /** True when there are bags but the query matched none — drives the "no results" state. */
    val hasNoSearchResults: Boolean
        get() = bags.isNotEmpty() && visibleBags.isEmpty()
}
