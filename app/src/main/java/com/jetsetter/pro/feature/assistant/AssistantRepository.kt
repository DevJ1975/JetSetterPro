package com.jetsetter.pro.feature.assistant

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock-first store for the Assistant surface. Holds the quick-action prompts plus a single
 * [AssistantTripContext] in memory and derives every canned answer (and its figures) from that
 * context, so the numbers are always self-consistent. No network / no real AI — a curated,
 * deterministic demo distinct from the free-form IRIS chat.
 *
 * The only user-mutable state is the "recently asked" list, persisted as JSON via
 * [ModuleStateStore] (Moshi) so it survives app restarts.
 */
@Singleton
class AssistantRepository @Inject constructor(
    private val moduleStateStore: ModuleStateStore,
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val recentsType = Types.newParameterizedType(List::class.java, String::class.java)
    private val recentsAdapter = moshi.adapter<List<String>>(recentsType)

    private val context = seedContext()
    private val prompts = MutableStateFlow(seedPrompts())

    /** Live view of the available quick actions. */
    fun observePrompts(): Flow<List<AssistantPrompt>> = prompts.asStateFlow()

    /** Most-recently asked prompt ids, newest first, persisted across restarts. */
    fun observeRecentIds(): Flow<List<String>> =
        moduleStateStore.observe(RECENTS_KEY).map { json -> decodeRecents(json) }

    /** Records [promptId] at the head of the recents list (deduped, capped at [MAX_RECENTS]). */
    suspend fun recordRecent(promptId: String) {
        if (prompts.value.none { it.id == promptId }) return
        val current = decodeRecents(moduleStateStore.read(RECENTS_KEY))
        val updated = (listOf(promptId) + current.filterNot { it == promptId }).take(MAX_RECENTS)
        moduleStateStore.save(RECENTS_KEY, recentsAdapter.toJson(updated))
    }

    /** Clears the recents list. */
    suspend fun clearRecents() {
        moduleStateStore.save(RECENTS_KEY, recentsAdapter.toJson(emptyList()))
    }

    /** Builds the canned answer for [promptId], deriving every figure from the trip context. */
    fun responseFor(promptId: String): AssistantResponse? {
        val prompt = prompts.value.firstOrNull { it.id == promptId } ?: return null
        return AssistantResponse(
            promptId = prompt.id,
            promptTitle = prompt.title,
            body = bodyFor(prompt.id),
            metrics = metricsFor(prompt.id),
            followUpIds = prompt.followUpIds,
        )
    }

    private fun decodeRecents(json: String?): List<String> =
        json?.let { runCatching { recentsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()

    private fun bodyFor(id: String): String = with(context) {
        when (id) {
            "summary" ->
                "You're headed to $destination for a $tripDays-day board meeting, $dateRange. " +
                    "Outbound is $flightNumber ($flightRoute) at $departureTime from gate $gate, you're " +
                    "staying at the $hotel, and your headline event is the $keynote on the $stormDay. " +
                    "Everything is confirmed — no action needed right now."

            "schedule" ->
                "Next up: $flightNumber ($flightRoute) boards at $boardingTime from gate $gate " +
                    "(departs $departureTime). After you land, $hotel check-in opens at $hotelCheckIn. " +
                    "The $keynote at $keynoteVenue starts $keynoteTime tomorrow."

            "packing" -> {
                val toGo = unpacked
                if (toGo.isEmpty()) {
                    "All ${packing.size} items for your $tripDays warm days in $destination are packed — " +
                        "you're set."
                } else {
                    "$packedCount of ${packing.size} items packed for $tripDays warm, humid days in " +
                        "$destination. Still to go: ${toGo.joinToString(", ") { it.name.lowercase() }}."
                }
            }

            "weather" ->
                "$destination is warm and humid this week: highs near $highF°F, lows around $lowF°F, " +
                    "with scattered thunderstorms on the $stormDay. Pack breathable layers and a compact " +
                    "umbrella for the walk to the stadium."

            "gate" ->
                "$flightNumber departs gate $gate in Terminal $terminal. Boarding starts $boardingTime, " +
                    "Zone $boardingZone (you're in seat $seat). It's a short walk from security toward the " +
                    "$loungeConcourse concourse — I'll alert you if the gate changes."

            "lounge" ->
                "The $loungeName on Concourse $loungeConcourse is closest — about a $loungeWalkMinutes-minute " +
                    "walk from gate $gate, open from $loungeOpens. Your Priority Pass also covers The Club ATL " +
                    "on Concourse F for a quieter spot before boarding."

            "expenses" -> {
                val top = topExpense
                "You're at ${formatMoney(spentCents)} across ${expenses.size} items this trip" +
                    (top?.let { ", led by ${it.label} (${formatMoney(it.amountCents)})" } ?: "") +
                    ". That leaves ${formatMoney(remainingCents)} of your ${formatMoney(budgetCents)} " +
                    "budget. Want me to draft an expense report?"
            }

            else -> ""
        }
    }

    private fun metricsFor(id: String): List<AssistantMetric> = with(context) {
        when (id) {
            "summary" -> listOf(
                AssistantMetric("Destination", destination),
                AssistantMetric("Dates", dateRange),
                AssistantMetric("Status", "All confirmed", AssistantMetricTone.POSITIVE),
            )

            "schedule" -> listOf(
                AssistantMetric("Next", flightNumber),
                AssistantMetric("Boards", boardingTime),
                AssistantMetric("Gate", gate),
            )

            "packing" -> listOf(
                AssistantMetric("Packed", "$packedCount/${packing.size}"),
                AssistantMetric(
                    label = "To go",
                    value = unpacked.size.toString(),
                    tone = if (unpacked.isEmpty()) AssistantMetricTone.POSITIVE else AssistantMetricTone.WARNING,
                ),
            )

            "weather" -> listOf(
                AssistantMetric("High", "$highF°F"),
                AssistantMetric("Low", "$lowF°F"),
                AssistantMetric("Storms", stormDay, AssistantMetricTone.WARNING),
            )

            "gate" -> listOf(
                AssistantMetric("Gate", gate),
                AssistantMetric("Boards", boardingTime),
                AssistantMetric("Seat", seat),
            )

            "lounge" -> listOf(
                AssistantMetric("Concourse", loungeConcourse),
                AssistantMetric("Walk", "$loungeWalkMinutes min"),
                AssistantMetric("Opens", loungeOpens),
            )

            "expenses" -> listOf(
                AssistantMetric("Spent", formatMoney(spentCents)),
                AssistantMetric("Budget", formatMoney(budgetCents)),
                AssistantMetric(
                    label = "Remaining",
                    value = formatMoney(remainingCents),
                    tone = if (remainingCents >= 0) AssistantMetricTone.POSITIVE else AssistantMetricTone.WARNING,
                ),
            )

            else -> emptyList()
        }
    }

    private fun seedContext() = AssistantTripContext(
        destination = "Atlanta, GA",
        dateRange = "Jul 14–16",
        tripDays = 3,
        flightNumber = "DL 1423",
        flightRoute = "LAS → ATL",
        terminal = "1",
        gate = "C22",
        seat = "12A",
        boardingZone = "1",
        boardingTime = "6:30 AM",
        departureTime = "7:00 AM",
        hotel = "Four Seasons Atlanta",
        hotelCheckIn = "3:00 PM",
        keynote = "Q3 Leadership Summit",
        keynoteVenue = "Mercedes-Benz Stadium",
        keynoteTime = "6:00 PM",
        highF = 88,
        lowF = 71,
        stormDay = "15th",
        loungeName = "Delta Sky Club",
        loungeConcourse = "C",
        loungeWalkMinutes = 3,
        loungeOpens = "5:00 AM",
        budgetCents = 250_000,
        expenses = listOf(
            AssistantExpense("the Delta airfare", 129_000),
            AssistantExpense("the Four Seasons deposit", 42_000),
            AssistantExpense("LAS airport parking", 8_400),
            AssistantExpense("the Lyft to LAS", 1_875),
        ),
        packing = listOf(
            AssistantPackingEntry("Tailored suit", packed = true),
            AssistantPackingEntry("Laptop + charger", packed = true),
            AssistantPackingEntry("Printed board deck", packed = true),
            AssistantPackingEntry("Passport", packed = true),
            AssistantPackingEntry("2 business-casual outfits", packed = false),
            AssistantPackingEntry("Compact umbrella", packed = false),
        ),
    )

    private fun seedPrompts(): List<AssistantPrompt> = listOf(
        AssistantPrompt(
            id = "summary",
            title = "Summarize my trip",
            subtitle = "3-day Atlanta board meeting",
            category = AssistantPromptCategory.TRIP,
            icon = AssistantIcons.summary,
            followUpIds = listOf("schedule", "gate", "expenses"),
        ),
        AssistantPrompt(
            id = "schedule",
            title = "What's next on my schedule?",
            subtitle = "Your upcoming events",
            category = AssistantPromptCategory.TRIP,
            icon = AssistantIcons.schedule,
            followUpIds = listOf("gate", "weather"),
        ),
        AssistantPrompt(
            id = "packing",
            title = "What should I pack?",
            subtitle = "Weather-aware packing list",
            category = AssistantPromptCategory.PACKING,
            icon = AssistantIcons.packing,
            followUpIds = listOf("weather"),
        ),
        AssistantPrompt(
            id = "weather",
            title = "What's the weather there?",
            subtitle = "Atlanta forecast",
            category = AssistantPromptCategory.PACKING,
            icon = AssistantIcons.weather,
            followUpIds = listOf("packing"),
        ),
        AssistantPrompt(
            id = "gate",
            title = "Find my gate",
            subtitle = "Departure gate & boarding",
            category = AssistantPromptCategory.AIRPORT,
            icon = AssistantIcons.gate,
            followUpIds = listOf("lounge", "schedule"),
        ),
        AssistantPrompt(
            id = "lounge",
            title = "Where's the nearest lounge?",
            subtitle = "Lounges near your gate",
            category = AssistantPromptCategory.AIRPORT,
            icon = AssistantIcons.lounge,
            followUpIds = listOf("gate"),
        ),
        AssistantPrompt(
            id = "expenses",
            title = "Track my expenses",
            subtitle = "Trip spend so far",
            category = AssistantPromptCategory.MONEY,
            icon = AssistantIcons.expenses,
            followUpIds = listOf("summary"),
        ),
    )

    private companion object {
        const val RECENTS_KEY = "assistant_recent_prompts"
        const val MAX_RECENTS = 4
    }
}
