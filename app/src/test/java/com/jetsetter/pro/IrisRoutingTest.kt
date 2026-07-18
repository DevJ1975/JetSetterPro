package com.jetsetter.pro

import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.ai.ConversationSession
import com.jetsetter.pro.core.ai.IrisRoute
import com.jetsetter.pro.core.ai.IrisRouting
import com.jetsetter.pro.core.ai.IrisToolIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure R1/R2 pieces of IRIS routing: the [IrisToolIntent] keyword heuristic, the
 * [IrisRouting] tier order (on-device first regardless of the Anthropic key; tool-intent turns
 * divert to Claude; demo last), and the [ConversationSession] contract (no-op under limits,
 * recreation on system-prompt hash change, last-6-turns + synthetic summary pair when over
 * budget). Pure-JVM (no Android deps).
 */
class IrisRoutingTest {

    // ── IrisToolIntent ───────────────────────────────────────────────────────────────────────

    @Test
    fun toolIntent_matchesToolShapedRequests() {
        listOf(
            "Log a \$40 dinner at Nobu",
            "I spent 30 euros on a taxi",
            "Can you scan this receipt?",
            "Add a trip to Lisbon in March",
            "track DL1423",
            "What's the weather in Tokyo this weekend?",
            "Do I need a visa for Vietnam?",
            "Check me in for my flight",
            "check-in please",
            "Help me pack for Denver",
            "Remember I prefer window seats",
            "When should I leave for the airport?",
            "Where is my luggage?",
            "Text my wife when we land",
        ).forEach { assertTrue("expected tool intent: \"$it\"", IrisToolIntent.likelyNeedsTools(it)) }
    }

    @Test
    fun toolIntent_ignoresConversationalTurns() {
        listOf(
            "",
            "   ",
            "Hi IRIS!",
            "Tell me a fun fact about the Eiffel Tower",
            "Thanks, that was helpful",
            "What does IRIS stand for?",
            "Recommend a good espresso place in Rome",
            "I was checking the news this morning", // "checking" must not trip the check-in rule
        ).forEach { assertFalse("expected NO tool intent: \"$it\"", IrisToolIntent.likelyNeedsTools(it)) }
    }

    // ── IrisRouting (R1 order) ───────────────────────────────────────────────────────────────

    @Test
    fun routing_onDeviceFirst_evenWithoutClaudeKey() {
        assertEquals(
            IrisRoute.ON_DEVICE,
            IrisRouting.decide(onDeviceAvailable = true, claudeConfigured = false, lastUserText = "Hi IRIS!"),
        )
    }

    @Test
    fun routing_conversationalTurnStaysOnDevice_whenBothTiersAvailable() {
        assertEquals(
            IrisRoute.ON_DEVICE,
            IrisRouting.decide(onDeviceAvailable = true, claudeConfigured = true, lastUserText = "Good morning!"),
        )
    }

    @Test
    fun routing_toolIntentDivertsToClaude_whenConfigured() {
        assertEquals(
            IrisRoute.CLAUDE,
            IrisRouting.decide(
                onDeviceAvailable = true,
                claudeConfigured = true,
                lastUserText = "Log a coffee expense",
            ),
        )
    }

    @Test
    fun routing_toolIntentWithoutClaude_staysOnDevice() {
        assertEquals(
            IrisRoute.ON_DEVICE,
            IrisRouting.decide(
                onDeviceAvailable = true,
                claudeConfigured = false,
                lastUserText = "Log a coffee expense",
            ),
        )
    }

    @Test
    fun routing_claudeWhenOnDeviceUnavailable() {
        assertEquals(
            IrisRoute.CLAUDE,
            IrisRouting.decide(onDeviceAvailable = false, claudeConfigured = true, lastUserText = "Good morning!"),
        )
    }

    @Test
    fun routing_demoWhenNothingIsAvailable() {
        assertEquals(
            IrisRoute.DEMO,
            IrisRouting.decide(onDeviceAvailable = false, claudeConfigured = false, lastUserText = "anything"),
        )
    }

    // ── ConversationSession (R2) ─────────────────────────────────────────────────────────────

    /** Alternating user/model turns (even index = user), each with a distinct first token `m<i>`. */
    private fun alternating(count: Int, padChars: Int = 8): List<AiMessage> =
        List(count) { i ->
            AiMessage(if (i % 2 == 0) "user" else "model", "m$i " + "x".repeat(padChars))
        }

    @Test
    fun session_underLimits_isANoOp() {
        val session = ConversationSession()
        val messages = alternating(8) // more than 6 turns, but tiny and prompt stable

        assertEquals(messages, session.prepareHistory(messages, "prompt-A"))
        assertEquals(messages, session.prepareHistory(messages, "prompt-A"))
    }

    @Test
    fun session_recreatesOnSystemPromptHashChange() {
        val session = ConversationSession()
        val messages = alternating(13) // m0..m12, ends with a user turn

        // First call establishes the hash — no recreation even though the hash was unset.
        assertEquals(messages, session.prepareHistory(messages, "prompt-A"))

        val recreated = session.prepareHistory(messages, "prompt-B")

        // Synthetic summary pair first…
        assertEquals("user", recreated[0].role)
        assertTrue(recreated[0].text.startsWith("[Earlier conversation summary:"))
        assertTrue(recreated[0].text.contains("User: m0"))
        assertEquals("model", recreated[1].role)
        // …then the kept tail, aligned so it starts with a user turn (last-6 window m7..m12 opens
        // on a model turn, so m7 is dropped and m8..m12 survive verbatim).
        assertEquals(messages.subList(8, 13), recreated.drop(2))
        assertEquals("user", recreated[2].role)
    }

    @Test
    fun session_overCharBudget_keepsLastSixPlusSummaryPair() {
        val session = ConversationSession()
        // 10 turns x ~3k chars ≈ 30k chars — over the 16k budget on the very first call.
        val messages = alternating(10, padChars = 3_000)

        val compacted = session.prepareHistory(messages, "prompt-A")

        // Last-6 window m4..m9 opens on a user turn, so all 6 are kept: 2 summary + 6 kept.
        assertEquals(8, compacted.size)
        assertEquals("user", compacted[0].role)
        assertTrue(compacted[0].text.startsWith("[Earlier conversation summary:"))
        assertEquals("model", compacted[1].role)
        assertEquals(messages.takeLast(6), compacted.drop(2))
        // The summary is truncation-bounded — never the full dropped text.
        assertTrue(compacted[0].text.length < 1_500)
        // Dropped turns are represented by their (truncated) first lines.
        assertTrue(compacted[0].text.contains("User: m0"))
        assertTrue(compacted[0].text.contains("IRIS: m1"))
    }

    @Test
    fun session_hashChangeWithSixOrFewerTurns_hasNothingToDrop() {
        val session = ConversationSession()
        val messages = alternating(4)

        session.prepareHistory(messages, "prompt-A")
        // Recreation triggers, but with ≤6 turns there is nothing to summarize away.
        assertEquals(messages, session.prepareHistory(messages, "prompt-B"))
    }
}
