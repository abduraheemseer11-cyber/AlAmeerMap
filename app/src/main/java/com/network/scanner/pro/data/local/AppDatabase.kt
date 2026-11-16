package com.network.scanner.pro.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.network.scanner.pro.crypto.CryptoManager
import com.network.scanner.pro.data.local.daos.DeviceDao
import com.network.scanner.pro.data.local.daos.InstallationDao
import com.network.scanner.pro.data.local.daos.SpeedTestDao
import com.network.scanner.pro.data.local.entities.DeviceEntity
import com.network.scanner.pro.data.local.entities.InstallationEntity
import com.network.scanner.pro.data.local.entities.PresenceEntity
import com.network.scanner.pro.data.local.entities.SpeedTestEntity
import com.network.scanner.pro.data.local.entities.WifiNetworkEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Single encrypted Room database (SQLCipher). The passphrase is a random
 * 32-byte value kept in Keystore-backed EncryptedSharedPreferences
 * (see [CryptoManager.getDatabasePassphrase]).
 */
@Database(
    entities = [
        DeviceEntity::class,
        WifiNetworkEntity::class,
        SpeedTestEntity::class,
        PresenceEntity::class,
        InstallationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun installationDao(): InstallationDao
    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        fun build(context: Context, passphrase: ByteArray): AppDatabase {
            // Load SQLCipher's native library before the first database is opened.
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                CryptoManager.DATABASE_FILE
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
