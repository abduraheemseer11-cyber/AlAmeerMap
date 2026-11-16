package com.network.scanner.pro.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.network.scanner.pro.data.local.entities.DeviceEntity
import com.network.scanner.pro.data.local.entities.PresenceEntity
import com.network.scanner.pro.data.local.entities.WifiNetworkEntity
import kotlinx.coroutines.flow.Flow

/** Devices, presence profiles and Wi-Fi scan results (everything the scanner module stores). */
@Dao
interface DeviceDao {

    // ---- devices ---------------------------------------------------------
    @Query("SELECT * FROM devices ORDER BY isOnline DESC, ip ASC")
    fun observeDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    suspend fun getAllDevices(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getDevice(id: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevices(devices: List<DeviceEntity>)

    @Query("UPDATE devices SET isBlocked = :blocked WHERE id = :id")
    suspend fun setBlocked(id: String, blocked: Boolean)

    @Query("UPDATE devices SET isPaused = :paused WHERE id = :id")
    suspend fun setPaused(id: String, paused: Boolean)

    @Query("UPDATE devices SET isTrusted = :trusted WHERE id = :id")
    suspend fun setTrusted(id: String, trusted: Boolean)

    @Query("UPDATE devices SET isPresenceTracked = :tracked WHERE id = :id")
    suspend fun setPresenceTracked(id: String, tracked: Boolean)

    @Query("UPDATE devices SET isOnline = :online, lastSeen = :lastSeen WHERE id = :id")
    suspend fun setOnline(id: String, online: Boolean, lastSeen: Long)

    @Query("SELECT * FROM devices WHERE isBlocked = 1 OR isPaused = 1")
    suspend fun getRestrictedDevices(): List<DeviceEntity>

    // ---- presence profiles ----------------------------------------------
    @Query("SELECT * FROM presence_profiles")
    suspend fun getPresence(): List<PresenceEntity>

    @Query("SELECT * FROM presence_profiles")
    fun observePresence(): Flow<List<PresenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPresence(profile: PresenceEntity)

    @Query("DELETE FROM presence_profiles WHERE deviceId = :deviceId")
    suspend fun deletePresence(deviceId: String)

    @Query("SELECT COUNT(*) FROM presence_profiles")
    suspend fun countPresence(): Int

    // ---- Wi-Fi scan results ---------------------------------------------
    @Query("SELECT * FROM wifi_networks ORDER BY level DESC")
    fun observeWifi(): Flow<List<WifiNetworkEntity>>

    @Query("DELETE FROM wifi_networks")
    suspend fun clearWifi()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWifi(networks: List<WifiNetworkEntity>)
}
