package com.jetsetter.pro.core.intelligence

import kotlin.math.exp
import kotlin.math.ln

/** A frequency+recency signal: a running [score], when it was [lastSeenEpochDay], and a raw [count]. */
data class ScoredSignal(
    val score: Double = 0.0,
    val lastSeenEpochDay: Long = 0L,
    val count: Int = 0,
)

/** Accept/dismiss tally for a suggestion or alert category. */
data class AcceptDismiss(
    val accepted: Int = 0,
    val dismissed: Int = 0,
    val lastSeenEpochDay: Long = 0L,
)

/**
 * Pure, explainable preference math — no gradients, no I/O, fully unit-testable.
 *
 * Signals decay with a half-life so recent behavior dominates: a signal not seen for [HALF_LIFE_DAYS]
 * days counts half as much. This is how IRIS "learns the user" without training any model.
 */
object PreferenceScoring {

    const val HALF_LIFE_DAYS = 45.0

    /** Multiplier in (0, 1] applied to an old score given days elapsed. */
    fun decayFactor(deltaDays: Long): Double =
        if (deltaDays <= 0L) 1.0 else exp(-ln(2.0) * deltaDays / HALF_LIFE_DAYS)

    /** Records one fresh observation of a signal at [nowEpochDay]. */
    fun bump(signal: ScoredSignal, nowEpochDay: Long): ScoredSignal {
        val decayed = signal.score * decayFactor(nowEpochDay - signal.lastSeenEpochDay)
        return ScoredSignal(decayed + 1.0, nowEpochDay, signal.count + 1)
    }

    /** The signal's score as of [nowEpochDay] (decay applied, not mutated). */
    fun effectiveScore(signal: ScoredSignal, nowEpochDay: Long): Double =
        signal.score * decayFactor(nowEpochDay - signal.lastSeenEpochDay)

    /** Top [n] keys by decayed score (ties broken by raw count, then key for determinism). */
    fun topN(map: Map<String, ScoredSignal>, n: Int, nowEpochDay: Long): List<String> =
        map.entries.asSequence()
            .filter { effectiveScore(it.value, nowEpochDay) > 0.0 }
            .sortedWith(
                compareByDescending<Map.Entry<String, ScoredSignal>> { effectiveScore(it.value, nowEpochDay) }
                    .thenByDescending { it.value.count }
                    .thenBy { it.key },
            )
            .take(n)
            .map { it.key }
            .toList()
}
