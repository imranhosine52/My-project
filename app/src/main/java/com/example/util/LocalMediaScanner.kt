package com.example.util

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⚡ LocalMediaScanner
 * ফোনের মেমোরি থেকে সমস্ত লোকাল ভিডিও ও ফোল্ডার স্ক্যান করার ইঞ্জিন।
 */
object LocalMediaScanner {
    private const val TAG = "LocalMediaScanner"
    private const val PREFS_NAME = "local_video_playback_prefs"
    private const val KEY_PROGRESS_PREFIX = "progress_vid_"

    /**
     * ফোনের সমস্ত ভিডিও স্ক্যান করে লিস্ট আকারে রিটার্ন করে (Newest First)
     */
    suspend fun getAllVideos(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.RESOLUTION
        )

        // নতুন ভিডিও সবার আগে দেখানোর জন্য সর্টিং
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val resolutionColumn = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn) ?: "Video_$id"
                    val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: displayName
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    val bucketId = cursor.getString(bucketIdColumn) ?: "0"
                    val bucketName = cursor.getString(bucketNameColumn) ?: "Internal Storage"
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                    val resolution = if (resolutionColumn != -1) cursor.getString(resolutionColumn) else null

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    // ০ বাইটের ক্ষতিগ্রস্ত ভিডিও বাদ দেওয়া
                    if (size > 0) {
                        videoList.add(
                            LocalVideoItem(
                                id = id,
                                title = title,
                                displayName = displayName,
                                durationMs = duration,
                                sizeBytes = size,
                                path = path,
                                contentUriString = contentUri.toString(),
                                folderName = bucketName,
                                bucketId = bucketId,
                                dateAdded = dateAdded,
                                mimeType = mimeType,
                                resolution = resolution
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local videos: ${e.message}", e)
        }

        videoList
    }

    /**
     * ভিডিওগুলোকে ফোল্ডার অনুযায়ী গ্রুপ করে ফোল্ডার লিস্ট রিটার্ন করে
     */
    suspend fun getVideoFolders(context: Context): List<LocalVideoFolder> = withContext(Dispatchers.IO) {
        val allVideos = getAllVideos(context)
        val folderMap = mutableMapOf<String, MutableList<LocalVideoItem>>()

        allVideos.forEach { video ->
            folderMap.getOrPut(video.bucketId) { mutableListOf() }.add(video)
        }

        folderMap.map { (bucketId, videos) ->
            val firstVideo = videos.first()
            val totalSize = videos.sumOf { it.sizeBytes }
            val folderPath = try {
                File(firstVideo.path).parent ?: firstVideo.folderName
            } catch (_: Exception) {
                firstVideo.folderName
            }

            LocalVideoFolder(
                bucketId = bucketId,
                folderName = firstVideo.folderName,
                folderPath = folderPath,
                videoCount = videos.size,
                totalSizeBytes = totalSize,
                thumbnailUriString = firstVideo.contentUriString
            )
        }.sortedByDescending { it.videoCount }
    }

    /**
     * নির্দিষ্ট ফোল্ডারের ভিডিও লোড করার ফাংশন
     */
    suspend fun getVideosInFolder(context: Context, bucketId: String): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        getAllVideos(context).filter { it.bucketId == bucketId }
    }

    // =========================================================================
    // ⏱️ ভিডিও দেখার লাস্ট পজিশন সংরক্ষণ ও রিজিউম লজিক
    // =========================================================================

    fun saveLastPlaybackPosition(context: Context, videoId: Long, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_PROGRESS_PREFIX$videoId", positionMs).apply()
    }

    fun getLastPlaybackPosition(context: Context, videoId: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_PROGRESS_PREFIX$videoId", 0L)
    }

    fun clearPlaybackProgress(context: Context, videoId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_PROGRESS_PREFIX$videoId").apply()
    }
}
