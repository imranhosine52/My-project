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

    /**
     * ⚡ ১-ক্লিকে Cloudflare R2 থেকে ডিরেক্ট MP4 ডাউনলোড শুরু করা
     */
    fun startDownload(
        context: Context,
        downloadUrl: String,
        title: String,
        episodeNumber: Int = 1,
        isMovie: Boolean = false
    ) {
        val cleanUrl = downloadUrl.trim()
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            Toast.makeText(context, "Direct download link not available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // ফাইলের নাম স্যানিটাইজ করা
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
            val fileName = if (isMovie) "${cleanTitle}.mp4" else "${cleanTitle}_EP_${episodeNumber}.mp4"

            // ফোল্ডার তৈরি (/Download/PlayDramaFlix)
            val downloadFolder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PlayDramaFlix"
            )
            if (!downloadFolder.exists()) downloadFolder.mkdirs()

            val targetFile = File(downloadFolder, fileName)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                val displayTitle = if (isMovie) title else "$title - Episode $episodeNumber"
                setTitle(displayTitle)
                setDescription("Downloading Full HD from Cloudflare R2...")
                setMimeType("video/mp4")
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(true)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PlayDramaFlix/$fileName")
                addRequestHeader("User-Agent", "Mozilla/5.0 (PlayDramaFlix Mobile App)")
                addRequestHeader("Referer", "https://playdramaflix.com/")
            }

            val downloadId = downloadManager.enqueue(request)
            Toast.makeText(context, "📥 Download started! Tracking in notification...", Toast.LENGTH_SHORT).show()

            // লাইভ প্রোগ্রেস নোটিফিকেশন মনিটর
            trackLiveProgress(context, downloadId, title, episodeNumber, isMovie, targetFile)

        } catch (e: Exception) {
            // সিস্টেম ডাউনলোডার ফেইল করলে ব্রাউজার দিয়ে ফলব্যাক
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 📊 রিয়েল-টাইম % এবং MB ট্র্যাকিং + ডাউনলোড শেষে "▶ Play Video" বাটন
     */
    private fun trackLiveProgress(
        context: Context,
        downloadId: Long,
        title: String,
        episodeNumber: Int,
        isMovie: Boolean,
        targetFile: File
    ) {
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows real-time progress for R2 downloads"
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
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        isDownloading = false

                        // 🎬 ডাউনলোড শেষে সরাসরি ভিডিও প্লে করার Intent
                        val fileUri = try {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
                        } catch (_: Exception) {
                            Uri.fromFile(targetFile)
                        }

                        val playIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        val pendingPlay = PendingIntent.getActivity(
                            context, notifId, playIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val totalMb = if (bytesTotal > 0) bytesTotal / (1024f * 1024f) else targetFile.length() / (1024f * 1024f)

                        val completeNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle("✅ $displayTitle Downloaded")
                            .setContentText("Download completed (${String.format(Locale.US, "%.1f", totalMb)} MB). Tap to play.")
                            .setContentIntent(pendingPlay)
                            .setAutoCancel(true)
                            .addAction(android.R.drawable.ic_media_play, "▶ Play Video", pendingPlay)
                            .build()

                        notifManager.notify(notifId, completeNotif)

                    } else if (status == DownloadManager.STATUS_FAILED) {
                        isDownloading = false
                        notifManager.cancel(notifId)
                    } else if (bytesTotal > 0) {
                        // 🔄 লাইভ পারসেন্টেজ ও সাইজ আপডেট
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
                    cursor.close()
                }
                delay(1000L) // প্রতি ১ সেকেন্ড পরপর স্ট্যাটাস চেক
            }
        }
    }
}
