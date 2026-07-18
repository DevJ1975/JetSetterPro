package com.jetsetter.pro.core.data.repository

import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.ai.ClaudeClient
import com.jetsetter.pro.core.ai.ClaudeEvent
import com.jetsetter.pro.core.ai.ConversationSession
import com.jetsetter.pro.core.ai.IrisPersona
import com.jetsetter.pro.core.ai.IrisRoute
import com.jetsetter.pro.core.ai.IrisRouting
import com.jetsetter.pro.core.ai.IrisSystemPromptBuilder
import com.jetsetter.pro.core.ai.IrisToolDispatcher
import com.jetsetter.pro.core.ai.OnDeviceAi
import com.jetsetter.pro.core.rag.AiTier
import com.jetsetter.pro.core.rag.ContextAssembler
import com.jetsetter.pro.core.secrets.Secrets
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
 * Tier routing (R1, pinned by [IrisRouting]) mirrors iOS: **on-device first** whenever Gemini Nano
 * is available — regardless of whether the Anthropic key is set — then Anthropic Claude
 * (`claude-sonnet-4-6`, streaming, with tool use) when configured, then the canned demo. One
 * exception: the on-device tier is plain chat only (the ML Kit Prompt API has no tool calling), so
 * a turn that plausibly needs a tool (per the [com.jetsetter.pro.core.ai.IrisToolIntent] heuristic)
 * diverts to Claude when it is configured.
 *
 * Both tiers share the same personalized system prompt — [IrisSystemPromptBuilder] (base persona +
 * preferences + traveler persona + learned profile + live context) plus the turn's RAG block from
 * [ContextAssembler]. Per the parity spec, personalization flows to the cloud AI tier too; the
 * privacy invariant that remains is that profile data never reaches third-party DATA APIs.
 *
 * Session contract (R2): the Claude HTTP tier is stateless (full message-array resend each turn —
 * same as iOS's remote fallback), so [ConversationSession] compacts the history when the stable
 * system header changes or the history outgrows ~4k est. tokens: last 6 turns kept, older turns
 * replaced by one synthetic summary pair.
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
    private val contextAssembler: ContextAssembler,
    private val promptBuilder: IrisSystemPromptBuilder,
) {
    /** R2 session state: compacts resent history on system-prompt change or char overflow. */
    private val session = ConversationSession()

    /**
     * Streams IRIS's reply token-by-token. [history] is the full conversation; the last entry is
     * the new user message. Roles map from the app's convention ("user"/"model") to Anthropic's
     * ("user"/"assistant"), and any leading assistant turn (IRIS's opening greeting) is dropped so
     * the request starts with a `user` turn.
     */
    fun stream(history: List<AiMessage>): Flow<String> = flow {
        val lastUserText = history.lastOrNull { it.role == "user" }?.text.orEmpty()
        val key = Secrets.anthropic

        val route = IrisRouting.decide(
            onDeviceAvailable = runCatching { onDevice.isAvailable() }.getOrDefault(false),
            claudeConfigured = Secrets.isConfigured(key),
            lastUserText = lastUserText,
        )

        if (route == IrisRoute.DEMO) {
            emit(IrisPersona.demoResponse(lastUserText))
            return@flow
        }

        // Tier 1 — on-device (Gemini Nano). Plain chat only: tool-intent turns were already routed
        // to Claude above. Nano folds prompt + history itself (PromptFolder), so no session
        // compaction is needed here.
        if (route == IrisRoute.ON_DEVICE) {
            val ctx = contextAssembler.assemble(lastUserText, AiTier.ON_DEVICE)
            val system = promptBuilder.build() + ctx.systemBlock
            onDevice.stream(system, history).collect { emit(it) }
            return@flow
        }

        // Tier 2 — Claude. The system prompt = stable personalized header + this turn's RAG block.
        // Only the header feeds the session hash: the RAG block varies with every query, and
        // hashing it would force a compaction each turn.
        val systemHeader = promptBuilder.build()
        val cloudCtx = contextAssembler.assemble(lastUserText, AiTier.CLOUD)
        val systemForClaude = systemHeader + cloudCtx.systemBlock

        val prepared = session.prepareHistory(history.dropWhile { it.role != "user" }, systemHeader)
        val conversation = prepared
            .map { textMessage(if (it.role == "model") "assistant" else "user", it.text) }
            .toMutableList()
        if (conversation.isEmpty()) {
            emit(IrisPersona.demoResponse(lastUserText))
            return@flow
        }

        var streamedAny = false
        runCatching {
            var round = 0
            while (round++ < MAX_TOOL_ROUNDS) {
                val toolCalls = mutableListOf<ClaudeEvent.ToolUse>()
                val assistantText = StringBuilder()
                var stopReason: String? = null

                claude.stream(key, systemForClaude, JSONArray(conversation), tools.schema).collect { event ->
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
            if (!streamedAny) emit(IrisPersona.demoResponse(lastUserText))
        }
    }.flowOn(Dispatchers.IO)

    private fun textMessage(role: String, text: String): JSONObject =
        JSONObject().put("role", role).put("content", text)

    private companion object {
        /** Caps the tool round-trips per user turn so a misbehaving loop can't run forever. */
        const val MAX_TOOL_ROUNDS = 5
    }
}
