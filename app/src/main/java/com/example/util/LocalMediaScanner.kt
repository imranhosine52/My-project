package com.example.util

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object LocalMediaScanner {
    private const val TAG = "LocalMediaScanner"
    private const val PREFS_NAME = "local_file_manager_prefs"
    private const val KEY_STARRED = "starred_file_paths"
    private const val KEY_TRASH = "trash_file_paths"
    private const val KEY_SAFE_FOLDER = "safe_file_paths"
    private const val KEY_SAFE_PIN = "safe_folder_pin_custom"
    private const val KEY_PROGRESS_PREFIX = "progress_vid_"
    private const val KEY_LAST_PLAYED_TIMESTAMP_PREFIX = "last_played_ts_"
    private const val KEY_TRASHED_METADATA_JSON = "trashed_metadata_json_v2"

    // =========================================================================
    // ⏱️ Playback History Tracking
    // =========================================================================
    fun saveLastPlaybackPosition(context: Context, videoId: Long, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("$KEY_PROGRESS_PREFIX$videoId", positionMs)
            .putLong("$KEY_LAST_PLAYED_TIMESTAMP_PREFIX$videoId", System.currentTimeMillis())
            .apply()
    }

    fun getLastPlaybackPosition(context: Context, videoId: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_PROGRESS_PREFIX$videoId", 0L)
    }

    fun getLastPlayedTimestamp(context: Context, videoId: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_LAST_PLAYED_TIMESTAMP_PREFIX$videoId", 0L)
    }

    // =========================================================================
    // 🎬 ১. ভিডিও স্ক্যানার + হোয়াটসঅ্যাপ স্ট্যাটাস
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
            MediaStore.Video.Media.RESOLUTION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.WIDTH else MediaStore.Video.Media._ID,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.HEIGHT else MediaStore.Video.Media._ID
        )

        try {
            val safeSet = getSafePaths(context)
            val trashSet = getTrashPaths(context)
            val starredSet = getStarredPaths(context)

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
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
                val widthCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Video.Media.WIDTH) else -1
                val heightCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT) else -1

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
                    val rawRes = if (resCol != -1) cursor.getString(resCol) else null
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    val resolutionBadge = VideoBadgeResolver.resolveResolutionBadge(width, height, rawRes)

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    if (size > 0 && path !in safeSet && path !in trashSet) {
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
                                resolution = resolutionBadge ?: rawRes,
                                isStarred = path in starredSet
                            )
                        )
                    }
                }
            }

            val statusVideos = getWhatsAppStatusFiles(context, isVideo = true, safeSet, trashSet, starredSet)
            list.addAll(statusVideos)

        } catch (e: Exception) {
            Log.e(TAG, "Video error: ${e.message}")
        }
        list
    }

    // =========================================================================
    // 🎵 ২. অডিও স্ক্যানার
    // =========================================================================
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
            val safeSet = getSafePaths(context)
            val trashSet = getTrashPaths(context)
            val starredSet = getStarredPaths(context)

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
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

                    if (size > 0 && dur > 1000 && path !in safeSet && path !in trashSet) {
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
                                isStarred = path in starredSet
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio error: ${e.message}")
        }
        list
    }

    // =========================================================================
    // 🖼️ ৩. ইমেজ স্ক্যানার
    // =========================================================================
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
            val safeSet = getSafePaths(context)
            val trashSet = getTrashPaths(context)
            val starredSet = getStarredPaths(context)

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
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
                    if (size > 0 && path !in safeSet && path !in trashSet) {
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
                                isStarred = path in starredSet
                            )
                        )
                    }
                }
            }

            val statusImages = getWhatsAppStatusFiles(context, isVideo = false, safeSet, trashSet, starredSet)
            list.addAll(statusImages)

        } catch (e: Exception) {
            Log.e(TAG, "Images error: ${e.message}")
        }
        list
    }

    // =========================================================================
    // 📁 ৪. ডকুমেন্টস ও ফাইল স্ক্যানার
    // =========================================================================
    suspend fun getAllDocuments(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val safeSet = getSafePaths(context)
        val trashSet = getTrashPaths(context)
        val starredSet = getStarredPaths(context)
        val addedPaths = mutableSetOf<String>()

        val validExtensions = setOf(
            "pdf", "doc", "docx", "txt", "zip", "rar", "7z", "apk", "xlsx", "xls", "ppt", "pptx", "csv", "json", "html", "epub", "iso"
        )

        try {
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
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.BUCKET_ID,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
            )

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.TITLE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val bIdCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                val bNameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Document_$id"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name
                    val size = cursor.getLong(sizeCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val date = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"

                    val ext = name.substringAfterLast('.', "").lowercase()

                    if (ext in validExtensions && size > 0 && path !in safeSet && path !in trashSet && path !in addedPaths) {
                        addedPaths.add(path)
                        val folderName = if (bNameCol != -1) cursor.getString(bNameCol) else null
                        val safeFolderName = folderName?.takeIf { it.isNotBlank() }
                            ?: try { File(path).parentFile?.name ?: "Documents" } catch (_: Exception) { "Documents" }

                        val bucketId = if (bIdCol != -1) cursor.getString(bIdCol) else null
                        val safeBucketId = bucketId?.takeIf { it.isNotBlank() } ?: safeFolderName.hashCode().toString()

                        val contentUri = ContentUris.withAppendedId(collection, id)

                        list.add(
                            LocalVideoItem(
                                id = id,
                                title = title,
                                displayName = name,
                                durationMs = 0L,
                                sizeBytes = size,
                                path = path,
                                contentUriString = contentUri.toString(),
                                folderName = safeFolderName,
                                bucketId = safeBucketId,
                                dateAdded = date,
                                mimeType = mime.ifBlank { getMimeType(ext) },
                                isStarred = path in starredSet
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore files scanner notice: ${e.message}")
        }

        try {
            val root = Environment.getExternalStorageDirectory()
            val targetFolders = mutableListOf(
                File(root, Environment.DIRECTORY_DOWNLOADS),
                File(root, Environment.DIRECTORY_DOCUMENTS),
                File(root, "Download"),
                File(root, "Documents"),
                File(root, "Telegram"),
                File(root, "Telegram/Telegram Documents"),
                File(root, "WhatsApp/Media/WhatsApp Documents"),
                File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents")
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }?.forEach {
                    targetFolders.add(it)
                }
            }

            for (dir in targetFolders.distinct()) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().maxDepth(3).filter { it.isFile && it.length() > 0 }.forEach { file ->
                        val path = file.absolutePath
                        val ext = file.extension.lowercase()
                        if (ext in validExtensions && path !in safeSet && path !in trashSet && path !in addedPaths) {
                            addedPaths.add(path)
                            val folder = file.parentFile?.name ?: "Documents"
                            list.add(
                                LocalVideoItem(
                                    id = file.hashCode().toLong(),
                                    title = file.nameWithoutExtension,
                                    displayName = file.name,
                                    durationMs = 0L,
                                    sizeBytes = file.length(),
                                    path = path,
                                    contentUriString = Uri.fromFile(file).toString(),
                                    folderName = folder,
                                    bucketId = folder.hashCode().toString(),
                                    dateAdded = file.lastModified() / 1000,
                                    mimeType = getMimeType(ext),
                                    isStarred = path in starredSet
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct disk docs scanner error: ${e.message}")
        }

        list
    }

    private fun getMimeType(ext: String): String = when (ext) {
        "pdf" -> "application/pdf"
        "doc", "docx" -> "application/msword"
        "zip" -> "application/zip"
        "rar", "7z" -> "application/x-rar-compressed"
        "apk" -> "application/vnd.android.package-archive"
        "xlsx", "xls" -> "application/vnd.ms-excel"
        "ppt", "pptx" -> "application/vnd.ms-powerpoint"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "html" -> "text/html"
        else -> "application/octet-stream"
    }

    private fun getWhatsAppStatusFiles(
        context: Context,
        isVideo: Boolean,
        safeSet: Set<String>,
        trashSet: Set<String>,
        starredSet: Set<String>
    ): List<LocalVideoItem> {
        val result = mutableListOf<LocalVideoItem>()
        val baseExt = Environment.getExternalStorageDirectory().absolutePath
        val statusDirs = listOf(
            File("$baseExt/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
            File("$baseExt/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
            File("$baseExt/WhatsApp/Media/.Statuses")
        )

        for (dir in statusDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    val path = file.absolutePath
                    val isVideoFile = path.endsWith(".mp4", true) || path.endsWith(".mkv", true)
                    val isImageFile = path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) || path.endsWith(".png", true)

                    if ((isVideo && isVideoFile) || (!isVideo && isImageFile)) {
                        if (path !in safeSet && path !in trashSet && file.length() > 0) {
                            val uri = Uri.fromFile(file)
                            val folderName = if (dir.path.contains("w4b")) "WA Business Status" else "WhatsApp Status"
                            result.add(
                                LocalVideoItem(
                                    id = file.hashCode().toLong(),
                                    title = file.nameWithoutExtension,
                                    displayName = file.name,
                                    durationMs = 0L,
                                    sizeBytes = file.length(),
                                    path = path,
                                    contentUriString = uri.toString(),
                                    folderName = folderName,
                                    bucketId = folderName.hashCode().toString(),
                                    dateAdded = file.lastModified() / 1000,
                                    mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                                    isStarred = path in starredSet
                                )
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    // =========================================================================
    // 📁 ৫. ফোল্ডার গ্রুপিং
    // =========================================================================
    suspend fun getCategoryFolders(context: Context, categoryIndex: Int): List<LocalVideoFolder> = withContext(Dispatchers.IO) {
        val items = when (categoryIndex) {
            1 -> getAllAudioTracks(context)
            2 -> getAllImages(context)
            3 -> getAllDocuments(context)
            else -> getAllVideos(context)
        }

        val folderMap = mutableMapOf<String, MutableList<LocalVideoItem>>()
        items.forEach { item ->
            val bId = item.bucketId.ifBlank { item.folderName.hashCode().toString() }
            folderMap.getOrPut(bId) { mutableListOf() }.add(item)
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

    // =========================================================================
    // 🎯 ৬. সেকশন ভিত্তিক ভিডিও ফিল্টারিং ও সর্টিং (Recently Played অপসারিত)
    // =========================================================================
    suspend fun getVideosForSection(
        context: Context,
        section: GallerySection,
        allVideos: List<LocalVideoItem>
    ): List<LocalVideoItem> = withContext(Dispatchers.Default) {
        when (section) {
            GallerySection.ALL_VIDEOS -> allVideos
            GallerySection.FOLDERS -> allVideos
            GallerySection.RECENTLY_ADDED -> {
                val fourteenDaysAgo = (System.currentTimeMillis() / 1000) - (14 * 24 * 3600)
                allVideos.filter { it.dateAdded >= fourteenDaysAgo }.sortedByDescending { it.dateAdded }
            }
            GallerySection.DOWNLOADS -> {
                allVideos.filter {
                    it.folderName.contains("download", ignoreCase = true) ||
                            it.path.contains("Download", ignoreCase = true)
                }
            }
            GallerySection.FAVORITES -> {
                val starred = getStarredPaths(context)
                allVideos.filter { it.path in starred }
            }
        }
    }

    fun applyFiltersAndSorting(
        videos: List<LocalVideoItem>,
        sortOrder: VideoSortOrder,
        filterConfig: GalleryFilterConfig,
        searchQuery: String
    ): List<LocalVideoItem> {
        var result = videos

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter {
                it.displayName.lowercase().contains(q) ||
                        it.title.lowercase().contains(q) ||
                        it.folderName.lowercase().contains(q)
            }
        }

        if (filterConfig.selectedFolderBucketId != null) {
            result = result.filter { it.bucketId == filterConfig.selectedFolderBucketId }
        }

        if (filterConfig.resolutionFilter != VideoResolutionFilter.ALL) {
            result = result.filter { item ->
                val badge = item.resolution ?: ""
                when (filterConfig.resolutionFilter) {
                    VideoResolutionFilter.UHD_4K -> badge.contains("4K", ignoreCase = true)
                    VideoResolutionFilter.FHD_1080P -> badge.contains("1080", ignoreCase = true)
                    VideoResolutionFilter.HD_720P -> badge.contains("720", ignoreCase = true)
                    VideoResolutionFilter.SD -> badge.contains("SD", ignoreCase = true)
                    else -> true
                }
            }
        }

        if (filterConfig.durationFilter != VideoDurationFilter.ALL) {
            result = result.filter { item ->
                val durationMs = item.durationMs
                when (filterConfig.durationFilter) {
                    VideoDurationFilter.SHORT_UNDER_5M -> durationMs in 1..(5 * 60 * 1000L)
                    VideoDurationFilter.MEDIUM_5_TO_20M -> durationMs in (5 * 60 * 1000L)..(20 * 60 * 1000L)
                    VideoDurationFilter.LONG_OVER_20M -> durationMs > (20 * 60 * 1000L)
                    else -> true
                }
            }
        }

        return when (sortOrder.field) {
            VideoSortField.DATE_ADDED -> {
                if (sortOrder.direction == SortDirection.DESCENDING) {
                    result.sortedByDescending { it.dateAdded }
                } else {
                    result.sortedBy { it.dateAdded }
                }
            }
            VideoSortField.TITLE -> {
                if (sortOrder.direction == SortDirection.ASCENDING) {
                    result.sortedWith { a, b -> a.displayName.compareTo(b.displayName, ignoreCase = true) }
                } else {
                    result.sortedWith { a, b -> b.displayName.compareTo(a.displayName, ignoreCase = true) }
                }
            }
            VideoSortField.SIZE -> {
                if (sortOrder.direction == SortDirection.DESCENDING) {
                    result.sortedByDescending { it.sizeBytes }
                } else {
                    result.sortedBy { it.sizeBytes }
                }
            }
            VideoSortField.DURATION -> {
                if (sortOrder.direction == SortDirection.DESCENDING) {
                    result.sortedByDescending { it.durationMs }
                } else {
                    result.sortedBy { it.durationMs }
                }
            }
        }
    }

    // =========================================================================
    // 🔒 ৭. সেফ ফোল্ডার ফাইলস
    // =========================================================================
    suspend fun getSafeFolderItems(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val safePaths = getSafePaths(context)
        val result = mutableListOf<LocalVideoItem>()

        for (path in safePaths) {
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                val name = file.name
                val ext = file.extension.lowercase()
                val mime = when {
                    ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> "video/mp4"
                    ext in listOf("mp3", "m4a", "wav", "aac") -> "audio/mpeg"
                    ext in listOf("jpg", "jpeg", "png", "webp") -> "image/jpeg"
                    else -> getMimeType(ext)
                }

                result.add(
                    LocalVideoItem(
                        id = file.hashCode().toLong(),
                        title = file.nameWithoutExtension,
                        displayName = name,
                        durationMs = 0L,
                        sizeBytes = file.length(),
                        path = path,
                        contentUriString = Uri.fromFile(file).toString(),
                        folderName = "Safe Folder",
                        bucketId = "safe_folder",
                        dateAdded = file.lastModified() / 1000,
                        mimeType = mime
                    )
                )
            }
        }
        result
    }

    // =========================================================================
    // ♻️ ৮. রিয়েল Recycle Bin ইঞ্জিন
    // =========================================================================
    private fun getRecycleBinPhysicalVault(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), ".recycle_bin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTrashedMetadataList(context: Context): MutableList<TrashedVideoMetadata> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TRASHED_METADATA_JSON, "[]") ?: "[]"
        val list = mutableListOf<TrashedVideoMetadata>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TrashedVideoMetadata(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        originalPath = obj.getString("originalPath"),
                        trashLocationPath = obj.getString("trashLocationPath"),
                        originalFileName = obj.getString("originalFileName"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        durationMs = obj.optLong("durationMs", 0L),
                        trashedTimestamp = obj.optLong("trashedTimestamp", System.currentTimeMillis()),
                        mimeType = obj.optString("mimeType", "video/mp4"),
                        contentUriString = obj.optString("contentUriString", null)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveTrashedMetadataList(context: Context, list: List<TrashedVideoMetadata>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("originalPath", item.originalPath)
                put("trashLocationPath", item.trashLocationPath)
                put("originalFileName", item.originalFileName)
                put("sizeBytes", item.sizeBytes)
                put("durationMs", item.durationMs)
                put("trashedTimestamp", item.trashedTimestamp)
                put("mimeType", item.mimeType)
                put("contentUriString", item.contentUriString)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_TRASHED_METADATA_JSON, jsonArray.toString()).apply()
    }

    suspend fun moveToRecycleBinReal(context: Context, item: LocalVideoItem): Boolean = withContext(Dispatchers.IO) {
        val srcFile = File(item.path)
        if (!srcFile.exists()) return@withContext false

        val vaultDir = getRecycleBinPhysicalVault(context)
        val trashedFileName = "bin_${System.currentTimeMillis()}_${srcFile.name}"
        val destFile = File(vaultDir, trashedFileName)

        var moved = srcFile.renameTo(destFile)
        if (!moved) {
            try {
                FileInputStream(srcFile).use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                moved = srcFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed stream copy to bin: ${e.message}")
            }
        }

        if (moved) {
            val metadataList = getTrashedMetadataList(context)
            metadataList.removeAll { it.originalPath == item.path }
            metadataList.add(
                TrashedVideoMetadata(
                    id = item.id,
                    originalPath = item.path,
                    trashLocationPath = destFile.absolutePath,
                    originalFileName = item.displayName,
                    sizeBytes = item.sizeBytes,
                    durationMs = item.durationMs,
                    trashedTimestamp = System.currentTimeMillis(),
                    mimeType = item.mimeType,
                    contentUriString = item.contentUriString
                )
            )
            saveTrashedMetadataList(context, metadataList)

            val trashPaths = getTrashPaths(context).toMutableSet()
            trashPaths.add(item.path)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_TRASH, trashPaths).apply()

            try {
                MediaScannerConnection.scanFile(context, arrayOf(item.path), null, null)
            } catch (_: Exception) {}

            return@withContext true
        }

        moveToTrash(context, item.path)
        true
    }

    suspend fun restoreFromRecycleBinReal(context: Context, metadata: TrashedVideoMetadata): Boolean = withContext(Dispatchers.IO) {
        val binFile = File(metadata.trashLocationPath)
        val origFile = File(metadata.originalPath)

        origFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        var restored = if (binFile.exists()) {
            binFile.renameTo(origFile) || run {
                try {
                    FileInputStream(binFile).use { inStream ->
                        FileOutputStream(origFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    binFile.delete()
                } catch (_: Exception) {
                    false
                }
            }
        } else false

        val metadataList = getTrashedMetadataList(context)
        metadataList.removeAll { it.originalPath == metadata.originalPath || it.trashLocationPath == metadata.trashLocationPath }
        saveTrashedMetadataList(context, metadataList)

        restoreFromTrash(context, metadata.originalPath)

        try {
            MediaScannerConnection.scanFile(context, arrayOf(metadata.originalPath), null, null)
        } catch (_: Exception) {}

        restored
    }

    suspend fun getRecycleBinMetadataList(context: Context): List<TrashedVideoMetadata> = withContext(Dispatchers.IO) {
        getTrashedMetadataList(context)
    }

    suspend fun getTrashItems(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val metadataList = getTrashedMetadataList(context)
        val result = mutableListOf<LocalVideoItem>()

        for (meta in metadataList) {
            val file = File(meta.trashLocationPath)
            val effectiveFile = if (file.exists()) file else File(meta.originalPath)
            if (effectiveFile.exists()) {
                result.add(
                    LocalVideoItem(
                        id = meta.id,
                        title = meta.originalFileName.substringBeforeLast('.'),
                        displayName = meta.originalFileName,
                        durationMs = meta.durationMs,
                        sizeBytes = meta.sizeBytes,
                        path = effectiveFile.absolutePath,
                        contentUriString = Uri.fromFile(effectiveFile).toString(),
                        folderName = "Recycle Bin",
                        bucketId = "recycle_bin",
                        dateAdded = meta.trashedTimestamp / 1000,
                        mimeType = meta.mimeType ?: "video/mp4",
                        isTrashed = true
                    )
                )
            }
        }

        val legacyTrashPaths = getTrashPaths(context)
        for (path in legacyTrashPaths) {
            if (result.none { it.path == path }) {
                val f = File(path)
                if (f.exists() && f.length() > 0) {
                    result.add(
                        LocalVideoItem(
                            id = f.hashCode().toLong(),
                            title = f.nameWithoutExtension,
                            displayName = f.name,
                            durationMs = 0L,
                            sizeBytes = f.length(),
                            path = path,
                            contentUriString = Uri.fromFile(f).toString(),
                            folderName = "Recycle Bin",
                            bucketId = "recycle_bin",
                            dateAdded = f.lastModified() / 1000,
                            mimeType = "video/mp4",
                            isTrashed = true
                        )
                    )
                }
            }
        }

        result
    }

    // =========================================================================
    // 📊 ৯. লাইভ ব্যাচ ডিলিশন ইঞ্জিন
    // =========================================================================
    suspend fun executeBatchDelete(
        context: Context,
        items: List<LocalVideoItem>,
        option: DeleteOption,
        onProgress: (DeleteProgressState) -> Unit
    ): DeleteSummaryReport = withContext(Dispatchers.IO) {
        val total = items.size
        var successCount = 0
        var failedCount = 0
        var totalFreedBytes = 0L
        val failedFileNames = mutableListOf<String>()

        items.forEachIndexed { index, item ->
            val fileName = item.displayName.ifBlank { item.title }
            val remaining = total - (index + 1)

            val currentProgress = if (total > 0) (((index) * 100) / total) else 0
            onProgress(
                DeleteProgressState(
                    isRunning = true,
                    operationType = option,
                    totalFiles = total,
                    successfullyDeletedCount = successCount,
                    remainingFilesCount = total - index,
                    failedCount = failedCount,
                    currentProcessingFileName = fileName,
                    progressPercent = currentProgress,
                    statusMessage = if (option == DeleteOption.MOVE_TO_RECYCLE_BIN) "Moving to Recycle Bin..." else "Permanently deleting..."
                )
            )

            val isSuccess = when (option) {
                DeleteOption.MOVE_TO_RECYCLE_BIN -> {
                    moveToRecycleBinReal(context, item)
                }
                DeleteOption.PERMANENTLY_DELETE -> {
                    val metaList = getTrashedMetadataList(context)
                    val matchingMeta = metaList.find { it.originalPath == item.path || it.trashLocationPath == item.path }
                    if (matchingMeta != null) {
                        File(matchingMeta.trashLocationPath).delete()
                        metaList.remove(matchingMeta)
                        saveTrashedMetadataList(context, metaList)
                    }
                    deletePermanently(context, item.path)
                }
            }

            if (isSuccess) {
                successCount++
                totalFreedBytes += item.sizeBytes
            } else {
                failedCount++
                failedFileNames.add(fileName)
            }

            val finalCurrentProgress = if (total > 0) (((index + 1) * 100) / total) else 100
            onProgress(
                DeleteProgressState(
                    isRunning = (index < total - 1),
                    operationType = option,
                    totalFiles = total,
                    successfullyDeletedCount = successCount,
                    remainingFilesCount = remaining,
                    failedCount = failedCount,
                    currentProcessingFileName = fileName,
                    progressPercent = finalCurrentProgress,
                    isFinished = (index == total - 1),
                    failedFilesList = failedFileNames,
                    statusMessage = if (index == total - 1) "Completed!" else if (option == DeleteOption.MOVE_TO_RECYCLE_BIN) "Moving..." else "Deleting..."
                )
            )
        }

        DeleteSummaryReport(
            operationType = option,
            totalFilesProcessed = total,
            successCount = successCount,
            failedCount = failedCount,
            failedFileNames = failedFileNames,
            totalFreedBytes = totalFreedBytes
        )
    }

    // =========================================================================
    // 🚀 ১০. ফাইল মুভ এবং নতুন ফোল্ডার তৈরি
    // =========================================================================
    suspend fun moveFileToDestination(context: Context, sourcePath: String, destinationFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = File(sourcePath)
            if (!src.exists()) return@withContext false

            val destDir = File(destinationFolderPath)
            if (!destDir.exists()) destDir.mkdirs()

            val targetFile = File(destDir, src.name)
            if (src.renameTo(targetFile)) {
                MediaScannerConnection.scanFile(context, arrayOf(sourcePath, targetFile.absolutePath), null, null)
                return@withContext true
            }

            val input = FileInputStream(src)
            val output = FileOutputStream(targetFile)
            input.use { inStream ->
                output.use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            src.delete()
            MediaScannerConnection.scanFile(context, arrayOf(sourcePath, targetFile.absolutePath), null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Move failed: ${e.message}")
            false
        }
    }

    suspend fun createNewFolderAtRoot(folderName: String): String? = withContext(Dispatchers.IO) {
        try {
            val root = Environment.getExternalStorageDirectory()
            val newDir = File(root, folderName)
            if (!newDir.exists()) newDir.mkdirs()
            newDir.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // ⭐ ১১. Starred, Safe Folder ও Custom PIN
    // =========================================================================
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

    private fun removeFromStarred(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_STARRED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.remove(path)) {
            prefs.edit().putStringSet(KEY_STARRED, current).apply()
        }
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

    fun restoreFromSafeFolder(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_SAFE_FOLDER, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(path)
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

    // =========================================================================
    // 🔥 ১২. ফোন স্টোরেজ থেকে পার্মানেন্ট ডিলিট ইঞ্জিন
    // =========================================================================
    fun cleanUpDeletedPath(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        removeFromStarred(context, path)
        restoreFromTrash(context, path)
        restoreFromSafeFolder(context, path)
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) {}
    }

    suspend fun deletePermanently(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        cleanUpDeletedPath(context, path)

        var isDeleted = false

        try {
            val file = File(path)
            if (file.exists()) {
                if (file.delete()) {
                    isDeleted = true
                }
            }
        } catch (_: Exception) {}

        try {
            val mediaUris = listOf(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Files.getContentUri("external")
                }
            )

            for (collectionUri in mediaUris) {
                try {
                    val count = context.contentResolver.delete(
                        collectionUri,
                        "${MediaStore.MediaColumns.DATA} = ?",
                        arrayOf(path)
                    )
                    if (count > 0) {
                        isDeleted = true
                        break
                    }
                } catch (se: SecurityException) {
                    Log.w(TAG, "ContentResolver security restriction for $path: ${se.message}")
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) {}

        isDeleted
    }

    suspend fun emptyTrash(context: Context): Boolean = withContext(Dispatchers.IO) {
        val metaList = getTrashedMetadataList(context)
        for (meta in metaList) {
            File(meta.trashLocationPath).delete()
            deletePermanently(context, meta.originalPath)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TRASH).remove(KEY_TRASHED_METADATA_JSON).apply()
        true
    }

    fun renameFile(context: Context, oldPath: String, newName: String): Boolean {
        return try {
            val file = File(oldPath)
            val ext = file.extension
            val newFileName = if (ext.isNotBlank()) "$newName.$ext" else newName
            val newFile = File(file.parentFile, newFileName)
            val success = file.renameTo(newFile)
            if (success) {
                MediaScannerConnection.scanFile(context, arrayOf(oldPath, newFile.absolutePath), null, null)
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    fun isSafeFolderPinSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_SAFE_PIN)
    }

    fun getSavedSafePin(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SAFE_PIN, null)
    }

    fun saveSafePin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAFE_PIN, pin).apply()
    }
}
