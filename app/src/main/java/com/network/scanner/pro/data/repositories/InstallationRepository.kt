package com.network.scanner.pro.data.repositories

import com.network.scanner.pro.crypto.CryptoManager
import com.network.scanner.pro.data.local.daos.InstallationDao
import com.network.scanner.pro.data.local.entities.InstallationEntity
import com.network.scanner.pro.data.local.entities.SyncState
import com.network.scanner.pro.data.remote.SupabaseProvider
import com.network.scanner.pro.domain.models.Installation
import com.network.scanner.pro.domain.models.InstallationInput
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first store for installations.
 *  - Every write goes to Room first (password encrypted BEFORE insertion).
 *  - A background sync then pushes pending rows to Supabase and pulls remote changes.
 *  - Row Level Security on the server guarantees users only see their own rows.
 */
@Singleton
class InstallationRepository @Inject constructor(
    private val dao: InstallationDao,
    private val crypto: CryptoManager,
    private val supabase: SupabaseProvider,
    private val auth: AuthRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Live list of the signed-in user's installations (empty when signed out). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInstallations(): Flow<List<Installation>> =
        auth.userIdFlow
            .distinctUntilChanged()
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    dao.observeForUser(userId).map { rows -> rows.map { it.toDomain() } }
                }
            }

    /** Creates or updates an installation. Fails if nobody is signed in. */
    suspend fun save(input: InstallationInput): Result<Installation> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = auth.currentUserId() ?: error("Not signed in")
            val existing = input.id?.let { dao.getById(it) }

            val encrypted = if (input.adminPassword.isNotEmpty()) {
                crypto.encrypt(input.adminPassword)
            } else {
                existing?.adminPasswordEncrypted.orEmpty()
            }

            val installation = Installation(
                id = input.id ?: UUID.randomUUID().toString(),
                networkNumber = input.networkNumber.trim(),
                routerType = input.routerType.trim(),
                routerIp = input.routerIp.trim(),
                adminUsername = input.adminUsername.trim(),
                adminPasswordEncrypted = encrypted,
                clientName = input.clientName.trim(),
                clientPhone = input.clientPhone.trim(),
                latitude = input.latitude,
                longitude = input.longitude,
                installationDate = input.installationDate
                    ?: existing?.installationDate
                    ?: System.currentTimeMillis(),
                notes = input.notes.trim(),
                userId = userId
            )
            dao.upsert(InstallationEntity.fromDomain(installation, SyncState.PENDING_UPSERT))
            syncInBackground()
            installation
        }
    }

    /** Marks the row as deleted locally; the sync removes it from Supabase. */
    suspend fun delete(id: String) {
        dao.markPendingDelete(id)
        syncInBackground()
    }

    /** Decrypts the stored admin password; null if it cannot be decrypted on this device. */
    suspend fun revealPassword(installation: Installation): String? =
        withContext(Dispatchers.Default) { crypto.decrypt(installation.adminPasswordEncrypted) }

    fun syncInBackground() {
        scope.launch { sync() }
    }

    /** Push pending changes, then pull the remote state. Returns true on success. */
    suspend fun sync(): Boolean {
        val userId = auth.currentUserId() ?: return false
        if (!supabase.isConfigured) return false
        return syncMutex.withLock {
            _syncing.value = true
            try {
                val table = supabase.client.postgrest.from(TABLE)

                // 1) pending deletes
                for (row in dao.getPendingDeletes(userId)) {
                    table.delete { filter { eq("id", row.id) } }
                    dao.deleteById(row.id)
                }

                // 2) pending inserts/updates
                val pending = dao.getPendingUpserts(userId)
                if (pending.isNotEmpty()) {
                    table.upsert(pending.map { it.toDomain() })
                    dao.upsertAll(pending.map { it.copy(syncState = SyncState.SYNCED) })
                }

                // 3) pull remote rows (RLS limits them to this user)
                val remote = table.select { filter { eq("user_id", userId) } }
                    .decodeList<Installation>()
                val pendingIds = dao.getPendingIds(userId).toSet()
                val remoteIds = remote.map { it.id }.toSet()

                dao.upsertAll(
                    remote.filter { it.id !in pendingIds }
                        .map { InstallationEntity.fromDomain(it, SyncState.SYNCED) }
                )
                // Rows deleted on another device disappear here too.
                for (row in dao.getSynced(userId)) {
                    if (row.id !in remoteIds) dao.deleteById(row.id)
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                _syncing.value = false
            }
        }
    }

    private companion object {
        const val TABLE = "installations"
    }
}
