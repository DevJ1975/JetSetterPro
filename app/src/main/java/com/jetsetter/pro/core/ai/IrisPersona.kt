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

    /**
     * Canned replies used when no AI provider is configured (or a live call fails before any
     * token). Copy is presentation-safe (no setup hints) and mirrors the seeded demo traveler —
     * DL 1423 LAS→ATL, the Atlanta board-meeting trip, and the Disruption Monitor's delay story —
     * so IRIS never contradicts what another screen is showing. (Deliberate deviation from the
     * older iOS wording; keep both platforms on THIS copy going forward.)
     */
    fun demoResponse(prompt: String): String = when {
        prompt.contains("delay", ignoreCase = true) || prompt.contains("disrupt", ignoreCase = true) ->
            "DL 1423 to Atlanta is showing a delay — departure moved from 7:00 AM to 8:35 AM " +
                "(1h 35m late, weather hold at ATL). I've already lined up 3 same-day alternatives; " +
                "open Trip Disruption and I'll rebook you in one tap."
        prompt.contains("pack", ignoreCase = true) ->
            "For your 3-day Atlanta trip: a suit, the printed board deck, laptop + charger, and your passport. Want a weather-based layer suggestion?"
        prompt.contains("expense", ignoreCase = true) ->
            "You're at \$1,812.75 across 4 items this trip. The Delta airfare (\$1,290) is the largest. Shall I export to Brex?"
        prompt.contains("leave", ignoreCase = true) || prompt.contains("traffic", ignoreCase = true) ||
            prompt.contains("navigate", ignoreCase = true) || prompt.contains("drive", ignoreCase = true) ->
            "Leave by 5:19 AM for your 7:00 AM departure: it's a 34-minute drive to Harry Reid Intl " +
                "in moderate traffic, 15 minutes to park, TSA is running 22 minutes, plus your 30-minute " +
                "gate buffer. Clear skies, 74°F for the drive. Open Departure Optimizer and tap Navigate — " +
                "the route map and guidance run right here in the app."
        prompt.contains("weather", ignoreCase = true) ->
            "Las Vegas is clear and 74°F for your drive to the airport. Atlanta is the one to watch — " +
                "a weather hold there is what's delaying DL 1423. I'm tracking both and will ping you if " +
                "your leave-by time moves."
        prompt.contains("seat", ignoreCase = true) || prompt.contains("check", ignoreCase = true) ->
            "Check-in for DL 1423 is open now. You're holding seat 3A in First — head to Check-In " +
                "to confirm it (or pick a new seat from the map) and I'll issue your boarding pass."
        prompt.isBlank() ->
            "I'm IRIS, your travel concierge. Ask me about your flights, itinerary, packing, or expenses."
        else ->
            "Here's where things stand: DL 1423 LAS→ATL departs from gate C22, you're in seat 3A, " +
                "and your Atlanta itinerary is fully booked. Ask me about delays, packing, seats, or expenses."
    }
}
