package com.example.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object DownloadHelper {
    /**
     * Downloads a file using Android's system DownloadManager service.
     * Sanitizes file names to prevent system errors.
     */
    fun enqueueDownload(
        context: Context,
        url: String,
        title: String,
        isAudio: Boolean = false,
        id: String? = null
    ): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return -1L

        val sanitizedTitle = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .ifEmpty { "tiktok_video" }
            .take(60)

        val uniqueId = id ?: System.currentTimeMillis().toString()
        val extension = if (isAudio) "mp3" else "mp4"
        val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
        val fileName = "TikTok_${uniqueId}_${sanitizedTitle}.$extension"

        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri).apply {
            setTitle(if (isAudio) "🎵 Audio - $sanitizedTitle" else "🎥 Video - $sanitizedTitle")
            setDescription("Tải xuống từ TikTok Bulk Downloader")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType(mimeType)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        try {
            val downloadId = downloadManager.enqueue(request)
            Toast.makeText(
                context, 
                "Bắt đầu tải: ${sanitizedTitle.take(25)}...", 
                Toast.LENGTH_SHORT
            ).show()
            return downloadId
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context, 
                "Lỗi tải xuống: ${e.localizedMessage}", 
                Toast.LENGTH_LONG
            ).show()
            return -1L
        }
    }

    /**
     * Return the readable string of the storage location.
     */
    fun getStorageLocationDescription(): String {
        return "Bộ nhớ trong > Download (Tải xuống)"
    }

    /**
     * Opens the system Downloads folder.
     */
    fun openDownloadsFolder(context: Context) {
        val intent = android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(
                    context, 
                    "Vị trí lưu: Tải xuống\nKhông thể mở trực tiếp trình quản lý tệp.", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
