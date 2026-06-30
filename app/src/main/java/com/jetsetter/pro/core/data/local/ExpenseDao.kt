package com.jetsetter.pro.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT id FROM expenses")
    suspend fun getAllIds(): List<String>

    /**
     * Deletes the expenses with the given ids. Call in chunks (see ExpenseRepository.SYNC_DELETE_CHUNK)
     * so the expanded `IN (:ids)` never exceeds SQLite's bound-variable limit.
     */
    @Query("DELETE FROM expenses WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}
