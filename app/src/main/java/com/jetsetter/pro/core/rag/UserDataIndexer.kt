package com.jetsetter.pro.core.rag

import com.jetsetter.pro.core.data.local.KbChunkEntity
import com.jetsetter.pro.core.data.local.KbConverters
import com.jetsetter.pro.core.data.repository.KbRepository
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indexes the user's own trips and expenses into the KB as `PERSONAL` chunks so the on-device IRIS
 * tier can retrieve them as grounding. This is the RAG half of "IRIS learns the user" — combined
 * with [com.jetsetter.pro.core.intelligence.TravelProfileStore] signal learning. No model is trained.
 *
 * On most devices the embedder is unavailable (no on-device model), so [reindex] is a cheap no-op
 * and personal data is never embedded. PERSONAL chunks are reachable only on the ON_DEVICE tier
 * (see [ContextAssembler]); they never reach Claude.
 */
@Singleton
class UserDataIndexer @Inject constructor(
    private val embedder: OnDeviceEmbedder,
    private val kb: KbRepository,
) {
    @Volatile private var lastSignature: String = ""

    suspend fun reindex(trips: List<Trip>, expenses: List<Expense>) {
        if (!embedder.isReady()) return
        val signature = buildSignature(trips, expenses)
        if (signature == lastSignature) return

        val docs = buildList {
            trips.forEach { t ->
                val items = t.items.joinToString("; ") { "${it.type.label}: ${it.title}" }
                add(
                    "user/trip:${t.id}" to
                        "Trip: ${t.name} to ${t.destination} (${t.startDate}–${t.endDate})." +
                        (if (items.isNotBlank()) " Itinerary: $items." else ""),
                )
            }
            expenses.forEach { e ->
                add(
                    "user/expense:${e.id}" to
                        "Expense: ${e.merchant}, ${"%.2f".format(e.amount)} ${e.currency} " +
                        "(${e.category.label}) on ${e.date}.",
                )
            }
        }

        val entities = docs.mapNotNull { (source, text) ->
            val vec = embedder.embed(text) ?: return@mapNotNull null
            KbChunkEntity(
                id = source,
                text = text,
                source = source,
                sourceType = "USER",
                sensitivity = Sensitivity.PERSONAL.wire,
                embedding = KbConverters.pack(vec),
                dim = vec.size,
                modelId = embedder.modelId,
                metadata = "{}",
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (entities.isNotEmpty()) kb.upsertUserChunks(entities)
        lastSignature = signature
    }

    private fun buildSignature(trips: List<Trip>, expenses: List<Expense>): String =
        (trips.map { it.id + it.items.size } + expenses.map { it.id }).sorted().joinToString(",")
}
