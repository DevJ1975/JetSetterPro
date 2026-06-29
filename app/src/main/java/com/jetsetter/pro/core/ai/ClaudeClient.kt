package com.jetsetter.pro.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One chat turn in Anthropic's wire format. [role] is "user" or "assistant". */
data class ClaudeMessage(val role: String, val text: String)

/**
 * Minimal streaming client for the Anthropic Messages API — the IRIS cloud tier.
 *
 * `POST https://api.anthropic.com/v1/messages` with `stream: true`; the response is Server-Sent
 * Events. We surface only the incremental assistant text: each `content_block_delta` of type
 * `text_delta` is emitted as a chunk, so the UI can render tokens as they arrive. `message_stop`
 * ends the stream; an `error` event or a non-2xx response throws so the caller can fall back to a
 * demo reply.
 *
 * Privacy: the caller passes only the conversation and the static [IrisPersona.SYSTEM_PROMPT] —
 * never the on-device learned profile (that must not leave the device, see the parity spec).
 */
@Singleton
class ClaudeClient @Inject constructor() {

    // Dedicated client: no read timeout so the SSE stream isn't cut mid-response; a call timeout
    // still bounds the whole exchange. Kept separate from the FlightAware OkHttpClient so its
    // x-apikey interceptor never touches Anthropic requests.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * Streams the assistant reply for [messages] (which must start with a `user` turn). Emits text
     * deltas in order. Throws on transport failure, a non-2xx status, or an `error` SSE event.
     */
    fun streamMessage(
        apiKey: String,
        system: String,
        messages: List<ClaudeMessage>,
        maxTokens: Int = MAX_TOKENS,
    ): Flow<String> = flow {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(payload(system, messages, maxTokens).toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = runCatching { response.body?.string() }.getOrNull()
                error("Anthropic HTTP ${response.code}${body?.let { ": $it" } ?: ""}")
            }
            val source = response.body?.source() ?: error("Anthropic: empty response body")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue            // skip `event:` lines + blanks
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                when (event.optString("type")) {
                    "content_block_delta" -> {
                        val delta = event.optJSONObject("delta")
                        if (delta?.optString("type") == "text_delta") {
                            val text = delta.optString("text")
                            if (text.isNotEmpty()) emit(text)
                        }
                    }
                    "error" -> {
                        val message = event.optJSONObject("error")?.optString("message")
                        error("Anthropic stream error: ${message ?: data}")
                    }
                    "message_stop" -> return@use
                }
            }
        }
    }

    private fun payload(system: String, messages: List<ClaudeMessage>, maxTokens: Int): String {
        val arr = JSONArray()
        messages.forEach { m -> arr.put(JSONObject().put("role", m.role).put("content", m.text)) }
        return JSONObject()
            .put("model", MODEL)
            .put("max_tokens", maxTokens)
            .put("stream", true)
            .put("system", system)
            .put("messages", arr)
            .toString()
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODEL = "claude-sonnet-4-6"
        const val MAX_TOKENS = 1024
        val JSON = "application/json".toMediaType()
    }
}
