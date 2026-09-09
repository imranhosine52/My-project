package com.example.util

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

object R2DownloadManager {
    private const val CHANNEL_ID = "r2_download_channel"
    private const val CHANNEL_NAME = "Video Downloads"

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🎯 যেকোনো URL থেকে সরাসরি ক্লাউডফ্লেয়ার R2-এর আসল MP4 লিঙ্ক তৈরি
     */
    fun resolveDirectMp4Url(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return ""

        if (url.contains("master.m3u8")) {
            url = url.replace("master.m3u8", "download.mp4")
        } else if (url.contains(".m3u8")) {
            url = url.substringBeforeLast("/") + "/download.mp4"
        }
        return url
    }

    /**
     * ⚡ ১-ক্লিকে Cloudflare R2 থেকে ডিরেক্ট MP4 ডাউনলোড
     */
    fun startDownload(
        context: Context,
        downloadUrl: String,
        title: String,
        episodeNumber: Int = 1,
        isMovie: Boolean = false
    ) {
        val cleanUrl = resolveDirectMp4Url(downloadUrl)

        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            showToast(context, "Direct download link not available")
            return
        }

        try {
            // ফাইলের নাম স্যানিটাইজ করা
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
            val fileName = if (isMovie) "${cleanTitle}.mp4" else "${cleanTitle}_EP_${episodeNumber}.mp4"

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                showToast(context, "Download Manager not available on this device")
                return
            }

            val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                val displayTitle = if (isMovie) title else "$title - Episode $episodeNumber"
                setTitle(displayTitle)
                setDescription("Downloading Full HD from Cloudflare R2...")
                setMimeType("video/mp4")
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(true)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // আপনার ম্যানিফেস্টের পারমিশন অনুযায়ী পাবলিক ডাউনলোড ডিরেক্টরি
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PlayDramaFlix/$fileName")
            }

            val downloadId = downloadManager.enqueue(request)
            showToast(context, "📥 Download started! Check notification.")

            // লাইভ নোটিফিকেশন মনিটর
            trackLiveProgress(context.applicationContext, downloadId, title, episodeNumber, isMovie, fileName)

        } catch (e: Exception) {
            // কোনো কারণে ডাউনলোডার আটকে গেলে ব্রাউজারে ডিরেক্ট ওপেন
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                showToast(context, "Download error: ${e.localizedMessage ?: "Unknown"}")
            }
        }
    }

    /**
     * 📊 রিয়েল-টাইম প্রোগ্রেস ট্র্যাকিং + ডাউনলোড শেষে সরাসরি ভিডিও প্লেয়ার Intent
     */
    private fun trackLiveProgress(
        context: Context,
        downloadId: Long,
        title: String,
        episodeNumber: Int,
        isMovie: Boolean,
        fileName: String
    ) {
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows real-time progress for video downloads"
                setShowBadge(false)
            }
            notifManager.createNotificationChannel(channel)
        }

        val notifId = (downloadId % 100000).toInt()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val displayTitle = if (isMovie) title else "$title - EP $episodeNumber"

        CoroutineScope(Dispatchers.IO).launch {
            var isDownloading = true
            while (isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager.query(query)

                if (cursor != null && cursor.moveToFirst()) {
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

                            // 🎬 FileProvider দিয়ে নিরাপদ URI তৈরি
                            val contentUri = try {
                                if (!localUriString.isNullOrBlank()) {
                                    val downloadedFile = File(Uri.parse(localUriString).path ?: "")
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", downloadedFile)
                                } else {
                                    val fallbackFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PlayDramaFlix/$fileName")
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
                                .addAction(android.R.drawable.ic_media_play, "▶ Play Video", pendingPlay)
                                .build()

                            notifManager.notify(notifId, completeNotif)
                            showToast(context, "✅ $displayTitle download complete!")
                        }

                        DownloadManager.STATUS_FAILED -> {
                            isDownloading = false
                            notifManager.cancel(notifId)
                            val errorMsg = when (reason) {
                                DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
                                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                                DownloadManager.ERROR_FILE_ERROR -> "Storage permission / write error"
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "Server HTTP connection error"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP Error: Link expired or forbidden"
                                else -> "Download failed (Code: $reason)"
                            }
                            showToast(context, "❌ $errorMsg")
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
                    cursor.close()
                }
                delay(1000L)
            }
        }
    }
}
