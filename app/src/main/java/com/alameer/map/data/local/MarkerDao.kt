package com.alameer.map.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Query("SELECT * FROM markers WHERE userId = :userId AND deleted = 0 ORDER BY networkNumber, clientName")
    fun observe(userId: String): Flow<List<MarkerEntity>>

    @Query("SELECT * FROM markers WHERE userId = :userId AND deleted = 0")
    suspend fun all(userId: String): List<MarkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MarkerEntity>)

    @Query("SELECT * FROM markers WHERE userId = :userId AND pendingSync = 1")
    suspend fun pending(userId: String): List<MarkerEntity>

    @Query("SELECT id FROM markers WHERE userId = :userId AND pendingSync = 0")
    suspend fun syncedIds(userId: String): List<String>

    @Query("DELETE FROM markers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Only clears the flag if the row was not edited again while the request was in flight. */
    @Query("UPDATE markers SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}
