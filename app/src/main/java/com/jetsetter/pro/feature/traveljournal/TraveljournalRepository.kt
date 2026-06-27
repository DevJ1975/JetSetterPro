package com.jetsetter.pro.feature.traveljournal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, mock-first store for trip journal entries. No network / persistence — it just hands
 * back realistic sample memories and mints new ones with today's date for the composer.
 */
@Singleton
class TraveljournalRepository @Inject constructor() {

    /** Realistic seed data so a beta tester opens the screen to a populated journal. */
    suspend fun loadEntries(): List<TripJournalEntry> = sampleEntries

    /**
     * Builds a fresh entry stamped with the current date, carrying the traveler's chosen mood and
     * (optional) location so the saved card reflects exactly what they entered. The ViewModel
     * prepends it to state.
     */
    fun createEntry(
        title: String,
        body: String,
        location: String = "",
        mood: TripJournalMood = TripJournalMood.JOYFUL,
    ): TripJournalEntry = TripJournalEntry(
        date = today(),
        title = title,
        body = body,
        location = location,
        mood = mood,
    )

    private fun today(): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())

    private val sampleEntries = listOf(
        TripJournalEntry(
            date = "Jun 22, 2026",
            title = "Sunrise over the caldera",
            body = "Woke at 4:45 to climb above Oia before the crowds. The whole bay turned " +
                "rose-gold and the windmills were still silhouettes. Best coffee of the trip, " +
                "drunk barefoot on a cliff.",
            location = "Oia, Santorini",
            mood = TripJournalMood.REFLECTIVE,
        ),
        TripJournalEntry(
            date = "Jun 20, 2026",
            title = "Street food crawl",
            body = "Followed a local food guide through six stalls in Chinatown. Boat noodles, " +
                "mango sticky rice, and a grilled-squid stand that had a line around the block. " +
                "Stomach full, wallet barely touched.",
            location = "Bangkok, Thailand",
            mood = TripJournalMood.JOYFUL,
        ),
        TripJournalEntry(
            date = "Jun 17, 2026",
            title = "Hiking the fjord ridge",
            body = "Eight miles up to the overlook with a cold drizzle the whole way. Worth every " +
                "switchback — the fjord opened up below us like a green knife cut into the rock. " +
                "Shared trail mix with two hikers from Munich.",
            location = "Bergen, Norway",
            mood = TripJournalMood.ADVENTUROUS,
        ),
        TripJournalEntry(
            date = "Jun 14, 2026",
            title = "First night in Tokyo",
            body = "Landed jet-lagged and wandered into a tiny six-seat ramen bar in Shinjuku. " +
                "No English menu, just a vending machine and a nod from the chef. Neon, rain, " +
                "and the quiet hum of a city that never quite sleeps.",
            location = "Tokyo, Japan",
            mood = TripJournalMood.RELAXED,
        ),
    )
}
