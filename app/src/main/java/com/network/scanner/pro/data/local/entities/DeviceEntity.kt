package com.network.scanner.pro.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.network.scanner.pro.domain.models.Device

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val ip: String,
    val mac: String,
    val name: String,
    val vendor: String,
    val role: String,
    val isOnline: Boolean,
    val lastSeen: Long,
    val isBlocked: Boolean = false,
    val isPaused: Boolean = false,
    val isPresenceTracked: Boolean = false,
    val isTrusted: Boolean = false
)

fun DeviceEntity.toDomain(): Device = Device(
    id = id, ip = ip, mac = mac, name = name, vendor = vendor, role = role,
    isOnline = isOnline, lastSeen = lastSeen, isBlocked = isBlocked, isPaused = isPaused,
    isPresenceTracked = isPresenceTracked, isTrusted = isTrusted
)

fun Device.toEntity(): DeviceEntity = DeviceEntity(
    id = id, ip = ip, mac = mac, name = name, vendor = vendor, role = role,
    isOnline = isOnline, lastSeen = lastSeen, isBlocked = isBlocked, isPaused = isPaused,
    isPresenceTracked = isPresenceTracked, isTrusted = isTrusted
)
