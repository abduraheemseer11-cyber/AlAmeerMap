package com.network.scanner.pro.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.network.scanner.pro.MainActivity
import com.network.scanner.pro.R

/** Notification channels and a single helper to post local notifications. */
object Notifications {
    const val CHANNEL_ALERTS = "presence_alerts"
    const val CHANNEL_SPEED = "speed_reports"
    const val CHANNEL_VPN = "guard_vpn"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SPEED,
                context.getString(R.string.channel_speed),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_VPN,
                context.getString(R.string.channel_vpn),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    /** Posts a notification unless the user disabled them or permission is missing. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, channel: String, title: String, text: String) {
        val appContext = context.applicationContext
        if (!AppPrefs(appContext).notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_device_default)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(appContext).notify(id, notification)
    }
}
