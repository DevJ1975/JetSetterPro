package com.jetsetter.pro.feature.localexperience

import kotlin.math.ln

/**
 * Single immutable UI-state object for the Local Experiences screen. Everything the screen
 * renders is derived from a few primitive fields, so impossible/contradictory states can't
 * arise and the filtered view never drifts out of sync with the inputs.
 *
 * - [selectedCategory] of `null` means "All".
 * - [savedIds] is the persisted set of bookmarked experience ids (survives restart).
 * - [showSavedOnly] narrows the list to saved items, combining with the category filter.
 * - [sort] picks a real comparator applied to the filtered list.
 */
data class LocalexperienceUiState(
    val experiences: List<LocalExperiencesItem> = emptyList(),
    val selectedCategory: LocalExperiencesCategory? = null,
    val savedIds: Set<String> = emptySet(),
    val showSavedOnly: Boolean = false,
    val sort: LocalExperienceSort = LocalExperienceSort.RECOMMENDED,
    val isLoading: Boolean = false,
) {
    /** Count of every experience in the catalog, per category — powers the filter-chip badges. */
    val categoryCounts: Map<LocalExperiencesCategory, Int>
        get() = experiences.groupingBy { it.category }.eachCount()

    /** How many catalog items are currently bookmarked (drives the "Saved" chip badge). */
    val savedCount: Int
        get() = experiences.count { it.id in savedIds }

    /**
     * The filtered + sorted list actually rendered. Category and "saved only" combine with AND;
     * the sort is applied last so the order on screen always matches the chosen option.
     */
    val visibleExperiences: List<LocalExperiencesItem>
        get() {
            val filtered = experiences.filter { item ->
                (selectedCategory == null || item.category == selectedCategory) &&
                    (!showSavedOnly || item.id in savedIds)
            }
            return when (sort) {
                // Blends rating with review volume (log-damped) so a 4.9★/200-review pick can
                // outrank a 5.0★/3-review one — genuinely distinct from pure TOP_RATED below.
                LocalExperienceSort.RECOMMENDED ->
                    filtered.sortedByDescending { it.recommendationScore }
                LocalExperienceSort.TOP_RATED -> filtered.sortedWith(
                    compareByDescending<LocalExperiencesItem> { it.rating }
                        .thenByDescending { it.reviewCount },
                )
                LocalExperienceSort.PRICE_LOW -> filtered.sortedBy { it.price }
                LocalExperienceSort.MOST_REVIEWED -> filtered.sortedByDescending { it.reviewCount }
            }
        }

    fun isSaved(id: String): Boolean = id in savedIds
}

/** Log-damped popularity score: rewards both a high rating and a deep review base. */
private val LocalExperiencesItem.recommendationScore: Double
    get() = rating * ln(1.0 + reviewCount)
