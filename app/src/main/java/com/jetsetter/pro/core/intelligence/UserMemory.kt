package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.core.model.Trip
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IRIS's on-device learned memory — how this person travels, inferred from their own behavior and
 * persisted as JSON via [ModuleStateStore].
 *
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 * PRIVACY INVARIANT (same as [TravelProfile]):
 *   ON-DEVICE ONLY. Never sent to Claude/Anthropic, never folded into a *cloud* prompt. It may be
 *   used as PERSONAL grounding on the on-device (Gemini Nano) tier and to power local UI / proactive
 *   nudges — nothing here leaves the device.
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 *
 * Learning is frequency + recency scoring (see [PreferenceScoring]) — explainable, cheap, and not a
 * trained model. Event signals (query topics, accept/dismiss, usage hour) accumulate incrementally;
 * ledger-derived affinities (airline/hotel/cuisine) are recomputed idempotently from the current
 * trips/expenses so repeated refreshes don't inflate scores.
 */
@Singleton
class UserMemory @Inject constructor(
    private val stateStore: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(Data::class.java)

    data class Data(
        val version: Int = 1,
        val topicScores: Map<String, ScoredSignal> = emptyMap(),
        val suggestionStats: Map<String, AcceptDismiss> = emptyMap(),
        val airlineAffinity: Map<String, ScoredSignal> = emptyMap(),
        val hotelPref: Map<String, ScoredSignal> = emptyMap(),
        val cuisinePref: Map<String, ScoredSignal> = emptyMap(),
        val editStats: Map<String, Int> = emptyMap(),
        val usageByHour: List<Double> = List(24) { 0.0 },
        val lastUpdatedEpochDay: Long = 0L,
    )

    suspend fun current(): Data =
        stateStore.read(KEY)?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull() } ?: Data()

    private suspend fun mutate(now: Long = today(), block: (Data) -> Data) {
        val updated = block(current()).copy(lastUpdatedEpochDay = now)
        stateStore.save(KEY, adapter.toJson(updated))
    }

    /** A coarse query topic the user asked IRIS about (e.g. "packing", "delays", "visa"). */
    suspend fun recordTopic(topic: String, now: Long = today()) {
        val key = topic.trim().lowercase()
        if (key.isEmpty()) return
        mutate(now) { d ->
            d.copy(topicScores = d.topicScores.bumped(key, now))
        }
    }

    /** A suggestion/alert (by category id) the user accepted (tapped) or dismissed. */
    suspend fun recordSuggestion(category: String, accepted: Boolean, now: Long = today()) {
        val key = category.trim()
        if (key.isEmpty()) return
        mutate(now) { d ->
            val prev = d.suggestionStats[key] ?: AcceptDismiss()
            val next = prev.copy(
                accepted = prev.accepted + if (accepted) 1 else 0,
                dismissed = prev.dismissed + if (accepted) 0 else 1,
                lastSeenEpochDay = now,
            )
            d.copy(suggestionStats = d.suggestionStats + (key to next))
        }
    }

    /** A field the user edited on an IRIS-proposed trip/expense (signals a miss to learn from). */
    suspend fun recordEdit(field: String, now: Long = today()) {
        val key = field.trim()
        if (key.isEmpty()) return
        mutate(now) { d -> d.copy(editStats = d.editStats + (key to (d.editStats[key] ?: 0) + 1)) }
    }

    /** Time-of-day engagement (0–23). Simple accumulation; peak hour is the argmax. */
    suspend fun recordUsage(hour: Int, now: Long = today()) {
        if (hour !in 0..23) return
        mutate(now) { d ->
            val arr = (d.usageByHour.takeIf { it.size == 24 } ?: List(24) { 0.0 }).toMutableList()
            arr[hour] = arr[hour] + 1.0
            d.copy(usageByHour = arr)
        }
    }

    /** Idempotently recompute airline/hotel/cuisine affinities from the current ledgers. */
    suspend fun refreshFromLedgers(trips: List<Trip>, expenses: List<Expense>, now: Long = today()) {
        val airline = mutableMapOf<String, ScoredSignal>()
        val hotel = mutableMapOf<String, ScoredSignal>()
        val cuisine = mutableMapOf<String, ScoredSignal>()

        fun add(map: MutableMap<String, ScoredSignal>, rawKey: String, dateIso: String) {
            val key = rawKey.trim()
            if (key.isEmpty()) return
            val day = epochDayOf(dateIso) ?: now
            val prev = map[key]
            map[key] = if (prev == null) {
                ScoredSignal(1.0, day, 1)
            } else {
                ScoredSignal(prev.score + 1.0, maxOf(prev.lastSeenEpochDay, day), prev.count + 1)
            }
        }

        trips.forEach { t ->
            t.items.forEach { item ->
                when (item.type) {
                    ItineraryItemType.FLIGHT -> add(airline, airlineOf(item.title), item.startDate)
                    ItineraryItemType.HOTEL -> add(hotel, item.title, item.startDate)
                    ItineraryItemType.RESTAURANT -> add(cuisine, item.title, item.startDate)
                    else -> Unit
                }
            }
        }
        expenses.forEach { e ->
            when (e.category) {
                ExpenseCategory.ACCOMMODATION -> add(hotel, e.merchant, e.date)
                ExpenseCategory.FOOD -> add(cuisine, e.merchant, e.date)
                else -> Unit
            }
        }

        mutate(now) { d ->
            d.copy(airlineAffinity = airline, hotelPref = hotel, cuisinePref = cuisine)
        }
    }

    /** The current learned snapshot for the proactive engine / on-device grounding. */
    suspend fun topPreferences(now: Long = today()): LearnedPreferences {
        val d = current()
        return LearnedPreferences(
            topTopics = PreferenceScoring.topN(d.topicScores, 3, now),
            topAirlines = PreferenceScoring.topN(d.airlineAffinity, 2, now),
            topHotels = PreferenceScoring.topN(d.hotelPref, 2, now),
            topCuisines = PreferenceScoring.topN(d.cuisinePref, 2, now),
            dismissedCategories = d.suggestionStats
                .filterValues { it.dismissed >= DISMISS_SUPPRESS_THRESHOLD && it.dismissed > it.accepted }
                .keys,
            peakHour = d.usageByHour
                .withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0.0 }?.index,
        )
    }

    private fun Map<String, ScoredSignal>.bumped(key: String, now: Long): Map<String, ScoredSignal> =
        this + (key to PreferenceScoring.bump(this[key] ?: ScoredSignal(), now))

    private companion object {
        const val KEY = "user_memory"
        const val DISMISS_SUPPRESS_THRESHOLD = 2

        fun today(): Long = LocalDate.now().toEpochDay()

        /** Parses the leading ISO date (YYYY-MM-DD) of a date/timestamp string to an epoch day. */
        fun epochDayOf(iso: String): Long? =
            runCatching { LocalDate.parse(iso.take(10)).toEpochDay() }.getOrNull()

        /** Light airline-name heuristic: keep the leading non-flight-number words. */
        fun airlineOf(title: String): String {
            val words = title.trim().split(Regex("\\s+"))
            val name = words.takeWhile { !it.any(Char::isDigit) }
            return (if (name.isNotEmpty()) name else words.take(1)).joinToString(" ")
        }
    }
}
