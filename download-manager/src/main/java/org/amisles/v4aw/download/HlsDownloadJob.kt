package org.amisles.v4aw.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.amisles.v4aw.model.DownloadChunkInfo
import org.amisles.v4aw.model.DownloadInfo
import org.amisles.v4aw.model.DownloadStatus
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HlsDownloadJob(
    private var task: DownloadInfo,
    private val client: OkHttpClient,
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val chunkDao: DownloadChunkDao,
    private val dispatcher: CoroutineDispatcher,
    private val onProgress: (DownloadInfo) -> Unit
) : DownloadJobBase {
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val totalDownloadedBytes = AtomicLong(0L)

    companion object {
        private const val TAG = "HlsDownloadJob"
        private const val BUFFER_SIZE = 8192
        private const val SPEED_UPDATE_INTERVAL = 500L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_CONCURRENT_SEGMENTS = 4
    }

    private val hlsClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun start() {
        try {
            val m3u8Content = fetchM3u8Content(task.videoSource)
            var playlist = M3u8Parser.parse(m3u8Content, task.videoSource)

            if (playlist.isMaster) {
                val bestVariant = M3u8Parser.getBestVariant(playlist)
                    ?: throw IllegalStateException("No variant found in master playlist")

                val variantContent = fetchM3u8Content(bestVariant.url)
                playlist = M3u8Parser.parse(variantContent, bestVariant.url)
            }

            if (playlist.segments.isEmpty()) {
                throw IllegalStateException("No segments found in M3U8 playlist")
            }

            val totalSegments = playlist.segments.size
            task = task.copy(
                fileSize = totalSegments.toLong(),
                threadCount = MAX_CONCURRENT_SEGMENTS,
                updatedAt = System.currentTimeMillis()
            )
            onProgress(task)

            downloadSegments(playlist)

            if (!isCancelled.get() && !isPaused.get()) {
                val outputDir = getDownloadDirectory()
                val outputFile = File(outputDir, task.fileName)

                mergeSegments(outputFile)

                val actualSize = outputFile.length()
                task = task.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = actualSize,
                    filePath = outputFile.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
                onProgress(task)
                chunkDao.deleteChunksByDownloadId(task.id)
            }
        } catch (e: Exception) {
            if (!isCancelled.get()) {
                Log.e(TAG, "HLS download failed: ${task.id}", e)
                task = task.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Unknown HLS download error",
                    updatedAt = System.currentTimeMillis()
                )
                onProgress(task)
            }
        }
    }

    override fun pause() {
        isPaused.set(true)
        jobScope.cancel()
        task = task.copy(
            status = DownloadStatus.PAUSED,
            updatedAt = System.currentTimeMillis()
        )
        onProgress(task)
    }

    override fun cancel() {
        isCancelled.set(true)
        jobScope.cancel()
    }

    private suspend fun fetchM3u8Content(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = hlsClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch M3U8: HTTP ${response.code}")
            }

            val content = response.body?.string() ?: throw IllegalStateException("Empty M3U8 response")
            response.close()
            content
        }
    }

    private suspend fun downloadSegments(playlist: M3u8Playlist) {
        val segments = playlist.segments
        val existingChunks = chunkDao.getChunksByDownloadId(task.id)
        val completedIndices = existingChunks.filter { it.completed }.map { it.chunkIndex }.toSet()

        totalDownloadedBytes.set(existingChunks.sumOf { it.downloadedBytes })

        val tempDir = getTempDir()
        if (!tempDir.exists()) tempDir.mkdirs()

        val pendingChunks = mutableListOf<DownloadChunkInfo>()

        for ((index, segment) in segments.withIndex()) {
            if (index in completedIndices) continue

            val chunk = if (existingChunks.any { it.chunkIndex == index }) {
                existingChunks.first { it.chunkIndex == index }
            } else {
                val chunk = DownloadChunkInfo(
                    downloadId = task.id,
                    chunkIndex = index,
                    startByte = index.toLong(),
                    endByte = (index + 1).toLong(),
                    downloadedBytes = 0L,
                    completed = false,
                    filePath = File(tempDir, "segment_${index}.ts").absolutePath
                )
                chunkDao.insertChunk(chunk)
                chunk
            }
            pendingChunks.add(chunk)
        }

        val chunkJobs = pendingChunks.map { chunk ->
            jobScope.async(dispatcher) {
                downloadSegmentWithRetry(playlist, chunk)
            }
        }

        val progressJob = jobScope.launch {
            trackProgress(segments.size.toLong())
        }

        try {
            chunkJobs.awaitAll()
        } finally {
            progressJob.cancel()
        }

        if (isCancelled.get() || isPaused.get()) return

        val allChunks = chunkDao.getChunksByDownloadId(task.id)
        val incompleteChunks = allChunks.filter { !it.completed }
        if (incompleteChunks.isNotEmpty()) {
            throw IllegalStateException("${incompleteChunks.size} segment(s) failed to download")
        }
    }

    private suspend fun downloadSegmentWithRetry(playlist: M3u8Playlist, chunk: DownloadChunkInfo) {
        var retryCount = 0
        var lastError: Exception? = null

        while (retryCount < MAX_RETRIES) {
            if (isCancelled.get() || isPaused.get()) return

            try {
                val segmentIndex = chunk.chunkIndex
                val segment = playlist.segments[segmentIndex]
                downloadSegment(segment, chunk)
                return
            } catch (e: Exception) {
                lastError = e
                retryCount++
                Log.w(TAG, "Segment ${chunk.chunkIndex} attempt $retryCount failed: ${e.message}")
                if (retryCount < MAX_RETRIES && !isCancelled.get() && !isPaused.get()) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        throw lastError ?: IllegalStateException("Segment ${chunk.chunkIndex} failed after $MAX_RETRIES retries")
    }

    private suspend fun downloadSegment(segment: M3u8Segment, chunk: DownloadChunkInfo) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(segment.url).build()
            val response = hlsClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                throw IllegalStateException("HTTP ${response.code} for segment ${segment.index}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response for segment ${segment.index}")
            val inputStream = body.byteStream()
            val tempFile = File(chunk.filePath)
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var segmentBytes = 0L

            try {
                while (true) {
                    if (isCancelled.get() || isPaused.get()) break

                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    outputStream.write(buffer, 0, read)
                    segmentBytes += read
                    totalDownloadedBytes.addAndGet(read.toLong())
                }
            } finally {
                outputStream.close()
                inputStream.close()
                response.close()
            }

            if (!isCancelled.get() && !isPaused.get()) {
                if (segment.encryption != null && segment.encryption.method != "NONE") {
                    decryptSegment(tempFile, segment.encryption)
                }

                chunkDao.updateChunk(
                    chunk.copy(downloadedBytes = segmentBytes, completed = true)
                )
            }
        }
    }

    private fun decryptSegment(file: File, encryption: M3u8Encryption) {
        val key = fetchEncryptionKey(encryption.keyUrl)
            ?: throw IllegalStateException("Failed to fetch encryption key from: ${encryption.keyUrl}")

        val iv = if (!encryption.iv.isNullOrEmpty()) {
            val ivHex = encryption.iv.removePrefix("0x").removePrefix("0X")
            val ivBytes = ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            IvParameterSpec(ivBytes)
        } else {
            IvParameterSpec(ByteArray(16))
        }

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)

        val encryptedData = file.readBytes()
        val decryptedData = cipher.doFinal(encryptedData)
        file.writeBytes(decryptedData)
    }

    private fun fetchEncryptionKey(keyUrl: String): ByteArray? {
        return try {
            val request = Request.Builder().url(keyUrl).build()
            val response = hlsClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch encryption key: HTTP ${response.code}")
                response.close()
                return null
            }

            val key = response.body?.bytes()
            response.close()
            key
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching encryption key", e)
            null
        }
    }

    private suspend fun mergeSegments(outputFile: File) {
        withContext(Dispatchers.IO) {
            val tempDir = getTempDir()
            val chunks = chunkDao.getChunksByDownloadId(task.id).sortedBy { it.chunkIndex }

            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                for (chunk in chunks) {
                    if (isCancelled.get() || isPaused.get()) break

                    val segmentFile = File(chunk.filePath)
                    if (!segmentFile.exists()) {
                        throw IllegalStateException("Segment file not found: ${chunk.filePath}")
                    }

                    segmentFile.inputStream().use { inputStream ->
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read == -1) break
                            outputStream.write(buffer, 0, read)
                        }
                    }

                    segmentFile.delete()
                }
            }

            tempDir.deleteRecursively()
        }
    }

    private suspend fun trackProgress(totalSegments: Long) {
        var lastSpeedUpdate = System.currentTimeMillis()
        var lastBytesRead = totalDownloadedBytes.get()

        while (currentCoroutineContext().isActive) {
            delay(SPEED_UPDATE_INTERVAL)

            if (isCancelled.get() || isPaused.get()) break

            val currentBytes = totalDownloadedBytes.get()
            val now = System.currentTimeMillis()
            val timeDiff = (now - lastSpeedUpdate).toFloat() / 1000f
            val speed = if (timeDiff > 0) ((currentBytes - lastBytesRead).toFloat() / timeDiff).toLong() else 0L

            val completedChunks = chunkDao.getCompletedChunkCount(task.id)
            val progress = if (totalSegments > 0) completedChunks.toFloat() / totalSegments else 0f

            task = task.copy(
                downloadedBytes = currentBytes,
                speed = speed,
                remainingTime = if (speed > 0 && totalSegments > completedChunks) {
                    ((totalSegments - completedChunks) * 2000L / 1000L)
                } else 0L,
                updatedAt = now
            )
            onProgress(task)

            lastSpeedUpdate = now
            lastBytesRead = currentBytes
        }
    }

    private fun getDownloadDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "Downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTempDir(): File {
        val dir = File(context.getExternalFilesDir(null), "Downloads${File.separator}.hls_temp${File.separator}${task.id}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
