package com.jetsetter.pro.core.data.repository

import android.util.Log
import com.jetsetter.pro.core.data.local.KbChunkDao
import com.jetsetter.pro.core.data.local.KbChunkEntity
import com.jetsetter.pro.core.data.local.KbConverters
import com.jetsetter.pro.core.rag.OnDeviceEmbedder
import com.jetsetter.pro.core.rag.Sensitivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-device RAG knowledge base ([KbChunkEntity]) and an in-memory vector cache for fast
 * brute-force cosine retrieval.
 *
 * The cache is loaded once (lazily, [ensureLoaded]) into a flat list — for a curated KB of
 * hundreds–few-thousand chunks a full scan per query is sub-millisecond, so no vector index is
 * needed (see the plan for the sqlite-vec / ObjectBox scale path).
 *
 * **Parity guard:** rows whose `modelId` differs from the current [OnDeviceEmbedder] are dropped on
 * load (and deleted) — they were embedded by a different model and live in an incompatible vector
 * space. `PERSONAL` rows get re-indexed from the live ledgers by `UserDataIndexer`; the bundled
 * `PUBLIC` KB is re-seeded by `KbSeeder` when the embedder/model changes.
 */
@Singleton
class KbRepository @Inject constructor(
    private val dao: KbChunkDao,
    private val embedder: OnDeviceEmbedder,
) {
    /** A retrieval-ready chunk: text + provenance + the unpacked vector. */
    data class CachedChunk(
        val id: String,
        val text: String,
        val source: String,
        val sensitivity: Sensitivity,
        val vec: FloatArray,
    )

    @Volatile private var cache: List<CachedChunk> = emptyList()
    @Volatile private var loaded = false
    private val loadMutex = Mutex()

    /** Loads the cache once. Safe to call on every retrieval. */
    suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            loadCacheLocked()
            loaded = true
        }
    }

    /** Forces a reload (after seeding or re-indexing user data). */
    suspend fun reload() = loadMutex.withLock { loadCacheLocked() }

    private suspend fun loadCacheLocked() {
        val rows = runCatching { dao.getAll() }.getOrElse {
            Log.w(TAG, "KB load failed", it); emptyList()
        }
        val (compatible, stale) = rows.partition { it.modelId == embedder.modelId }
        if (stale.isNotEmpty()) {
            Log.w(TAG, "Dropping ${stale.size} KB chunk(s) from a stale embedding model.")
            runCatching { stale.forEach { dao.deleteBySource(it.source) } }
        }
        cache = compatible.map {
            CachedChunk(it.id, it.text, it.source, Sensitivity.fromWire(it.sensitivity), KbConverters.unpack(it.embedding))
        }
    }

    /** Snapshot of cached chunks for retrieval. */
    fun cached(): List<CachedChunk> = cache

    suspend fun count(): Int = dao.count()

    /** Replaces the entire bundled PUBLIC KB (used by KbSeeder on first run / version bump). */
    suspend fun replacePublicKb(chunks: List<KbChunkEntity>) {
        dao.deleteBySensitivity(Sensitivity.PUBLIC.wire)
        dao.insertAll(chunks)
        reload()
    }

    /** Upserts PERSONAL chunks indexed from the user's own data (on-device only). */
    suspend fun upsertUserChunks(chunks: List<KbChunkEntity>) {
        if (chunks.isEmpty()) return
        dao.insertAll(chunks)
        reload()
    }

    suspend fun deleteBySource(source: String) {
        dao.deleteBySource(source)
        reload()
    }

    private companion object {
        const val TAG = "KbRepository"
    }
}
