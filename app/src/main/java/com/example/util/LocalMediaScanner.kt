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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    fun saveLastPlaybackPosition(context: Context, videoId: Long, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_PROGRESS_PREFIX$videoId", positionMs).apply()
    }

    fun getLastPlaybackPosition(context: Context, videoId: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_PROGRESS_PREFIX$videoId", 0L)
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
            MediaStore.Video.Media.RESOLUTION
        )

        try {
            val safeSet = getSafePaths(context)
            val trashSet = getTrashPaths(context)
            val starredSet = getStarredPaths(context)

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
                                resolution = res,
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
    // 📁 ৪. ফিক্সড ডকুমেন্টস ও ফাইল স্ক্যানার (PDF, ZIP, APK, Doc, TXT, Excel etc.)
    // =========================================================================
    suspend fun getAllDocuments(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideoItem>()
        val safeSet = getSafePaths(context)
        val trashSet = getTrashPaths(context)
        val starredSet = getStarredPaths(context)
        val addedPaths = mutableSetOf<String>()

        val validExtensions = setOf(
            "pdf", "doc", "docx", "txt", "zip", "rar", "7z", "apk", "xlsx", "xls", "ppt", "pptx", "csv", "json", "html"
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

            // ১. MediaStore.Files কুয়েরি
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

            // ২. Download এবং Documents ফোল্ডার ডিরেক্ট স্ক্যান (যদি কিছু মিস হয়ে থাকে)
            val root = Environment.getExternalStorageDirectory()
            val targetFolders = listOf(
                File(root, Environment.DIRECTORY_DOWNLOADS),
                File(root, Environment.DIRECTORY_DOCUMENTS),
                File(root, "Download"),
                File(root, "Documents")
            )

            for (dir in targetFolders) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().maxDepth(2).filter { it.isFile && it.length() > 0 }.forEach { file ->
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
            Log.e(TAG, "Docs scanner error: ${e.message}")
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
    // 🔒 ৬. সেফ ফোল্ডার ফাইলস
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
                    ext in listOf("mp4", "mkv", "avi", "mov") -> "video/mp4"
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
    // 🚀 ৭. ফাইল মুভ এবং নতুন ফোল্ডার তৈরি
    // =========================================================================
    suspend fun moveFileToDestination(context: Context, sourcePath: String, destinationFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = File(sourcePath)
            if (!src.exists()) return@withContext false

            val destDir = File(destinationFolderPath)
            if (!destDir.exists()) destDir.mkdirs()

            val targetFile = File(destDir, src.name)
            if (src.renameTo(targetFile)) return@withContext true

            val input = FileInputStream(src)
            val output = FileOutputStream(targetFile)
            input.use { inStream ->
                output.use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            src.delete()
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
    // ⭐ ৮. Starred, Safe Folder ও Custom PIN
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

    fun deletePermanently(context: Context, path: String): Boolean {
        restoreFromTrash(context, path)
        restoreFromSafeFolder(context, path)
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
