package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("DELETE FROM download_history")
    suspend fun clearAllHistory()
}
