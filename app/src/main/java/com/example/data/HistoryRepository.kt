package com.example.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insert(item: HistoryEntity) {
        historyDao.insertHistory(item)
    }

    suspend fun deleteById(id: String) {
        historyDao.deleteHistoryById(id)
    }

    suspend fun clearAll() {
        historyDao.clearAllHistory()
    }
}
