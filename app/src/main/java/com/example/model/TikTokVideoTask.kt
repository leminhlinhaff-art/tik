package com.example.model

enum class TaskStatus {
    IDLE,
    RESOLVING,
    SUCCESS,
    FAILED
}

data class TikTokVideoTask(
    val id: String,
    val originalUrl: String,
    val status: TaskStatus = TaskStatus.IDLE,
    val title: String? = null,
    val coverUrl: String? = null,
    val videoPlayUrl: String? = null,
    val videoWmPlayUrl: String? = null,
    val musicUrl: String? = null,
    val authorName: String? = null,
    val authorUsername: String? = null,
    val errorMessage: String? = null
)
