package com.jetsetter.pro.core.kb

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.jetsetter.pro.core.data.local.KbChunkEntity
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.repository.KbRepository
import com.jetsetter.pro.core.rag.OnDeviceEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the bundled, pre-embedded general-travel KB into Room on first run (and re-seeds on a
 * [KbVersion] bump). The artifact is a SQLite DB in `assets/` whose `kb_chunks` table matches
 * [KbChunkEntity] one-to-one and whose embeddings were produced by `tools/iris-kb` using the SAME
 * model as the on-device [OnDeviceEmbedder].
 *
 * Seeding is best-effort and fails safe: a missing artifact, a model-id mismatch, or any read error
 * leaves the KB empty and IRIS simply runs un-grounded — never a crash. PERSONAL chunks (the user's
 * own indexed data) live in the same table but are untouched here.
 */
@Singleton
class KbSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kbRepository: KbRepository,
    private val embedder: OnDeviceEmbedder,
    private val moduleState: ModuleStateStore,
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (moduleState.read(KbVersion.seedFlagKey) != null) {
            kbRepository.ensureLoaded()
            return@withContext
        }

        // Optional manifest gate — bail before copying the whole DB if the embedder won't match.
        readManifest()?.let { manifest ->
            val embedderId = manifest.optString("embedder_id")
            if (embedderId.isNotEmpty() && embedderId != embedder.modelId) {
                Log.w(TAG, "Bundled KB embedder '$embedderId' != on-device '${embedder.modelId}'; skipping seed.")
                return@withContext
            }
        }

        val chunks = runCatching { readChunksFromAsset() }.getOrElse {
            if (it is FileNotFoundException) {
                Log.i(TAG, "No bundled KB (${KbVersion.assetDb}); IRIS runs un-grounded.")
            } else {
                Log.w(TAG, "Failed to read bundled KB", it)
            }
            return@withContext
        }

        if (chunks.isEmpty()) return@withContext
        // replacePublicKb + KbRepository's parity filter drop any row from a stale embedding model.
        kbRepository.replacePublicKb(chunks)
        moduleState.save(KbVersion.seedFlagKey, "true")
        Log.i(TAG, "Seeded ${chunks.size} KB chunks (kb v${KbVersion.CURRENT}).")
    }

    private fun readManifest(): JSONObject? =
        runCatching {
            context.assets.open(KbVersion.assetManifest).use { JSONObject(it.readBytes().decodeToString()) }
        }.getOrNull()

    /** Copies the asset DB to a temp file, reads every `kb_chunks` row, then cleans up. */
    private fun readChunksFromAsset(): List<KbChunkEntity> {
        val temp = File(context.cacheDir, KbVersion.assetDb)
        context.assets.open(KbVersion.assetDb).use { input ->
            temp.outputStream().use { input.copyTo(it) }
        }
        return try {
            SQLiteDatabase.openDatabase(temp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT id, text, source, sourceType, sensitivity, embedding, dim, modelId, metadata, updatedAt FROM kb_chunks",
                    null,
                ).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                KbChunkEntity(
                                    id = c.getString(0),
                                    text = c.getString(1),
                                    source = c.getString(2),
                                    sourceType = c.getString(3),
                                    sensitivity = c.getString(4),
                                    embedding = c.getBlob(5),
                                    dim = c.getInt(6),
                                    modelId = c.getString(7),
                                    metadata = c.getString(8),
                                    updatedAt = c.getLong(9),
                                ),
                            )
                        }
                    }
                }
            }
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val TAG = "KbSeeder"
    }
}
