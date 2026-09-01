package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.data.model.ActiveDownloadTask
import com.example.data.model.DownloadPlatform
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadableVideoInfo
import com.example.data.model.VideoFormatOption
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * 🚀 AppDownloadManager
 * User-Agent হেডার সহ হাই-স্পিড ব্যাকগ্রাউন্ড ডাউনলোড ও লাইভ ট্র্যাকার।
 */
object AppDownloadManager {
    private const val TAG = "AppDownloadManager"
    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val _activeDownloads = MutableStateFlow<List<ActiveDownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownloadTask>> = _activeDownloads.asStateFlow()

    private var trackingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startDownload(
        context: Context,
        videoInfo: DownloadableVideoInfo,
        format: VideoFormatOption
    ): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            Toast.makeText(context, "Download service not available", Toast.LENGTH_SHORT).show()
            return -1L
        }

        try {
            val cleanTitle = videoInfo.title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .take(50)
                .ifBlank { "Video_${System.currentTimeMillis() % 10000}" }

            val ext = format.extension.lowercase().ifBlank { if (format.isAudioOnly) "mp3" else "mp4" }
            val fileName = "${cleanTitle}_${System.currentTimeMillis() % 1000}.$ext"

            val targetFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )

            // 🎯 গুগল ভিডিও ব্লক এড়াতে User-Agent এবং হেডার যুক্ত করা হয়েছে
            val request = DownloadManager.Request(format.downloadUrl.toUri()).apply {
                setTitle(cleanTitle)
                setDescription("Downloading from ${videoInfo.platform.label}...")
                setMimeType(if (format.isAudioOnly) "audio/mpeg" else "video/mp4")
                addRequestHeader("User-Agent", BROWSER_USER_AGENT)
                addRequestHeader("Accept", "*/*")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)

            val newTask = ActiveDownloadTask(
                downloadId = downloadId,
                title = cleanTitle,
                platform = videoInfo.platform,
                formatLabel = format.qualityLabel,
                progressPercent = 0,
                status = DownloadStatus.DOWNLOADING,
                localFilePath = targetFile.absolutePath
            )

            _activeDownloads.update { current -> listOf(newTask) + current.filter { it.downloadId != downloadId } }

            startTrackingProgress(context)

            Toast.makeText(context, "⚡ Download started: $cleanTitle", Toast.LENGTH_SHORT).show()
            return downloadId

        } catch (e: Exception) {
            Log.e(TAG, "Download start failed: ${e.message}", e)
            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    private fun startTrackingProgress(context: Context) {
        if (trackingJob?.isActive == true) return

        trackingJob = scope.launch {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            while (isActive) {
                val currentTasks = _activeDownloads.value
                if (currentTasks.isEmpty() || currentTasks.all { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }) {
                    break
                }

                val updatedList = currentTasks.map { task ->
                    if (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.FAILED) {
                        task
                    } else {
                        queryDownloadStatus(downloadManager, task, context)
                    }
                }

                _activeDownloads.value = updatedList
                delay(600L)
            }
        }
    }

    private fun queryDownloadStatus(
        downloadManager: DownloadManager,
        task: ActiveDownloadTask,
        context: Context
    ): ActiveDownloadTask {
        val query = DownloadManager.Query().setFilterById(task.downloadId)
        try {
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                    val statusInt = if (statusCol != -1) cursor.getInt(statusCol) else DownloadManager.STATUS_RUNNING
                    val downloadedBytes = if (downloadedCol != -1) cursor.getLong(downloadedCol) else 0L
                    val totalBytes = if (totalCol != -1) cursor.getLong(totalCol) else 0L
                    val localUri = if (localUriCol != -1) cursor.getString(localUriCol) else task.localFilePath

                    val percent = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else 0

                    val newStatus = when (statusInt) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (localUri != null) {
                                val cleanPath = Uri.parse(localUri).path ?: task.localFilePath
                                cleanPath?.let { path ->
                                    MediaScannerConnection.scanFile(
                                        context,
                                        arrayOf(path),
                                        null
                                    ) { scannedPath, _ ->
                                        Log.i(TAG, "✓ Added to Local Gallery: $scannedPath")
                                    }
                                }
                            }
                            DownloadStatus.COMPLETED
                        }
                        DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                        DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                        else -> DownloadStatus.DOWNLOADING
                    }

                    return task.copy(
                        progressPercent = percent,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        status = newStatus,
                        localFilePath = localUri ?: task.localFilePath
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query notice: ${e.message}")
        }
        return task
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            _activeDownloads.update { list -> list.filter { it.downloadId != downloadId } }
            Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Cancel error: ${e.message}")
        }
    }

    fun openDownloadedFile(context: Context, task: ActiveDownloadTask) {
        try {
            val path = task.localFilePath ?: return
            val file = if (path.startsWith("file://")) File(Uri.parse(path).path ?: "") else File(path)

            if (!file.exists()) {
                Toast.makeText(context, "File not found on device", Toast.LENGTH_SHORT).show()
                return
            }

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mime = if (file.name.endsWith(".mp3", true)) "audio/*" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
