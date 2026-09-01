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
 * ভিডিও, অডিও ও ইমেজ স্ক্যান করার মাল্টি-মিডিয়া ইঞ্জিন।
 */
object LocalMediaScanner {
    private const val TAG = "LocalMediaScanner"
    private const val PREFS_NAME = "local_video_playback_prefs"
    private const val KEY_PROGRESS_PREFIX = "progress_vid_"

    /**
     * ফোনের সমস্ত ভিডিও স্ক্যান করা
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

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val resCol = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name
                    val dur = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val bId = cursor.getString(bucketIdCol) ?: "0"
                    val bName = cursor.getString(bucketNameCol) ?: "Internal Storage"
                    val date = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "video/*"
                    val res = if (resCol != -1) cursor.getString(resCol) else null

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    if (size > 0) {
                        videoList.add(
                            LocalVideoItem(
                                id = id,
                                title = title,
                                displayName = name,
                                durationMs = dur,
                                sizeBytes = size,
                                path = path,
                                contentUriString = uri.toString(),
                                folderName = bName,
                                bucketId = bId,
                                dateAdded = date,
                                mimeType = mime,
                                resolution = res
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video scan error: ${e.message}")
        }
        videoList
    }

    /**
     * ফোনের সমস্ত অডিও / মিউজিক ফাইল স্ক্যান করা
     */
    suspend fun getAllAudioTracks(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE
        )

        try {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Audio_$id"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name
                    val dur = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val date = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"

                    val folder = try { File(path).parentFile?.name ?: "Music" } catch (_: Exception) { "Music" }
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    if (size > 0 && dur > 1000) {
                        audioList.add(
                            LocalVideoItem(
                                id = id,
                                title = title,
                                displayName = name,
                                durationMs = dur,
                                sizeBytes = size,
                                path = path,
                                contentUriString = uri.toString(),
                                folderName = folder,
                                bucketId = folder.hashCode().toString(),
                                dateAdded = date,
                                mimeType = mime,
                                resolution = "MP3"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio scan error: ${e.message}")
        }
        audioList
    }

    /**
     * ফোল্ডার অনুযায়ী গ্রুপ করা
     */
    suspend fun getMediaFolders(context: Context, isAudio: Boolean = false): List<LocalVideoFolder> = withContext(Dispatchers.IO) {
        val items = if (isAudio) getAllAudioTracks(context) else getAllVideos(context)
        val folderMap = mutableMapOf<String, MutableList<LocalVideoItem>>()

        items.forEach { item ->
            folderMap.getOrPut(item.bucketId) { mutableListOf() }.add(item)
        }

        folderMap.map { (bucketId, list) ->
            val first = list.first()
            val totalSize = list.sumOf { it.sizeBytes }
            val path = try { File(first.path).parent ?: first.folderName } catch (_: Exception) { first.folderName }

            LocalVideoFolder(
                bucketId = bucketId,
                folderName = first.folderName,
                folderPath = path,
                videoCount = list.size,
                totalSizeBytes = totalSize,
                thumbnailUriString = first.contentUriString
            )
        }.sortedByDescending { it.videoCount }
    }

    fun saveLastPlaybackPosition(context: Context, videoId: Long, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_PROGRESS_PREFIX$videoId", positionMs).apply()
    }

    fun getLastPlaybackPosition(context: Context, videoId: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_PROGRESS_PREFIX$videoId", 0L)
    }
}
