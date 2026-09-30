package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.R
import com.example.data.remote.CountingRequestBody
import com.example.data.remote.ReelsApiClient
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale

class ReelUploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ReelUploadWorker"
        const val CHANNEL_ID = "reels_upload_channel"
        const val NOTIFICATION_ID = 20251

        // ইনপুট কি (Input Keys)
        const val KEY_USER_ID = "key_user_id"
        const val KEY_PAGE_ID = "key_page_id"
        const val KEY_TITLE = "key_title"
        const val KEY_DESCRIPTION = "key_description"
        const val KEY_HASHTAGS = "key_hashtags"
        const val KEY_CATEGORY = "key_category"
        const val KEY_LINK_URL = "key_link_url"
        const val KEY_PRIVACY = "key_privacy"
        const val KEY_PLAYLIST_ID = "key_playlist_id" // 👈 নতুন সিরিজ কি
        const val KEY_EPISODE_NUM = "key_episode_num" // 👈 নতুন পর্ব নম্বর কি
        const val KEY_VIDEO_PATH = "key_video_path"
        const val KEY_THUMB_PATH = "key_thumb_path"

        fun enqueueUpload(
            context: Context,
            userId: Int,
            pageId: Int,
            title: String,
            description: String,
            hashtags: String,
            category: String,
            linkUrl: String?,
            privacy: String,
            playlistId: Int? = null,    // 👈 ফিক্সড: প্যারামিটার যুক্ত করা হয়েছে
            episodeNum: Int = 1,        // 👈 ফিক্সড: প্যারামিটার যুক্ত করা হয়েছে
            videoPath: String,
            thumbPath: String?
        ): java.util.UUID {
            val inputData = workDataOf(
                KEY_USER_ID to userId,
                KEY_PAGE_ID to pageId,
                KEY_TITLE to title,
                KEY_DESCRIPTION to description,
                KEY_HASHTAGS to hashtags,
                KEY_CATEGORY to category,
                KEY_LINK_URL to linkUrl,
                KEY_PRIVACY to privacy,
                KEY_PLAYLIST_ID to (playlistId ?: -1),
                KEY_EPISODE_NUM to episodeNum,
                KEY_VIDEO_PATH to videoPath,
                KEY_THUMB_PATH to thumbPath
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val uploadWorkRequest = OneTimeWorkRequestBuilder<ReelUploadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "upload_reel_${System.currentTimeMillis()}",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                uploadWorkRequest
            )

            return uploadWorkRequest.id
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val userId = inputData.getInt(KEY_USER_ID, 0)
        val pageId = inputData.getInt(KEY_PAGE_ID, 0)
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val description = inputData.getString(KEY_DESCRIPTION).orEmpty()
        val hashtags = inputData.getString(KEY_HASHTAGS).orEmpty()
        val category = inputData.getString(KEY_CATEGORY) ?: "Entertainment"
        val linkUrl = inputData.getString(KEY_LINK_URL)
        val privacy = inputData.getString(KEY_PRIVACY) ?: "public"
        
        // 🎯 সিরিজ ও পর্বের ডেটা পড়া
        val rawPlaylistId = inputData.getInt(KEY_PLAYLIST_ID, -1)
        val playlistId = if (rawPlaylistId > 0) rawPlaylistId else null
        val episodeNum = inputData.getInt(KEY_EPISODE_NUM, 1)

        val videoPath = inputData.getString(KEY_VIDEO_PATH) ?: return Result.failure()
        val thumbPath = inputData.getString(KEY_THUMB_PATH)

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            Log.e(TAG, "Video file not found at: $videoPath")
            return Result.failure()
        }

        createNotificationChannel()

        // ফোরগ্রাউন্ড সার্ভিস চালু করা (Notification tray)
        val initialForegroundInfo = createForegroundInfo(
            title = if (title.isNotBlank()) title else "Reel",
            progress = 0,
            uploadedMb = 0.0f,
            totalMb = (videoFile.length() / (1024f * 1024f))
        )
        setForeground(initialForegroundInfo)

        return try {
            val totalBytes = videoFile.length()
            val totalMb = totalBytes / (1024f * 1024f)
            var lastUpdateTimestamp = 0L

            // লাইভ প্রোগ্রেস ট্র্যাকার রিকোয়েস্ট বডি
            val requestFile = CountingRequestBody(videoFile, "video/mp4") { bytesWritten, _ ->
                val now = System.currentTimeMillis()
                // নোটিফিকেশন ট্রে প্রতি ৩০০ms পরপর আপডেট হবে
                if (now - lastUpdateTimestamp > 300 || bytesWritten == totalBytes) {
                    lastUpdateTimestamp = now
                    val percent = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                    val uploadedMb = bytesWritten / (1024f * 1024f)
                    updateNotification(
                        title = if (title.isNotBlank()) title else "Reel",
                        progress = percent,
                        uploadedMb = uploadedMb,
                        totalMb = totalMb
                    )
                }
            }

            val videoPart = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)

            // কাস্টম থাম্বনেইল তৈরি (যদি ইউজার নির্বাচন করে থাকে)
            val thumbPart = thumbPath?.let { path ->
                val thumbFile = File(path)
                if (thumbFile.exists()) {
                    val thumbReq = thumbFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("custom_thumb", thumbFile.name, thumbReq)
                } else null
            }

            // টেক্সট ফিল্ডসমূহকে RequestBody-তে রূপান্তর
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val pageIdPart = pageId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val titlePart = title.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val descPart = description.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val hashtagsPart = hashtags.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryPart = category.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val linkUrlPart = linkUrl?.trim()?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
            val privacyPart = privacy.trim().toRequestBody("text/plain".toMediaTypeOrNull())

            // 🎯 সিরিজ আইডি এবং পর্ব নম্বরকে Multipart RequestBody-তে রূপান্তর
            val playlistIdPart = playlistId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val episodeNumPart = episodeNum.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            // 🚀 VPS 2 API কল (playlistId ও episodeNum সহ)
            val response = ReelsApiClient.vps2UploadService.uploadFullReelWorkflow(
                userId = uidPart,
                pageId = pageIdPart,
                title = titlePart,
                description = descPart,
                hashtags = hashtagsPart,
                category = categoryPart,
                linkUrl = linkUrlPart,
                privacy = privacyPart,
                playlistId = playlistIdPart,
                episodeNum = episodeNumPart,
                video = videoPart,
                customThumb = thumbPart
            )

            // আপলোড সফল হলে টেম্পোরারি ফাইল ক্লিনআপ
            try { videoFile.delete() } catch (_: Exception) {}
            try { thumbPath?.let { File(it).delete() } } catch (_: Exception) {}

            if (response.isSuccessful && response.body()?.success == true) {
                showSuccessNotification()
                delay(3000)
                notificationManager.cancel(NOTIFICATION_ID)
                Result.success()
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Upload failed"
                showErrorNotification(errorMsg)
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Reel background upload error: ${e.message}", e)
            showErrorNotification(e.localizedMessage ?: "Network connection failed")
            Result.failure()
        }
    }

    private fun createForegroundInfo(title: String, progress: Int, uploadedMb: Float, totalMb: Float): ForegroundInfo {
        val notification = buildNotification(title, progress, uploadedMb, totalMb)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, progress: Int, uploadedMb: Float, totalMb: Float) {
        val notification = buildNotification(title, progress, uploadedMb, totalMb)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, progress: Int, uploadedMb: Float, totalMb: Float): android.app.Notification {
        val subtext = "${String.format(Locale.US, "%.1f", uploadedMb)} MB of ${String.format(Locale.US, "%.1f", totalMb)} MB"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading Reel to DramaFlix...")
            .setContentText("$title • $subtext ($progress%)")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showSuccessNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("✓ Reel published successfully!")
            .setContentText("Your reel is now live on DramaFlix Reels.")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(errorMsg: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Reel upload failed")
            .setContentText(errorMsg)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reel Video Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live upload progress of reels"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
