package com.example.util

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import java.util.Locale

object R2DownloadManager {
    private const val CHANNEL_ID = "playdramaflix_downloads_channel"
    private const val CHANNEL_NAME = "Video Downloads"

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🎯 যেকোনো URL থেকে সরাসরি আসল Cloudflare R2 MP4 লিংক তৈরি
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
     * ⚡ ১-ক্লিকে নিশ্চিত ডাউনলোড ও সাথে সাথে নোটিফিকেশন প্যানেলে প্রদর্শন
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

        // এম্বেড লিংক থাকলে ব্রাউজারে রিডাইরেক্ট
        if (cleanUrl.contains("/e/") || cleanUrl.contains("/embed") || cleanUrl.contains("byse")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                showToast(context, "Opening player page for download...")
            } catch (_: Exception) {}
            return
        }

        // নোটিফিকেশন চ্যানেল তৈরি (IMPORTANCE_DEFAULT যাতে স্ক্রিনে স্পষ্টভাবে দেখা যায়)
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        try {
            // ফাইলের নাম ক্লিন করা (টাইমস্ট্যাম্প সহ যেন কখনোই ফেইল না মারে)
            val cleanTitle = title
                .replace("&#039;", "")
                .replace("&amp;", "and")
                .replace(Regex("[\\\\/:*?\"<>|]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")

            val safeTitle = if (cleanTitle.isNotBlank()) cleanTitle.take(35) else "Drama"
            val uniqueTime = System.currentTimeMillis() % 100000
            val fileName = if (isMovie) "${safeTitle}_${uniqueTime}.mp4" else "${safeTitle}_EP_${episodeNumber}_${uniqueTime}.mp4"
            val displayTitle = if (isMovie) title else "$title - Episode $episodeNumber"

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                showToast(context, "Download Manager not available on this device")
                return
            }

            val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                setTitle(displayTitle)
                setDescription("Downloading from PlayDramaFlix...")
                setMimeType("video/mp4")

                // 🛡️ Cloudflare Bot Protection ও 403 Forbidden বাইপাস করার জন্য আসল ব্রাউজার হেডার
                addRequestHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                addRequestHeader("Accept", "*/*")
                addRequestHeader("Connection", "keep-alive")

                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(true)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // পাবলিক ডাউনলোড ফোল্ডার (/storage/emulated/0/Download/)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadId = downloadManager.enqueue(request)
            onDownloadStarted(downloadId)

            val notifId = (downloadId % 100000).toInt()

            // 🎯 ক্লিক করার ০ মিলি-সেকেন্ডে সাথে সাথে নোটিফিকেশন প্যানেলে নোটিফিকেশন তৈরি
            val initialNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("⬇️ Starting: $displayTitle")
                .setContentText("Connecting to server...")
                .setProgress(100, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notifManager.notify(notifId, initialNotif)
            showToast(context, "📥 Download started! Check notification panel.")

            // লাইভ প্রোগ্রেস ট্র্যাকার
            trackLiveProgress(context.applicationContext, downloadId, notifId, displayTitle, fileName, cleanUrl)

        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                showToast(context, "Downloading via browser...")
            } catch (_: Exception) {
                showToast(context, "Download failed to start")
            }
        }
    }

    /**
     * 📊 রিয়েল-টাইম প্রোগ্রেস নোটিফিকেশন আপডেট
     */
    private fun trackLiveProgress(
        context: Context,
        downloadId: Long,
        notifId: Int,
        displayTitle: String,
        fileName: String,
        originalUrl: String
    ) {
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        CoroutineScope(Dispatchers.IO).launch {
            var isDownloading = true
            var loopCount = 0

            while (isDownloading && loopCount < 7200) {
                loopCount++
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = try { downloadManager.query(query) } catch (_: Exception) { null }

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                        val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                        val bytesTotal = if (bytesTotalIndex != -1) cursor.getLong(bytesTotalIndex) else 0L
                        val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1
                        val reason = if (reasonIndex != -1) cursor.getInt(reasonIndex) else -1
                        val localUriString = if (localUriIndex != -1) cursor.getString(localUriIndex) else null

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                isDownloading = false

                                val contentUri = try {
                                    if (!localUriString.isNullOrBlank()) {
                                        val downloadedFile = File(Uri.parse(localUriString).path ?: "")
                                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", downloadedFile)
                                    } else {
                                        val fallbackFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fallbackFile)
                                    }
                                } catch (_: Exception) {
                                    Uri.parse(localUriString ?: "")
                                }

                                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(contentUri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }

                                val pendingPlay = PendingIntent.getActivity(
                                    context, notifId, playIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )

                                val totalMb = if (bytesTotal > 0) bytesTotal / (1024f * 1024f) else 0f

                                val completeNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                    .setContentTitle("✅ $displayTitle Downloaded")
                                    .setContentText("Completed (${String.format(Locale.US, "%.1f", totalMb)} MB). Tap to play.")
                                    .setContentIntent(pendingPlay)
                                    .setAutoCancel(true)
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .addAction(android.R.drawable.ic_media_play, "▶ Play Video", pendingPlay)
                                    .build()

                                notifManager.notify(notifId, completeNotif)
                                showToast(context, "✅ Download Complete: $displayTitle")
                            }

                            DownloadManager.STATUS_FAILED -> {
                                isDownloading = false
                                notifManager.cancel(notifId)

                                // যদি কোনো ডিভাইসের সিস্টেম ডাউনলোডার ফেইল করে, সাথে সাথে ব্রাউজারে নামিয়ে দেবে
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }

                            DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                                val progressPercent = if (bytesTotal > 0) {
                                    ((bytesDownloaded * 100) / bytesTotal).toInt().coerceIn(0, 100)
                                } else 0

                                val downloadedMb = bytesDownloaded / (1024f * 1024f)
                                val totalMb = bytesTotal / (1024f * 1024f)

                                val subText = if (bytesTotal > 0) {
                                    "$progressPercent% (${String.format(Locale.US, "%.1f", downloadedMb)} MB / ${String.format(Locale.US, "%.1f", totalMb)} MB)"
                                } else {
                                    "Downloading stream..."
                                }

                                val progressNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                                    .setSmallIcon(android.R.drawable.stat_sys_download)
                                    .setContentTitle("⬇️ $displayTitle")
                                    .setContentText(subText)
                                    .setProgress(100, progressPercent, bytesTotal <= 0)
                                    .setOngoing(true)
                                    .setOnlyAlertOnce(true)
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .build()

                                notifManager.notify(notifId, progressNotif)
                            }
                        }
                    } else {
                        isDownloading = false
                    }
                    cursor.close()
                }
                delay(1000L)
            }
        }
    }
}
