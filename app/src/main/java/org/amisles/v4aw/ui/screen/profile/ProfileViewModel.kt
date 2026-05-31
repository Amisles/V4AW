package org.amisles.v4aw.ui.screen.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amisles.v4aw.download.DownloadManager
import org.amisles.v4aw.i18n.Strings
import java.io.File
import javax.inject.Inject

data class ProfileUiState(
    val cacheSize: String = Strings.current.calculating,
    val downloadPath: String = "",
    val speedLimitKbps: Long = 0
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refreshCacheSize()
        refreshDownloadSettings()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = calculateCacheSize()
            _uiState.value = _uiState.value.copy(cacheSize = formatSize(size))
        }
    }

    fun refreshDownloadSettings() {
        val path = downloadManager.customDownloadPath
            ?: File(context.getExternalFilesDir(null), "Downloads").absolutePath
        _uiState.value = _uiState.value.copy(
            downloadPath = path,
            speedLimitKbps = downloadManager.speedLimitKbps
        )
    }

    fun setDownloadPath(path: String) {
        downloadManager.customDownloadPath = path.ifBlank { null }
        _uiState.value = _uiState.value.copy(downloadPath = path)
    }

    fun resetDownloadPath() {
        downloadManager.customDownloadPath = null
        val defaultPath = File(context.getExternalFilesDir(null), "Downloads").absolutePath
        _uiState.value = _uiState.value.copy(downloadPath = defaultPath)
    }

    fun setSpeedLimit(kbps: Long) {
        downloadManager.speedLimitKbps = kbps
        _uiState.value = _uiState.value.copy(speedLimitKbps = kbps)
    }

    fun clearCache() {
        viewModelScope.launch {
            deleteCache()
            refreshCacheSize()
        }
    }

    private suspend fun calculateCacheSize(): Long = withContext(Dispatchers.IO) {
        var size = 0L
        val cacheDir = context.cacheDir
        if (cacheDir.exists()) {
            size += getFolderSize(cacheDir)
        }
        val webviewCacheDir = File(context.applicationInfo.dataDir, "app_webview")
        if (webviewCacheDir.exists()) {
            size += getFolderSize(webviewCacheDir)
        }
        return@withContext size
    }

    private suspend fun deleteCache() = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        if (cacheDir.exists()) {
            deleteDir(cacheDir)
        }
        val webviewCacheDir = File(context.applicationInfo.dataDir, "app_webview")
        if (webviewCacheDir.exists()) {
            deleteDir(webviewCacheDir)
        }
    }

    private fun deleteDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                deleteDir(file)
            }
        }
        dir.delete()
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getFolderSize(file) else file.length()
            }
        } else {
            size = dir.length()
        }
        return size
    }

    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
