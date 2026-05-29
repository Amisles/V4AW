package org.amisles.v4aw.domain.usecase

import org.amisles.v4aw.model.HistoryItem
import org.amisles.v4aw.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    fun getHistory(): Flow<List<HistoryItem>> {
        return historyRepository.getHistory()
    }

    suspend fun saveHistory(item: HistoryItem) {
        historyRepository.saveHistory(item)
    }

    suspend fun deleteHistory(item: HistoryItem) {
        historyRepository.deleteHistory(item)
    }

    suspend fun clearHistory() {
        historyRepository.clearHistory()
    }
}
