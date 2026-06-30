package com.jetsetter.pro.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Thin lifecycle-safe wrapper around [TextToSpeech] for speaking IRIS replies aloud — the output
 * mirror of [VoiceInput].
 *
 * Create one per consumer; speak only after the engine reports ready (handled internally), and always
 * call [shutdown] when finished (TTS holds a system connection). Long replies are split into sentence
 * chunks so streamed answers can be spoken progressively and to stay under the TTS input cap.
 */
class VoiceOutput(
    context: Context,
    private val onReady: (Boolean) -> Unit = {},
) {
    @Volatile private var ready = false
    @Volatile private var tts: TextToSpeech? = null
    private var counter = 0
    @Volatile private var lastUtteranceId: String? = null
    @Volatile private var pendingOnDone: (() -> Unit)? = null

    /** Whether TTS initialized and a usable language is available — gate the speak UI on this. */
    val isReady: Boolean get() = ready

    init {
        val engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS && languageUsable()
            onReady(ready)
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) pendingOnDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) pendingOnDone?.invoke()
            }
        })
        tts = engine
    }

    private fun languageUsable(): Boolean {
        val result = tts?.setLanguage(Locale.getDefault()) ?: return false
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * Speaks [text]. [flush] interrupts any current speech; otherwise it's queued. [onDone] fires once
     * when the final chunk completes (or errors). No-op until [isReady].
     */
    fun speak(text: String, flush: Boolean = true, onDone: () -> Unit = {}) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) return
        val parts = chunk(text)
        if (parts.isEmpty()) return
        pendingOnDone = onDone
        parts.forEachIndexed { index, part ->
            val mode = if (index == 0 && flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val id = "iris-${counter++}"
            if (index == parts.lastIndex) lastUtteranceId = id
            engine.speak(part, mode, null, id)
        }
    }

    /** Stop speaking immediately (keeps the engine alive). */
    fun stop() {
        runCatching { tts?.stop() }
    }

    /** Release the engine. Safe to call repeatedly. */
    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        pendingOnDone = null
    }

    /** Splits text into chunks under [max] chars, preferring sentence boundaries. */
    private fun chunk(text: String, max: Int = MAX_CHARS): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= max) return listOf(trimmed)
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in trimmed.split(Regex("(?<=[.!?])\\s+"))) {
            when {
                sentence.length > max -> {
                    if (sb.isNotEmpty()) { parts.add(sb.toString()); sb.clear() }
                    sentence.chunked(max).forEach { parts.add(it) }
                }
                sb.isNotEmpty() && sb.length + sentence.length + 1 > max -> {
                    parts.add(sb.toString()); sb.clear(); sb.append(sentence)
                }
                else -> {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(sentence)
                }
            }
        }
        if (sb.isNotEmpty()) parts.add(sb.toString())
        return parts
    }

    private companion object {
        /** TextToSpeech input cap is ~4000 chars; stay comfortably below it. */
        const val MAX_CHARS = 3800
    }
}
