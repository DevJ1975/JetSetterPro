package com.jetsetter.pro.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single owner of IRIS speech I/O (R9): drives the hands-free loop
 * `LISTENING → THINKING → SPEAKING → LISTENING` (spec §1.8) around one [VoiceInput], one
 * [VoiceOutput] and the system [AudioManager].
 *
 * Responsibilities:
 * - The recognizer is torn down BEFORE speaking (so TTS is never transcribed) and auto-resumed
 *   from [VoiceOutput]'s onDone.
 * - Transient may-duck audio focus is held only while speaking.
 * - Routing: system default (speaker for media) with best-effort Bluetooth —
 *   [AudioManager.setCommunicationDevice] on API 31+ when a BT device is present (and
 *   BLUETOOTH_CONNECT is granted), legacy SCO below — all failures degrade silently.
 * - [speakOnly] serves the plain "speak replies aloud" toggle without engaging the loop.
 *
 * Plain class (no Hilt) so the ViewModel can create it lazily with the application context and
 * release it in `onCleared` via [release]. All public methods must be called from the main thread
 * ([SpeechRecognizer]'s contract); internal TTS callbacks re-post to main.
 */
class VoiceLoopController(
    context: Context,
    private val onFinalTranscript: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val voiceInput = VoiceInput(appContext)
    private val voiceOutput = VoiceOutput(appContext)
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(VoiceLoopState.IDLE)

    /** Current loop phase — drive the Listening/Thinking/Speaking chip off this. */
    val state: StateFlow<VoiceLoopState> = _state.asStateFlow()

    private val _partial = MutableStateFlow<String?>(null)

    /** Live partial hypothesis while LISTENING (ghost it into the input field), else null. */
    val partial: StateFlow<String?> = _partial.asStateFlow()

    private var retryCount = 0
    private var focusRequest: AudioFocusRequest? = null

    /** Whether speech recognition is available at all — gate the hands-free toggle on this. */
    val isRecognitionAvailable: Boolean get() = voiceInput.isAvailable

    /** Enter the loop: route audio (best-effort Bluetooth) and start listening. */
    fun start() {
        if (_state.value != VoiceLoopState.IDLE) return
        voiceOutput.stop() // a plain speak-aloud reply may still be playing
        applyBluetoothRouting()
        if (apply(VoiceLoopEvent.StartTapped) == VoiceLoopState.LISTENING) beginListening()
    }

    /** Leave the loop from any state: mic down, speech stopped, focus + routing released. */
    fun stop() {
        apply(VoiceLoopEvent.StopTapped)
        _partial.value = null
        voiceInput.destroy()
        voiceOutput.stop()
        abandonFocus()
        clearBluetoothRouting()
    }

    /**
     * The reply for the in-flight turn is complete: tear the recognizer down, speak the reply
     * under transient may-duck focus, and auto-resume listening when TTS finishes. No-op unless
     * the loop is active.
     */
    fun onReplyReady(text: String) {
        if (_state.value == VoiceLoopState.IDLE) return
        // Mic down BEFORE any audio plays so the recognizer never hears IRIS herself.
        voiceInput.destroy()
        _partial.value = null
        if (apply(VoiceLoopEvent.ReplyComplete) != VoiceLoopState.SPEAKING) return
        if (text.isBlank() || !voiceOutput.isReady) {
            // Nothing speakable — skip straight back to listening so the loop never stalls.
            onSpeechFinished()
            return
        }
        requestFocus()
        voiceOutput.speak(text) {
            // UtteranceProgressListener callbacks arrive off-main; SpeechRecognizer needs main.
            mainHandler.post { onSpeechFinished() }
        }
    }

    /**
     * Speak [text] once WITHOUT the listening loop — the plain "speak replies aloud" path (R9:
     * all IRIS speech flows through this controller). Holds focus only while speaking.
     */
    fun speakOnly(text: String) {
        if (text.isBlank() || !voiceOutput.isReady) return
        requestFocus()
        voiceOutput.speak(text) { mainHandler.post { abandonFocus() } }
    }

    /** Stop any current speech immediately (loop state, if active, is unaffected). */
    fun stopSpeaking() {
        voiceOutput.stop()
        abandonFocus()
    }

    /** Full teardown for `onCleared` — after this the controller must not be reused. */
    fun release() {
        stop()
        voiceOutput.shutdown()
    }

    // ---- internals ----

    /** Run the pure state machine, thread the retry budget, publish, return the new state. */
    private fun apply(event: VoiceLoopEvent): VoiceLoopState {
        val next = VoiceLoopStateMachine.transition(_state.value, event, retryCount)
        retryCount = next.retryCount
        _state.value = next.state
        return next.state
    }

    private fun beginListening() {
        _partial.value = null
        voiceInput.start(
            onResult = { phrase ->
                if (_state.value != VoiceLoopState.LISTENING) return@start
                _partial.value = null
                apply(VoiceLoopEvent.FinalTranscript)
                onFinalTranscript(phrase)
            },
            onError = {
                if (_state.value != VoiceLoopState.LISTENING) return@start
                val next = apply(VoiceLoopEvent.RecognitionError)
                if (next == VoiceLoopState.LISTENING) {
                    beginListening() // the machine granted one silent retry
                } else {
                    stop() // budget exhausted — end the loop cleanly
                }
            },
            onPartial = { hypothesis -> _partial.value = hypothesis },
            preferOffline = true,
            completeSilenceMillis = VoiceInput.DEFAULT_COMPLETE_SILENCE_MILLIS,
        )
    }

    private fun onSpeechFinished() {
        abandonFocus()
        if (apply(VoiceLoopEvent.TtsFinished) == VoiceLoopState.LISTENING) beginListening()
    }

    // minSdk is 26, so the AudioFocusRequest (API 26+) path is unconditional.
    private fun requestFocus() {
        if (focusRequest != null) return
        runCatching {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        }
    }

    private fun abandonFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    /**
     * Best-effort Bluetooth routing; default (speaker) routing is left alone otherwise.
     * BLUETOOTH_CONNECT is a runtime permission on API 31+ (R9) — skip silently when not granted.
     */
    private fun applyBluetoothRouting() {
        if (!hasBluetoothPermission()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) audioManager.setCommunicationDevice(btDevice)
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                }
            }
        }
    }

    private fun clearBluetoothRouting() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
}
