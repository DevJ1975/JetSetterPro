package com.jetsetter.pro.core.ai

import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real on-device IRIS tier: Gemini Nano via the ML Kit GenAI Prompt API.
 *
 * [isAvailable] is true only when the feature is `AVAILABLE` (downloaded + ready); DOWNLOADABLE /
 * DOWNLOADING / UNAVAILABLE all map to false so [com.jetsetter.pro.core.data.repository.IrisRepository]
 * falls through to Claude rather than blocking on a download. [stream] flattens the (already
 * RAG-augmented, privacy-gated) system block + history into one prompt — Nano has no system role —
 * and emits streamed text chunks.
 *
 * Stays deliberately "dumb about privacy": the caller (ContextAssembler → IrisRepository) decides
 * what grounding goes into [system]; on this tier that may include PERSONAL context, which never
 * leaves the device.
 */
@Singleton
class GeminiNanoOnDeviceAi @Inject constructor(
    private val manager: NanoModelManager,
) : OnDeviceAi {

    override suspend fun isAvailable(): Boolean =
        runCatching { manager.status() == FeatureStatus.AVAILABLE }.getOrDefault(false)

    override fun stream(system: String, history: List<AiMessage>): Flow<String> = flow {
        val model = manager.client() ?: return@flow
        val prompt = PromptFolder.fold(system, history)
        // Each emission carries the newly-generated text for the top candidate.
        model.generateContentStream(prompt).collect { response ->
            response.candidates.firstOrNull()?.text?.let { if (it.isNotEmpty()) emit(it) }
        }
    }.catch {
        // Swallow on-device errors: IrisRepository keeps any partial text or substitutes a demo
        // reply only if nothing streamed. Never crash the chat.
    }.flowOn(Dispatchers.Default)
}
