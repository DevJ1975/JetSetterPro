package com.jetsetter.pro.core.rag

import com.jetsetter.pro.core.rag.RetrievalService.RetrievedChunk
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the RAG grounding block IRIS appends to its system prompt for a turn.
 *
 * Privacy contract (parity spec): personalization goes to BOTH AI tiers — on-device Gemini Nano and
 * Anthropic Claude alike may be grounded with PUBLIC and PERSONAL knowledge, just as
 * [com.jetsetter.pro.core.ai.IrisSystemPromptBuilder] feeds both tiers the preference / persona /
 * profile / live-context sections. The invariant that remains: profile- and preference-derived data
 * is never sent to third-party DATA APIs (FlightAware, Open-Meteo, FX, SITA, …) — those requests
 * carry only IATA codes, coordinates, currency codes, and flight idents.
 *
 * Traveler-context assembly (learned profile, upcoming trips, preferences) has moved to the system
 * prompt builder; this class contributes retrieved knowledge chunks only.
 */
@Singleton
class ContextAssembler @Inject constructor(
    private val retrieval: RetrievalService,
) {
    data class GroundingContext(val systemBlock: String, val sources: List<RetrievedChunk>) {
        fun isEmpty(): Boolean = systemBlock.isBlank()
    }

    suspend fun assemble(query: String, tier: AiTier): GroundingContext {
        // Both tiers may be grounded with public AND personal knowledge (see class KDoc). The
        // [tier] parameter stays so call sites remain explicit about which tier they serve.
        val allowed = setOf(Sensitivity.PUBLIC, Sensitivity.PERSONAL)
        val sources = retrieval.retrieve(query, allowed = allowed)

        val sb = StringBuilder()
        if (sources.isNotEmpty()) {
            sb.append("\n\n## Knowledge\n")
            sb.append("Use these facts when relevant. If they don't cover the question, answer from general knowledge and say so.\n")
            sources.forEach { sb.append("- ").append(it.text.trim()).append('\n') }
        }
        return GroundingContext(sb.toString(), sources)
    }
}
