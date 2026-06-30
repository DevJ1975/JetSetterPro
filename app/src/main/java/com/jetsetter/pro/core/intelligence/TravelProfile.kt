package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny, locally-learned snapshot of how this person travels — derived on-device from the trip and
 * expense ledgers and persisted as JSON via [ModuleStateStore].
 *
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 * PRIVACY INVARIANT — READ BEFORE USING THIS ANYWHERE:
 *   This profile is **ON-DEVICE ONLY**. It must NEVER be sent to Claude, Anthropic, or any other
 *   third party, and must never be folded into an IRIS prompt. IRIS only ever receives the
 *   conversation transcript plus its static system prompt — nothing learned here leaves the device.
 *   Use this strictly to power local UI (badges, defaults, on-device suggestions).
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class TravelProfile @Inject constructor(
    private val stateStore: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(Data::class.java)

    /** Locally-aggregated traveller profile. Plain data — never serialized off-device. */
    data class Data(
        val tripCount: Int = 0,
        val topDestination: String? = null,
        val totalSpend: Double = 0.0,
        val homeAirport: String = "",
    )

    /** Reads the last persisted profile, or an empty default when nothing's been learned yet. */
    suspend fun current(): Data =
        stateStore.read(KEY)?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull() }
            ?: Data()

    /**
     * Recomputes the aggregates from the live ledgers and persists the result on-device. [homeAirport]
     * is preserved across updates (it's user-set, not derived) unless a non-blank value is supplied.
     */
    suspend fun update(trips: List<Trip>, expenses: List<Expense>, homeAirport: String? = null) {
        val topDestination = trips
            .groupingBy { it.destination }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val resolvedHomeAirport = homeAirport?.takeIf { it.isNotBlank() }
            ?: current().homeAirport

        val profile = Data(
            tripCount = trips.size,
            topDestination = topDestination,
            totalSpend = expenses.sumOf { it.amount },
            homeAirport = resolvedHomeAirport,
        )
        stateStore.save(KEY, adapter.toJson(profile))
    }

    private companion object {
        const val KEY = "travel_profile"
    }
}
