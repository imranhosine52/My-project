package com.example.util

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.core.content.FileProvider
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 🚀 AppDownloadManager
 * সম্পূর্ণ ভিডিও ফাইল সরাসরি ডাউনলোডার।
 */
object AppDownloadManager {
    private const val TAG = "AppDownloadManager"
    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val _activeDownloads = MutableStateFlow<List<ActiveDownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownloadTask>> = _activeDownloads.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun startDownload(
        context: Context,
        videoInfo: DownloadableVideoInfo,
        format: VideoFormatOption
    ): Long {
        val downloadId = System.currentTimeMillis()

        try {
            val cleanTitle = videoInfo.title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .take(45)
                .ifBlank { "Video_${System.currentTimeMillis() % 10000}" }

            val ext = format.extension.lowercase().ifBlank { if (format.isAudioOnly) "mp3" else "mp4" }
            val fileName = "${cleanTitle}_${System.currentTimeMillis() % 1000}.$ext"

            val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, fileName)

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

            val job = scope.launch {
                executeStreamDownload(context, downloadId, format.downloadUrl, targetFile, cleanTitle)
            }
            downloadJobs[downloadId] = job

            Toast.makeText(context, "⚡ Downloading: $cleanTitle", Toast.LENGTH_SHORT).show()
            return downloadId

        } catch (e: Exception) {
            Log.e(TAG, "Download start error: ${e.message}", e)
            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    private suspend fun executeStreamDownload(
        context: Context,
        downloadId: Long,
        streamUrl: String,
        targetFile: File,
        title: String
    ) = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val cookies = CookieManager.getInstance().getCookie(streamUrl) ?: ""

            val requestBuilder = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Connection", "keep-alive")

            if (cookies.isNotBlank()) {
                requestBuilder.header("Cookie", cookies)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Server returned HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()
            val contentType = body.contentType()?.toString()?.lowercase() ?: ""

            // এইচটিএমএল এরর পেজ বাদ দেওয়া
            if (contentType.contains("text/html")) {
                throw IllegalStateException("Received HTML page instead of video file")
            }

            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(64 * 1024) // 64KB হাই-স্পিড বাফার
            var bytesRead: Int
            var downloadedBytes = 0L
            var lastUpdateMs = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!isActive) {
                    targetFile.delete()
                    return@withContext
                }

                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 300 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                    lastUpdateMs = now
                    val percent = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(1, 100)
                    } else {
                        ((downloadedBytes / (1024 * 1024)) % 95).toInt().coerceAtLeast(5)
                    }

                    _activeDownloads.update { list ->
                        list.map { task ->
                            if (task.downloadId == downloadId) {
                                task.copy(
                                    progressPercent = percent,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    status = DownloadStatus.DOWNLOADING
                                )
                            } else task
                        }
                    }
                }
            }

            outputStream.flush()

            // 🎬 ডাউনলোড সম্পন্ন: গ্যালারিতে রেজিস্টার
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                null
            ) { path, _ ->
                Log.i(TAG, "✓ Full Video Saved: $path (${targetFile.length() / (1024 * 1024)} MB)")
            }

            _activeDownloads.update { list ->
                list.map { task ->
                    if (task.downloadId == downloadId) {
                        task.copy(
                            progressPercent = 100,
                            downloadedBytes = downloadedBytes,
                            totalBytes = downloadedBytes,
                            status = DownloadStatus.COMPLETED
                        )
                    } else task
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "✓ Download Complete: $title", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $downloadId: ${e.message}", e)
            _activeDownloads.update { list ->
                list.map { task ->
                    if (task.downloadId == downloadId) task.copy(status = DownloadStatus.FAILED) else task
                }
            }
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            downloadJobs.remove(downloadId)
        }
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)

        val task = _activeDownloads.value.find { it.downloadId == downloadId }
        task?.localFilePath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }

        _activeDownloads.update { list -> list.filter { it.downloadId != downloadId } }
        Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
    }

    fun openDownloadedFile(context: Context, task: ActiveDownloadTask) {
        try {
            val path = task.localFilePath ?: return
            val file = File(path)

            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
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
