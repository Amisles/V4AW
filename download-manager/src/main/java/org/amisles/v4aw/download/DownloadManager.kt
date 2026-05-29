package org.amisles.v4aw.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.amisles.v4aw.model.DownloadInfo
import org.amisles.v4aw.model.DownloadStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    companion object {
        private const val BUFFER_SIZE = 8192
        private const val SPEED_UPDATE_INTERVAL = 500L

        private val VALID_VIDEO_CONTENT_TYPES = setOf(
            "video/mp4", "video/webm", "video/x-flv", "video/quicktime",
            "video/mp2t", "video/x-m4v", "application/x-mpegURL"
        )

        private val STREAMING_EXTENSIONS = setOf(".m3u8", ".mpd")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = ConcurrentHashMap<String, DownloadJob>()

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
            if (isStreamingFormat(videoSource)) {
                val task = DownloadInfo(
                    id = id,
                    videoTitle = title,
                    videoUrl = url,
                    videoSource = videoSource,
                    thumbnailUrl = thumbnailUrl,
                    fileName = generateFileName(title, videoSource),
                    status = DownloadStatus.FAILED,
                    errorMessage = "Streaming format cannot be downloaded directly",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                downloadDao.insertTask(task)
                return@launch
            }

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

            val job = DownloadJob(
                task = task,
                client = client,
                context = context,
                downloadDao = downloadDao,
                onProgress = { info ->
                    updateProgress(info)
                }
            )

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

            val job = DownloadJob(
                task = updatedTask,
                client = client,
                context = context,
                downloadDao = downloadDao,
                onProgress = { info ->
                    updateProgress(info)
                }
            )

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

            task.filePath?.let { path ->
                File(path).delete()
            }
        }
    }

    fun deleteDownload(id: String, deleteFile: Boolean = true) {
        scope.launch {
            activeDownloads[id]?.cancel()
            activeDownloads.remove(id)

            val task = downloadDao.getTaskById(id)
            if (task != null) {
                if (deleteFile) {
                    task.filePath?.let { path ->
                        File(path).delete()
                    }
                }
                downloadDao.deleteTaskById(id)
            }
        }
    }

    private fun updateProgress(info: DownloadInfo) {
        scope.launch {
            downloadDao.updateTask(info)
        }
    }

    private fun generateFileName(title: String, url: String): String {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        val extension = when {
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

    private fun isStreamingFormat(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return STREAMING_EXTENSIONS.any { lowerUrl.contains(it) }
    }

    private class DownloadJob(
        private var task: DownloadInfo,
        private val client: OkHttpClient,
        private val context: Context,
        private val downloadDao: DownloadDao,
        private val onProgress: (DownloadInfo) -> Unit
    ) {
        private var isPaused = false
        private var isCancelled = false
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        suspend fun start() {
            try {
                val fileInfo = getFileInfo(task.videoSource)

                if (fileInfo.size > 0) {
                    task = task.copy(fileSize = fileInfo.size)
                }

                val downloadDir = getDownloadDirectory()
                val file = File(downloadDir, task.fileName)

                if (task.filePath == null) {
                    task = task.copy(filePath = file.absolutePath)
                }

                downloadSingleThread(file)

                if (!isCancelled) {
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
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    task = task.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Unknown download error",
                        updatedAt = System.currentTimeMillis()
                    )
                    onProgress(task)
                }
            }
        }

        fun pause() {
            isPaused = true
            scope.cancel()
            task = task.copy(
                status = DownloadStatus.PAUSED,
                updatedAt = System.currentTimeMillis()
            )
            onProgress(task)
        }

        fun cancel() {
            isCancelled = true
            scope.cancel()
        }

        private data class FileInfo(
            val size: Long,
            val contentType: String?
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

                    response.close()
                    FileInfo(contentLength, contentType)
                } catch (e: Exception) {
                    FileInfo(0, null)
                }
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
                    FileOutputStream(file, true)
                } else {
                    FileOutputStream(file)
                }

                val inputStream = body.byteStream()

                if (task.downloadedBytes > 0) {
                    inputStream.skip(task.downloadedBytes)
                }

                var bytesRead = task.downloadedBytes
                val buffer = ByteArray(BUFFER_SIZE)
                var lastSpeedUpdate = System.currentTimeMillis()
                var lastBytesRead = bytesRead

                while (true) {
                    if (isCancelled || isPaused) {
                        break
                    }

                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    outputStream.write(buffer, 0, read)
                    bytesRead += read

                    val now = System.currentTimeMillis()
                    if (now - lastSpeedUpdate >= SPEED_UPDATE_INTERVAL) {
                        val timeDiff = (now - lastSpeedUpdate).toFloat() / 1000f
                        val speed = if (timeDiff > 0) ((bytesRead - lastBytesRead).toFloat() / timeDiff).toLong() else 0L
                        val remainingTime = if (speed > 0 && totalSize > 0) {
                            ((totalSize - bytesRead) / speed).toLong()
                        } else {
                            0L
                        }

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

                    task = task.copy(downloadedBytes = bytesRead, updatedAt = now)
                }

                outputStream.close()
                inputStream.close()
                response.close()
            }
        }

        private fun getDownloadDirectory(): File {
            val dir = File(context.getExternalFilesDir(null), "Downloads")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }
}
