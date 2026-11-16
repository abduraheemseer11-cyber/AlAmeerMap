package com.network.scanner.pro.data.local.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.network.scanner.pro.data.local.entities.SpeedTestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {

    @Query("SELECT * FROM speed_tests ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SpeedTestEntity>>

    @Query("SELECT * FROM speed_tests ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): SpeedTestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpeedTestEntity): Long

    @Delete
    suspend fun delete(entity: SpeedTestEntity)

    @Query("DELETE FROM speed_tests")
    suspend fun deleteAll()
}
