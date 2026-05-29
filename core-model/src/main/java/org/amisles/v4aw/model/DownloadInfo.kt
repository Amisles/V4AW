package org.amisles.v4aw.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadInfo(
    @PrimaryKey
    val id: String = "",
    val videoTitle: String = "",
    val videoUrl: String = "",
    val videoSource: String = "",
    val thumbnailUrl: String? = null,
    val fileName: String = "",
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val speed: Long = 0L,
    val remainingTime: Long = 0L,
    val errorMessage: String? = null,
    val threadCount: Int = 1,
    val supportsRange: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
