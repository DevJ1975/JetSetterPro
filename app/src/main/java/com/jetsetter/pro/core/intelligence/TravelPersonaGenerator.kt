package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.ai.ClaudeClient
import com.jetsetter.pro.core.ai.ClaudeEvent
import com.jetsetter.pro.core.ai.OnDeviceAi
import com.jetsetter.pro.core.secrets.Secrets
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates the 2–3 sentence traveler persona (spec §1.6) from a computed [TravelProfileData].
 * Model ladder mirrors IRIS routing: on-device Gemini Nano when available, else Anthropic Claude
 * when configured, else a deterministic template assembled from the profile's top facts — so a
 * persona always exists once anything has been learned. `""` for an empty profile.
 *
 * The result is cached by [TravelProfileStore] keyed on the profile summary's hash; this class is
 * stateless. Output is capped at ~[MAX_CHARS] chars so the prompt append stays small.
 */
@Singleton
class TravelPersonaGenerator @Inject constructor(
    private val onDeviceAi: OnDeviceAi,
    private val claudeClient: ClaudeClient,
) {

    /** The persona text for [profile]; `""` when the profile is empty. Never throws. */
    suspend fun generate(profile: TravelProfileData): String {
        if (profile.isEmpty) return ""
        val summary = profile.summaryForPrompt()
        val generated = runCatching {
            when {
                onDeviceAi.isAvailable() -> onDevicePersona(summary)
                Secrets.isConfigured(Secrets.anthropic) -> claudePersona(summary)
                else -> null
            }
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return (generated ?: templatePersona(profile)).take(MAX_CHARS)
    }

    /** One on-device chat turn, stream collected to a single string. */
    private suspend fun onDevicePersona(summary: String): String {
        val builder = StringBuilder()
        onDeviceAi.stream(SYSTEM_PROMPT, listOf(AiMessage(role = "user", text = userPrompt(summary))))
            .collect { builder.append(it) }
        return builder.toString()
    }

    /** One Claude turn (no tools), text deltas collected to a single string. */
    private suspend fun claudePersona(summary: String): String {
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", userPrompt(summary)),
        )
        val builder = StringBuilder()
        claudeClient
            .stream(Secrets.anthropic, SYSTEM_PROMPT, messages, tools = null, maxTokens = PERSONA_MAX_TOKENS)
            .collect { event -> if (event is ClaudeEvent.Text) builder.append(event.text) }
        return builder.toString()
    }

    internal companion object {
        /** Persona budget — 2–3 sentences; anything longer is truncated. */
        internal const val MAX_CHARS = 400

        /** Small cap for the Claude call: a persona is far shorter than a chat reply. */
        internal const val PERSONA_MAX_TOKENS = 300

        private const val SYSTEM_PROMPT =
            "You write concise third-person traveler personas from learned travel-profile data."

        private fun userPrompt(summary: String): String =
            "Write a 2-3 sentence traveler persona from this profile summary. " +
                "No preamble, no headings — just the persona text.\n\n$summary"

        /**
         * Deterministic fallback persona assembled from the profile's top facts — used when no AI
         * tier is available so the persona section never silently disappears.
         */
        internal fun templatePersona(profile: TravelProfileData): String {
            val habits = buildList {
                profile.typicalSeat?.let { add("prefers a ${it.position} seat toward the ${it.zone} of the cabin") }
                profile.preferredCabin?.let { add("usually books ${it.lowercase()}") }
                profile.topAirlines.firstOrNull()?.let { add("flies most often with ${it.value}") }
            }
            val places = buildList {
                if (profile.frequentCities.isNotEmpty()) {
                    add("returns regularly to ${profile.frequentCities.take(2).joinToString(" and ") { it.value }}")
                }
                profile.typicalTripDurationDays?.let { add("typically travels for about $it days at a time") }
            }
            val sentences = buildList {
                add("A traveler who " + (habits.takeIf { it.isNotEmpty() } ?: listOf("is still building a profile")).joinToString(", ") + ".")
                if (places.isNotEmpty()) {
                    add("They " + places.joinToString(" and ") + ".")
                }
            }
            return sentences.joinToString(" ").take(MAX_CHARS)
        }
    }
}
