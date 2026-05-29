package org.amisles.v4aw.domain.repository

import org.amisles.v4aw.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getHistory(): Flow<List<HistoryItem>>
    suspend fun saveHistory(item: HistoryItem)
    suspend fun deleteHistory(item: HistoryItem)
    suspend fun clearHistory()
}