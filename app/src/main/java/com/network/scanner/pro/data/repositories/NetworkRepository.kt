package com.network.scanner.pro.data.repositories

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.network.scanner.pro.data.local.daos.DeviceDao
import com.network.scanner.pro.data.local.entities.DeviceEntity
import com.network.scanner.pro.data.local.entities.PresenceEntity
import com.network.scanner.pro.data.local.entities.toDomain
import com.network.scanner.pro.data.local.entities.toEntity
import com.network.scanner.pro.domain.models.Device
import com.network.scanner.pro.domain.models.DeviceRole
import com.network.scanner.pro.domain.models.WifiNetwork
import com.network.scanner.pro.util.AppPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * All real network logic of the scanner module:
 *  - current Wi-Fi info (SSID, IP, gateway, signal, security)
 *  - ping sweep of the /24 subnet + ARP table + OUI vendor lookup
 *  - ping, port scan, traceroute, DNS lookup, Wi-Fi scan
 *  - persistence of devices and their flags (blocked, paused, presence, trusted)
 */
@Singleton
class NetworkRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val deviceDao: DeviceDao,
    private val prefs: AppPrefs
) {
    private val appContext: Context = context.applicationContext

    private val wifiManager: WifiManager
        get() = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    data class WifiSnapshot(
        val ssid: String,
        val bssid: String,
        val ip: String,
        val gateway: String,
        val rssi: Int,
        val signalPercent: Int,
        val linkSpeedMbps: Int,
        /** "WPA3", "WPA2", "WPA", "WEP", "Open" or "Unknown". */
        val security: String
    )

    data class PortResult(val port: Int, val service: String, val risky: Boolean)

    // =====================================================================
    // Wi-Fi information
    // =====================================================================

    @Suppress("DEPRECATION")
    fun currentWifi(): WifiSnapshot? {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val linkProperties = cm.getLinkProperties(network) ?: return null

        val ip = linkProperties.linkAddresses
            .firstOrNull { it.address is Inet4Address }?.address?.hostAddress ?: return null

        var gateway = linkProperties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress.orEmpty()

        val manager = wifiManager
        val info = manager.connectionInfo
        if (gateway.isBlank()) {
            val raw = manager.dhcpInfo?.gateway ?: 0
            if (raw != 0) gateway = intToIp(raw)
        }

        val rawSsid = info?.ssid.orEmpty().trim('"')
        val ssid = if (rawSsid == "<unknown ssid>") "" else rawSsid
        val rssi = info?.rssi ?: -100
        val bssid = info?.bssid.orEmpty()

        return WifiSnapshot(
            ssid = ssid,
            bssid = bssid,
            ip = ip,
            gateway = gateway,
            rssi = rssi,
            signalPercent = ((rssi + 100) * 2).coerceIn(0, 100),
            linkSpeedMbps = (info?.linkSpeed ?: 0).coerceAtLeast(0),
            security = securityOfConnected(bssid)
        )
    }

    /** Looks the connected BSSID up in the scan results to learn its security type. */
    private fun securityOfConnected(bssid: String): String {
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return SECURITY_UNKNOWN
        return try {
            @Suppress("DEPRECATION")
            val match = wifiManager.scanResults.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
            if (match == null) SECURITY_UNKNOWN else securityFromCapabilities(match.capabilities)
        } catch (e: SecurityException) {
            SECURITY_UNKNOWN
        }
    }

    // =====================================================================
    // Device discovery
    // =====================================================================

    fun observeDevices(): Flow<List<Device>> =
        deviceDao.observeDevices().map { rows -> rows.map { it.toDomain() } }

    suspend fun getDevices(): List<Device> =
        deviceDao.getAllDevices().map { it.toDomain() }

    suspend fun knownDeviceIds(): Set<String> =
        deviceDao.getAllDevices().map { it.id }.toSet()

    /**
     * Ping sweep of the local /24 (derived from the gateway), then ARP table
     * lookup and vendor resolution. Results are merged into Room, preserving
     * user flags (blocked, paused, presence, trusted). The very first scan
     * marks every device as trusted (baseline); later newcomers are untrusted.
     */
    suspend fun scan(): List<Device> = withContext(Dispatchers.IO) {
        val wifi = currentWifi() ?: return@withContext emptyList()
        val subnet = wifi.gateway.ifBlank { wifi.ip }.substringBeforeLast('.')

        val aliveIps: List<String> = coroutineScope {
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    val ip = "$subnet.$host"
                    if (ip == wifi.ip || isHostAlive(ip)) ip else null
                }
            }.awaitAll().filterNotNull()
        }

        val arp = readArpTable()
        val now = System.currentTimeMillis()
        val existing = deviceDao.getAllDevices()
        val baseline = existing.isEmpty()
        val byId = existing.associateBy { it.id }
        val byIp = existing.associateBy { it.ip }

        val names: Map<String, String> = coroutineScope {
            aliveIps.map { ip ->
                async(Dispatchers.IO) {
                    val name = if (ip == wifi.ip) Build.MODEL.orEmpty() else resolveHostName(ip).orEmpty()
                    ip to name
                }
            }.awaitAll().toMap()
        }

        val foundIds = HashSet<String>()
        val updated = aliveIps.map { ip ->
            val mac = arp[ip].orEmpty()
            val key = mac.ifEmpty { ip }
            val previous = byId[key] ?: byIp[ip]
            val id = previous?.id ?: key
            foundIds.add(id)

            val role = when (ip) {
                wifi.ip -> DeviceRole.SELF
                wifi.gateway -> DeviceRole.ROUTER
                else -> DeviceRole.DEVICE
            }
            val vendor = when {
                mac.isEmpty() -> previous?.vendor.orEmpty()
                isRandomMac(mac) -> RANDOM_MAC_LABEL
                else -> lookupVendor(mac)
            }
            DeviceEntity(
                id = id,
                ip = ip,
                mac = mac.ifEmpty { previous?.mac.orEmpty() },
                name = names[ip].orEmpty().ifBlank { previous?.name.orEmpty() },
                vendor = vendor,
                role = role,
                isOnline = true,
                lastSeen = now,
                isBlocked = previous?.isBlocked ?: false,
                isPaused = previous?.isPaused ?: false,
                isPresenceTracked = previous?.isPresenceTracked ?: false,
                isTrusted = previous?.isTrusted ?: (baseline || role != DeviceRole.DEVICE)
            )
        }
        val offline = existing.filter { it.id !in foundIds }.map { it.copy(isOnline = false) }

        deviceDao.upsertDevices(updated + offline)
        updated.map { it.toDomain() }
    }

    /** True if the host answers an ICMP/echo probe or accepts/refuses a TCP connection. */
    suspend fun isHostAlive(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (InetAddress.getByName(ip).isReachable(1000)) return@withContext true
        } catch (e: Exception) {
            // fall through to the TCP probes
        }
        for (port in PROBE_PORTS) {
            try {
                Socket().use { it.connect(InetSocketAddress(ip, port), 150) }
                return@withContext true
            } catch (e: java.net.ConnectException) {
                // "Connection refused" proves the host is up.
                if (e.message?.contains("refused", ignoreCase = true) == true) return@withContext true
            } catch (e: Exception) {
                // timeout / unreachable: try next port
            }
        }
        false
    }

    /** Round-trip time in ms, or null if the host does not respond. */
    suspend fun ping(host: String, timeoutMs: Int = 1500): Long? = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(host)
            val start = System.nanoTime()
            if (address.isReachable(timeoutMs)) {
                (System.nanoTime() - start) / 1_000_000
            } else {
                tcpRoundTrip(host)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tcpRoundTrip(host: String): Long? {
        for (port in listOf(80, 443, 22, 445)) {
            val start = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), 800) }
                return (System.nanoTime() - start) / 1_000_000
            } catch (e: java.net.ConnectException) {
                if (e.message?.contains("refused", ignoreCase = true) == true) {
                    return (System.nanoTime() - start) / 1_000_000
                }
            } catch (e: Exception) {
                // try next
            }
        }
        return null
    }

    /**
     * Reads the IP -> MAC table. Android 10+ blocks /proc/net/arp for normal
     * apps (the file is then empty/unreadable), so `ip neigh` is tried as well.
     * Both usually work only on rooted devices; MACs then stay empty.
     */
    private suspend fun readArpTable(): Map<String, String> = withContext(Dispatchers.IO) {
        val table = HashMap<String, String>()
        val ipRegex = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        val macRegex = Regex("""([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}""")
        try {
            File("/proc/net/arp").readLines().drop(1).forEach { line ->
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 4 && ipRegex.matches(cols[0]) && macRegex.matches(cols[3]) &&
                    cols[3] != "00:00:00:00:00:00"
                ) {
                    table[cols[0]] = cols[3].uppercase()
                }
            }
        } catch (e: Exception) {
            // unreadable on Android 10+
        }
        if (table.isEmpty()) {
            try {
                val process = ProcessBuilder("ip", "neigh").redirectErrorStream(true).start()
                val lines = process.inputStream.bufferedReader().readLines()
                process.waitFor()
                for (line in lines) {
                    val cols = line.trim().split(Regex("\\s+"))
                    val i = cols.indexOf("lladdr")
                    if (i >= 0 && i + 1 < cols.size && ipRegex.matches(cols[0]) &&
                        macRegex.matches(cols[i + 1])
                    ) {
                        table[cols[0]] = cols[i + 1].uppercase()
                    }
                }
            } catch (e: Exception) {
                // not available
            }
        }
        table
    }

    /** Reverse DNS with a hard 1.5 s limit (the lookup itself cannot be interrupted). */
    private suspend fun resolveHostName(ip: String): String? = withTimeoutOrNull(1500) {
        suspendCancellableCoroutine<String?> { cont ->
            thread(isDaemon = true) {
                val name = try {
                    InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
                } catch (e: Exception) {
                    null
                }
                if (cont.isActive) cont.resume(name)
            }
        }
    }

    // =====================================================================
    // Device flags (blocked / paused / trusted / presence)
    // =====================================================================

    suspend fun setBlocked(id: String, blocked: Boolean) = deviceDao.setBlocked(id, blocked)

    suspend fun setPaused(id: String, paused: Boolean) = deviceDao.setPaused(id, paused)

    suspend fun setTrusted(id: String, trusted: Boolean) = deviceDao.setTrusted(id, trusted)

    suspend fun setPresence(device: Device, tracked: Boolean) {
        deviceDao.setPresenceTracked(device.id, tracked)
        if (tracked) {
            deviceDao.upsertPresence(
                PresenceEntity(
                    deviceId = device.id,
                    ip = device.ip,
                    displayName = device.displayName,
                    role = device.role,
                    notifyOnOnline = true,
                    lastKnownOnline = device.isOnline,
                    lastChecked = System.currentTimeMillis()
                )
            )
        } else {
            deviceDao.deletePresence(device.id)
        }
    }

    suspend fun hasPresenceTracked(): Boolean = deviceDao.countPresence() > 0

    /** Stores the IPs of blocked/paused devices for the VPN service and returns them. */
    suspend fun syncRestrictedIps(): List<String> {
        val ips = deviceDao.getRestrictedDevices().map { it.ip }.distinct()
        prefs.blockedIps = ips.toSet()
        return ips
    }

    // =====================================================================
    // Tools: ports, ping, traceroute, DNS
    // =====================================================================

    suspend fun scanPorts(
        host: String,
        ports: Map<Int, String> = COMMON_PORTS,
        timeoutMs: Int = 600
    ): List<PortResult> = coroutineScope {
        ports.map { (port, service) ->
            async(Dispatchers.IO) {
                if (isPortOpen(host, port, timeoutMs)) {
                    PortResult(port, service, port in RISKY_PORTS)
                } else {
                    null
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.port }
    }

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
    } catch (e: Exception) {
        false
    }

    fun pingStream(host: String, count: Int = 4): Flow<String> = flow {
        val address = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            emit("ping: cannot resolve $host")
            return@flow
        }
        emit("PING $host (${address.hostAddress})")
        val times = mutableListOf<Long>()
        for (seq in 1..count) {
            val start = System.nanoTime()
            val ok = try { address.isReachable(1500) } catch (e: Exception) { false }
            val ms = (System.nanoTime() - start) / 1_000_000
            if (ok) {
                times.add(ms)
                emit("reply from ${address.hostAddress}: seq=$seq time=$ms ms")
            } else {
                emit("seq=$seq request timed out")
            }
            delay(400)
        }
        val avg = if (times.isEmpty()) 0 else times.sum() / times.size
        emit("--- $host: ${times.size}/$count received, avg $avg ms")
    }.flowOn(Dispatchers.IO)

    fun tracerouteStream(host: String): Flow<String> = flow {
        val address = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            emit("traceroute: cannot resolve $host")
            return@flow
        }
        val target = address.hostAddress.orEmpty()
        emit("traceroute to $host ($target), 20 hops max")
        val hopRegex = Regex("(?i)from\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)")
        for (ttl in 1..20) {
            val output = pingWithTtl(target, ttl)
            val hop = hopRegex.find(output)?.groupValues?.get(1)
            emit(if (hop == null) "$ttl  * * *" else "$ttl  $hop")
            if (output.contains("bytes from", ignoreCase = true)) break
        }
        emit("done")
    }.flowOn(Dispatchers.IO)

    private fun pingWithTtl(ip: String, ttl: Int): String = try {
        val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", "-t", ttl.toString(), ip)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        text
    } catch (e: Exception) {
        ""
    }

    suspend fun dnsLookup(host: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val lines = mutableListOf("Query: $host")
            InetAddress.getAllByName(host).forEach {
                val type = if (it is Inet4Address) "A   " else "AAAA"
                lines.add("$type ${it.hostAddress}")
            }
            if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
                lines.add("PTR  ${InetAddress.getByName(host).canonicalHostName}")
            }
            lines
        } catch (e: Exception) {
            listOf("Lookup failed: ${e.message}")
        }
    }

    // =====================================================================
    // Wi-Fi analyzer
    // =====================================================================

    /** Triggers a scan (throttled by Android) and returns the access points found. */
    @Suppress("DEPRECATION")
    suspend fun scanWifi(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        val manager = wifiManager
        try {
            manager.startScan()
            delay(2500)
            val now = System.currentTimeMillis()
            val networks = manager.scanResults
                .map { it.toWifiNetwork() }
                .distinctBy { it.bssid }
                .sortedByDescending { it.level }
            deviceDao.clearWifi()
            deviceDao.upsertWifi(networks.map { it.toEntity(now) })
            networks
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun ScanResult.toWifiNetwork(): WifiNetwork = WifiNetwork(
        ssid = SSID.orEmpty(),
        bssid = BSSID.orEmpty(),
        level = level,
        frequency = frequency,
        channel = channelFromFrequency(frequency),
        security = securityFromCapabilities(capabilities.orEmpty())
    )

    // =====================================================================
    // helpers
    // =====================================================================

    private fun intToIp(value: Int): String =
        "${value and 0xFF}.${(value shr 8) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 24) and 0xFF}"

    private fun isRandomMac(mac: String): Boolean =
        mac.length >= 2 && ((mac.substring(0, 2).toIntOrNull(16) ?: 0) and 2) != 0

    private fun lookupVendor(mac: String): String =
        OUI_VENDORS[mac.uppercase().take(8)].orEmpty()

    companion object {
        const val SECURITY_UNKNOWN = "Unknown"
        const val RANDOM_MAC_LABEL = "Private (random MAC)"

        private val PROBE_PORTS = intArrayOf(80, 443, 445, 22, 62078, 8080)

        val COMMON_PORTS: Map<Int, String> = mapOf(
            21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
            80 to "HTTP", 110 to "POP3", 139 to "NetBIOS", 143 to "IMAP", 443 to "HTTPS",
            445 to "SMB", 554 to "RTSP", 1900 to "UPnP", 3389 to "RDP", 5900 to "VNC",
            8000 to "HTTP-Alt", 8080 to "HTTP-Proxy", 8443 to "HTTPS-Alt",
            8554 to "RTSP-Alt", 37777 to "Dahua DVR"
        )

        /** Ports that should normally not be reachable on a home/office LAN device. */
        val RISKY_PORTS: Set<Int> = setOf(21, 23, 139, 445, 3389, 5900)

        fun channelFromFrequency(freq: Int): Int = when (freq) {
            2484 -> 14
            in 2412..2472 -> (freq - 2407) / 5
            in 5160..5895 -> (freq - 5000) / 5
            in 5955..7115 -> (freq - 5950) / 5
            else -> 0
        }

        fun securityFromCapabilities(capabilities: String): String {
            val c = capabilities.uppercase()
            return when {
                c.contains("SAE") || c.contains("WPA3") -> "WPA3"
                c.contains("WPA2") || c.contains("RSN") -> "WPA2"
                c.contains("WPA") -> "WPA"
                c.contains("WEP") -> "WEP"
                else -> "Open"
            }
        }

        /** Compact built-in OUI table (first 3 bytes of the MAC -> manufacturer). */
        private val OUI_VENDORS: Map<String, String> = mapOf(
            // Apple
            "00:03:93" to "Apple", "00:0A:95" to "Apple", "00:1B:63" to "Apple",
            "00:1E:C2" to "Apple", "00:25:00" to "Apple", "00:26:BB" to "Apple",
            "3C:07:54" to "Apple", "F0:18:98" to "Apple", "A4:5E:60" to "Apple",
            // Samsung
            "00:12:47" to "Samsung", "00:15:99" to "Samsung", "00:16:32" to "Samsung",
            "00:17:C9" to "Samsung", "00:1D:25" to "Samsung", "8C:77:12" to "Samsung",
            "BC:20:A4" to "Samsung",
            // Xiaomi
            "28:6C:07" to "Xiaomi", "34:CE:00" to "Xiaomi", "50:64:2B" to "Xiaomi",
            "64:09:80" to "Xiaomi", "78:11:DC" to "Xiaomi", "F8:A4:5F" to "Xiaomi",
            // Huawei
            "00:E0:FC" to "Huawei", "00:18:82" to "Huawei", "00:25:9E" to "Huawei",
            "00:9A:CD" to "Huawei",
            // TP-Link
            "50:C7:BF" to "TP-Link", "14:CC:20" to "TP-Link", "60:E3:27" to "TP-Link",
            "EC:08:6B" to "TP-Link", "A0:F3:C1" to "TP-Link", "F4:F2:6D" to "TP-Link",
            "98:DA:C4" to "TP-Link",
            // D-Link
            "00:05:5D" to "D-Link", "00:13:46" to "D-Link", "00:15:E9" to "D-Link",
            "00:17:9A" to "D-Link", "00:1B:11" to "D-Link",
            // Netgear
            "00:09:5B" to "Netgear", "00:14:6C" to "Netgear", "00:1B:2F" to "Netgear",
            "00:24:B2" to "Netgear",
            // ZTE
            "00:15:EB" to "ZTE", "00:19:C6" to "ZTE", "00:1E:73" to "ZTE", "00:25:12" to "ZTE",
            // Network gear
            "00:00:0C" to "Cisco",
            "00:15:6D" to "Ubiquiti", "00:27:22" to "Ubiquiti", "04:18:D6" to "Ubiquiti",
            "24:A4:3C" to "Ubiquiti", "44:D9:E7" to "Ubiquiti", "78:8A:20" to "Ubiquiti",
            "00:0C:42" to "MikroTik", "4C:5E:0C" to "MikroTik", "64:D1:54" to "MikroTik",
            "6C:3B:6B" to "MikroTik", "B8:69:F4" to "MikroTik", "CC:2D:E0" to "MikroTik",
            // Amazon
            "34:D2:70" to "Amazon", "40:B4:CD" to "Amazon", "44:65:0D" to "Amazon",
            "68:37:E9" to "Amazon", "74:C2:46" to "Amazon", "F0:27:2D" to "Amazon",
            // Espressif (ESP32/ESP8266 IoT)
            "24:0A:C4" to "Espressif", "24:6F:28" to "Espressif", "30:AE:A4" to "Espressif",
            "5C:CF:7F" to "Espressif", "84:0D:8E" to "Espressif", "A4:CF:12" to "Espressif",
            "EC:FA:BC" to "Espressif",
            // Raspberry Pi
            "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi", "E4:5F:01" to "Raspberry Pi",
            // IP cameras
            "4C:BD:8F" to "Hikvision", "54:C4:15" to "Hikvision", "44:19:B6" to "Hikvision",
            "C0:56:E3" to "Hikvision", "BC:AD:28" to "Hikvision", "28:57:BE" to "Hikvision",
            "3C:EF:8C" to "Dahua", "4C:11:BF" to "Dahua", "90:02:A9" to "Dahua",
            // Virtualisation
            "00:50:56" to "VMware", "00:0C:29" to "VMware", "08:00:27" to "VirtualBox",
            "00:15:5D" to "Microsoft (Hyper-V)",
            // Misc
            "00:17:88" to "Philips Hue", "00:E0:4C" to "Realtek", "00:02:B3" to "Intel",
            "00:13:E8" to "Intel", "00:16:6F" to "Intel", "00:01:4A" to "Sony",
            "00:13:A9" to "Sony", "00:0E:58" to "Sonos", "34:7E:5C" to "Sonos",
            "00:09:BF" to "Nintendo", "00:16:56" to "Nintendo", "00:80:77" to "Brother",
            "00:00:85" to "Canon", "00:00:48" to "Epson", "94:65:2D" to "OnePlus",
            "C8:3A:35" to "Tenda"
        )
    }
}
