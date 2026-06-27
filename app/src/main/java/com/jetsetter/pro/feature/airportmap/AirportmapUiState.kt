package com.jetsetter.pro.feature.airportmap

/**
 * Single immutable UI-state object for the Airport Guide screen — one object rather than
 * scattered flags, so previews stay trivial and impossible states can't arise. The view derives
 * everything (filtered sections, walk times, summary stats) from the raw [allItems] plus the
 * traveller's controls ([origin] / [query] / [categoryFilter] / [sort]), so the numbers on screen
 * can never drift out of sync with the inputs.
 */
data class AirportmapUiState(
    val airportName: String = "",
    val airportCode: String = "",
    val allItems: List<AirportGuideItem> = emptyList(),
    /** Where the traveller is right now; all walk times are measured from here. */
    val origin: Concourse = Concourse.T,
    val query: String = "",
    /** `null` means "All categories". */
    val categoryFilter: AirportAmenityCategory? = null,
    val sort: AmenitySort = AmenitySort.NEAREST,
    val expandedCategories: Set<AirportAmenityCategory> = emptySet(),
    val isLoading: Boolean = false,
) {
    val totalCount: Int get() = allItems.size

    /** Walk time, in minutes, from the current [origin] to [item]. */
    fun walkMinutes(item: AirportGuideItem): Int =
        AmenityWalkModel.minutesBetween(origin, item.concourse, item.localWalkMinutes)

    /** Items left after the active search query and category filter are applied. */
    private val matchingItems: List<AirportGuideItem>
        get() = allItems.filter { item ->
            (categoryFilter == null || item.category == categoryFilter) && item.matches(query)
        }

    /** Count of amenities currently shown (after filter + search). */
    val visibleCount: Int get() = matchingItems.size

    /** Filtered amenities grouped into category sections, each ordered by the active [sort]. */
    val sections: List<AirportGuideSection>
        get() {
            val grouped = matchingItems.groupBy { it.category }
            return AirportAmenityCategory.values().mapNotNull { category ->
                grouped[category]?.let { items ->
                    val ordered = when (sort) {
                        AmenitySort.NEAREST -> items.sortedBy { walkMinutes(it) }
                        AmenitySort.AREA -> items.sortedWith(
                            compareBy({ it.concourse.ordinal }, { it.localWalkMinutes }),
                        )
                    }
                    AirportGuideSection(category, ordered)
                }
            }
        }

    /** Nearest amenity in the current view (respects filter + search), by walk time. */
    val nearest: AirportGuideItem? get() = matchingItems.minByOrNull { walkMinutes(it) }
    val nearestWalkMinutes: Int? get() = nearest?.let { walkMinutes(it) }

    /** How many of the currently shown amenities are a short (<= 5 min) walk away. */
    val withinShortWalk: Int
        get() = matchingItems.count { walkMinutes(it) <= AmenityWalkModel.SHORT_WALK_MINUTES }

    fun countFor(category: AirportAmenityCategory): Int = allItems.count { it.category == category }

    val hasActiveFilter: Boolean get() = categoryFilter != null || query.isNotBlank()
    val isEmpty: Boolean get() = !isLoading && allItems.isEmpty()
    val isFilteredEmpty: Boolean get() = !isLoading && allItems.isNotEmpty() && matchingItems.isEmpty()

    fun isExpanded(category: AirportAmenityCategory): Boolean = category in expandedCategories

    /**
     * Whether a section should render expanded. An active search or a single-category filter
     * force the matching sections open so results are never hidden behind a collapsed header.
     */
    fun effectiveExpanded(category: AirportAmenityCategory): Boolean = when {
        query.isNotBlank() -> true
        categoryFilter == category -> true
        else -> category in expandedCategories
    }

    /** The header chevron is only interactive when the section isn't force-expanded. */
    fun canToggle(category: AirportAmenityCategory): Boolean =
        query.isBlank() && categoryFilter != category
}
