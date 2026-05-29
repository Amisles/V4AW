package org.amisles.v4aw.data.repository

import kotlinx.coroutines.flow.Flow
import org.amisles.v4aw.data.local.dao.HistoryDao
import org.amisles.v4aw.model.HistoryItem
import org.amisles.v4aw.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun getHistory(): Flow<List<HistoryItem>> {
        return historyDao.getAllHistory()
    }

    override suspend fun saveHistory(item: HistoryItem) {
        val existing = historyDao.getByUrl(item.url)
        if (existing != null) {
            historyDao.insertHistory(item.copy(id = existing.id, timestamp = System.currentTimeMillis()))
        } else {
            historyDao.insertHistory(item)
        }
    }

    override suspend fun deleteHistory(item: HistoryItem) {
        historyDao.deleteHistory(item)
    }

    override suspend fun clearHistory() {
        historyDao.clearHistory()
    }
}
