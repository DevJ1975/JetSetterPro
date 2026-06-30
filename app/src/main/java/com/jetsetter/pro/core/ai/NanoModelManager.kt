package com.jetsetter.pro.core.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Gemini Nano (ML Kit GenAI Prompt API) client lifecycle so the chat hot path stays cheap.
 *
 * The model is created lazily and cached. [status] maps the AICore feature state onto the four
 * [FeatureStatus] ints. [ensureReady] is the conservative startup path — it warms up an already
 * AVAILABLE model but never kicks off a multi-hundred-MB download on its own; an explicit
 * [requestDownload] (e.g. from a Settings opt-in) does that. Everything is wrapped so a device
 * without AICore simply reports unavailable instead of throwing.
 */
@Singleton
class NanoModelManager @Inject constructor() {

    sealed interface State {
        data object Unknown : State
        data object Unavailable : State
        data object Downloadable : State
        data class Downloading(val bytes: Long) : State
        data object Ready : State
        data class Failed(val reason: String?) : State
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var model: GenerativeModel? = null
    @Volatile private var constructionFailed = false

    /** The cached client, or null if it can't be constructed on this device. */
    suspend fun client(): GenerativeModel? {
        model?.let { return it }
        if (constructionFailed) return null
        return mutex.withLock {
            model ?: runCatching { Generation.getClient() }
                .onFailure {
                    constructionFailed = true
                    _state.value = State.Unavailable
                    Log.w(TAG, "Gemini Nano client unavailable on this device.", it)
                }
                .getOrNull()?.also { model = it }
        }
    }

    /** Current feature status; UNAVAILABLE on any error (so routing falls through to Claude). */
    suspend fun status(): Int {
        val mp = client() ?: return FeatureStatus.UNAVAILABLE
        return runCatching { mp.checkStatus() }.getOrElse {
            Log.w(TAG, "checkStatus failed", it)
            FeatureStatus.UNAVAILABLE
        }
    }

    /** Warms up an already-available model; reflects (but does not initiate) download state. */
    suspend fun ensureReady() {
        val mp = client() ?: return
        when (runCatching { mp.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)) {
            FeatureStatus.AVAILABLE -> {
                runCatching { mp.warmup() }
                _state.value = State.Ready
            }
            FeatureStatus.DOWNLOADING -> _state.value = State.Downloading(0)
            FeatureStatus.DOWNLOADABLE -> _state.value = State.Downloadable
            else -> _state.value = State.Unavailable
        }
    }

    /** Explicitly downloads the on-device model (call from a user-initiated Settings opt-in). */
    suspend fun requestDownload() {
        val mp = client() ?: return
        runCatching {
            mp.download().collect { s: DownloadStatus ->
                _state.value = when (s) {
                    is DownloadStatus.DownloadProgress -> State.Downloading(s.totalBytesDownloaded)
                    is DownloadStatus.DownloadCompleted -> State.Ready
                    is DownloadStatus.DownloadFailed -> State.Failed(s.toString())
                    else -> _state.value
                }
            }
            if (_state.value !is State.Failed) {
                runCatching { mp.warmup() }
                _state.value = State.Ready
            }
        }.onFailure {
            _state.value = State.Failed(it.message)
            Log.w(TAG, "Nano download failed", it)
        }
    }

    private companion object {
        const val TAG = "NanoModelManager"
    }
}
