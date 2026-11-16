package com.network.scanner.pro.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.network.scanner.pro.domain.models.WifiNetwork

@Entity(tableName = "wifi_networks")
data class WifiNetworkEntity(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val level: Int,
    val frequency: Int,
    val channel: Int,
    val security: String,
    val scannedAt: Long
)

fun WifiNetworkEntity.toDomain(): WifiNetwork = WifiNetwork(
    ssid = ssid, bssid = bssid, level = level, frequency = frequency,
    channel = channel, security = security
)

fun WifiNetwork.toEntity(scannedAt: Long): WifiNetworkEntity = WifiNetworkEntity(
    bssid = bssid, ssid = ssid, level = level, frequency = frequency,
    channel = channel, security = security, scannedAt = scannedAt
)
