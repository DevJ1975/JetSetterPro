package com.jetsetter.pro.core.data.repository

import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.ai.ClaudeClient
import com.jetsetter.pro.core.ai.ClaudeMessage
import com.jetsetter.pro.core.ai.IrisPersona
import com.jetsetter.pro.core.secrets.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the IRIS assistant (Intelligent Routing & Itinerary Specialist).
 *
 * Tiering mirrors iOS: on-device (Phase C — Gemini Nano via ML Kit, not yet wired) → **Anthropic
 * Claude** (`claude-sonnet-4-6`, streaming) → canned demo. The cloud tier streams when the
 * `API_ANTHROPIC` key is configured; otherwise (or if the request fails before any token arrives)
 * it falls back to [IrisPersona.demoResponse] so the IRIS tab always answers.
 */
@Singleton
class IrisRepository @Inject constructor(
    private val claude: ClaudeClient,
) {
    /**
     * Streams IRIS's reply token-by-token. [history] is the full conversation; the last entry is
     * the new user message. Roles map from the app's Gemini-style convention ("user"/"model") to
     * Anthropic's ("user"/"assistant"), and any leading assistant turn (IRIS's opening greeting)
     * is dropped so the request starts with a `user` turn as the API requires.
     */
    fun stream(history: List<AiMessage>): Flow<String> = flow {
        val lastUserText = history.lastOrNull { it.role == "user" }?.text.orEmpty()

        val key = Secrets.anthropic
        if (!Secrets.isConfigured(key)) {
            emit(IrisPersona.demoResponse(lastUserText))
            return@flow
        }

        val messages = history
            .dropWhile { it.role != "user" }
            .map { ClaudeMessage(role = if (it.role == "model") "assistant" else "user", text = it.text) }
        if (messages.isEmpty()) {
            emit(IrisPersona.demoResponse(lastUserText))
            return@flow
        }

        var streamedAny = false
        runCatching {
            claude.streamMessage(key, IrisPersona.SYSTEM_PROMPT, messages).collect { delta ->
                streamedAny = true
                emit(delta)
            }
        }.onFailure {
            // Only substitute a demo reply if nothing streamed yet; a mid-stream drop keeps the
            // partial answer rather than discarding it.
            if (!streamedAny) emit(IrisPersona.demoResponse(lastUserText))
        }
    }.flowOn(Dispatchers.IO)
}
