package com.jetsetter.pro

import com.jetsetter.pro.core.ai.IrisPersona
import com.jetsetter.pro.core.ai.IrisPromptCache
import com.jetsetter.pro.core.ai.IrisSystemPromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure prompt-assembly contract behind
 * [com.jetsetter.pro.core.ai.IrisSystemPromptBuilder] (spec §1.2 session-start appends): sections
 * in iOS order (preferences → persona → learned profile → live context), empty sections omitted,
 * over-budget trimming drops live-context itinerary bullets before profile ranking bullets, and
 * the (inputs-hash → prompt) cache rebuilds only when an input changes. Pure-JVM (no Android deps).
 */
class IrisSystemPromptBuilderTest {

    private val base = IrisPersona.BASE_PROMPT

    private val prefs = "- seating: window seat\n- dietary: vegetarian"
    private val persona = "A frequent business traveler who favors Delta and boutique hotels."
    private val profile =
        "Learned travel profile:\n" +
            "- Typical seat: window, forward zone (confidence 80%, 5 flights)\n" +
            "- Top airlines: Delta, United"
    private val live =
        "LIVE TRAVELER DATA:\n" +
            "Trip: Board Meeting — Atlanta (2026-07-20 to 2026-07-23)\n" +
            "Upcoming itinerary:\n" +
            "- 2026-07-20: DL1423 LAS-ATL\n" +
            "- 2026-07-21: Dinner at Nobu\n" +
            "Expenses: none logged.\n" +
            "This is the traveler's live data. Do not invent trips, flights, or expenses beyond what is listed here."

    // ── Section order (iOS order) ────────────────────────────────────────────────────────────

    @Test
    fun assemble_appendsSectionsInIosOrder() {
        val out = IrisSystemPromptBuilder.assemble(base, prefs, persona, profile, live, 16_000)

        assertTrue(out.startsWith(base))
        val iPrefs = out.indexOf(IrisSystemPromptBuilder.PREFS_LABEL)
        val iPersona = out.indexOf(IrisSystemPromptBuilder.PERSONA_LABEL)
        val iProfile = out.indexOf("Learned travel profile:")
        val iLive = out.indexOf("LIVE TRAVELER DATA:")
        assertTrue("preferences section missing", iPrefs > 0)
        assertTrue("prefs must precede persona", iPrefs < iPersona)
        assertTrue("persona must precede profile", iPersona < iProfile)
        assertTrue("profile must precede live context", iProfile < iLive)
    }

    // ── Empty sections omitted ───────────────────────────────────────────────────────────────

    @Test
    fun assemble_allSectionsEmpty_returnsBareBase() {
        assertEquals(base, IrisSystemPromptBuilder.assemble(base, "", "", "", "", 16_000))
        // Blank (whitespace-only) inputs count as empty too.
        assertEquals(base, IrisSystemPromptBuilder.assemble(base, "  ", "\n", " \t", "", 16_000))
    }

    @Test
    fun assemble_omitsOnlyTheEmptySections() {
        val out = IrisSystemPromptBuilder.assemble(base, "", "", "", live, 16_000)

        assertFalse(out.contains(IrisSystemPromptBuilder.PREFS_LABEL))
        assertFalse(out.contains(IrisSystemPromptBuilder.PERSONA_LABEL))
        assertFalse(out.contains("Learned travel profile:"))
        assertTrue(out.endsWith(live))
    }

    // ── Trim order: live-context itinerary bullets first, then profile rankings ─────────────

    @Test
    fun assemble_overBudget_dropsLiveItineraryBulletsBeforeProfileRankings() {
        // Budget = exactly what the dynamic block costs once the live bullets are gone, so
        // trimming must remove both itinerary lines and nothing else.
        val liveNoBullets = live.lines().filterNot { it.startsWith("- ") }.joinToString("\n")
        val expected =
            IrisSystemPromptBuilder.assemble(base, prefs, persona, profile, liveNoBullets, Int.MAX_VALUE)
        val budget = expected.length - base.length

        val out = IrisSystemPromptBuilder.assemble(base, prefs, persona, profile, live, budget)

        assertFalse(out.contains("- 2026-07-20: DL1423 LAS-ATL"))
        assertFalse(out.contains("- 2026-07-21: Dinner at Nobu"))
        // Profile ranking bullets survive: dropping the live bullets was enough.
        assertTrue(out.contains("- Top airlines: Delta, United"))
        assertTrue(out.contains("- Typical seat: window, forward zone (confidence 80%, 5 flights)"))
        assertEquals(expected, out)
    }

    @Test
    fun assemble_tinyBudget_dropsProfileRankingsAfterLiveIsExhausted() {
        val out = IrisSystemPromptBuilder.assemble(base, "", "", profile, live, 10)

        // Every bullet is gone — live itinerary lines and profile ranking lines alike.
        assertFalse(out.contains("- 2026-07-20"))
        assertFalse(out.contains("- 2026-07-21"))
        assertFalse(out.contains("- Typical seat"))
        assertFalse(out.contains("- Top airlines"))
        // Trimming gives up once no bullets remain: the non-bullet skeleton survives.
        assertTrue(out.contains("Learned travel profile:"))
        assertTrue(out.contains("LIVE TRAVELER DATA:"))
    }

    @Test
    fun assemble_underBudget_isUntrimmed() {
        val out = IrisSystemPromptBuilder.assemble(base, prefs, persona, profile, live, 16_000)
        assertTrue(out.contains("- 2026-07-20: DL1423 LAS-ATL"))
        assertTrue(out.contains("- 2026-07-21: Dinner at Nobu"))
        assertTrue(out.contains("- Top airlines: Delta, United"))
    }

    // ── Cache: rebuild only when an input changes ────────────────────────────────────────────

    @Test
    fun cache_sameInputs_hitsWithoutReassembling() {
        val cache = IrisPromptCache()

        val first = cache.promptFor(base, prefs, persona, profile, live)
        val second = cache.promptFor(base, prefs, persona, profile, live)

        assertSame(first, second) // the cached instance itself, not a re-render
        assertEquals(1, cache.rebuilds)
    }

    @Test
    fun cache_changedInput_rebuilds() {
        val cache = IrisPromptCache()

        val first = cache.promptFor(base, prefs, persona, profile, live)
        val changed = cache.promptFor(base, prefs, persona, profile, live + "\nNext flight: DL1423")

        assertNotEquals(first, changed)
        assertEquals(2, cache.rebuilds)

        // And the new inputs are now the cached generation.
        val again = cache.promptFor(base, prefs, persona, profile, live + "\nNext flight: DL1423")
        assertSame(changed, again)
        assertEquals(2, cache.rebuilds)
    }
}
