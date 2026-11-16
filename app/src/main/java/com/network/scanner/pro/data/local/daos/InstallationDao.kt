package com.network.scanner.pro.data.local.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.network.scanner.pro.data.local.entities.InstallationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallationDao {

    /** Everything the user should see on the map: rows not waiting to be deleted. */
    @Query("SELECT * FROM installations WHERE userId = :userId AND syncState != 2 ORDER BY installationDate DESC")
    fun observeForUser(userId: String): Flow<List<InstallationEntity>>

    @Query("SELECT * FROM installations WHERE userId = :userId AND syncState != 2")
    suspend fun getAllForUser(userId: String): List<InstallationEntity>

    @Query("SELECT * FROM installations WHERE id = :id")
    suspend fun getById(id: String): InstallationEntity?

    @Query("SELECT * FROM installations WHERE userId = :userId AND syncState = 1")
    suspend fun getPendingUpserts(userId: String): List<InstallationEntity>

    @Query("SELECT * FROM installations WHERE userId = :userId AND syncState = 2")
    suspend fun getPendingDeletes(userId: String): List<InstallationEntity>

    @Query("SELECT * FROM installations WHERE userId = :userId AND syncState = 0")
    suspend fun getSynced(userId: String): List<InstallationEntity>

    @Query("SELECT id FROM installations WHERE userId = :userId AND syncState != 0")
    suspend fun getPendingIds(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InstallationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InstallationEntity>)

    @Query("UPDATE installations SET syncState = 2 WHERE id = :id")
    suspend fun markPendingDelete(id: String)

    @Delete
    suspend fun delete(entity: InstallationEntity)

    @Query("DELETE FROM installations WHERE id = :id")
    suspend fun deleteById(id: String)
}
