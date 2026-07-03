package com.jetsetter.pro.core.data.repository

import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.ai.ClaudeClient
import com.jetsetter.pro.core.ai.ClaudeEvent
import com.jetsetter.pro.core.ai.IrisPersona
import com.jetsetter.pro.core.ai.IrisToolDispatcher
import com.jetsetter.pro.core.ai.OnDeviceAi
import com.jetsetter.pro.core.secrets.Secrets
import com.jetsetter.pro.feature.departureoptimizer.DepartureoptimizerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the IRIS assistant (Intelligent Routing & Itinerary Specialist).
 *
 * Tiering mirrors iOS: on-device (Phase C — Gemini Nano, seam wired via [OnDeviceAi]) →
 * **Anthropic Claude** (`claude-sonnet-4-6`, streaming, with tool use) → canned demo. The
 * on-device tier serves plain chat only and (with the default no-op binding) reports unavailable,
 * so routing currently falls through to it. When the `API_ANTHROPIC` key is set IRIS streams from
 * Claude and may call [IrisToolDispatcher] tools to actually act on the app (add a trip, log an
 * expense, summarize spend); otherwise it falls back to [IrisPersona.demoResponse].
 *
 * The tool loop (see [stream]): stream a turn → if it ends with `tool_use`, echo the assistant's
 * `tool_use` blocks, run the tools, feed the `tool_result`s back, and stream again — until the
 * model finishes (`end_turn`) or a safety bound is hit. All emitted text flows into one chat bubble.
 */
@Singleton
class IrisRepository @Inject constructor(
    private val claude: ClaudeClient,
    private val tools: IrisToolDispatcher,
    private val onDevice: OnDeviceAi,
    private val departureRepository: DepartureoptimizerRepository,
) {
    /**
     * Streams IRIS's reply token-by-token. [history] is the full conversation; the last entry is
     * the new user message. Roles map from the app's convention ("user"/"model") to Anthropic's
     * ("user"/"assistant"), and any leading assistant turn (IRIS's opening greeting) is dropped so
     * the request starts with a `user` turn.
     */
    fun stream(history: List<AiMessage>): Flow<String> = flow {
        val lastUserText = history.lastOrNull { it.role == "user" }?.text.orEmpty()

        // The Departure Optimizer's live snapshot, so demo replies about leave-by/traffic/weather
        // always agree with what that screen currently shows (including after a re-roll).
        val departureEstimate = runCatching { departureRepository.load() }.getOrNull()

        val key = Secrets.anthropic
        if (!Secrets.isConfigured(key)) {
            emit(IrisPersona.demoResponse(lastUserText, departureEstimate))
            return@flow
        }

        val conversation = history
            .dropWhile { it.role != "user" }
            .map { textMessage(if (it.role == "model") "assistant" else "user", it.text) }
            .toMutableList()
        if (conversation.isEmpty()) {
            emit(IrisPersona.demoResponse(lastUserText, departureEstimate))
            return@flow
        }

        // Tier 1 — on-device (Phase C, Gemini Nano). Plain chat only: tools are not supported
        // on-device, so any turn needing tool use falls through to the Claude loop below. The
        // default binding reports unavailable, so this branch is a no-op until a real backend
        // is supplied (see OnDeviceAi).
        if (onDevice.isAvailable()) {
            onDevice.stream(IrisPersona.SYSTEM_PROMPT, history).collect { emit(it) }
            return@flow
        }

        var streamedAny = false
        runCatching {
            var round = 0
            while (round++ < MAX_TOOL_ROUNDS) {
                val toolCalls = mutableListOf<ClaudeEvent.ToolUse>()
                val assistantText = StringBuilder()
                var stopReason: String? = null

                claude.stream(key, IrisPersona.SYSTEM_PROMPT, JSONArray(conversation), tools.schema).collect { event ->
                    when (event) {
                        is ClaudeEvent.Text -> {
                            streamedAny = true
                            assistantText.append(event.text)
                            emit(event.text)
                        }
                        is ClaudeEvent.ToolUse -> toolCalls.add(event)
                        is ClaudeEvent.Done -> stopReason = event.stopReason
                    }
                }

                if (stopReason != "tool_use" || toolCalls.isEmpty()) break

                // Echo the assistant turn (any text + the tool_use blocks), then feed tool results back.
                val assistantContent = JSONArray()
                if (assistantText.isNotBlank()) {
                    assistantContent.put(JSONObject().put("type", "text").put("text", assistantText.toString()))
                }
                toolCalls.forEach { call ->
                    assistantContent.put(
                        JSONObject()
                            .put("type", "tool_use")
                            .put("id", call.id)
                            .put("name", call.name)
                            .put("input", call.input),
                    )
                }
                conversation.add(JSONObject().put("role", "assistant").put("content", assistantContent))

                val results = JSONArray()
                toolCalls.forEach { call ->
                    val output = runCatching { tools.execute(call.name, call.input) }
                        .getOrElse { "Tool ${call.name} failed: ${it.message}" }
                    results.put(
                        JSONObject().put("type", "tool_result").put("tool_use_id", call.id).put("content", output),
                    )
                }
                conversation.add(JSONObject().put("role", "user").put("content", results))
            }
        }.onFailure {
            // Only substitute a demo reply if nothing streamed yet; a mid-stream drop keeps the partial.
            if (!streamedAny) emit(IrisPersona.demoResponse(lastUserText, departureEstimate))
        }
    }.flowOn(Dispatchers.IO)

    private fun textMessage(role: String, text: String): JSONObject =
        JSONObject().put("role", role).put("content", text)

    private companion object {
        /** Caps the tool round-trips per user turn so a misbehaving loop can't run forever. */
        const val MAX_TOOL_ROUNDS = 5
    }
}
