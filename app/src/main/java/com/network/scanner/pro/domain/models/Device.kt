package com.network.scanner.pro.domain.models

/** Role of a discovered host on the local network. */
object DeviceRole {
    const val ROUTER = "router"
    const val SELF = "self"
    const val DEVICE = "device"
}

/** A host discovered by the network scanner. */
data class Device(
    /** MAC address when known, otherwise the IP address. Stable primary key. */
    val id: String,
    val ip: String,
    /** Empty when Android does not expose the ARP table (Android 10+ without root). */
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
) {
    /** Best human readable label. */
    val displayName: String
        get() = name.ifBlank { vendor.ifBlank { ip } }

    /** Locally administered bit set => randomised (private) MAC address. */
    val hasRandomMac: Boolean
        get() = mac.length >= 2 && (mac.substring(0, 2).toIntOrNull(16)?.and(2) ?: 0) != 0
}
