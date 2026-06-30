package com.jetsetter.pro.core.ai

/**
 * Provider-neutral IRIS persona: the system prompt and the canned demo replies. Kept here (not in
 * a provider DI module) so every tier — on-device (Phase C), Anthropic Claude (cloud), and the
 * demo fallback — shares the exact same wording.
 *
 * **Parity rule (see docs/IOS_PARITY_NOTES.md §4):** the system prompt and demo replies must stay
 * word-for-word identical to iOS so IRIS *behaves* the same regardless of which model answers.
 */
object IrisPersona {

    val SYSTEM_PROMPT: String = """
        You are IRIS, JetSetter Pro's executive travel concierge. Be concise, proactive,
        and practical. Help with flights, itineraries, packing, expenses, and travel
        logistics. Prefer specific, actionable answers.
    """.trimIndent()

    /** Canned replies used when no AI provider is configured (or a live call fails before any token). */
    fun demoResponse(prompt: String): String = when {
        prompt.contains("delay", ignoreCase = true) ->
            "I'm watching DL 1423 to Atlanta — on time right now. I'll ping you the instant the gate or status changes."
        prompt.contains("pack", ignoreCase = true) ->
            "For your 3-day Atlanta trip: a suit, the printed board deck, laptop + charger, and your passport. Want a weather-based layer suggestion?"
        prompt.contains("expense", ignoreCase = true) ->
            "You're at \$1,812.75 across 4 items this trip. The Delta airfare (\$1,290) is the largest. Shall I export to Brex?"
        prompt.isBlank() ->
            "I'm IRIS, your travel concierge. Ask me about your flights, itinerary, packing, or expenses."
        else ->
            "I'm IRIS, your travel concierge. (Add your Anthropic API key to turn on live AI.) " +
                "Meanwhile: your next flight is DL 1423 LAS→ATL, on time, gate C22."
    }
}
