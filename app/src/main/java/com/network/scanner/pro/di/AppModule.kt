package com.network.scanner.pro.di

import android.content.Context
import com.network.scanner.pro.crypto.CryptoManager
import com.network.scanner.pro.data.local.AppDatabase
import com.network.scanner.pro.data.local.daos.DeviceDao
import com.network.scanner.pro.data.local.daos.InstallationDao
import com.network.scanner.pro.data.local.daos.SpeedTestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Hilt bindings for classes that cannot use constructor injection. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        crypto: CryptoManager
    ): AppDatabase = AppDatabase.build(context, crypto.getDatabasePassphrase())

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun provideInstallationDao(db: AppDatabase): InstallationDao = db.installationDao()

    @Provides
    fun provideSpeedTestDao(db: AppDatabase): SpeedTestDao = db.speedTestDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
