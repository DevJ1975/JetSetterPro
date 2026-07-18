package com.jetsetter.pro.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One retrievable knowledge chunk for IRIS's on-device RAG layer.
 *
 * Rows come from two places:
 *  - the bundled, pre-embedded general-travel KB, seeded once from assets (sensitivity = `PUBLIC`);
 *  - the user's own data indexed on-device — trips/expenses (sensitivity = `PERSONAL`).
 *
 * [embedding] is the chunk's vector packed as little-endian float32 bytes (see [KbConverters]). Every
 * row stamps the [modelId] + [dim] that produced it so retrieval can refuse to mix vectors from
 * incompatible embedding models — a different model means a different vector space.
 *
 * PRIVACY: `PERSONAL` rows are reachable only on the on-device IRIS tier and must never be loaded
 * into a Claude/cloud request. The tier→sensitivity gate lives in `core/rag/ContextAssembler`.
 *
 * (ByteArray fields intentionally skip data-class equals/hashCode — Room never relies on them.)
 */
@Entity(tableName = "kb_chunks")
data class KbChunkEntity(
    @PrimaryKey val id: String,
    val text: String,
    val source: String,
    val sourceType: String,   // KB | USER | TOOL
    val sensitivity: String,  // PUBLIC | PERSONAL
    val embedding: ByteArray,
    val dim: Int,
    val modelId: String,
    val metadata: String,     // JSON blob (tags, region codes, season, lastReviewed, …)
    val updatedAt: Long,
)
