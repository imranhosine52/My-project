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
import java.util.concurrent.ConcurrentHashMap

data class DownloadTaskInfo(
    val notifId: Int,
    val downloadUrl: String,
    val title: String,
    val episodeNumber: Int,
    val isMovie: Boolean,
    val posterUrl: String?,
    val displayTitle: String,
    val fileName: String
)

class VideoDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val activeDownloadJobs = ConcurrentHashMap<Int, Job>()
    private val activeTaskInfoMap = ConcurrentHashMap<Int, DownloadTaskInfo>()

    companion object {
        const val CHANNEL_ID = "playdramaflix_foreground_downloads"
        const val CHANNEL_NAME = "Media Downloads"

        const val ACTION_START = "com.example.download.ACTION_START"
        const val ACTION_PAUSE = "com.example.download.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.download.ACTION_RESUME"
        const val ACTION_CANCEL = "com.example.download.ACTION_CANCEL"

        const val EXTRA_TASK_ID = "extra_download_task_id"
        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_TITLE = "extra_download_title"
        const val EXTRA_EPISODE = "extra_download_episode"
        const val EXTRA_IS_MOVIE = "extra_download_is_movie"
        const val EXTRA_POSTER_URL = "extra_download_poster_url"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        createNotificationChannel()

        when (action) {
            ACTION_PAUSE -> {
                val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
                if (taskId != -1) {
                    pauseDownload(taskId)
                }
            }
            ACTION_RESUME -> {
                val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
                if (taskId != -1) {
                    resumeDownload(taskId)
                }
            }
            ACTION_CANCEL -> {
                val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
                if (taskId != -1) {
                    cancelDownload(taskId)
                }
            }
            else -> {
                val downloadUrl = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
                val episodeNumber = intent.getIntExtra(EXTRA_EPISODE, 1)
                val isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
                val posterUrl = intent.getStringExtra(EXTRA_POSTER_URL)

                val notifId = (System.currentTimeMillis() % 100000).toInt()
                val displayTitle = if (isMovie) title else "$title - Episode $episodeNumber"

                val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().replace(Regex("\\s+"), "_")
                val safeTitle = if (cleanTitle.isNotBlank()) cleanTitle.take(35) else "Drama"
                val uniqueTime = System.currentTimeMillis() % 10000
                val fileName = if (isMovie) "${safeTitle}_${uniqueTime}.mp4" else "${safeTitle}_EP_${episodeNumber}_${uniqueTime}.mp4"

                val taskInfo = DownloadTaskInfo(
                    notifId = notifId,
                    downloadUrl = downloadUrl,
                    title = title,
                    episodeNumber = episodeNumber,
                    isMovie = isMovie,
                    posterUrl = posterUrl,
                    displayTitle = displayTitle,
                    fileName = fileName
                )

                activeTaskInfoMap[notifId] = taskInfo
                startDownloadJob(taskInfo)
            }
        }

        return START_NOT_STICKY
    }

    private fun startDownloadJob(taskInfo: DownloadTaskInfo) {
        DownloadStateTracker.startTask(
            id = taskInfo.notifId,
            title = taskInfo.title,
            episodeNumber = taskInfo.episodeNumber,
            posterUrl = taskInfo.posterUrl
        )

        val initialNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("⬇️ Starting: ${taskInfo.displayTitle}")
            .setContentText("Connecting to server...")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(taskInfo.notifId, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(taskInfo.notifId, initialNotif)
        }

        val job = serviceScope.launch {
            executeDownload(taskInfo)
        }

        activeDownloadJobs[taskInfo.notifId] = job
    }

    private fun pauseDownload(taskId: Int) {
        val job = activeDownloadJobs.remove(taskId)
        job?.cancel()

        DownloadStateTracker.setPaused(taskId, true)

        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val task = activeTaskInfoMap[taskId]
        val pauseTitle = task?.displayTitle ?: "Download"

        val pausedNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("⏸️ Paused: $pauseTitle")
            .setContentText("Download paused. Tap play to resume.")
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notifManager.notify(taskId, pausedNotif)
    }

    private fun resumeDownload(taskId: Int) {
        val taskInfo = activeTaskInfoMap[taskId] ?: return
        if (activeDownloadJobs[taskId]?.isActive == true) return

        DownloadStateTracker.setPaused(taskId, false)

        val job = serviceScope.launch {
            executeDownload(taskInfo)
        }

        activeDownloadJobs[taskId] = job
    }

    private fun cancelDownload(taskId: Int) {
        val job = activeDownloadJobs.remove(taskId)
        job?.cancel()

        val taskInfo = activeTaskInfoMap.remove(taskId)
        if (taskInfo != null) {
            val tempDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "temp_downloads")
            val partFile = File(tempDir, "${taskInfo.fileName}.part")
            if (partFile.exists()) {
                partFile.delete()
            }
        }

        DownloadStateTracker.removeTask(taskId)
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(taskId)

        if (activeDownloadJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun executeDownload(taskInfo: DownloadTaskInfo) {
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val tempDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "temp_downloads").apply {
            if (!exists()) mkdirs()
        }
        val partFile = File(tempDir, "${taskInfo.fileName}.part")
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            var currentUrl = taskInfo.downloadUrl
            var redirects = 0

            while (redirects < 5) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) PlayDramaFlix Downloader")
                    setRequestProperty("Accept", "*/*")
                    if (existingBytes > 0L) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
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
            val responseCode = activeConn.responseCode

            val isPartial = (responseCode == 206)
            val isSuccess = (responseCode in 200..299)

            if (!isSuccess && !isPartial) {
                throw IllegalStateException("Server returned HTTP $responseCode")
            }

            val append = isPartial && existingBytes > 0L
            if (!append && existingBytes > 0L) {
                existingBytes = 0L
                partFile.delete()
            }

            val contentLength = activeConn.contentLengthLong
            val totalBytesExpected = if (append) existingBytes + contentLength else contentLength
            val totalMb = if (totalBytesExpected > 0) totalBytesExpected / (1024f * 1024f) else 0f

            inputStream = activeConn.inputStream
            outputStream = FileOutputStream(partFile, append)

            val buffer = ByteArray(32 * 1024)
            var totalBytesDownloaded = existingBytes
            var lastUpdateTimestamp = 0L

            // 🎯 ফিক্সড: লুপের ভেতরে সরাসরি ডিক্লেয়ার করায় কোনো ইনিশিয়ালাইজেশন এরর আসবে না
            while (currentCoroutineContext().isActive) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                outputStream.write(buffer, 0, bytesRead)
                totalBytesDownloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdateTimestamp > 400 || totalBytesDownloaded == totalBytesExpected) {
                    lastUpdateTimestamp = now
                    val pct = if (totalBytesExpected > 0) ((totalBytesDownloaded * 100) / totalBytesExpected).toInt().coerceIn(0, 100) else 0
                    val downMb = totalBytesDownloaded / (1024f * 1024f)

                    DownloadStateTracker.updateProgress(
                        id = taskInfo.notifId,
                        progressPercent = pct,
                        downloadedMb = downMb,
                        totalMb = totalMb
                    )

                    val progressText = if (totalMb > 0) {
                        "$pct% (${String.format(Locale.US, "%.1f", downMb)} MB / ${String.format(Locale.US, "%.1f", totalMb)} MB)"
                    } else {
                        "${String.format(Locale.US, "%.1f", downMb)} MB downloaded"
                    }

                    val progressNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("⬇️ ${taskInfo.displayTitle}")
                        .setContentText(progressText)
                        .setProgress(100, pct, totalBytesExpected <= 0)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()

                    notifManager.notify(taskInfo.notifId, progressNotif)
                }
            }

            outputStream.flush()

            if (!currentCoroutineContext().isActive) {
                return
            }

            var savedUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, taskInfo.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                savedUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: throw IllegalStateException("Could not create MediaStore record")

                contentResolver.openOutputStream(savedUri)?.use { out ->
                    partFile.inputStream().use { inStream ->
                        inStream.copyTo(out)
                    }
                }

                val updateCv = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(savedUri, updateCv, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, taskInfo.fileName)
                partFile.copyTo(target, overwrite = true)
                savedUri = Uri.fromFile(target)
            }

            partFile.delete()

            MediaScannerConnection.scanFile(this, arrayOf(taskInfo.fileName), arrayOf("video/mp4"), null)

            DownloadStateTracker.completeTask(
                id = taskInfo.notifId,
                filePath = taskInfo.fileName,
                contentUri = savedUri?.toString()
            )

            showCompletionNotification(taskInfo, savedUri)

            activeDownloadJobs.remove(taskInfo.notifId)
            activeTaskInfoMap.remove(taskInfo.notifId)

        } catch (e: CancellationException) {
            // পজের কারণে স্বাভাবিক বন্ধ
        } catch (e: Exception) {
            DownloadStateTracker.setPaused(taskInfo.notifId, true)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}

            if (activeDownloadJobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private fun showCompletionNotification(taskInfo: DownloadTaskInfo, savedUri: Uri?) {
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        var posterBitmap: Bitmap? = null
        if (!taskInfo.posterUrl.isNullOrBlank()) {
            try {
                val stream = URL(taskInfo.posterUrl).openStream()
                posterBitmap = BitmapFactory.decodeStream(stream)
            } catch (_: Exception) {}
        }

        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(savedUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingPlay = PendingIntent.getActivity(
            this, taskInfo.notifId, playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val successNotif = try {
            val customView = RemoteViews(packageName, R.layout.notification_download_complete).apply {
                setTextViewText(R.id.notif_title, "Download successfully")
                setTextViewText(R.id.notif_subtitle, taskInfo.displayTitle)
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
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("✅ Download successfully")
                .setContentText(taskInfo.displayTitle)
                .setContentIntent(pendingPlay)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_media_play, "Play", pendingPlay)
                .build()
        }

        notifManager.notify(taskInfo.notifId, successNotif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and completion for video downloads"
                setShowBadge(false)
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
