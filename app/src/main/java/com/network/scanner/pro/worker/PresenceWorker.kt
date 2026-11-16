package com.network.scanner.pro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.network.scanner.pro.R
import com.network.scanner.pro.data.local.daos.DeviceDao
import com.network.scanner.pro.data.repositories.NetworkRepository
import com.network.scanner.pro.util.Notifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * "Digital Home Presence": every 15 minutes pings the devices the user marked
 * and posts a notification when one changes from OFFLINE to ONLINE.
 */
@HiltWorker
class PresenceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val deviceDao: DeviceDao,
    private val networkRepository: NetworkRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tracked = deviceDao.getPresence()
        if (tracked.isEmpty()) return Result.success()

        // Away from the home Wi-Fi every ping would fail; do not mistake that for "offline".
        if (networkRepository.currentWifi() == null) return Result.success()

        val now = System.currentTimeMillis()
        for (profile in tracked) {
            val online = networkRepository.isHostAlive(profile.ip)

            if (online && !profile.lastKnownOnline && profile.notifyOnOnline) {
                Notifications.post(
                    context = applicationContext,
                    id = NOTIFICATION_BASE + abs(profile.deviceId.hashCode() % 10_000),
                    channel = Notifications.CHANNEL_ALERTS,
                    title = applicationContext.getString(R.string.presence_title),
                    text = applicationContext.getString(R.string.presence_connected, profile.displayName)
                )
            }
            deviceDao.upsertPresence(profile.copy(lastKnownOnline = online, lastChecked = now))
            deviceDao.setOnline(profile.deviceId, online, if (online) now else profile.lastChecked)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "presence_monitor"
        private const val NOTIFICATION_BASE = 5000

        /** Periodic check every 15 minutes (WorkManager's minimum interval). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PresenceWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
