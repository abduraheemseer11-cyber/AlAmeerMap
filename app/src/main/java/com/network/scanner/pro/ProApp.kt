package com.network.scanner.pro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import com.network.scanner.pro.crypto.CryptoManager
import com.network.scanner.pro.util.Notifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration as OsmConfiguration
import java.io.File
import javax.inject.Inject

/**
 * Application entry point.
 *  - Hilt root component.
 *  - Supplies WorkManager with a Hilt-aware WorkerFactory (the default
 *    initializer is removed in the manifest).
 *  - Configures OSMDroid (user agent + cache location).
 *  - Pre-computes the PBKDF2 encryption key off the main thread.
 */
@HiltAndroidApp
class ProApp : Application(), WorkConfiguration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cryptoManager: CryptoManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        configureOsmdroid()
        appScope.launch { runCatching { cryptoManager.warmUp() } }
    }

    private fun configureOsmdroid() {
        val cfg = OsmConfiguration.getInstance()
        cfg.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        cfg.userAgentValue = packageName
        val base = File(cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
    }
}
