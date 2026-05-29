package org.amisles.v4aw.data.local.dao

import androidx.room.*
import org.amisles.v4aw.model.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): HistoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryItem)

    @Delete
    suspend fun deleteHistory(history: HistoryItem)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
