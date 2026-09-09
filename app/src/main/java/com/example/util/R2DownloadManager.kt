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
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 🎯 যেকোনো URL থেকে আসল Cloudflare R2 MP4 লিংক ফিল্টার ও প্রস্তুত করা
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
     * ⚡ ১-ক্লিকে নিশ্চিত ডাউনলোড ইঞ্জিন
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

        // 🌐 ১. Byse এম্বেড লিংক হলে ইন-অ্যাপ ব্রাউজারে রিডাইরেক্ট (ক্র্যাশ বা এরর ছাড়াই)
        if (cleanUrl.contains("/e/") || cleanUrl.contains("/embed") || cleanUrl.contains("byse")) {
            showToast(context, "Opening player page for download...")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                showToast(context, "Cannot open download link")
            }
            return
        }

        // 🔔 ২. নোটিফিকেশন পারমিশন রিমাইন্ডার (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                showToast(context, "⚠️ Please allow notifications to see download progress")
            }
        }

        try {
            // 🏷️ ৩. ফাইলের নাম তৈরি (টাইমস্ট্যাম্প সহ যেন কনফ্লিক্ট বা ডুপ্লিকেট ফেইল না মারে)
            val cleanTitle = title
                .replace("&#039;", "")
                .replace("&amp;", "and")
                .replace(Regex("[\\\\/:*?\"<>|]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")

            val safeTitle = if (cleanTitle.isNotBlank()) cleanTitle.take(35) else "Drama"
            val uniqueTime = System.currentTimeMillis() % 100000
            val fileName = if (isMovie) "${safeTitle}_${uniqueTime}.mp4" else "${safeTitle}_EP_${episodeNumber}_${uniqueTime}.mp4"

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                fallbackExternalDownload(context, cleanUrl)
                return
            }

            val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                val displayTitle = if (isMovie) title else "$title - Episode $episodeNumber"
                setTitle(displayTitle)
                setDescription("Downloading from PlayDramaFlix...")
                setMimeType("video/mp4")

                // 🛡️ Cloudflare Bot Protection ও 403 Forbidden বাইপাস হেডার
                addRequestHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                addRequestHeader("Accept", "*/*")
                addRequestHeader("Connection", "keep-alive")

                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(true)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // পাবলিক ডাউনলোড ডিরেক্টরি
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadId = downloadManager.enqueue(request)
            onDownloadStarted(downloadId)
            showToast(context, "📥 Download started! Check notification bar.")

            // রিয়েল-টাইম নোটিফিকেশন ট্র্যাকিং
            trackLiveProgress(context.applicationContext, downloadId, title, episodeNumber, isMovie, fileName, cleanUrl)

        } catch (e: Exception) {
            // ডাউনলোড ম্যানেজারে কোনো সমস্যা হলে অল্টারনেট ফলব্যাক ইঞ্জিন
            fallbackExternalDownload(context, cleanUrl)
        }
    }

    private fun fallbackExternalDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            showToast(context, "Starting download via browser...")
        } catch (_: Exception) {
            showToast(context, "Download failed to start")
        }
    }

    /**
     * 📊 লাইভ প্রোগ্রেস ট্র্যাকার ও প্লেয়ার ইন্টিগ্রেশন
     */
    private fun trackLiveProgress(
        context: Context,
        downloadId: Long,
        title: String,
        episodeNumber: Int,
        isMovie: Boolean,
        fileName: String,
        originalUrl: String
    ) {
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress for video files"
                setShowBadge(true)
            }
            notifManager.createNotificationChannel(channel)
        }

        val notifId = (downloadId % 100000).toInt()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val displayTitle = if (isMovie) title else "$title - EP $episodeNumber"

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
                                showToast(context, "✅ $displayTitle download complete! Tap to play.")
                            }

                            DownloadManager.STATUS_FAILED -> {
                                isDownloading = false
                                notifManager.cancel(notifId)

                                // যদি কোনো কারণে ডাউনলোড ম্যানেজার ফেইল করে, সাথে সাথে অল্টারনেট ব্রাউজার ডাউনলোড ট্রিগার হবে
                                fallbackExternalDownload(context, originalUrl)
                            }

                            DownloadManager.STATUS_RUNNING -> {
                                if (bytesTotal > 0) {
                                    val progressPercent = ((bytesDownloaded * 100) / bytesTotal).toInt().coerceIn(0, 100)
                                    val downloadedMb = bytesDownloaded / (1024f * 1024f)
                                    val totalMb = bytesTotal / (1024f * 1024f)

                                    val progressNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setSmallIcon(android.R.drawable.stat_sys_download)
                                        .setContentTitle("⬇️ Downloading $displayTitle")
                                        .setContentText("$progressPercent% (${String.format(Locale.US, "%.1f", downloadedMb)} MB / ${String.format(Locale.US, "%.1f", totalMb)} MB)")
                                        .setProgress(100, progressPercent, false)
                                        .setOngoing(true)
                                        .setOnlyAlertOnce(true)
                                        .build()

                                    notifManager.notify(notifId, progressNotif)
                                }
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
