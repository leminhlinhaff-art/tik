package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class HistoryEntity(
    @PrimaryKey val id: String, // Typically the TikTok video ID or unique key
    val originalUrl: String,
    val title: String,
    val coverUrl: String?,
    val videoPlayUrl: String?,
    val musicUrl: String?,
    val authorUsername: String?,
    val timestamp: Long = System.currentTimeMillis()
)
