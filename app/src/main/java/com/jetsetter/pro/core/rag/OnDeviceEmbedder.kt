package com.jetsetter.pro.core.rag

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces embedding vectors for RAG entirely on-device.
 *
 * [modelId] identifies the exact embedding model in use. It is stamped onto every stored chunk and
 * compared on load so the app never mixes vectors from two different models (a different model is a
 * different vector space). The offline KB builder (`tools/iris-kb`) must embed documents with the
 * SAME model id + the SAME `.tflite` file.
 */
interface OnDeviceEmbedder {
    /** Stable id of the embedding model; must match the offline builder and the KB manifest. */
    val modelId: String

    /** Embedding dimension once known (after the first successful embed), else null. */
    val dimension: Int?

    /** True when the embedder initialized and can serve. False ⇒ RAG is simply off (no grounding). */
    suspend fun isReady(): Boolean

    /** Embeds [text], or returns null when the embedder is unavailable / errors. */
    suspend fun embed(text: String): FloatArray?

    /** Embeds many texts; entries that fail are dropped (returned list may be shorter). */
    suspend fun embedAll(texts: List<String>): List<FloatArray>
}

/**
 * [OnDeviceEmbedder] backed by MediaPipe Tasks [TextEmbedder] running a bundled `.tflite` model.
 *
 * The embedder is heavy, so it is created once (guarded by a mutex) and reused. If the model asset
 * is missing or initialization fails on this device, [isReady] returns false and [embed] returns
 * null — RetrievalService then yields no chunks and IRIS runs un-grounded rather than crashing.
 * L2-normalization is enabled so cosine == dot product and matches the offline `normalize=true`.
 */
@Singleton
class MediaPipeTextEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnDeviceEmbedder {

    override val modelId: String = MODEL_ID

    @Volatile
    private var cachedDimension: Int? = null
    override val dimension: Int? get() = cachedDimension

    private val initMutex = Mutex()
    @Volatile private var embedder: TextEmbedder? = null
    @Volatile private var initFailed = false

    private suspend fun obtain(): TextEmbedder? {
        embedder?.let { return it }
        if (initFailed) return null
        return initMutex.withLock {
            embedder?.let { return it }
            if (initFailed) return null
            runCatching {
                val options = TextEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                    .setL2Normalize(true)
                    .setQuantize(false)
                    .build()
                TextEmbedder.createFromOptions(context, options)
            }.onFailure {
                initFailed = true
                Log.w(TAG, "On-device embedder unavailable; RAG grounding disabled.", it)
            }.getOrNull()?.also { embedder = it }
        }
    }

    override suspend fun isReady(): Boolean = obtain() != null

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        val mp = obtain() ?: return@withContext null
        runCatching {
            val result = mp.embed(text)
            val vector = result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
            vector?.also { cachedDimension = it.size }
        }.getOrElse {
            Log.w(TAG, "Embed failed", it)
            null
        }
    }

    override suspend fun embedAll(texts: List<String>): List<FloatArray> =
        texts.mapNotNull { embed(it) }

    private companion object {
        const val TAG = "OnDeviceEmbedder"
        /** Must equal the offline builder's model id and the value stamped on every KB row. */
        const val MODEL_ID = "use-v1"
        const val MODEL_ASSET = "use_embedder.tflite"
    }
}
