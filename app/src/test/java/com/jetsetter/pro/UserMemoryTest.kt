package com.jetsetter.pro

import com.jetsetter.pro.core.intelligence.PreferenceScoring
import com.jetsetter.pro.core.intelligence.ScoredSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure preference-learning math behind [com.jetsetter.pro.core.intelligence.UserMemory]:
 * recency decay, repeated bumps, and deterministic top-N selection. Pure-JVM (no Android deps).
 */
class UserMemoryTest {

    @Test
    fun decay_halvesScoreAtHalfLife() {
        val half = PreferenceScoring.HALF_LIFE_DAYS.toLong()
        assertEquals(1.0, PreferenceScoring.decayFactor(0), 1e-9)
        assertEquals(0.5, PreferenceScoring.decayFactor(half), 1e-3)
        // Older than half-life decays further.
        assertTrue(PreferenceScoring.decayFactor(half * 2) < 0.3)
    }

    @Test
    fun bump_accumulatesWithRecencyDecay() {
        // Two bumps 45 days apart: the first contribution is halved before the second adds 1.0.
        val first = PreferenceScoring.bump(ScoredSignal(), nowEpochDay = 0)
        assertEquals(1.0, first.score, 1e-9)
        assertEquals(1, first.count)

        val second = PreferenceScoring.bump(first, nowEpochDay = 45)
        assertEquals(1.5, second.score, 1e-2) // 1.0*0.5 + 1.0
        assertEquals(2, second.count)
        assertEquals(45L, second.lastSeenEpochDay)
    }

    @Test
    fun topN_ranksByDecayedScore_recentBeatsStale() {
        val now = 100L
        val signals = mapOf(
            // Stale but high raw score (last seen 90 days ago) -> heavily decayed.
            "stale" to ScoredSignal(score = 5.0, lastSeenEpochDay = 10, count = 5),
            // Recent, modest score -> wins after decay.
            "recent" to ScoredSignal(score = 2.0, lastSeenEpochDay = 98, count = 2),
        )
        val top = PreferenceScoring.topN(signals, n = 1, nowEpochDay = now)
        assertEquals(listOf("recent"), top)
    }

    @Test
    fun topN_isDeterministicAndBounded() {
        val now = 0L
        val signals = mapOf(
            "a" to ScoredSignal(3.0, 0, 3),
            "b" to ScoredSignal(2.0, 0, 2),
            "c" to ScoredSignal(1.0, 0, 1),
        )
        assertEquals(listOf("a", "b"), PreferenceScoring.topN(signals, n = 2, nowEpochDay = now))
        // Zero-score signals are excluded.
        assertTrue(PreferenceScoring.topN(emptyMap(), n = 3, nowEpochDay = now).isEmpty())
    }
}
