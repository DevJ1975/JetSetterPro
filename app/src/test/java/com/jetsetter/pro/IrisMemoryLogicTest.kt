package com.jetsetter.pro

import com.jetsetter.pro.core.intelligence.IrisMemoryLogic
import com.jetsetter.pro.core.intelligence.IrisPreference
import com.jetsetter.pro.core.intelligence.IrisPreferenceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure confidence rules behind [com.jetsetter.pro.core.intelligence.IrisMemory]
 * (spec §1.5): create at 0.7, +0.1 per reinforcement capped at 1.0, case-insensitive dedupe on
 * (category, trimmed value), confidence-descending recall, and the empty-summary contract.
 * Pure-JVM (no Android deps).
 */
class IrisMemoryLogicTest {

    private val day1 = "2026-07-17T10:00:00Z"
    private val day2 = "2026-07-18T10:00:00Z"

    @Test
    fun remember_createsAtInitialConfidence() {
        val out = IrisMemoryLogic.rememberInto(
            existing = emptyList(),
            category = IrisPreferenceCategory.SEATING,
            value = "window seat",
            nowIso = day1,
        )

        assertEquals(1, out.size)
        val pref = out.single()
        assertEquals(0.7, pref.confidence, 1e-9)
        assertEquals(IrisPreferenceCategory.SEATING, pref.category)
        assertEquals("window seat", pref.value)
        assertEquals(day1, pref.createdAt)
        assertEquals(day1, pref.lastReinforcedAt)
    }

    @Test
    fun remember_reinforcesExistingByOneStep() {
        val created = IrisMemoryLogic.rememberInto(
            emptyList(), IrisPreferenceCategory.DIETARY, "vegetarian", day1,
        )

        val reinforced = IrisMemoryLogic.rememberInto(
            created, IrisPreferenceCategory.DIETARY, "vegetarian", day2,
        )

        assertEquals(1, reinforced.size)
        val pref = reinforced.single()
        assertEquals(0.8, pref.confidence, 1e-9) // 0.7 → 0.8
        assertEquals(created.single().id, pref.id) // same record, not a new one
        assertEquals(day1, pref.createdAt) // creation timestamp preserved
        assertEquals(day2, pref.lastReinforcedAt) // reinforcement timestamp advanced
    }

    @Test
    fun remember_capsConfidenceAtOne() {
        var list = IrisMemoryLogic.rememberInto(
            emptyList(), IrisPreferenceCategory.AIRLINE_PREFERENCE, "Delta", day1,
        )
        // 0.7 + 3×0.1 reaches the 1.0 cap; further reinforcements must not exceed it.
        repeat(6) {
            list = IrisMemoryLogic.rememberInto(
                list, IrisPreferenceCategory.AIRLINE_PREFERENCE, "Delta", day2,
            )
            assertTrue(list.single().confidence <= 1.0)
        }
        assertEquals(1, list.size)
        assertEquals(1.0, list.single().confidence, 1e-9)
    }

    @Test
    fun remember_dedupesCaseInsensitivelyAndTrimmed() {
        val created = IrisMemoryLogic.rememberInto(
            emptyList(), IrisPreferenceCategory.SEATING, "window seat", day1,
        )

        val reinforced = IrisMemoryLogic.rememberInto(
            created, IrisPreferenceCategory.SEATING, "  Window Seat  ", day2,
        )

        assertEquals(1, reinforced.size) // reinforced, not duplicated
        assertEquals(0.8, reinforced.single().confidence, 1e-9)
        assertEquals("window seat", reinforced.single().value) // original casing kept
    }

    @Test
    fun remember_sameValueDifferentCategory_isASeparateEntry() {
        val first = IrisMemoryLogic.rememberInto(
            emptyList(), IrisPreferenceCategory.DESTINATIONS, "Tokyo", day1,
        )
        val second = IrisMemoryLogic.rememberInto(
            first, IrisPreferenceCategory.ACTIVITIES, "Tokyo", day1,
        )

        assertEquals(2, second.size)
        assertTrue(second.all { it.confidence in 0.699..0.701 })
    }

    @Test
    fun recallSorted_ordersByConfidenceDescending() {
        var list = emptyList<IrisPreference>()
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "aisle seat", day1)
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "window seat", day1)
        // Reinforce "window seat" twice so it outranks "aisle seat" despite later insertion.
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "window seat", day2)
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "window seat", day2)

        val recalled = IrisMemoryLogic.recallSorted(list)
        assertEquals(listOf("window seat", "aisle seat"), recalled.map { it.value })
        assertEquals(0.9, recalled[0].confidence, 1e-9)
        assertEquals(0.7, recalled[1].confidence, 1e-9)
    }

    @Test
    fun recallSorted_filtersByCategory() {
        var list = emptyList<IrisPreference>()
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.DIETARY, "vegetarian", day1)
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "window seat", day1)

        val dietary = IrisMemoryLogic.recallSorted(list, IrisPreferenceCategory.DIETARY)
        assertEquals(listOf("vegetarian"), dietary.map { it.value })
        assertTrue(IrisMemoryLogic.recallSorted(list, IrisPreferenceCategory.HOTEL_STYLE).isEmpty())
    }

    @Test
    fun summaryFor_emptyListIsEmptyString() {
        assertEquals("", IrisMemoryLogic.summaryFor(emptyList()))
    }

    @Test
    fun summaryFor_groupsByCategoryWithWireNames() {
        var list = emptyList<IrisPreference>()
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "window seat", day1)
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "exit row", day1)
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.HOTEL_STYLE, "boutique", day1)
        // Reinforce "exit row" so it leads within its category line.
        list = IrisMemoryLogic.rememberInto(list, IrisPreferenceCategory.SEATING, "exit row", day2)

        val summary = IrisMemoryLogic.summaryFor(list)
        val lines = summary.lines()
        assertEquals(2, lines.size)
        // Categories appear in enum declaration order (seating before hotelStyle),
        // values within a line by confidence descending, labeled with camelCase wire names.
        assertEquals("- seating: exit row, window seat", lines[0])
        assertEquals("- hotelStyle: boutique", lines[1])
    }
}
