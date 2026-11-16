package com.network.scanner.pro.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A device the user wants to monitor ("Digital Home Presence").
 * [deviceId] equals DeviceEntity.id. [lastKnownOnline] is the state seen by
 * the previous PresenceWorker run, used to detect OFFLINE -> ONLINE changes.
 */
@Entity(tableName = "presence_profiles")
data class PresenceEntity(
    @PrimaryKey val deviceId: String,
    val ip: String,
    val displayName: String,
    val role: String = "",
    val notifyOnOnline: Boolean = true,
    val lastKnownOnline: Boolean = false,
    val lastChecked: Long = 0L
)
