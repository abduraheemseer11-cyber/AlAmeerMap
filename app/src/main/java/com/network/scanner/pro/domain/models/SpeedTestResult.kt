package com.network.scanner.pro.domain.models

/** Phase of a running speed test, used for live progress callbacks. */
enum class SpeedPhase { PING, DOWNLOAD, UPLOAD }

data class SpeedTestResult(
    val id: Long = 0,
    val timestamp: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Long
)
