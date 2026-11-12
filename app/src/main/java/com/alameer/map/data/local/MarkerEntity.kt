package com.alameer.map.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "markers", indices = [Index("userId")])
data class MarkerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val modemNumber: String,
    val networkNumber: String,
    val modemType: String,
    val deviceIp: String,
    val username: String,
    /** AES-GCM ciphertext (see CryptoManager). Never plain text. */
    val passwordEnc: String,
    val clientName: String,
    val clientPhone: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Long,
    /** true = changed locally and not yet confirmed by Supabase */
    val pendingSync: Boolean = false,
    /** true = deleted locally, waiting to be deleted remotely */
    val deleted: Boolean = false,
)
