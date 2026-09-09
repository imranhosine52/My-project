package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.util.DownloadStateTracker
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class VideoDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val CHANNEL_ID = "playdramaflix_foreground_downloads"
        const val CHANNEL_NAME = "Media Downloads"

        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_TITLE = "extra_download_title"
        const val EXTRA_EPISODE = "extra_download_episode"
        const val EXTRA_IS_MOVIE = "extra_download_is_movie"
        const val EXTRA_POSTER_URL = "extra_download_poster_url"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadUrl = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
        val episodeNumber = intent.getIntExtra(EXTRA_EPISODE, 1)
        val isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
        val posterUrl = intent.getStringExtra(EXTRA_POSTER_URL)

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        val displayTitle = if (isMovie) title else "$title - Episode $episodeNumber"

        createNotificationChannel()

        // ১. ফোরগ্রাউন্ড নোটিফিকেশন স্টার্ট করা (Android OS যেন সার্ভিস কিল না করতে পারে)
        val initialNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("⬇️ Starting: $displayTitle")
            .setContentText("Connecting to high-speed server...")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notifId, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notifId, initialNotif)
        }

        // ২. UI-এর জন্য DownloadStateTracker শুরু করা
        DownloadStateTracker.startTask(
            id = notifId,
            title = title,
            episodeNumber = episodeNumber,
            posterUrl = posterUrl
        )

        // ৩. ব্যাকগ্রাউন্ডে ডাউনলোড এক্সিকিউট করা
        serviceScope.launch {
            downloadFile(
                downloadUrl = downloadUrl,
                title = title,
                episodeNumber = episodeNumber,
                isMovie = isMovie,
                posterUrl = posterUrl,
                notifId = notifId,
                displayTitle = displayTitle
            )
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadFile(
        downloadUrl: String,
        title: String,
        episodeNumber: Int,
        isMovie: Boolean,
        posterUrl: String?,
        notifId: Int,
        displayTitle: String
    ) {
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().replace(Regex("\\s+"), "_")
        val safeTitle = if (cleanTitle.isNotBlank()) cleanTitle.take(35) else "Drama"
        val uniqueTime = System.currentTimeMillis() % 10000
        val fileName = if (isMovie) "${safeTitle}_${uniqueTime}.mp4" else "${safeTitle}_EP_${episodeNumber}_${uniqueTime}.mp4"

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var savedUri: Uri? = null

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
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val newLoc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!newLoc.isNullOrBlank()) {
                        currentUrl = newLoc
                        redirects++
                        continue
                    }
                }
                connection = conn
                break
            }

            val activeConn = connection ?: throw IllegalStateException("Network connection failed")
            if (activeConn.responseCode !in 200..299) {
                throw IllegalStateException("Server returned HTTP ${activeConn.responseCode}")
            }

            val contentLength = activeConn.contentLengthLong
            val totalMb = if (contentLength > 0) contentLength / (1024f * 1024f) else 0f
            inputStream = activeConn.inputStream

            // 📁 Android 10+ MediaStore API দিয়ে সরাসরি ফোনের Download ফোল্ডারে ফাইল তৈরি
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                savedUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: throw IllegalStateException("Could not create MediaStore record in Downloads")
                outputStream = contentResolver.openOutputStream(savedUri!!)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, fileName)
                outputStream = FileOutputStream(target)
                savedUri = Uri.fromFile(target)
            }

            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastUpdateTimestamp = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!serviceScope.isActive) break

                outputStream?.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val now = System.currentTimeMillis()
                // প্রতি ৪০০ মিলিসেকেন্ড পরপর নোটিফিকেশন ও অ্যাপের DownloadsScreen আপডেট
                if (now - lastUpdateTimestamp > 400 || totalBytesRead == contentLength) {
                    lastUpdateTimestamp = now
                    val pct = if (contentLength > 0) ((totalBytesRead * 100) / contentLength).toInt() else 0
                    val downMb = totalBytesRead / (1024f * 1024f)

                    // ১. অ্যাপের ভেতর DownloadsScreen এর জন্য লাইভ ডাটা আপডেট
                    DownloadStateTracker.updateProgress(
                        id = notifId,
                        progressPercent = pct,
                        downloadedMb = downMb,
                        totalMb = totalMb
                    )

                    // ২. ফোনের নোটিফিকেশন প্যানেল আপডেট
                    val progressText = if (totalMb > 0) {
                        "$pct% (${String.format(Locale.US, "%.1f", downMb)} MB / ${String.format(Locale.US, "%.1f", totalMb)} MB)"
                    } else {
                        "${String.format(Locale.US, "%.1f", downMb)} MB downloaded"
                    }

                    val progressNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("⬇️ $displayTitle")
                        .setContentText(progressText)
                        .setProgress(100, pct, contentLength <= 0)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()

                    notifManager.notify(notifId, progressNotif)
                }
            }

            outputStream?.flush()

            // ডাউনলোড সম্পন্ন: IS_PENDING ফ্ল্যাগ ওপেন করা
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != null) {
                val cv = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(savedUri, cv, null, null)
            }

            // সিস্টেম গ্যালারিতে ফাইলটি ইনডেক্স করা
            MediaScannerConnection.scanFile(this, arrayOf(fileName), arrayOf("video/mp4"), null)

            // অ্যাপের ভেতরের ট্র্যাকারকে সম্পন্ন ঘোষণা করা
            DownloadStateTracker.completeTask(
                id = notifId,
                filePath = fileName,
                contentUri = savedUri?.toString()
            )

            // 🖼️ পোস্টার থাম্বনেইল বিটম্যাপ ডাউনলোড
            var posterBitmap: Bitmap? = null
            if (!posterUrl.isNullOrBlank()) {
                try {
                    val stream = URL(posterUrl).openStream()
                    posterBitmap = BitmapFactory.decodeStream(stream)
                } catch (_: Exception) {}
            }

            // 🎬 নোটিফিকেশনের প্লে ইন্টেন্ট তৈরি
            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(savedUri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingPlay = PendingIntent.getActivity(
                this, notifId, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // =========================================================================
            // 🌟 MovieBox এর হুবহু কাস্টম নোটিফিকেশন রিমোট ভিউ
            // =========================================================================
            val successNotif = try {
                val customView = RemoteViews(packageName, R.layout.notification_download_complete).apply {
                    setTextViewText(R.id.notif_title, "Download successfully")
                    setTextViewText(R.id.notif_subtitle, displayTitle)
                    setOnClickPendingIntent(R.id.notif_play_btn_layout, pendingPlay)

                    if (posterBitmap != null) {
                        setImageViewBitmap(R.id.notif_poster, posterBitmap)
                    } else {
                        setImageViewResource(R.id.notif_poster, R.mipmap.ic_launcher)
                    }
                }

                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(customView)
                    .setContentIntent(pendingPlay)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
            } catch (_: Exception) {
                // লেআউট কোনো কারণে এরর করলে স্ট্যান্ডার্ড ফলব্যাক নোটিফিকেশন
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("✅ Download successfully")
                    .setContentText(displayTitle)
                    .setContentIntent(pendingPlay)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(android.R.drawable.ic_media_play, "Play", pendingPlay)
                    .apply {
                        if (posterBitmap != null) setLargeIcon(posterBitmap)
                    }
                    .build()
            }

            // সার্ভিস সমাপ্ত করা কিন্তু ফিনিশড নোটিফিকেশন রেখে দেওয়া
            stopForeground(STOP_FOREGROUND_DETACH)
            notifManager.notify(notifId, successNotif)

        } catch (e: Exception) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            DownloadStateTracker.removeTask(notifId)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows progress and completion for video downloads"
                setShowBadge(true)
            }
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
