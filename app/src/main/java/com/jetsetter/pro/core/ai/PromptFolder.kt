package com.jetsetter.pro.core.ai

/**
 * Folds IRIS's persona + RAG context + recent history into a single prompt string for Gemini Nano.
 *
 * Nano (ML Kit GenAI Prompt API) has **no system role** and a small (~4k-token) input window, so we
 * flatten everything into one prompt. The system/RAG block is kept whole (it's the grounding), then
 * the most recent turns are included newest-first until the character budget is hit — older turns are
 * dropped rather than the grounding. ~4 chars ≈ 1 token, so ~14k chars ≈ ~3.5k tokens of input.
 */
object PromptFolder {

    fun fold(system: String, history: List<AiMessage>, maxInputChars: Int = 14_000): String {
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
