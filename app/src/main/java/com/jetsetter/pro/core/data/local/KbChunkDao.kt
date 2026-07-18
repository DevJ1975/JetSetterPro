package com.jetsetter.pro.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the on-device RAG knowledge base ([KbChunkEntity]). Reads are non-Flow lists: the
 * retrieval cache is loaded once into memory (see `core/data/repository/KbRepository`), so there is
 * no need to observe row-level changes.
 */
@Dao
interface KbChunkDao {

    @Query("SELECT COUNT(*) FROM kb_chunks")
    suspend fun count(): Int

    @Query("SELECT * FROM kb_chunks")
    suspend fun getAll(): List<KbChunkEntity>

    @Query("SELECT * FROM kb_chunks WHERE sensitivity = :sensitivity")
    suspend fun getBySensitivity(sensitivity: String): List<KbChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<KbChunkEntity>)

    @Query("DELETE FROM kb_chunks WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM kb_chunks WHERE source LIKE :prefix || '%'")
    suspend fun deleteBySourcePrefix(prefix: String)

    @Query("DELETE FROM kb_chunks WHERE sensitivity = :sensitivity")
    suspend fun deleteBySensitivity(sensitivity: String)

    @Query("DELETE FROM kb_chunks")
    suspend fun clear()
}
