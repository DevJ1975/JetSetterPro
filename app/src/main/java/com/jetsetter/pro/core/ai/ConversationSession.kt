package com.jetsetter.pro.core.ai

/**
 * R2 session contract for the Claude tier. The Anthropic Messages API is stateless — the full
 * message array is resent every turn (same as iOS's remote fallback; documented, not a bug) — so
 * "session recreation" here means compacting the history we resend.
 *
 * The session holds the last system-prompt hash and the running character estimate of the history.
 * [prepareHistory] returns the messages unchanged while both are stable; when the system prompt's
 * hash changes OR the history exceeds [MAX_HISTORY_CHARS] (~4k est. tokens), it recreates the
 * session: the last [KEPT_TURNS] turns are kept verbatim (aligned so the array still starts with a
 * `user` turn) and one synthetic user/assistant summary pair — built by deterministic truncation of
 * the dropped turns' first lines, no model call — is prepended in their place.
 *
 * Pure Kotlin (no Android/coroutine deps) so unit tests can drive it directly.
 */
class ConversationSession {

    private var systemPromptHash: Int? = null

    /** Estimated size of the last-seen history in characters (~4 chars ≈ 1 token). */
    var estimatedHistoryChars: Int = 0
        private set

    /**
     * Returns [messages] ready for the Anthropic array: unchanged while the session is stable,
     * compacted (summary pair + last [KEPT_TURNS] turns) when [systemPrompt]'s hash changed since
     * the previous call or the history overflows [MAX_HISTORY_CHARS]. Roles use the app's
     * "user"/"model" convention; the caller maps to Anthropic roles afterwards.
     */
    @Synchronized
    fun prepareHistory(messages: List<AiMessage>, systemPrompt: String): List<AiMessage> {
        val hash = systemPrompt.hashCode()
        val promptChanged = systemPromptHash != null && systemPromptHash != hash
        systemPromptHash = hash
        estimatedHistoryChars = messages.sumOf { it.text.length }
        if (!promptChanged && estimatedHistoryChars <= MAX_HISTORY_CHARS) return messages
        return compact(messages)
    }

    companion object {
        /** History budget before compaction (~4k est. tokens at ~4 chars/token). */
        const val MAX_HISTORY_CHARS = 16_000

        /** Most-recent turns preserved verbatim through a compaction. */
        const val KEPT_TURNS = 6

        private const val SUMMARY_PREFIX = "[Earlier conversation summary: "
        private const val SUMMARY_ACK = "Understood — I'll keep that earlier context in mind."

        /** Per-turn and total truncation bounds for the deterministic summary. */
        private const val MAX_LINE_CHARS = 80
        private const val MAX_SUMMARY_CHARS = 1_200

        /**
         * Keeps the last [KEPT_TURNS] messages (dropping any leading non-`user` ones so the array
         * still opens with a user turn) and prepends the synthetic summary pair for the rest.
         * No-op when there is nothing to drop.
         */
        internal fun compact(messages: List<AiMessage>): List<AiMessage> {
            if (messages.size <= KEPT_TURNS) return messages
            var kept = messages.takeLast(KEPT_TURNS)
            while (kept.isNotEmpty() && kept.first().role != "user") kept = kept.drop(1)
            val dropped = messages.dropLast(kept.size)
            if (dropped.isEmpty()) return kept
            return listOf(
                AiMessage(role = "user", text = summarize(dropped)),
                AiMessage(role = "model", text = SUMMARY_ACK),
            ) + kept
        }

        /** Deterministic one-message summary: each dropped turn's first line, truncated. */
        internal fun summarize(dropped: List<AiMessage>): String {
            val body = dropped.joinToString("; ") { msg ->
                val who = if (msg.role == "model") "IRIS" else "User"
                val firstLine = msg.text.lineSequence().firstOrNull().orEmpty().trim()
                "$who: ${firstLine.take(MAX_LINE_CHARS)}"
            }
            return SUMMARY_PREFIX + body.take(MAX_SUMMARY_CHARS) + "]"
        }
    }
}
