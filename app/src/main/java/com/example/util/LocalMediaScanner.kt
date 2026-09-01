package com.example.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import com.example.data.model.StorageCategorySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * ⚡ LocalMediaScanner
 * Google Files স্টাইল অল-মিডিয়া স্ক্যানার ও ফাইল ম্যানেজার ইঞ্জিন।
 */
object LocalMediaScanner {
    private const val TAG = "LocalMediaScanner"
    private const val PREFS_NAME = "local_file_manager_prefs"
    private const val KEY_STARRED = "starred_file_paths"
    private const val KEY_TRASH = "trash_file_paths"
    private const val KEY_SAFE_FOLDER = "safe_file_paths"
    private const val KEY_SAFE_PIN = "safe_folder_pin"
    private const val KEY_PROGRESS_PREFIX = "progress_vid_"

    // =========================================================================
    // ⏱️ ভিডিও দেখার লাস্ট পজিশন সংরক্ষণ ও রিজিউম মেথড
    // =========================================================================
    fun saveLastPlaybackPosition(context: Context, videoId: Long, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_PROGRESS_PREFIX$videoId", positionMs).apply()
    }

    fun getLastPlaybackPosition(context: Context, videoId: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_PROGRESS_PREFIX$videoId", 0L)
    }

    // =========================================================================
    // 📊 ১. স্টোরেজ সাইজ সামারি ক্যালকুলেটর (Dashboard Cards)
    // =========================================================================
    suspend fun getStorageSummary(context: Context): StorageCategorySummary = withContext(Dispatchers.IO) {
        val vids = getAllVideos(context)
        val imgs = getAllImages(context)
        val auds = getAllAudioTracks(context)
        val docs = getAllDocuments(context)
        val dls = getAllDownloads(context)
        val apks = getAllApks(context)

        StorageCategorySummary(
            videosSizeText = formatBytes(vids.sumOf { it.sizeBytes }),
            imagesSizeText = formatBytes(imgs.sumOf { it.sizeBytes }),
            audioSizeText = formatBytes(auds.sumOf { it.sizeBytes }),
            documentsSizeText = formatBytes(docs.sumOf { it.sizeBytes }),
            downloadsSizeText = formatBytes(dls.sumOf { it.sizeBytes }),
            appsSizeText = formatBytes(apks.sumOf { it.sizeBytes })
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.1f KB", kb)
        }
    }

    // =========================================================================
    // 🎬 ২. মিডিয়া কুয়েরি মেথডসমূহ
    // =========================================================================

    suspend fun getAllVideos(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

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

                val starredSet = getStarredPaths(context)
                val safeSet = getSafePaths(context)
                val trashSet = getTrashPaths(context)

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
                    if (size > 0 && path !in safeSet) {
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
                                resolution = res,
                                isStarred = path in starredSet,
                                isSafe = path in safeSet,
                                isTrashed = path in trashSet
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getAllImages(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

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

                val starredSet = getStarredPaths(context)
                val safeSet = getSafePaths(context)
                val trashSet = getTrashPaths(context)

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
                    if (size > 0 && path !in safeSet) {
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
                                isStarred = path in starredSet,
                                isSafe = path in safeSet,
                                isTrashed = path in trashSet
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getAllAudioTracks(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

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

                val starredSet = getStarredPaths(context)
                val safeSet = getSafePaths(context)
                val trashSet = getTrashPaths(context)

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

                    if (size > 0 && dur > 1000 && path !in safeSet) {
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
                                isStarred = path in starredSet,
                                isSafe = path in safeSet,
                                isTrashed = path in trashSet
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getAllDocuments(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/%' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%'"

        try {
            context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { cursor ->
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

                    if (size > 0 && !name.endsWith(".apk") && !name.endsWith(".db") && !name.endsWith(".xml")) {
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
                                mimeType = mime
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getAllDownloads(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val all = getAllVideos(context) + getAllAudioTracks(context) + getAllImages(context) + getAllDocuments(context)
        all.filter { it.folderName.contains("download", ignoreCase = true) || it.path.contains("/Download/", ignoreCase = true) }
    }

    suspend fun getAllApks(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadDir.walkTopDown().filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }.forEach { file ->
            list.add(
                LocalVideoItem(
                    id = file.hashCode().toLong(),
                    title = file.nameWithoutExtension,
                    displayName = file.name,
                    sizeBytes = file.length(),
                    path = file.absolutePath,
                    contentUriString = Uri.fromFile(file).toString(),
                    folderName = "Apps",
                    mimeType = "application/vnd.android.package-archive"
                )
            )
        }
        list
    }

    suspend fun getCategoryFolders(context: Context, categoryIndex: Int): List<LocalVideoFolder> = withContext(Dispatchers.IO) {
        val items = when (categoryIndex) {
            1 -> getAllAudioTracks(context)
            2 -> getAllImages(context)
            3 -> getAllDocuments(context)
            4 -> getAllDownloads(context)
            5 -> getAllApks(context)
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

    fun getStarredPaths(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_STARRED, emptySet()) ?: emptySet()
    }

    fun toggleStarred(context: Context, path: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_STARRED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newState = if (path in current) {
            current.remove(path)
            false
        } else {
            current.add(path)
            true
        }
        prefs.edit().putStringSet(KEY_STARRED, current).apply()
        return newState
    }

    fun getSafePaths(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SAFE_FOLDER, emptySet()) ?: emptySet()
    }

    fun moveToSafeFolder(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_SAFE_FOLDER, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(path)
        prefs.edit().putStringSet(KEY_SAFE_FOLDER, current).apply()
    }

    fun getTrashPaths(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_TRASH, emptySet()) ?: emptySet()
    }

    fun moveToTrash(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_TRASH, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(path)
        prefs.edit().putStringSet(KEY_TRASH, current).apply()
    }

    fun restoreFromTrash(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_TRASH, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(path)
        prefs.edit().putStringSet(KEY_TRASH, current).apply()
    }

    fun deletePermanently(context: Context, path: String): Boolean {
        restoreFromTrash(context, path)
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun renameFile(context: Context, oldPath: String, newName: String): Boolean {
        return try {
            val file = File(oldPath)
            val ext = file.extension
            val newFileName = if (ext.isNotBlank()) "$newName.$ext" else newName
            val newFile = File(file.parentFile, newFileName)
            file.renameTo(newFile)
        } catch (_: Exception) {
            false
        }
    }

    fun getSafeFolderPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SAFE_PIN, "0000") ?: "0000"
    }

    fun setSafeFolderPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAFE_PIN, pin).apply()
    }
}
