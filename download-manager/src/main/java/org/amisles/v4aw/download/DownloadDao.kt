package org.amisles.v4aw.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.amisles.v4aw.model.DownloadInfo

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING' OR status = 'PAUSED' ORDER BY updatedAt DESC")
    fun getDownloadingTasks(): Flow<List<DownloadInfo>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun getCompletedTasks(): Flow<List<DownloadInfo>>

    @Query("SELECT * FROM downloads WHERE status = 'FAILED' OR status = 'CANCELLED' ORDER BY updatedAt DESC")
    fun getFailedTasks(): Flow<List<DownloadInfo>>

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<DownloadInfo>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadInfo)

    @Update
    suspend fun updateTask(task: DownloadInfo)

    @Delete
    suspend fun deleteTask(task: DownloadInfo)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED' OR status = 'CANCELLED' OR status = 'FAILED'")
    suspend fun clearOldTasks()
}
