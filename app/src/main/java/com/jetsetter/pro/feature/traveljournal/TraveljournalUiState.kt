package com.jetsetter.pro.feature.traveljournal

/**
 * Single immutable UI-state object for the Trip Journal screen — entries, the in-progress
 * composer draft, and the active mood filter — so the screen stays a pure function of state
 * and previews are trivial. Every summary number below is *derived* from [entries], so it can
 * never contradict what the traveler has actually logged.
 */
data class TraveljournalUiState(
    val entries: List<TripJournalEntry> = emptyList(),
    val draftTitle: String = "",
    val draftBody: String = "",
    val draftLocation: String = "",
    val draftMood: TripJournalMood = TripJournalMood.JOYFUL,
    /** Active mood filter for the entries list; null means "All". */
    val moodFilter: TripJournalMood? = null,
    val isLoading: Boolean = false,
) {
    /** A memory needs a real title before it can be saved — no more "Untitled memory" fallbacks. */
    val canAddEntry: Boolean get() = draftTitle.isNotBlank()

    /**
     * True once the traveler has started writing a body/location but still owes us a title, so we
     * can surface an inline validation hint instead of silently disabling the button.
     */
    val showTitleError: Boolean
        get() = draftTitle.isBlank() && (draftBody.isNotBlank() || draftLocation.isNotBlank())

    /** Entries after the active mood filter (null = show everything); newest-first order is kept. */
    val visibleEntries: List<TripJournalEntry>
        get() = moodFilter?.let { mood -> entries.filter { it.mood == mood } } ?: entries

    /** How many entries carry each mood — drives the filter-chip counts (only present moods shown). */
    val moodCounts: Map<TripJournalMood, Int>
        get() = entries.groupingBy { it.mood }.eachCount()

    val memoryCount: Int get() = entries.size

    /** Distinct, case-insensitive places that actually have a non-blank location. */
    val placeCount: Int
        get() = entries
            .mapNotNull { it.location.trim().lowercase().ifEmpty { null } }
            .distinct()
            .size

    /** Total words written across every entry's title + body — recomputed from the real text. */
    val wordCount: Int
        get() = entries.sumOf { it.title.wordTokens() + it.body.wordTokens() }

    /** The most frequently logged mood, if any entries exist. */
    val dominantMood: TripJournalMood?
        get() = moodCounts.maxByOrNull { it.value }?.key
}

private fun String.wordTokens(): Int =
    trim().split(Regex("\\s+")).count { it.isNotEmpty() }
