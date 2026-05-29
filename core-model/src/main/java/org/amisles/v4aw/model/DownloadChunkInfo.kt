package org.amisles.v4aw.model

import androidx.room.Entity

@Entity(tableName = "download_chunks", primaryKeys = ["downloadId", "chunkIndex"])
data class DownloadChunkInfo(
    val downloadId: String = "",
    val chunkIndex: Int = 0,
    val startByte: Long = 0L,
    val endByte: Long = 0L,
    val downloadedBytes: Long = 0L,
    val completed: Boolean = false,
    val filePath: String = ""
)
