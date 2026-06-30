package com.jetsetter.pro.core.intelligence

/**
 * A snapshot of what IRIS has learned about the user, for the deterministic [ProactiveEngine].
 *
 * PRIVACY: derived from [UserMemory], which is PERSONAL and on-device only. Pass this to the engine
 * and on-device UI — never into a Claude/cloud request.
 */
data class LearnedPreferences(
    val topTopics: List<String> = emptyList(),
    val topAirlines: List<String> = emptyList(),
    val topHotels: List<String> = emptyList(),
    val topCuisines: List<String> = emptyList(),
    /** Alert/suggestion categories the user keeps dismissing — demote or suppress these. */
    val dismissedCategories: Set<String> = emptySet(),
    /** Hour of day (0–23) the user most often engages, if known. */
    val peakHour: Int? = null,
) {
    fun isEmpty(): Boolean =
        topTopics.isEmpty() && topAirlines.isEmpty() && topHotels.isEmpty() &&
            topCuisines.isEmpty() && dismissedCategories.isEmpty() && peakHour == null

    /** Compact personal lines for the on-device IRIS prompt (PERSONAL — on-device tier only). */
    fun toContext(): LearnedContext = LearnedContext(
        buildList {
            if (topAirlines.isNotEmpty()) add("- Prefers airline(s): ${topAirlines.joinToString(", ")}")
            if (topHotels.isNotEmpty()) add("- Prefers hotel(s): ${topHotels.joinToString(", ")}")
            if (topCuisines.isNotEmpty()) add("- Prefers cuisine(s): ${topCuisines.joinToString(", ")}")
            if (topTopics.isNotEmpty()) add("- Frequently asks about: ${topTopics.joinToString(", ")}")
        },
    )
}

/** Formatted personal preference lines for the on-device prompt. */
data class LearnedContext(val lines: List<String> = emptyList()) {
    fun isEmpty(): Boolean = lines.isEmpty()
}
