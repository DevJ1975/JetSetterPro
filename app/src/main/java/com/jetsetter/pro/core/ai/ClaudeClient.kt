package com.jetsetter.pro.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** A decoded item from the Anthropic streaming response. */
sealed interface ClaudeEvent {
    /** An incremental chunk of assistant text. */
    data class Text(val text: String) : ClaudeEvent

    /** A completed `tool_use` block — IRIS wants the app to run [name] with [input]. */
    data class ToolUse(val id: String, val name: String, val input: JSONObject) : ClaudeEvent

    /** The turn finished; [stopReason] is `end_turn`, `tool_use`, `max_tokens`, `refusal`, … */
    data class Done(val stopReason: String?) : ClaudeEvent
}

/**
 * Minimal streaming client for the Anthropic Messages API — the IRIS cloud tier.
 *
 * `POST https://api.anthropic.com/v1/messages` with `stream: true`; the response is Server-Sent
 * Events, decoded into a [Flow] of [ClaudeEvent]. Text deltas arrive as [ClaudeEvent.Text] (so the
 * UI can render tokens live); `tool_use` blocks are reassembled from their `input_json_delta`
 * fragments and surfaced as [ClaudeEvent.ToolUse]; the turn ends with [ClaudeEvent.Done] carrying
 * the stop reason. A non-2xx response or an `error` SSE event throws so the caller can fall back.
 *
 * Privacy: per the parity spec, the system prompt may carry the traveler's stored preferences,
 * persona, learned-profile summary, and live context (see [IrisSystemPromptBuilder]) — Anthropic
 * is a sanctioned AI tier for personalization. The invariant that remains: preference/profile data
 * never goes to third-party DATA APIs (FlightAware, Open-Meteo, FX, …), which receive only IATA
 * codes, coordinates, currency codes, and flight idents.
 */
@Singleton
class ClaudeClient @Inject constructor(
    @Named("sseHttp") sseHttp: OkHttpClient,
) {

    // Streaming client from NetworkModule ("sseHttp"): no read timeout so the SSE stream isn't
    // cut mid-response; a call timeout still bounds the whole exchange; the GET RetryInterceptor
    // is stripped so a streamed POST is never replayed. Kept separate from the FlightAware client
    // so its x-apikey interceptor never touches Anthropic requests.
    private val http = sseHttp

    /**
     * Streams one assistant turn. [messages] is the Anthropic-format message array (each element a
     * `{role, content}` object whose content is a string or a block array) and must begin with a
     * `user` turn. [tools] is an optional tool-schema array. Throws on transport failure, a non-2xx
     * status, or an `error` SSE event.
     */
    fun stream(
        apiKey: String,
        system: String,
        messages: JSONArray,
        tools: JSONArray? = null,
        maxTokens: Int = MAX_TOKENS,
    ): Flow<ClaudeEvent> = flow {
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", maxTokens)
            .put("stream", true)
            .put("system", system)
            .put("messages", messages)
        if (tools != null && tools.length() > 0) body.put("tools", tools)

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = runCatching { response.body?.string() }.getOrNull()
                error("Anthropic HTTP ${response.code}${errBody?.let { ": $it" } ?: ""}")
            }
            val source = response.body?.source() ?: error("Anthropic: empty response body")

            // tool_use blocks arrive split across events; reassemble them by content-block index.
            val toolBlocks = HashMap<Int, ToolAccumulator>()
            var stopReason: String? = null

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue          // skip `event:` lines, pings, blanks
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                val event = runCatching { JSONObject(data) }.getOrNull() ?: continue

                when (event.optString("type")) {
                    "content_block_start" -> {
                        val block = event.optJSONObject("content_block")
                        if (block?.optString("type") == "tool_use") {
                            toolBlocks[event.optInt("index")] =
                                ToolAccumulator(block.optString("id"), block.optString("name"))
                        }
                    }
                    "content_block_delta" -> {
                        val delta = event.optJSONObject("delta") ?: continue
                        when (delta.optString("type")) {
                            "text_delta" -> delta.optString("text")
                                .takeIf { it.isNotEmpty() }
                                ?.let { emit(ClaudeEvent.Text(it)) }
                            "input_json_delta" ->
                                toolBlocks[event.optInt("index")]?.json?.append(delta.optString("partial_json"))
                        }
                    }
                    "content_block_stop" -> {
                        toolBlocks.remove(event.optInt("index"))?.let { acc ->
                            val raw = acc.json.toString().ifBlank { "{}" }
                            val input = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                            emit(ClaudeEvent.ToolUse(acc.id, acc.name, input))
                        }
                    }
                    "message_delta" ->
                        event.optJSONObject("delta")?.optString("stop_reason")
                            ?.takeIf { it.isNotEmpty() }?.let { stopReason = it }
                    "error" -> {
                        val message = event.optJSONObject("error")?.optString("message")
                        error("Anthropic stream error: ${message ?: data}")
                    }
                    "message_stop" -> {
                        emit(ClaudeEvent.Done(stopReason))
                        return@use
                    }
                }
            }
            emit(ClaudeEvent.Done(stopReason)) // stream closed without an explicit message_stop
        }
    }

    private class ToolAccumulator(val id: String, val name: String) {
        val json = StringBuilder()
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODEL = "claude-sonnet-4-6"
        const val MAX_TOKENS = 1024
        val JSON = "application/json".toMediaType()
    }
}
