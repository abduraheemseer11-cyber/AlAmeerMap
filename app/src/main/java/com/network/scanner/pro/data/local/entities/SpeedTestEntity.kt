package com.network.scanner.pro.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.network.scanner.pro.domain.models.SpeedTestResult

@Entity(tableName = "speed_tests")
data class SpeedTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Long
)

fun SpeedTestEntity.toDomain(): SpeedTestResult = SpeedTestResult(
    id = id, timestamp = timestamp, downloadMbps = downloadMbps,
    uploadMbps = uploadMbps, pingMs = pingMs
)

fun SpeedTestResult.toEntity(): SpeedTestEntity = SpeedTestEntity(
    id = id, timestamp = timestamp, downloadMbps = downloadMbps,
    uploadMbps = uploadMbps, pingMs = pingMs
)
