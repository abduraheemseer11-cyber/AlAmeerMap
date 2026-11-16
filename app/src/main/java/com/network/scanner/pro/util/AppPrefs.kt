package com.network.scanner.pro.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain (non-secret) app settings. Secrets live in CryptoManager's encrypted
 * preferences, never here. Can also be created manually in services:
 * `AppPrefs(applicationContext)`.
 */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Automatically block devices that appear after the first (baseline) scan. */
    var autoBlockUnknown: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BLOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BLOCK, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    /** IPs that the local VPN should black-hole (blocked or paused devices). */
    var blockedIps: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED, HashSet(value)).apply()

    /** Last known Supabase user id, so offline-first features keep working. */
    var lastUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_USER_ID).apply()
            else prefs.edit().putString(KEY_USER_ID, value).apply()
        }

    /** Hour of day (0-23) of the scheduled speed test, or -1 when disabled. */
    var speedTestHour: Int
        get() = prefs.getInt(KEY_SPEED_HOUR, -1)
        set(value) = prefs.edit().putInt(KEY_SPEED_HOUR, value).apply()

    private companion object {
        const val NAME = "netguard_prefs"
        const val KEY_AUTO_BLOCK = "auto_block_unknown"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_BLOCKED = "blocked_ips"
        const val KEY_USER_ID = "last_user_id"
        const val KEY_SPEED_HOUR = "speed_test_hour"
    }
}
