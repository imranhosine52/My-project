package com.example.util

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⚡ LocalMediaScanner
 * ভিডিও, মিউজিক/অডিও, ছবি (Images) ও ডকুমেন্টস ফাইল স্ক্যানার ইঞ্জিন।
 */
object LocalMediaScanner {
    private const val TAG = "LocalMediaScanner"
    private const val PREFS_NAME = "local_video_playback_prefs"
    private const val KEY_PROGRESS_PREFIX = "progress_vid_"

    /**
     * 🎬 ১. ফোনের সমস্ত ভিডিও স্ক্যান করা
     */
    suspend fun getAllVideos(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
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

        try {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val bIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
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
                    val bId = cursor.getString(bIdCol) ?: "0"
                    val bName = cursor.getString(bNameCol) ?: "Videos"
                    val date = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "video/*"
                    val res = if (resCol != -1) cursor.getString(resCol) else null

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    if (size > 0) {
                        list.add(
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
            Log.e(TAG, "Video query error: ${e.message}")
        }
        list
    }

    /**
     * 🎵 ২. ফোনের সমস্ত অডিও / মিউজিক স্ক্যান করা
     */
    suspend fun getAllAudioTracks(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
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
                        list.add(
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
            Log.e(TAG, "Audio query error: ${e.message}")
        }
        list
    }

    /**
     * 🖼️ ৩. ফোনের সমস্ত ছবি (Images) স্ক্যান করা
     */
    suspend fun getAllImages(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )

        try {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.TITLE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val bIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image_$id"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name
                    val size = cursor.getLong(sizeCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val bId = cursor.getString(bIdCol) ?: "0"
                    val bName = cursor.getString(bNameCol) ?: "Images"
                    val date = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    if (size > 0) {
                        list.add(
                            LocalVideoItem(
                                id = id,
                                title = title,
                                displayName = name,
                                durationMs = 0L,
                                sizeBytes = size,
                                path = path,
                                contentUriString = uri.toString(),
                                folderName = bName,
                                bucketId = bId,
                                dateAdded = date,
                                mimeType = mime,
                                resolution = "IMG"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image query error: ${e.message}")
        }
        list
    }

    /**
     * 📁 ৪. ফাইল ও ডকুমেন্টস স্ক্যান করা (PDF, DOCX, ZIP, APK ইত্যাদি)
     */
    suspend fun getAllDocuments(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val mimeSelection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/%' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%'"

        try {
            context.contentResolver.query(collection, projection, mimeSelection, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.TITLE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "File_$id"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name
                    val size = cursor.getLong(sizeCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val date = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"

                    val folder = try { File(path).parentFile?.name ?: "Documents" } catch (_: Exception) { "Documents" }
                    val uri = ContentUris.withAppendedId(collection, id)

                    if (size > 0 && !name.endsWith(".db") && !name.endsWith(".xml")) {
                        list.add(
                            LocalVideoItem(
                                id = id,
                                title = title,
                                displayName = name,
                                durationMs = 0L,
                                sizeBytes = size,
                                path = path,
                                contentUriString = uri.toString(),
                                folderName = folder,
                                bucketId = folder.hashCode().toString(),
                                dateAdded = date,
                                mimeType = mime,
                                resolution = "DOC"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Files query error: ${e.message}")
        }
        list
    }

    /**
     * ফোল্ডার অনুযায়ী গ্রুপ তৈরি
     */
    suspend fun getCategoryFolders(context: Context, categoryIndex: Int): List<LocalVideoFolder> = withContext(Dispatchers.IO) {
        val items = when (categoryIndex) {
            1 -> getAllAudioTracks(context)
            2 -> getAllImages(context)
            3 -> getAllDocuments(context)
            else -> getAllVideos(context)
        }

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
