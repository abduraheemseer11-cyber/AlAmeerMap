package com.alameer.map

import android.app.Application
import android.content.Context
import android.net.Uri
import com.alameer.map.data.AuthRepository
import com.alameer.map.data.MarkerRepository
import com.alameer.map.data.local.AppDatabase
import com.alameer.map.data.remote.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import org.osmdroid.config.Configuration
import java.io.File

/** Tiny service locator (keeps the project free of DI frameworks). */
class AppContainer(app: Application) {
    val supabase: SupabaseClient by lazy { SupabaseProvider.create() }
    private val database by lazy { AppDatabase.build(app) }
    val markerRepository by lazy { MarkerRepository(database.markerDao(), supabase) }
    val authRepository by lazy { AuthRepository(supabase) }

    /** Password-reset deep link delivered by MainActivity, consumed by AuthViewModel. */
    val deepLink = MutableStateFlow<Uri?>(null)
}

class AlAmeerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // OSMDroid: keep tiles in app-private storage (no storage permission needed).
        val osm = Configuration.getInstance()
        osm.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        osm.userAgentValue = packageName
        val base = File(filesDir, "osmdroid").apply { mkdirs() }
        osm.osmdroidBasePath = base
        osm.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
        osm.tileFileSystemCacheMaxBytes = 600L * 1024 * 1024
        osm.tileFileSystemCacheTrimBytes = 500L * 1024 * 1024
    }
}
