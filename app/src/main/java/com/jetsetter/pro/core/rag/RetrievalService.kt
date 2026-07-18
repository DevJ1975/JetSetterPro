package com.jetsetter.pro.core.rag

import com.jetsetter.pro.core.data.repository.KbRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Semantic retrieval over the on-device KB. Embeds the query once, scores every cached chunk in the
 * allowed sensitivity set by cosine similarity, and returns the top-k above a minimum-score floor.
 *
 * The [allowed] set is the privacy gate's teeth: the caller ([ContextAssembler]) passes
 * `{PUBLIC}` for the Claude tier and `{PUBLIC, PERSONAL}` for the on-device tier.
 */
@Singleton
class RetrievalService @Inject constructor(
    private val embedder: OnDeviceEmbedder,
    private val kb: KbRepository,
) {
    /** A retrieved chunk with its similarity to the query. */
    data class RetrievedChunk(
        val text: String,
        val source: String,
        val sensitivity: Sensitivity,
        val score: Float,
    )

    suspend fun retrieve(
        query: String,
        k: Int = DEFAULT_K,
        allowed: Set<Sensitivity>,
        minScore: Float = DEFAULT_MIN_SCORE,
    ): List<RetrievedChunk> {
        if (query.isBlank() || k <= 0 || allowed.isEmpty()) return emptyList()
        val queryVec = embedder.embed(query) ?: return emptyList() // embedder unavailable ⇒ RAG off
        kb.ensureLoaded()
        return kb.cached().asSequence()
            .filter { it.sensitivity in allowed }
            .map { RetrievedChunk(it.text, it.source, it.sensitivity, VectorMath.cosine(queryVec, it.vec)) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(k)
            .toList()
    }

    private companion object {
        const val DEFAULT_K = 5
        /** Drops weak matches so the prompt isn't padded with irrelevant chunks. Tune with eval. */
        const val DEFAULT_MIN_SCORE = 0.25f
    }
}
