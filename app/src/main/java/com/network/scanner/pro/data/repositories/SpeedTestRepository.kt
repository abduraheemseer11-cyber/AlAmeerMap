package com.network.scanner.pro.data.repositories

import com.network.scanner.pro.data.local.daos.SpeedTestDao
import com.network.scanner.pro.data.local.entities.toDomain
import com.network.scanner.pro.data.local.entities.toEntity
import com.network.scanner.pro.domain.models.SpeedPhase
import com.network.scanner.pro.domain.models.SpeedTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import javax.inject.Inject
import javax.inject.Singleton

/** Real speed test against Cloudflare's public endpoints, results saved to Room. */
@Singleton
class SpeedTestRepository @Inject constructor(
    private val client: OkHttpClient,
    private val dao: SpeedTestDao
) {

    fun observeHistory(limit: Int = 30): Flow<List<SpeedTestResult>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    suspend fun clearHistory() = dao.deleteAll()

    /**
     * Runs ping -> download -> upload. [onProgress] receives live values:
     * ms for PING, Mbps for DOWNLOAD and UPLOAD. Throws on network errors.
     */
    suspend fun runTest(
        onProgress: (SpeedPhase, Double) -> Unit = { _, _ -> }
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val ping = measurePing()
        onProgress(SpeedPhase.PING, ping.toDouble())
        val download = measureDownload { onProgress(SpeedPhase.DOWNLOAD, it) }
        val upload = measureUpload { onProgress(SpeedPhase.UPLOAD, it) }

        val result = SpeedTestResult(
            timestamp = System.currentTimeMillis(),
            downloadMbps = download,
            uploadMbps = upload,
            pingMs = ping
        )
        dao.insert(result.toEntity())
        result
    }

    /** Best of 5 tiny requests (0 bytes) = round-trip latency. */
    private suspend fun measurePing(): Long {
        var best = Long.MAX_VALUE
        repeat(5) {
            currentCoroutineContext().ensureActive()
            val start = System.nanoTime()
            client.newCall(Request.Builder().url("$BASE/__down?bytes=0").build())
                .execute().use { it.body?.bytes() }
            val ms = (System.nanoTime() - start) / 1_000_000
            if (ms < best) best = ms
        }
        return best
    }

    private suspend fun measureDownload(onProgress: (Double) -> Unit): Double {
        val request = Request.Builder().url("$BASE/__down?bytes=$DOWNLOAD_BYTES").build()
        var total = 0L
        val start = System.nanoTime()
        var lastEmit = start
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val stream = response.body?.byteStream() ?: error("Empty body")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                val now = System.nanoTime()
                if (now - lastEmit > 150_000_000L) {
                    lastEmit = now
                    onProgress(mbps(total, now - start))
                }
            }
        }
        val mbps = mbps(total, System.nanoTime() - start)
        onProgress(mbps)
        return mbps
    }

    private suspend fun measureUpload(onProgress: (Double) -> Unit): Double {
        val size = UPLOAD_BYTES
        val start = System.nanoTime()
        var lastEmit = start
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size.toLong()
            override fun writeTo(sink: BufferedSink) {
                val chunk = ByteArray(64 * 1024)
                var written = 0
                while (written < size) {
                    val n = minOf(chunk.size, size - written)
                    sink.write(chunk, 0, n)
                    sink.flush()
                    written += n
                    val now = System.nanoTime()
                    if (now - lastEmit > 150_000_000L) {
                        lastEmit = now
                        onProgress(mbps(written.toLong(), now - start))
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        client.newCall(Request.Builder().url("$BASE/__up").post(body).build())
            .execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.bytes()
            }
        val mbps = mbps(size.toLong(), System.nanoTime() - start)
        onProgress(mbps)
        return mbps
    }

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0) 0.0 else bytes * 8.0 / 1_000_000.0 / (nanos / 1_000_000_000.0)

    private companion object {
        const val BASE = "https://speed.cloudflare.com"
        const val DOWNLOAD_BYTES = 10_000_000
        const val UPLOAD_BYTES = 5_000_000
    }
}
