package com.jetsetter.pro.feature.traveljournal

import java.util.UUID

/**
 * A single journal entry the traveler logs during a trip. Module-prefixed name so it can't
 * collide with [com.jetsetter.pro.core.model] or any other feature's models.
 */
data class TripJournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val title: String,
    val body: String,
    val location: String = "",
    val mood: TripJournalMood = TripJournalMood.REFLECTIVE,
)

/** How the traveler felt while writing the entry — drives the accent chip + icon on each card. */
enum class TripJournalMood(val label: String) {
    JOYFUL("Joyful"),
    REFLECTIVE("Reflective"),
    ADVENTUROUS("Adventurous"),
    RELAXED("Relaxed"),
}
