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
import com.network.scanner.pro.data.repositories.SpeedTestRepository
import com.network.scanner.pro.util.Notifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Runs a speed test once a day at the chosen hour, stores it in Room and notifies the result. */
@HiltWorker
class ScheduledSpeedTestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val speedTestRepository: SpeedTestRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = speedTestRepository.runTest()
            Notifications.post(
                context = applicationContext,
                id = NOTIFICATION_ID,
                channel = Notifications.CHANNEL_SPEED,
                title = applicationContext.getString(R.string.speed_report_title),
                text = applicationContext.getString(
                    R.string.speed_report_text,
                    String.format(Locale.US, "%.1f", result.downloadMbps),
                    String.format(Locale.US, "%.1f", result.uploadMbps),
                    result.pingMs
                )
            )
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "scheduled_speed_test"
        private const val NOTIFICATION_ID = 6001

        /** Repeats every 24 h, first run at the next [hour]:00 local time. */
        fun scheduleDaily(context: Context, hour: Int) {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = next.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<ScheduledSpeedTestWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
