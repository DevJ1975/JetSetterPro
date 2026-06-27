package com.jetsetter.pro.core.data.repository

import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.content
import com.jetsetter.pro.core.ai.AiMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the IRIS assistant (Intelligent Routing & Itinerary Specialist).
 *
 * On iOS, IRIS routed Apple Intelligence (on-device) → Claude → canned demo. On Android it
 * runs on **Firebase AI Logic** (Gemini, via the no-cost Google AI backend; model + system
 * prompt are configured in [com.jetsetter.pro.core.di.FirebaseModule]). If the live call fails
 * — e.g. Firebase AI Logic isn't enabled yet, or there's no network — it falls back to canned
 * demo replies so the IRIS tab always responds.
 */
@Singleton
class IrisRepository @Inject constructor(
    private val model: GenerativeModel,
) {
    /** [history] is the full conversation; the last entry should be the new user message. */
    suspend fun ask(history: List<AiMessage>): String {
        val last = history.lastOrNull() ?: return demoResponse("")
        // Gemini requires the chat history to start with a user turn, so drop any leading
        // assistant turns (e.g. IRIS's opening greeting) before the first user message.
        val prior = history.dropLast(1).dropWhile { it.role != "user" }
        return runCatching {
            val chat = model.startChat(
                history = prior.map { turn -> content(role = turn.role) { text(turn.text) } },
            )
            chat.sendMessage(last.text).text?.takeIf { it.isNotBlank() } ?: demoResponse(last.text)
        }.getOrElse { demoResponse(last.text) }
    }

    private fun demoResponse(prompt: String): String = when {
        prompt.contains("delay", ignoreCase = true) ->
            "I'm watching DL 1423 to Atlanta — on time right now. I'll ping you the instant the gate or status changes."
        prompt.contains("pack", ignoreCase = true) ->
            "For your 3-day Atlanta trip: a suit, the printed board deck, laptop + charger, and your passport. Want a weather-based layer suggestion?"
        prompt.contains("expense", ignoreCase = true) ->
            "You're at \$1,812.75 across 4 items this trip. The Delta airfare (\$1,290) is the largest. Shall I export to Brex?"
        prompt.isBlank() ->
            "I'm IRIS, your travel concierge. Ask me about your flights, itinerary, packing, or expenses."
        else ->
            "I'm IRIS, your travel concierge. (Enable Firebase AI Logic for the project to turn on live AI.) " +
                "Meanwhile: your next flight is DL 1423 LAS→ATL, on time, gate C22."
    }
}
