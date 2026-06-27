package com.jetsetter.pro.feature.localexperience

/**
 * Categories used both for the filter row and for tagging each curated activity.
 * Module-prefixed names avoid collisions with other feature modules.
 */
enum class LocalExperiencesCategory(val label: String) {
    FOOD_AND_DRINK("Food & Drink"),
    OUTDOOR("Outdoor"),
    CULTURE("Culture"),
    NIGHTLIFE("Nightlife"),
    WELLNESS("Wellness"),
    TOURS("Tours"),
}

/**
 * Ordering the tester can apply to the visible list. Each option drives a real comparator in
 * [LocalexperienceUiState.visibleExperiences] — no cosmetic-only sort that contradicts the rows.
 */
enum class LocalExperienceSort(val label: String) {
    RECOMMENDED("Recommended"),
    TOP_RATED("Top rated"),
    PRICE_LOW("Price"),
    MOST_REVIEWED("Most reviewed"),
}

/**
 * A single curated local activity rendered as a card. [colorHex] backs the colored photo
 * placeholder (used as `Color(colorHex)`, so encode with the 0xFF alpha prefix).
 */
data class LocalExperiencesItem(
    val id: String,
    val title: String,
    val category: LocalExperiencesCategory,
    val neighborhood: String,
    val durationLabel: String,
    val price: Double,
    val rating: Double,
    val reviewCount: Int,
    val colorHex: Long,
)
