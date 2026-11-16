package com.network.scanner.pro.domain.models

/** One access point returned by a Wi-Fi scan. */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    /** Signal strength in dBm. */
    val level: Int,
    /** Centre frequency in MHz. */
    val frequency: Int,
    /** Wi-Fi channel number derived from [frequency]. */
    val channel: Int,
    /** "WPA3", "WPA2", "WPA", "WEP" or "Open". */
    val security: String
) {
    val is24Ghz: Boolean get() = frequency in 2400..2500
}
