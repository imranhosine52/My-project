package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

object R2DownloadManager {
    private const val CHANNEL_ID = "playdramaflix_direct_downloads"
    private const val CHANNEL_NAME = "High-Speed Video Downloads"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDownloadJob: Job? = null

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🎯 URL ক্লিন এবং আসল R2 MP4 লিংক তৈরি
     */
    fun resolveDirectMp4Url(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return ""

        try {
            url = URLDecoder.decode(url, "UTF-8")
        } catch (_: Exception) {}

        if (url.contains("master.m3u8")) {
            url = url.replace("master.m3u8", "download.mp4")
        } else if (url.endsWith(".m3u8") || url.contains(".m3u8")) {
            url = url.substringBeforeLast("/") + "/download.mp4"
        }
        return url
    }

    /**
     * ⚡ হাই-স্পিড ডিরেক্ট ডাউনলোড ইঞ্জিন (কোনো হ্যাং বা আটকে থাকা ছাড়াই)
     */
    fun startDownload(
        context: Context,
        downloadUrl: String,
        title: String,
        episodeNumber: Int = 1,
        isMovie: Boolean = false,
        onDownloadStarted: (downloadId: Long) -> Unit = {}
    ) {
        val cleanUrl = resolveDirectMp4Url(downloadUrl)

        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            showToast(context, "Direct download link not available")
            return
        }

        // Byse এম্বেড লিংক হলে ব্রাউজারে পাঠানো
        if (cleanUrl.contains("/e/") || cleanUrl.contains("/embed") || cleanUrl.contains("byse")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                showToast(context, "Opening player for download...")
            } catch (_: Exception) {}
            return
        }

        val appContext = context.applicationContext
        val notifManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // নোটিফিকেশন চ্যানেল
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows live download progress and completion for video files"
                setShowBadge(true)
                enableVibration(false)
            }
            notifManager.createNotificationChannel(channel)
        }

        // নিরাপদ ফাইলনেম তৈরি
        val cleanTitle = title
            .replace("&#039;", "")
            .replace("&amp;", "and")
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")

        val safeTitle = if (cleanTitle.isNotBlank()) cleanTitle.take(35) else "Drama"
        val uniqueTime = System.currentTimeMillis() % 10000
        val fileName = if (isMovie) "${safeTitle}_${uniqueTime}.mp4" else "${safeTitle}_EP_${episodeNumber}_${uniqueTime}.mp4"
        val displayTitle = if (isMovie) title else "$title - Episode $episodeNumber"

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        onDownloadStarted(notifId.toLong())

        showToast(appContext, "📥 Download started! Check notification.")

        // 🚀 সরাসরি অ্যাপের নিজস্ব নেটওয়ার্ক দিয়ে ডাউনলোড শুরু
        activeDownloadJob?.cancel()
        activeDownloadJob = CoroutineScope(Dispatchers.IO).launch {
            executeDirectDownload(
                context = appContext,
                notifManager = notifManager,
                notifId = notifId,
                downloadUrl = cleanUrl,
                fileName = fileName,
                displayTitle = displayTitle
            )
        }
    }

    /**
     * 📥 ব্যাকগ্রাউন্ড বাইট-বাই-বাইট স্ট্রিমিং ও MediaStore-এ সেভ
     */
    private suspend fun executeDirectDownload(
        context: Context,
        notifManager: NotificationManager,
        notifId: Int,
        downloadUrl: String,
        fileName: String,
        displayTitle: String
    ) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var savedUri: Uri? = null

        // প্রাথমিক নোটিফিকেশন
        val baseNotif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("⬇️ $displayTitle")
            .setContentText("Connecting to server...")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notifManager.notify(notifId, baseNotif.build())

        try {
            var currentUrl = downloadUrl
            var redirects = 0

            // রিডাইরেক্ট হ্যান্ডলার
            while (redirects < 5) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Connection", "keep-alive")
                    connect()
                }

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    val newLocation = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!newLocation.isNullOrBlank()) {
                        currentUrl = newLocation
                        redirects++
                        continue
                    }
                }

                connection = conn
                break
            }

            val activeConn = connection ?: throw IllegalStateException("Connection failed")
            if (activeConn.responseCode !in 200..299) {
                throw IllegalStateException("Server returned HTTP ${activeConn.responseCode}")
            }

            val contentLength = activeConn.contentLengthLong
            val totalMb = if (contentLength > 0) contentLength / (1024f * 1024f) else 0f

            inputStream = activeConn.inputStream

            // 📁 Android 10+ MediaStore API দিয়ে সরাসরি ফোনের Download ফোল্ডারে ফাইল তৈরি
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                savedUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Could not create file in Downloads")

                outputStream = context.contentResolver.openOutputStream(savedUri)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                outputStream = FileOutputStream(targetFile)
                savedUri = Uri.fromFile(targetFile)
            }

            val buffer = ByteArray(32 * 1024) // ৩২ KB বাফার (দ্রুত ডাউনলোডের জন্য)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastUpdateTimestamp = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!CoroutineScope(Dispatchers.IO).isActive) break

                outputStream?.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val now = System.currentTimeMillis()
                // প্রতি ৩০০ মিলি-সেকেন্ডে নোটিফিকেশনে লাইভ সংখ্যা আপডেট
                if (now - lastUpdateTimestamp > 300 || totalBytesRead == contentLength) {
                    lastUpdateTimestamp = now

                    val progressPercent = if (contentLength > 0) {
                        ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                    } else 0

                    val downloadedMb = totalBytesRead / (1024f * 1024f)

                    val progressText = if (totalMb > 0) {
                        "$progressPercent% (${String.format(Locale.US, "%.1f", downloadedMb)} MB / ${String.format(Locale.US, "%.1f", totalMb)} MB)"
                    } else {
                        "${String.format(Locale.US, "%.1f", downloadedMb)} MB downloaded"
                    }

                    val updateNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("⬇️ $displayTitle")
                        .setContentText(progressText)
                        .setProgress(100, progressPercent, contentLength <= 0)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()

                    notifManager.notify(notifId, updateNotif)
                }
            }

            outputStream?.flush()

            // ডাউনলোড সম্পন্ন: IS_PENDING ফ্ল্যাগ মুক্ত করা
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != null) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                context.contentResolver.update(savedUri, completeValues, null, null)
            }

            // ফোন গ্যালারিতে সাথে সাথে শো করানোর জন্য স্ক্যান
            MediaScannerConnection.scanFile(context, arrayOf(fileName), arrayOf("video/mp4"), null)

            // ✅ সফল নোটিফিকেশন (ট্যাপ করলেই ভিডিও প্লে হবে)
            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(savedUri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingPlay = PendingIntent.getActivity(
                context, notifId, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val finalMb = if (totalMb > 0) totalMb else (totalBytesRead / (1024f * 1024f))

            val successNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("✅ $displayTitle Downloaded")
                .setContentText("Completed (${String.format(Locale.US, "%.1f", finalMb)} MB). Saved in Downloads.")
                .setContentIntent(pendingPlay)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_media_play, "▶ Play Video", pendingPlay)
                .build()

            notifManager.notify(notifId, successNotif)
            showToast(context, "✅ Download Complete! Saved in Downloads.")

        } catch (e: Exception) {
            notifManager.cancel(notifId)
            showToast(context, "Download failed: ${e.localizedMessage ?: "Network error"}")
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }
}
