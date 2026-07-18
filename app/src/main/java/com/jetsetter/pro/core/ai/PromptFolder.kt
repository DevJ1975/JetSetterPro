package com.jetsetter.pro.core.ai

/**
 * Folds IRIS's persona + RAG context + recent history into a single prompt string for Gemini Nano.
 *
 * Nano (ML Kit GenAI Prompt API) has **no system role**, so we flatten everything into one prompt.
 * Folding applies to HISTORY ONLY: the system header (persona + dynamic sections + RAG block) is
 * kept whole — it's the grounding — and the most recent turns are included newest-first until the
 * character budget is hit; older turns are dropped, never the header. ~4 chars ≈ 1 token, so
 * ~24k chars ≈ ~6k tokens of input — sized (R2) so the full spec persona plus its dynamic
 * sections (≤ ~16k chars, see [IrisSystemPromptBuilder]) always fits with room for recent turns.
 */
object PromptFolder {

    fun fold(system: String, history: List<AiMessage>, maxInputChars: Int = 24_000): String {
        val header = system.trim()
        val budget = (maxInputChars - header.length - RESERVE).coerceAtLeast(0)

        val rendered = ArrayDeque<String>()
        var used = 0
        for (msg in history.asReversed()) {
            val who = if (msg.role == "model") "IRIS" else "User"
            val line = "$who: ${msg.text.trim()}\n"
            if (used + line.length > budget && rendered.isNotEmpty()) break
            rendered.addFirst(line)
            used += line.length
        }

        return buildString {
            append(header).append("\n\n")
            rendered.forEach { append(it) }
            append("IRIS:")
        }
    }

    /** Headroom for the trailing cue and minor formatting. */
    private const val RESERVE = 32
}
