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

class DashDownloadJob(
    private var task: DownloadInfo,
    private val client: OkHttpClient,
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val chunkDao: DownloadChunkDao,
    private val dispatcher: CoroutineDispatcher,
    private val onProgress: (DownloadInfo) -> Unit,
    private val downloadDir: File
) : DownloadJobBase {
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val totalDownloadedBytes = AtomicLong(0L)

    companion object {
        private const val TAG = "DashDownloadJob"
        private const val BUFFER_SIZE = 8192
        private const val SPEED_UPDATE_INTERVAL = 500L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_CONCURRENT_SEGMENTS = 4
    }

    private val dashClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun start() {
        try {
            val mpdContent = fetchMpdContent(task.videoSource)
            val manifest = DashParser.parse(mpdContent, task.videoSource)

            if (manifest.periods.isEmpty()) {
                throw IllegalStateException("No periods found in DASH manifest")
            }

            val period = manifest.periods.first()
            val videoAdaptation = manifest.periods.firstOrNull { p ->
                p.adaptations.any { DashParser.isVideoAdaptation(it) }
            }?.adaptations?.find { DashParser.isVideoAdaptation(it) }
                ?: period.adaptations.firstOrNull()

            val audioAdaptation = manifest.periods.firstOrNull { p ->
                p.adaptations.any { DashParser.isAudioAdaptation(it) }
            }?.adaptations?.find { DashParser.isAudioAdaptation(it) }

            if (videoAdaptation == null) {
                throw IllegalStateException("No video adaptation found in DASH manifest")
            }

            val videoRep = DashParser.getBestVideoRepresentation(videoAdaptation)
                ?: throw IllegalStateException("No video representation found")

            val videoSegments = DashParser.getSegmentUrls(videoRep, task.videoSource)
            if (videoSegments.isEmpty()) {
                throw IllegalStateException("No video segments found")
            }

            val hasAudio = audioAdaptation != null
            val audioRep = audioAdaptation?.let { DashParser.getBestAudioRepresentation(it) }
            val audioSegments = audioRep?.let { DashParser.getSegmentUrls(it, task.videoSource) } ?: emptyList()

            val totalSegments = videoSegments.size + audioSegments.size
            task = task.copy(
                fileSize = totalSegments.toLong(),
                threadCount = MAX_CONCURRENT_SEGMENTS,
                updatedAt = System.currentTimeMillis()
            )
            onProgress(task)

            val tempDir = getTempDir()
            if (!tempDir.exists()) tempDir.mkdirs()

            downloadVideoSegments(videoSegments)

            if (hasAudio && audioSegments.isNotEmpty() && !isCancelled.get() && !isPaused.get()) {
                downloadAudioSegments(audioSegments)
            }

            if (!isCancelled.get() && !isPaused.get()) {
                val outputDir = getDownloadDirectory()
                val outputFile = File(outputDir, task.fileName)

                if (hasAudio && audioSegments.isNotEmpty()) {
                    mergeAudioVideo(outputFile)
                } else {
                    mergeVideoOnly(outputFile)
                }

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
                Log.e(TAG, "DASH download failed: ${task.id}", e)
                task = task.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Unknown DASH download error",
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

    private suspend fun fetchMpdContent(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = dashClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch MPD: HTTP ${response.code}")
            }

            val content = response.body?.string() ?: throw IllegalStateException("Empty MPD response")
            response.close()
            content
        }
    }

    private suspend fun downloadVideoSegments(segments: List<String>) {
        downloadSegmentsOfType(segments, "video")
    }

    private suspend fun downloadAudioSegments(segments: List<String>) {
        downloadSegmentsOfType(segments, "audio")
    }

    private suspend fun downloadSegmentsOfType(segments: List<String>, type: String) {
        val existingChunks = chunkDao.getChunksByDownloadId(task.id)
        val typePrefix = "${type}_"
        val completedIndices = existingChunks
            .filter { it.filePath.contains(typePrefix) && it.completed }
            .map { it.chunkIndex }
            .toSet()

        totalDownloadedBytes.set(existingChunks.sumOf { it.downloadedBytes })

        val tempDir = getTempDir()
        if (!tempDir.exists()) tempDir.mkdirs()

        val pendingChunks = mutableListOf<DownloadChunkInfo>()

        for ((index, segmentUrl) in segments.withIndex()) {
            if (index in completedIndices) continue

            val chunk = if (existingChunks.any { it.chunkIndex == index && it.filePath.contains(typePrefix) }) {
                existingChunks.first { it.chunkIndex == index && it.filePath.contains(typePrefix) }
            } else {
                val chunk = DownloadChunkInfo(
                    downloadId = task.id,
                    chunkIndex = index,
                    startByte = index.toLong(),
                    endByte = (index + 1).toLong(),
                    downloadedBytes = 0L,
                    completed = false,
                    filePath = File(tempDir, "${typePrefix}segment_${index}.m4s").absolutePath
                )
                chunkDao.insertChunk(chunk)
                chunk
            }
            pendingChunks.add(chunk)
        }

        val chunkJobs = pendingChunks.map { chunk ->
            jobScope.async(dispatcher) {
                downloadSegmentWithRetry(segments, chunk)
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
        val incompleteChunks = allChunks.filter { !it.completed && it.filePath.contains(typePrefix) }
        if (incompleteChunks.isNotEmpty()) {
            throw IllegalStateException("${incompleteChunks.size} $type segment(s) failed to download")
        }
    }

    private suspend fun downloadSegmentWithRetry(segments: List<String>, chunk: DownloadChunkInfo) {
        var retryCount = 0
        var lastError: Exception? = null

        while (retryCount < MAX_RETRIES) {
            if (isCancelled.get() || isPaused.get()) return

            try {
                val segmentIndex = chunk.chunkIndex
                if (segmentIndex >= segments.size) {
                    throw IllegalStateException("Segment index $segmentIndex out of range")
                }
                val segmentUrl = segments[segmentIndex]
                downloadSegment(segmentUrl, chunk)
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

    private suspend fun downloadSegment(segmentUrl: String, chunk: DownloadChunkInfo) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(segmentUrl).build()
            val response = dashClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                throw IllegalStateException("HTTP ${response.code} for segment ${chunk.chunkIndex}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response for segment ${chunk.chunkIndex}")
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
                chunkDao.updateChunk(
                    chunk.copy(downloadedBytes = segmentBytes, completed = true)
                )
            }
        }
    }

    private suspend fun mergeVideoOnly(outputFile: File) {
        withContext(Dispatchers.IO) {
            val tempDir = getTempDir()
            val chunks = chunkDao.getChunksByDownloadId(task.id)
                .filter { it.filePath.contains("video_") }
                .sortedBy { it.chunkIndex }

            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                for (chunk in chunks) {
                    if (isCancelled.get() || isPaused.get()) break

                    val segmentFile = File(chunk.filePath)
                    if (!segmentFile.exists()) {
                        throw IllegalStateException("Video segment file not found: ${chunk.filePath}")
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
        }
    }

    private suspend fun mergeAudioVideo(outputFile: File) {
        withContext(Dispatchers.IO) {
            val tempDir = getTempDir()
            val videoChunks = chunkDao.getChunksByDownloadId(task.id)
                .filter { it.filePath.contains("video_") }
                .sortedBy { it.chunkIndex }
            val audioChunks = chunkDao.getChunksByDownloadId(task.id)
                .filter { it.filePath.contains("audio_") }
                .sortedBy { it.chunkIndex }

            val videoFile = File(tempDir, "video_only.mp4")
            val audioFile = File(tempDir, "audio_only.m4a")

            FileOutputStream(videoFile).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                for (chunk in videoChunks) {
                    val segmentFile = File(chunk.filePath)
                    if (segmentFile.exists()) {
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
            }

            FileOutputStream(audioFile).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                for (chunk in audioChunks) {
                    val segmentFile = File(chunk.filePath)
                    if (segmentFile.exists()) {
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
            }

            if (audioFile.exists() && audioFile.length() > 0) {
                interleaveAudioVideo(videoFile, audioFile, outputFile)
            } else {
                videoFile.inputStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }

            videoFile.delete()
            audioFile.delete()
            tempDir.deleteRecursively()
        }
    }

    private fun interleaveAudioVideo(videoFile: File, audioFile: File, outputFile: File) {
        val videoData = videoFile.readBytes()
        val audioData = audioFile.readBytes()

        val chunkSize = 512 * 1024
        FileOutputStream(outputFile).use { outputStream ->
            var videoOffset = 0
            var audioOffset = 0

            while (videoOffset < videoData.size || audioOffset < audioData.size) {
                if (videoOffset < videoData.size) {
                    val end = minOf(videoOffset + chunkSize, videoData.size)
                    outputStream.write(videoData, videoOffset, end - videoOffset)
                    videoOffset = end
                }
                if (audioOffset < audioData.size) {
                    val end = minOf(audioOffset + chunkSize, audioData.size)
                    outputStream.write(audioData, audioOffset, end - audioOffset)
                    audioOffset = end
                }
            }
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
        if (downloadDir.exists() || downloadDir.mkdirs()) {
            return downloadDir
        }
        val dir = File(context.getExternalFilesDir(null), "Downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTempDir(): File {
        val dir = File(context.getExternalFilesDir(null), "Downloads${File.separator}.dash_temp${File.separator}${task.id}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
