package com.network.scanner.pro.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.network.scanner.pro.MainActivity
import com.network.scanner.pro.R
import com.network.scanner.pro.util.AppPrefs
import com.network.scanner.pro.util.Notifications
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Local VPN used to block / pause devices ("black-hole").
 *
 * HOW IT WORKS: the VPN only routes the blocked IPs (/32 routes) into the
 * tunnel. Every packet this phone sends to such an IP lands in [runPacketLoop]
 * and is dropped; all other traffic bypasses the VPN and is not affected.
 * We deliberately do NOT add a 0.0.0.0/0 route: without a user-space TCP/IP
 * stack to forward allowed packets, that would cut the phone's own internet.
 *
 * LIMITATION: this filters traffic of THIS phone only. It cannot disconnect
 * another device from the router (that needs router-level control).
 */
class GuardVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var packetThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var blockedIps: Set<String> = emptySet()

    private val droppedPackets = AtomicLong(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // ACTION_START (or a system restart with a null intent).
        blockedIps = intent?.getStringArrayExtra(EXTRA_BLOCKED_IPS)?.toSet()
            ?: AppPrefs(applicationContext).blockedIps
        showForegroundNotification()
        restart()
        return START_STICKY
    }

    /** Re-creates the tunnel with the current blocked set. Stops the service when there is nothing to block. */
    fun restart() {
        stopVpn()
        val targets = blockedIps.filter { IPV4_REGEX.matches(it) }
        if (targets.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val builder = Builder()
            .setSession("NetGuard")
            .addAddress("10.0.0.2", 32)
            .setMtu(1500)
        targets.forEach { builder.addRoute(it, 32) }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.w(TAG, "establish() failed", e)
            null
        }
        if (pfd == null) {
            // VPN permission was not granted (or was revoked).
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tunnel = pfd
        running = true
        packetThread = Thread({ runPacketLoop(pfd) }, "netguard-packet-loop").also { it.start() }
    }

    /** Reads IPv4 packets from the tunnel and drops every one whose source or destination is blocked. */
    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32_767)
        try {
            while (running) {
                val length = input.read(buffer)
                if (length < 0) break
                if (length < 20) continue
                val version = (buffer[0].toInt() shr 4) and 0x0F
                if (version != 4) {
                    droppedPackets.incrementAndGet()
                    continue
                }
                // Only blocked IPs are routed into this tunnel and we have no
                // forwarding stack, so the packet is simply discarded. The
                // addresses are kept for a future per-IP statistics view.
                val source = ipv4At(buffer, 12)
                val destination = ipv4At(buffer, 16)
                if (source in blockedIps || destination in blockedIps) {
                    droppedPackets.incrementAndGet()
                }
            }
        } catch (e: IOException) {
            // Tunnel closed by stopVpn(): normal shutdown.
        } finally {
            try { input.close() } catch (e: IOException) { /* ignore */ }
        }
    }

    private fun ipv4At(buffer: ByteArray, offset: Int): String =
        "${buffer[offset].toInt() and 0xFF}.${buffer[offset + 1].toInt() and 0xFF}." +
            "${buffer[offset + 2].toInt() and 0xFF}.${buffer[offset + 3].toInt() and 0xFF}"

    private fun stopVpn() {
        running = false
        try { tunnel?.close() } catch (e: IOException) { /* ignore */ }
        tunnel = null
        packetThread?.interrupt()
        packetThread = null
    }

    private fun showForegroundNotification() {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GuardVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_device_default)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text, blockedIps.size))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.vpn_notification_stop), stopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onRevoke() {
        stopVpn()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GuardVpnService"
        private const val NOTIFICATION_ID = 4101
        private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        const val ACTION_START = "com.network.scanner.pro.action.START_VPN"
        const val ACTION_STOP = "com.network.scanner.pro.action.STOP_VPN"
        const val EXTRA_BLOCKED_IPS = "blocked_ips"

        /** Starts (or reconfigures) the VPN with the given blocked IPs. VPN permission must already be granted. */
        fun start(context: Context, blockedIps: Collection<String>) {
            val intent = Intent(context, GuardVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_BLOCKED_IPS, blockedIps.toTypedArray())
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GuardVpnService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
