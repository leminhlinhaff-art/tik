package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.TikWmApiService
import com.example.model.TaskStatus
import com.example.model.TikTokVideoTask
import com.example.utils.DownloadHelper
import com.example.utils.LinkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TikTokViewModel : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _tasks = MutableStateFlow<List<TikTokVideoTask>>(emptyList())
    val tasks: StateFlow<List<TikTokVideoTask>> = _tasks.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    /**
     * Cleans up links, keeping only TikTok link strings and removing irrelevant text and blank rows.
     */
    fun cleanLinks() {
        val currentText = _inputText.value
        val links = LinkParser.extractLinks(currentText)
        _inputText.value = links.joinToString("\n")
    }

    /**
     * Resets inputs and empties the queue.
     */
    fun refreshAll() {
        _inputText.value = ""
        _tasks.value = emptyList()
        _isResolving.value = false
    }

    /**
     * Extracts links and submits them to concurrently run resolution tasks.
     */
    fun analyzeAndFetch() {
        cleanLinks()
        val links = LinkParser.extractLinks(_inputText.value)
        if (links.isEmpty()) return

        _isResolving.value = true

        val newTasks = links.map { url ->
            TikTokVideoTask(
                id = UUID.randomUUID().toString(),
                originalUrl = url,
                status = TaskStatus.RESOLVING
            )
        }
        _tasks.value = newTasks

        viewModelScope.launch {
            newTasks.forEach { task ->
                launch {
                    fetchSingleTaskInfo(task)
                }
            }
        }
    }

    private suspend fun fetchSingleTaskInfo(task: TikTokVideoTask) {
        val result = TikWmApiService.fetchVideoInfo(task.originalUrl)
        
        _tasks.value = _tasks.value.map { currentTask ->
            if (currentTask.originalUrl == task.originalUrl) {
                if (result != null && result.code == 0 && result.data != null) {
                    val data = result.data
                    currentTask.copy(
                        status = TaskStatus.SUCCESS,
                        title = data.title?.trim()?.ifEmpty { null } ?: "Video không tiêu đề",
                        coverUrl = getFullUrl(data.cover),
                        videoPlayUrl = getFullUrl(data.play),
                        videoWmPlayUrl = getFullUrl(data.wmplay),
                        musicUrl = getFullUrl(data.music),
                        authorName = data.author?.nickname,
                        authorUsername = data.author?.uniqueId
                    )
                } else {
                    currentTask.copy(
                        status = TaskStatus.FAILED,
                        errorMessage = result?.msg ?: "Không tìm thấy video hoặc liên kết sai."
                    )
                }
            } else {
                currentTask
            }
        }
        checkFinishStatus()
    }

    private fun checkFinishStatus() {
        val anyActive = _tasks.value.any { it.status == TaskStatus.RESOLVING }
        _isResolving.value = anyActive
    }

    private fun getFullUrl(path: String?): String? {
        if (path == null) return null
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        return "https://www.tikwm.com$path"
    }

    /**
     * Triggers bulk downloads of all successfully resolved high-def videos without watermark.
     */
    fun downloadAllVideos(context: Context) {
        val successTasks = _tasks.value.filter { it.status == TaskStatus.SUCCESS }
        successTasks.forEach { task ->
            task.videoPlayUrl?.let { playUrl ->
                DownloadHelper.enqueueDownload(
                    context = context,
                    url = playUrl,
                    title = task.title ?: "tiktok_video",
                    isAudio = false,
                    id = task.id
                )
            }
        }
    }

    /**
     * Downloads an individual file.
     */
    fun downloadSingle(context: Context, task: TikTokVideoTask, isAudio: Boolean) {
        val downloadUrl = if (isAudio) task.musicUrl else task.videoPlayUrl
        if (downloadUrl != null) {
            DownloadHelper.enqueueDownload(
                context = context,
                url = downloadUrl,
                title = task.title ?: "tiktok_item",
                isAudio = isAudio,
                id = task.id
            )
        }
    }
}
