package org.amisles.v4aw.ui.screen.downloads

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.amisles.v4aw.model.DownloadInfo
import org.amisles.v4aw.model.DownloadStatus
import org.amisles.v4aw.download.DownloadManager
import org.amisles.v4aw.i18n.Strings
import javax.inject.Inject

data class DownloadsUiState(
    val selectedTab: DownloadTab = DownloadTab.DOWNLOADING,
    val downloadingItems: List<DownloadTask> = emptyList(),
    val completedItems: List<DownloadTask> = emptyList(),
    val failedItems: List<DownloadTask> = emptyList(),
    val selectedItems: List<DownloadTask> = emptyList(),
    val storageUsed: String = "0 GB",
    val storageTotal: String = "0 GB"
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.downloadProgress.collect { progressMap ->
                updateDownloads(progressMap.values.toList())
            }
        }

        updateStorageInfo()
    }

    private fun updateDownloads(downloads: List<DownloadInfo>) {
        val downloadingItems = downloads
            .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING }
            .map { it.toDownloadTask() }
        val completedItems = downloads
            .filter { it.status == DownloadStatus.COMPLETED }
            .map { it.toDownloadTask() }
        val failedItems = downloads
            .filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
            .map { it.toDownloadTask() }

        val (storageUsed, storageTotal) = getStorageInfo()

        _uiState.update {
            it.copy(
                downloadingItems = downloadingItems,
                completedItems = completedItems,
                failedItems = failedItems,
                storageUsed = storageUsed,
                storageTotal = storageTotal
            )
        }
    }

    private fun updateStorageInfo() {
        viewModelScope.launch {
            val (storageUsed, storageTotal) = getStorageInfo()

            _uiState.update {
                it.copy(
                    storageUsed = storageUsed,
                    storageTotal = storageTotal
                )
            }
        }
    }

    private fun getStorageInfo(): Pair<String, String> {
        return try {
            val file = context.getExternalFilesDir(null)
            if (file != null) {
                val statFs = StatFs(file.absolutePath)
                val blockSize = statFs.blockSizeLong
                val totalBlocks = statFs.blockCountLong
                val availableBlocks = statFs.availableBlocksLong
                
                val totalBytes = blockSize * totalBlocks
                val usedBytes = totalBytes - (blockSize * availableBlocks)
                
                val totalGb = totalBytes / (1024L * 1024L * 1024L)
                val usedGb = usedBytes / (1024L * 1024L * 1024L)
                
                Pair("$usedGb GB", "$totalGb GB")
            } else {
                Pair("0 GB", "0 GB")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair("0 GB", "0 GB")
        }
    }

    fun setSelectedTab(tab: DownloadTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun startDownload(
        id: String,
        title: String,
        url: String,
        videoSourceUrl: String,
        thumbnailUrl: String? = null
    ) {
        viewModelScope.launch {
            downloadManager.startDownload(
                id = id,
                title = title,
                url = url,
                videoSource = videoSourceUrl,
                thumbnailUrl = thumbnailUrl
            )
        }
    }

    fun togglePause(task: DownloadTask) {
        viewModelScope.launch {
            if (task.status == DownloadStatus.DOWNLOADING) {
                downloadManager.pauseDownload(task.id)
            } else if (task.status == DownloadStatus.PAUSED) {
                downloadManager.resumeDownload(task.id)
            }
        }
    }

    fun retryDownload(task: DownloadTask) {
        viewModelScope.launch {
            downloadManager.resumeDownload(task.id)
        }
    }

    fun deleteTask(task: DownloadTask) {
        viewModelScope.launch {
            downloadManager.deleteDownload(task.id)
            _uiState.update {
                it.copy(
                    selectedItems = it.selectedItems.filterNot { item -> item.id == task.id }
                )
            }
        }
    }

    fun toggleSelection(task: DownloadTask) {
        _uiState.update {
            val isSelected = it.selectedItems.any { item -> item.id == task.id }
            if (isSelected) {
                it.copy(selectedItems = it.selectedItems.filterNot { item -> item.id == task.id })
            } else {
                it.copy(selectedItems = it.selectedItems + task)
            }
        }
    }

    fun selectAll() {
        val currentItems = when (_uiState.value.selectedTab) {
            DownloadTab.DOWNLOADING -> _uiState.value.downloadingItems
            DownloadTab.COMPLETED -> _uiState.value.completedItems
            DownloadTab.FAILED -> _uiState.value.failedItems
        }
        _uiState.update { it.copy(selectedItems = currentItems) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptyList()) }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            _uiState.value.selectedItems.forEach { task ->
                downloadManager.deleteDownload(task.id)
            }
            _uiState.update { it.copy(selectedItems = emptyList()) }
        }
    }
}

private fun DownloadInfo.toDownloadTask(): DownloadTask {
    val fileSizeText = if (fileSize > 0) {
        formatFileSize(fileSize)
    } else {
        Strings.current.unknownSize
    }

    val speedText = if (status == DownloadStatus.DOWNLOADING && speed > 0) {
        formatSpeed(speed)
    } else {
        ""
    }

    val remainingTimeText = if (status == DownloadStatus.DOWNLOADING && speed > 0 && fileSize > downloadedBytes) {
        formatRemainingTime(fileSize - downloadedBytes, speed)
    } else {
        ""
    }

    val progress = if (fileSize > 0) {
        downloadedBytes.toFloat() / fileSize.toFloat()
    } else {
        0f
    }

    return DownloadTask(
        id = id,
        title = videoTitle,
        fileName = fileName,
        fileSize = fileSizeText,
        progress = progress,
        speed = speedText,
        remainingTime = remainingTimeText,
        status = status,
        errorMessage = errorMessage,
        filePath = filePath,
        thumbnailUrl = thumbnailUrl,
        threadCount = threadCount,
        supportsRange = supportsRange
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }
}

private fun formatRemainingTime(remainingBytes: Long, bytesPerSecond: Long): String {
    val seconds = remainingBytes / bytesPerSecond
    return when {
        seconds >= 3600 -> Strings.current.timeHours.format(seconds / 3600)
        seconds >= 60 -> Strings.current.timeMinutes.format(seconds / 60)
        else -> Strings.current.timeSeconds.format(seconds)
    }
}

data class DownloadTask(
    val id: String,
    val title: String,
    val fileName: String,
    val fileSize: String,
    val progress: Float,
    val speed: String,
    val remainingTime: String,
    val status: DownloadStatus,
    val errorMessage: String? = null,
    val filePath: String? = null,
    val thumbnailUrl: String? = null,
    val threadCount: Int = 1,
    val supportsRange: Boolean = false
)
