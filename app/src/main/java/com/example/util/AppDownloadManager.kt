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
 * 🚀 AppDownloadManager (High-Speed In-App Multi-Threaded Stream Downloader)
 * সিস্টেম DownloadManager এর সীমাবদ্ধতা এড়িয়ে রিয়েলটাইম লাইভ প্রোগ্রেস সহ সরাসরি ফাইলে ডাউনলোড করে।
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

    /**
     * ⚡ ডাউনলোড শুরু করার মূল ফাংশন
     */
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

            // 🚀 ব্যাকগ্রাউন্ডে ইন-অ্যাপ স্ট্রিম ডাউনলোড চালানো
            val job = scope.launch {
                executeStreamDownload(context, downloadId, format.downloadUrl, targetFile, cleanTitle)
            }
            downloadJobs[downloadId] = job

            Toast.makeText(context, "⚡ Download started: $cleanTitle", Toast.LENGTH_SHORT).show()
            return downloadId

        } catch (e: Exception) {
            Log.e(TAG, "Download start error: ${e.message}", e)
            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    /**
     * 📥 স্ট্রিম বাইট রিডার ও লাইভ প্রোগ্রেস রাইটার
     */
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

            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(32 * 1024) // 32KB হাই-স্পিড বাফার
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
                if (now - lastUpdateMs > 300 || downloadedBytes == totalBytes) {
                    lastUpdateMs = now
                    val percent = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        // চ্যাঙ্কড স্ট্রিমের জন্য প্রোগ্রেস সিমুলেশন
                        ((downloadedBytes / (1024 * 512)) % 95).toInt().coerceAtLeast(5)
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

            // 🎬 ডাউনলোড সম্পন্ন: গ্যালারিতে রেজিস্টার করা
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                null
            ) { path, _ ->
                Log.i(TAG, "✓ Download Finished & Added to Gallery: $path")
            }

            _activeDownloads.update { list ->
                list.map { task ->
                    if (task.downloadId == downloadId) {
                        task.copy(
                            progressPercent = 100,
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes,
                            status = DownloadStatus.COMPLETED
                        )
                    } else task
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "✓ Download Completed: $title", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download execution failed for $downloadId: ${e.message}", e)
            _activeDownloads.update { list ->
                list.map { task ->
                    if (task.downloadId == downloadId) {
                        task.copy(status = DownloadStatus.FAILED)
                    } else task
                }
            }
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            downloadJobs.remove(downloadId)
        }
    }

    /**
     * ডাউনলোড বাতিল ও ফাইল ডিলিট
     */
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
        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
    }

    /**
     * ডাউনলোড করা ফাইল সরাসরি প্লে করা
     */
    fun openDownloadedFile(context: Context, task: ActiveDownloadTask) {
        try {
            val path = task.localFilePath ?: return
            val file = File(path)

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
