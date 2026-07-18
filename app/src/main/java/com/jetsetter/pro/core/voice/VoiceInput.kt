package com.jetsetter.pro.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin lifecycle-safe wrapper around [SpeechRecognizer] for voice input — push-to-talk by default,
 * with optional partial streaming / on-device preference / silence tuning for the hands-free loop
 * (spec §1.8).
 *
 * Create one per consumer, call [start] to begin listening and [stop] to end early; always call
 * [destroy] when finished (the underlying recognizer holds a system connection that must be
 * released). Callbacks are delivered on the main thread, matching [SpeechRecognizer]'s contract.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    /** Whether on-device speech recognition is available — gate the mic UI on this. */
    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Begin listening. [onResult] fires once with the best recognized phrase; [onError] fires on
     * any failure (no match, permission, busy, etc.). Exactly one of the two is invoked per session.
     *
     * Optional (all default to the original push-to-talk behavior, so existing call sites compile
     * unchanged):
     * - [onPartial] — when non-null, partial hypotheses stream through it while the user speaks
     *   ([RecognizerIntent.EXTRA_PARTIAL_RESULTS]).
     * - [preferOffline] — request on-device recognition when available
     *   ([RecognizerIntent.EXTRA_PREFER_OFFLINE]); spec §1.8 prefers on-device for the loop.
     * - [completeSilenceMillis] — end-of-utterance silence window (~1.2 s per spec §1.8), applied
     *   to both the complete and possibly-complete silence extras. These extras are advisory —
     *   some recognizers ignore them.
     */
    fun start(
        onResult: (String) -> Unit,
        onError: () -> Unit,
        onPartial: ((String) -> Unit)? = null,
        preferOffline: Boolean = false,
        completeSilenceMillis: Long = DEFAULT_COMPLETE_SILENCE_MILLIS,
    ) {
        if (!isAvailable) {
            onError()
            return
        }

        // Tear down any previous session before starting a fresh one.
        destroy()

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (onPartial == null) return
                val hypothesis = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!hypothesis.isNullOrEmpty()) onPartial(hypothesis)
            }

            override fun onError(error: Int) {
                onError()
            }

            override fun onResults(results: Bundle?) {
                val phrase = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (phrase.isNullOrEmpty()) onError() else onResult(phrase)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, onPartial != null)
            if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // The recognizer service reads these extras as Int — a Long value makes it throw
            // ClassCastException internally and fall back to 0, so convert at the boundary.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                completeSilenceMillis.toInt(),
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                completeSilenceMillis.toInt(),
            )
        }

        runCatching { sr.startListening(intent) }.onFailure { onError() }
    }

    /** Stop listening but keep the recognizer alive (pending results may still arrive). */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    /** Release the recognizer and its system connection. Safe to call repeatedly. */
    fun destroy() {
        recognizer?.let { sr ->
            runCatching { sr.stopListening() }
            runCatching { sr.cancel() }
            runCatching { sr.destroy() }
        }
        recognizer = null
    }

    companion object {
        /** End-of-utterance silence window — ~1.2 s per spec §1.8. */
        const val DEFAULT_COMPLETE_SILENCE_MILLIS = 1200L
    }
}
