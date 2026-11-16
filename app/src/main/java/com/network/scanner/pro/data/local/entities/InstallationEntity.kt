package com.network.scanner.pro.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.network.scanner.pro.domain.models.Installation

/** Sync state of a local installation row (offline-first). */
object SyncState {
    const val SYNCED = 0
    const val PENDING_UPSERT = 1
    const val PENDING_DELETE = 2
}

@Entity(tableName = "installations", indices = [Index("userId")])
data class InstallationEntity(
    @PrimaryKey val id: String,
    val networkNumber: String,
    val routerType: String,
    val routerIp: String,
    val adminUsername: String,
    /** AES-256-GCM ciphertext produced by CryptoManager. The database itself is also SQLCipher-encrypted. */
    val adminPasswordEncrypted: String,
    val clientName: String,
    val clientPhone: String,
    val latitude: Double,
    val longitude: Double,
    val installationDate: Long,
    val notes: String,
    val userId: String,
    val syncState: Int = SyncState.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Installation = Installation(
        id = id,
        networkNumber = networkNumber,
        routerType = routerType,
        routerIp = routerIp,
        adminUsername = adminUsername,
        adminPasswordEncrypted = adminPasswordEncrypted,
        clientName = clientName,
        clientPhone = clientPhone,
        latitude = latitude,
        longitude = longitude,
        installationDate = installationDate,
        notes = notes,
        userId = userId
    )

    companion object {
        fun fromDomain(installation: Installation, syncState: Int): InstallationEntity =
            InstallationEntity(
                id = installation.id,
                networkNumber = installation.networkNumber,
                routerType = installation.routerType,
                routerIp = installation.routerIp,
                adminUsername = installation.adminUsername,
                adminPasswordEncrypted = installation.adminPasswordEncrypted,
                clientName = installation.clientName,
                clientPhone = installation.clientPhone,
                latitude = installation.latitude,
                longitude = installation.longitude,
                installationDate = installation.installationDate,
                notes = installation.notes,
                userId = installation.userId,
                syncState = syncState
            )
    }
}

fun InstallationEntity.toDomainModel(): Installation = toDomain()

fun Installation.toEntity(syncState: Int): InstallationEntity =
    InstallationEntity.fromDomain(this, syncState)
