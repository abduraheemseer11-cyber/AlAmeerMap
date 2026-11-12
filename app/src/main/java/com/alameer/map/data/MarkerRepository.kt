package com.alameer.map.data

import com.alameer.map.data.export.ExportMarker
import com.alameer.map.data.local.MarkerDao
import com.alameer.map.data.local.MarkerEntity
import com.alameer.map.data.remote.MarkerDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

data class ImportResult(val imported: Int, val skipped: Int, val invalid: Int)

/**
 * Offline-first: every change is written to Room immediately (the UI reads Room),
 * then pushed to Supabase. If the push fails the row stays "pending" and is retried
 * on the next sync.
 */
class MarkerRepository(
    private val dao: MarkerDao,
    private val supabase: SupabaseClient,
) {
    private companion object {
        const val TABLE = "markers"
        const val PAGE = 1000
        const val CHUNK = 200
    }

    fun observe(userId: String): Flow<List<MarkerEntity>> = dao.observe(userId)

    suspend fun save(entity: MarkerEntity) {
        dao.upsert(entity.copy(updatedAt = System.currentTimeMillis(), pendingSync = true, deleted = false))
        push(entity.userId)
    }

    suspend fun delete(entity: MarkerEntity) {
        dao.upsert(entity.copy(updatedAt = System.currentTimeMillis(), pendingSync = true, deleted = true))
        push(entity.userId)
    }

    /** Push local changes first, then pull. Returns false if the network/server failed. */
    suspend fun sync(userId: String): Boolean = push(userId) && pull(userId)

    suspend fun push(userId: String): Boolean = try {
        val pending = dao.pending(userId)
        val upserts = pending.filter { !it.deleted }
        val deletes = pending.filter { it.deleted }

        upserts.chunked(CHUNK).forEach { chunk ->
            supabase.from(TABLE).upsert(chunk.map { it.toDto() })
            chunk.forEach { dao.markSynced(it.id, it.updatedAt) }
        }
        deletes.chunked(CHUNK).forEach { chunk ->
            val ids = chunk.map { it.id }
            supabase.from(TABLE).delete { filter { isIn("id", ids) } }
            dao.deleteByIds(ids)
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    suspend fun pull(userId: String): Boolean = try {
        val remote = ArrayList<MarkerDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(TABLE).select {
                order("id", Order.ASCENDING)
                range(offset, offset + PAGE - 1)
            }.decodeList<MarkerDto>()
            remote += page
            if (page.size < PAGE) break
            offset += PAGE
        }

        // Never overwrite something the user changed locally and hasn't pushed yet.
        val pendingIds = dao.pending(userId).map { it.id }.toSet()
        dao.upsertAll(remote.filter { it.id !in pendingIds }.map { it.toEntity() })

        // Remove rows that were deleted elsewhere (only already-synced ones).
        val remoteIds = remote.map { it.id }.toSet()
        val gone = dao.syncedIds(userId).filter { it !in remoteIds }
        gone.chunked(500).forEach { dao.deleteByIds(it) }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    suspend fun import(userId: String, items: List<ExportMarker>): ImportResult {
        val seen = dao.all(userId)
            .map { dupKey(it.networkNumber, it.deviceIp, it.clientPhone, it.latitude, it.longitude) }
            .toHashSet()
        val fresh = ArrayList<MarkerEntity>()
        var skipped = 0
        var invalid = 0
        val now = System.currentTimeMillis()

        for (i in items) {
            val lat = i.latitude
            val lon = i.longitude
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                invalid++
                continue
            }
            val key = dupKey(i.networkNumber.orEmpty(), i.deviceIp.orEmpty(), i.clientPhone.orEmpty(), lat, lon)
            if (!seen.add(key)) {
                skipped++
                continue
            }
            fresh += MarkerEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                modemNumber = i.modemNumber.orEmpty().trim(),
                networkNumber = i.networkNumber.orEmpty().trim(),
                modemType = i.modemType.orEmpty().trim(),
                deviceIp = i.deviceIp.orEmpty().trim(),
                username = i.username.orEmpty().trim(),
                passwordEnc = CryptoManager.encrypt(i.password.orEmpty(), userId),
                clientName = i.clientName.orEmpty().trim(),
                clientPhone = i.clientPhone.orEmpty().trim(),
                latitude = lat,
                longitude = lon,
                updatedAt = now,
                pendingSync = true,
            )
        }
        dao.upsertAll(fresh)
        return ImportResult(fresh.size, skipped, invalid)
    }

    private fun dupKey(net: String, ip: String, phone: String, lat: Double, lon: Double): String =
        listOf(net.trim().lowercase(), ip.trim().lowercase(), phone.trim(),
            String.format(Locale.US, "%.5f", lat), String.format(Locale.US, "%.5f", lon)).joinToString("|")
}

private fun MarkerEntity.toDto() = MarkerDto(
    id = id, userId = userId, modemNumber = modemNumber, networkNumber = networkNumber,
    modemType = modemType, deviceIp = deviceIp, username = username, passwordEnc = passwordEnc,
    clientName = clientName, clientPhone = clientPhone, latitude = latitude, longitude = longitude,
)

private fun MarkerDto.toEntity() = MarkerEntity(
    id = id, userId = userId, modemNumber = modemNumber, networkNumber = networkNumber,
    modemType = modemType, deviceIp = deviceIp, username = username, passwordEnc = passwordEnc,
    clientName = clientName, clientPhone = clientPhone, latitude = latitude, longitude = longitude,
    updatedAt = 0L, pendingSync = false, deleted = false,
)
