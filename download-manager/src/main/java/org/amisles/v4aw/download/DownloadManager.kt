package org.amisles.v4aw.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.amisles.v4aw.model.DownloadChunkInfo
import org.amisles.v4aw.model.DownloadInfo
import org.amisles.v4aw.model.DownloadStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val chunkDao: DownloadChunkDao
) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val BUFFER_SIZE = 8192
        private const val SPEED_UPDATE_INTERVAL = 500L
        private const val MAX_THREAD_COUNT = 4
        private const val MIN_CHUNK_SIZE = 1024 * 1024L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val FILE_SIZE_THRESHOLD_FOR_MULTI_THREAD = 2 * 1024 * 1024L

        private val HLS_EXTENSIONS = setOf(".m3u8")
        private val DASH_EXTENSIONS = setOf(".mpd")

        private const val NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID_PREFIX = 1000

        const val PREFS_NAME = "download_prefs"
        const val KEY_DOWNLOAD_PATH = "download_path"
        const val KEY_SPEED_LIMIT_KBPS = "speed_limit_kbps"
        const val DEFAULT_SPEED_LIMIT_KBPS = 0L
    }

    private val downloadPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var speedLimitKbps: Long
        get() = downloadPrefs.getLong(KEY_SPEED_LIMIT_KBPS, DEFAULT_SPEED_LIMIT_KBPS)
        set(value) = downloadPrefs.edit().putLong(KEY_SPEED_LIMIT_KBPS, value).apply()

    var customDownloadPath: String?
        get() = downloadPrefs.getString(KEY_DOWNLOAD_PATH, null)
        set(value) = downloadPrefs.edit().putString(KEY_DOWNLOAD_PATH, value).apply()

    fun getEffectiveDownloadDir(): File {
        val customPath = customDownloadPath
        if (customPath != null) {
            val dir = File(customPath)
            if (dir.exists() || dir.mkdirs()) {
                return dir
            }
        }
        val dir = File(context.getExternalFilesDir(null), "Downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Download progress notifications"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun showCompletionNotification(task: DownloadInfo) {
        try {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(task.videoTitle)
                .setContentText("Download completed")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID_PREFIX + task.id.hashCode(), notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show download notification: ${e.message}")
        }
    }

    private fun showFailedNotification(task: DownloadInfo) {
        try {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(task.videoTitle)
                .setContentText("Download failed: ${task.errorMessage}")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID_PREFIX + task.id.hashCode(), notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show download notification: ${e.message}")
        }
    }

    private fun buildRateLimitedClient(): OkHttpClient {
        val limitKbps = speedLimitKbps
        return if (limitKbps > 0) {
            client.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val originalResponse = chain.proceed(chain.request())
                    val limitBytesPerSec = limitKbps * 1024
                    originalResponse.newBuilder()
                        .body(RateLimitedResponseBody(originalResponse.body!!, limitBytesPerSec))
                        .build()
                }
                .build()
        } else {
            client
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val downloadDispatcher = Dispatchers.IO.limitedParallelism(MAX_THREAD_COUNT)
    private val activeDownloads = ConcurrentHashMap<String, DownloadJobBase>()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadInfo>> = _downloadProgress.asStateFlow()

    init {
        scope.launch {
            downloadDao.getAllTasks().collect { tasks ->
                val map = tasks.associateBy { it.id }
                _downloadProgress.value = map
            }
        }
    }

    fun startDownload(
        id: String,
        title: String,
        url: String,
        videoSource: String,
        thumbnailUrl: String? = null
    ) {
        scope.launch {
            val existingTask = downloadDao.getTaskById(id)
            if (existingTask != null && existingTask.status == DownloadStatus.DOWNLOADING) {
                return@launch
            }

            val fileName = generateFileName(title, videoSource)
            val task = DownloadInfo(
                id = id,
                videoTitle = title,
                videoUrl = url,
                videoSource = videoSource,
                thumbnailUrl = thumbnailUrl,
                fileName = fileName,
                status = DownloadStatus.DOWNLOADING,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            downloadDao.insertTask(task)

            val effectiveClient = buildRateLimitedClient()
            val effectiveDir = getEffectiveDownloadDir()

            val job: DownloadJobBase = when {
                isHlsFormat(videoSource) -> HlsDownloadJob(
                    task = task,
                    client = effectiveClient,
                    context = context,
                    downloadDao = downloadDao,
                    chunkDao = chunkDao,
                    dispatcher = downloadDispatcher,
                    onProgress = { info -> updateProgress(info) },
                    downloadDir = effectiveDir
                )
                isDashFormat(videoSource) -> DashDownloadJob(
                    task = task,
                    client = effectiveClient,
                    context = context,
                    downloadDao = downloadDao,
                    chunkDao = chunkDao,
                    dispatcher = downloadDispatcher,
                    onProgress = { info -> updateProgress(info) },
                    downloadDir = effectiveDir
                )
                else -> DownloadJob(
                    task = task,
                    client = effectiveClient,
                    context = context,
                    downloadDao = downloadDao,
                    chunkDao = chunkDao,
                    dispatcher = downloadDispatcher,
                    onProgress = { info -> updateProgress(info) },
                    downloadDir = effectiveDir
                )
            }

            activeDownloads[id] = job
            job.start()
        }
    }

    fun pauseDownload(id: String) {
        scope.launch {
            activeDownloads[id]?.pause()
            activeDownloads.remove(id)
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            val task = downloadDao.getTaskById(id) ?: return@launch
            if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) {
                return@launch
            }

            val updatedTask = task.copy(status = DownloadStatus.DOWNLOADING, updatedAt = System.currentTimeMillis())
            downloadDao.updateTask(updatedTask)

            val effectiveClient = buildRateLimitedClient()
            val effectiveDir = getEffectiveDownloadDir()

            val job: DownloadJobBase = when {
                isHlsFormat(task.videoSource) -> HlsDownloadJob(
                    task = updatedTask,
                    client = effectiveClient,
                    context = context,
                    downloadDao = downloadDao,
                    chunkDao = chunkDao,
                    dispatcher = downloadDispatcher,
                    onProgress = { info -> updateProgress(info) },
                    downloadDir = effectiveDir
                )
                isDashFormat(task.videoSource) -> DashDownloadJob(
                    task = updatedTask,
                    client = effectiveClient,
                    context = context,
                    downloadDao = downloadDao,
                    chunkDao = chunkDao,
                    dispatcher = downloadDispatcher,
                    onProgress = { info -> updateProgress(info) },
                    downloadDir = effectiveDir
                )
                else -> DownloadJob(
                    task = updatedTask,
                    client = effectiveClient,
                    context = context,
                    downloadDao = downloadDao,
                    chunkDao = chunkDao,
                    dispatcher = downloadDispatcher,
                    onProgress = { info -> updateProgress(info) },
                    downloadDir = effectiveDir
                )
            }

            activeDownloads[id] = job
            job.start()
        }
    }

    fun cancelDownload(id: String) {
        scope.launch {
            activeDownloads[id]?.cancel()
            activeDownloads.remove(id)

            val task = downloadDao.getTaskById(id) ?: return@launch
            val updatedTask = task.copy(
                status = DownloadStatus.CANCELLED,
                updatedAt = System.currentTimeMillis()
            )
            downloadDao.updateTask(updatedTask)

            task.filePath?.let { path -> File(path).delete() }
            chunkDao.deleteChunksByDownloadId(id)
        }
    }

    fun deleteDownload(id: String, deleteFile: Boolean = true) {
        scope.launch {
            activeDownloads[id]?.cancel()
            activeDownloads.remove(id)

            val task = downloadDao.getTaskById(id)
            if (task != null) {
                if (deleteFile) {
                    task.filePath?.let { path -> File(path).delete() }
                    val hlsTempDir = File(context.getExternalFilesDir(null), "Downloads${File.separator}.hls_temp${File.separator}${task.id}")
                    if (hlsTempDir.exists()) hlsTempDir.deleteRecursively()
                    val dashTempDir = File(context.getExternalFilesDir(null), "Downloads${File.separator}.dash_temp${File.separator}${task.id}")
                    if (dashTempDir.exists()) dashTempDir.deleteRecursively()
                }
                downloadDao.deleteTaskById(id)
                chunkDao.deleteChunksByDownloadId(id)
            }
        }
    }

    private fun updateProgress(info: DownloadInfo) {
        scope.launch {
            downloadDao.updateTask(info)
            if (info.status == DownloadStatus.COMPLETED) {
                showCompletionNotification(info)
            } else if (info.status == DownloadStatus.FAILED) {
                showFailedNotification(info)
            }
        }
    }

    private fun generateFileName(title: String, url: String): String {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        val extension = when {
            url.contains(".m3u8", ignoreCase = true) -> "mp4"
            url.contains(".mpd", ignoreCase = true) -> "mp4"
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".webm", ignoreCase = true) -> "webm"
            url.contains(".flv", ignoreCase = true) -> "flv"
            url.contains(".mov", ignoreCase = true) -> "mov"
            url.contains(".ts", ignoreCase = true) -> "ts"
            url.contains(".m4v", ignoreCase = true) -> "m4v"
            else -> "mp4"
        }
        return "${cleanTitle.take(50)}_${System.currentTimeMillis()}.$extension"
    }

    private fun isHlsFormat(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return HLS_EXTENSIONS.any { lowerUrl.contains(it) }
    }

    private fun isDashFormat(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return DASH_EXTENSIONS.any { lowerUrl.contains(it) }
    }

    private class DownloadJob(
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

        override suspend fun start() {
            try {
                val fileInfo = getFileInfo(task.videoSource)
                val supportsRange = fileInfo.acceptRanges && fileInfo.size > 0

                if (fileInfo.size > 0) {
                    task = task.copy(fileSize = fileInfo.size, supportsRange = supportsRange)
                }

                val downloadDir = getDownloadDirectory()
                val file = File(downloadDir, task.fileName)

                if (task.filePath == null) {
                    task = task.copy(filePath = file.absolutePath)
                }

                if (supportsRange && fileInfo.size >= FILE_SIZE_THRESHOLD_FOR_MULTI_THREAD) {
                    downloadMultiThread(file, fileInfo.size)
                } else {
                    downloadSingleThread(file)
                }

                if (!isCancelled.get()) {
                    val downloadedFile = File(task.filePath ?: "")
                    val actualSize = downloadedFile.length()

                    if (task.fileSize > 0 && actualSize < task.fileSize * 0.9) {
                        throw IllegalStateException("Download incomplete: expected ${task.fileSize} bytes, got $actualSize bytes")
                    }

                    task = task.copy(
                        status = DownloadStatus.COMPLETED,
                        downloadedBytes = actualSize,
                        updatedAt = System.currentTimeMillis()
                    )
                    onProgress(task)
                    chunkDao.deleteChunksByDownloadId(task.id)
                }
            } catch (e: Exception) {
                if (!isCancelled.get()) {
                    Log.e(TAG, "Download failed: ${task.id}", e)
                    task = task.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Unknown download error",
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

        private data class FileInfo(
            val size: Long,
            val contentType: String?,
            val acceptRanges: Boolean
        )

        private suspend fun getFileInfo(url: String): FileInfo {
            return withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(url).head().build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP error: ${response.code}")
                    }

                    val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                    val contentType = response.header("Content-Type")
                    val acceptRanges = response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true

                    response.close()
                    FileInfo(contentLength, contentType, acceptRanges)
                } catch (e: Exception) {
                    FileInfo(0, null, false)
                }
            }
        }

        private suspend fun downloadMultiThread(file: File, fileSize: Long) {
            val threadCount = calculateThreadCount(fileSize)
            val chunkSize = fileSize / threadCount
            val chunks = mutableListOf<DownloadChunkInfo>()

            val existingChunks = chunkDao.getChunksByDownloadId(task.id)
            val isResuming = existingChunks.isNotEmpty()

            if (isResuming) {
                for (chunk in existingChunks) {
                    chunks.add(chunk)
                    totalDownloadedBytes.addAndGet(chunk.downloadedBytes)
                }
            } else {
                for (i in 0 until threadCount) {
                    val startByte = i * chunkSize
                    val endByte = if (i == threadCount - 1) fileSize - 1 else (i + 1) * chunkSize - 1
                    val chunk = DownloadChunkInfo(
                        downloadId = task.id,
                        chunkIndex = i,
                        startByte = startByte,
                        endByte = endByte,
                        downloadedBytes = 0L,
                        completed = false,
                        filePath = file.absolutePath
                    )
                    chunks.add(chunk)
                    chunkDao.insertChunk(chunk)
                }
            }

            task = task.copy(threadCount = threadCount, supportsRange = true)
            onProgress(task)

            if (!file.exists()) {
                file.createNewFile()
            }

            val raf = RandomAccessFile(file, "rw")
            raf.setLength(fileSize)
            raf.close()

            val chunkJobs = chunks.filter { !it.completed }.map { chunk ->
                jobScope.async(dispatcher) {
                    downloadChunkWithRetry(chunk)
                }
            }

            val progressJob = jobScope.launch {
                trackProgress(fileSize)
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
                throw IllegalStateException("${incompleteChunks.size} chunk(s) failed to download")
            }
        }

        private suspend fun downloadChunkWithRetry(chunk: DownloadChunkInfo) {
            var retryCount = 0
            var lastError: Exception? = null

            while (retryCount < MAX_RETRIES) {
                if (isCancelled.get() || isPaused.get()) return

                try {
                    downloadChunk(chunk)
                    return
                } catch (e: Exception) {
                    lastError = e
                    retryCount++
                    Log.w(TAG, "Chunk ${chunk.chunkIndex} attempt $retryCount failed: ${e.message}")
                    if (retryCount < MAX_RETRIES && !isCancelled.get() && !isPaused.get()) {
                        delay(RETRY_DELAY_MS)
                    }
                }
            }

            throw lastError ?: IllegalStateException("Chunk ${chunk.chunkIndex} failed after $MAX_RETRIES retries")
        }

        private suspend fun downloadChunk(chunk: DownloadChunkInfo) {
            withContext(Dispatchers.IO) {
                val currentDownloaded = chunk.downloadedBytes
                val startByte = chunk.startByte + currentDownloaded
                val endByte = chunk.endByte

                if (startByte > endByte) {
                    updateChunkCompletion(chunk)
                    return@withContext
                }

                val request = Request.Builder()
                    .url(task.videoSource)
                    .header("Range", "bytes=$startByte-$endByte")
                    .build()

                val response = client.newCall(request).execute()

                val expectedCode = if (currentDownloaded > 0) HttpURLConnection.HTTP_PARTIAL else HttpURLConnection.HTTP_PARTIAL
                if (response.code != HttpURLConnection.HTTP_PARTIAL && response.code != HttpURLConnection.HTTP_OK) {
                    response.close()
                    throw IllegalStateException("HTTP error for chunk ${chunk.chunkIndex}: ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Response body is null for chunk ${chunk.chunkIndex}")
                val inputStream = body.byteStream()
                val raf = RandomAccessFile(chunk.filePath, "rw")
                raf.seek(startByte)

                val buffer = ByteArray(BUFFER_SIZE)
                var chunkDownloaded = currentDownloaded

                try {
                    while (true) {
                        if (isCancelled.get() || isPaused.get()) break

                        val read = inputStream.read(buffer)
                        if (read == -1) break

                        raf.write(buffer, 0, read)
                        chunkDownloaded += read.toLong()
                        totalDownloadedBytes.addAndGet(read.toLong())

                        if (chunkDownloaded % (BUFFER_SIZE * 4) == 0L) {
                            chunkDao.updateChunk(
                                chunk.copy(downloadedBytes = chunkDownloaded)
                            )
                        }
                    }
                } finally {
                    raf.close()
                    inputStream.close()
                    response.close()
                }

                if (!isCancelled.get() && !isPaused.get()) {
                    chunkDao.updateChunk(
                        chunk.copy(downloadedBytes = chunkDownloaded)
                    )

                    if (chunkDownloaded >= (chunk.endByte - chunk.startByte + 1)) {
                        updateChunkCompletion(chunk)
                    }
                }
            }
        }

        private suspend fun updateChunkCompletion(chunk: DownloadChunkInfo) {
            chunkDao.updateChunk(chunk.copy(completed = true, downloadedBytes = chunk.endByte - chunk.startByte + 1))
        }

        private suspend fun trackProgress(fileSize: Long) {
            var lastSpeedUpdate = System.currentTimeMillis()
            var lastBytesRead = totalDownloadedBytes.get()

            while (currentCoroutineContext().isActive) {
                delay(SPEED_UPDATE_INTERVAL)

                if (isCancelled.get() || isPaused.get()) break

                val currentBytes = totalDownloadedBytes.get()
                val now = System.currentTimeMillis()
                val timeDiff = (now - lastSpeedUpdate).toFloat() / 1000f
                val speed = if (timeDiff > 0) ((currentBytes - lastBytesRead).toFloat() / timeDiff).toLong() else 0L
                val remainingTime = if (speed > 0 && fileSize > 0) {
                    ((fileSize - currentBytes) / speed).toLong()
                } else 0L

                task = task.copy(
                    downloadedBytes = currentBytes,
                    speed = speed,
                    remainingTime = remainingTime,
                    updatedAt = now
                )
                onProgress(task)

                lastSpeedUpdate = now
                lastBytesRead = currentBytes
            }
        }

        private suspend fun downloadSingleThread(file: File) {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(task.videoSource)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP error: ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Response body is null")
                val totalSize = body.contentLength()

                if (totalSize > 0) {
                    task = task.copy(fileSize = totalSize)
                }

                val outputStream = if (task.downloadedBytes > 0 && file.exists()) {
                    java.io.FileOutputStream(file, true)
                } else {
                    java.io.FileOutputStream(file)
                }

                val inputStream = body.byteStream()

                if (task.downloadedBytes > 0) {
                    inputStream.skip(task.downloadedBytes)
                }

                var bytesRead = task.downloadedBytes
                totalDownloadedBytes.set(bytesRead)
                val buffer = ByteArray(BUFFER_SIZE)
                var lastSpeedUpdate = System.currentTimeMillis()
                var lastBytesRead = bytesRead

                while (true) {
                    if (isCancelled.get() || isPaused.get()) break

                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    outputStream.write(buffer, 0, read)
                    bytesRead += read
                    totalDownloadedBytes.set(bytesRead)

                    val now = System.currentTimeMillis()
                    if (now - lastSpeedUpdate >= SPEED_UPDATE_INTERVAL) {
                        val timeDiff = (now - lastSpeedUpdate).toFloat() / 1000f
                        val speed = if (timeDiff > 0) ((bytesRead - lastBytesRead).toFloat() / timeDiff).toLong() else 0L
                        val remainingTime = if (speed > 0 && totalSize > 0) {
                            ((totalSize - bytesRead) / speed).toLong()
                        } else 0L

                        task = task.copy(
                            downloadedBytes = bytesRead,
                            speed = speed,
                            remainingTime = remainingTime,
                            updatedAt = now
                        )
                        onProgress(task)

                        lastSpeedUpdate = now
                        lastBytesRead = bytesRead
                    }
                }

                outputStream.close()
                inputStream.close()
                response.close()
            }
        }

        private fun calculateThreadCount(fileSize: Long): Int {
            if (fileSize < FILE_SIZE_THRESHOLD_FOR_MULTI_THREAD) return 1
            val count = (fileSize / MIN_CHUNK_SIZE).toInt().coerceIn(2, MAX_THREAD_COUNT)
            return count
        }

        private fun getDownloadDirectory(): File {
            if (downloadDir.exists() || downloadDir.mkdirs()) {
                return downloadDir
            }
            val dir = File(context.getExternalFilesDir(null), "Downloads")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }
}
