package org.amisles.v4aw.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.amisles.v4aw.model.DownloadChunkInfo

@Dao
interface DownloadChunkDao {
    @Query("SELECT * FROM download_chunks WHERE downloadId = :downloadId ORDER BY chunkIndex")
    suspend fun getChunksByDownloadId(downloadId: String): List<DownloadChunkInfo>

    @Query("SELECT * FROM download_chunks WHERE downloadId = :downloadId ORDER BY chunkIndex")
    fun getChunksFlowByDownloadId(downloadId: String): Flow<List<DownloadChunkInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: DownloadChunkInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DownloadChunkInfo>)

    @Update
    suspend fun updateChunk(chunk: DownloadChunkInfo)

    @Query("DELETE FROM download_chunks WHERE downloadId = :downloadId")
    suspend fun deleteChunksByDownloadId(downloadId: String)

    @Query("SELECT SUM(downloadedBytes) FROM download_chunks WHERE downloadId = :downloadId")
    suspend fun getTotalDownloadedBytes(downloadId: String): Long?

    @Query("SELECT COUNT(*) FROM download_chunks WHERE downloadId = :downloadId AND completed = 1")
    suspend fun getCompletedChunkCount(downloadId: String): Int
}
