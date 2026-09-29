package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.CountingRequestBody
import com.example.data.remote.ReelsApiClient
import com.example.data.remote.ReelsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class ReelsRepository(
    private val context: Context,
    private val vps1Service: ReelsApiService = ReelsApiClient.vps1Service,
    private val vps2UploadService: ReelsApiService = ReelsApiClient.vps2UploadService,
    private val authRepository: AuthRepository = AuthRepository(context)
) {
    companion object {
        private const val TAG = "ReelsRepository"
        const val MAX_REEL_DURATION_MS = 180_000L
        const val MAX_REEL_SIZE_BYTES = 80L * 1024L * 1024L
    }

    private fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    suspend fun getReelsFeed(tab: String = "for_you", page: Int = 1): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 }
        try {
            val response = vps1Service.getReelsFeed(
                action = "get_reels",
                tab = tab,
                userId = userId,
                page = page
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.reels)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feed exception: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🔥 CRITICAL: REAL-TIME WATCH TRACKER ENGINE (ALGORITHM TRIGGER)
    // =========================================================================
    suspend fun trackReelWatch(
        reelId: Int,
        watchTimeSec: Int,
        isCompleted: Boolean,
        isSkipped: Boolean,
        isRewatch: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        try {
            val response = vps1Service.trackReelWatch(
                action = "track_reel_watch",
                reelId = reelId,
                userId = userId,
                watchTimeSec = watchTimeSec,
                isCompleted = isCompleted,
                isSkipped = isSkipped,
                isRewatch = isRewatch
            )
            if (response.isSuccessful) {
                Log.d(TAG, "✓ Algorithm Ping: Reel $reelId | ${watchTimeSec}s | Completed: $isCompleted | Skipped: $isSkipped | Rewatch: $isRewatch")
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Algorithm ping notice: ${e.message}")
            Result.success(false)
        }
    }

    suspend fun uploadReel(
        pageId: Int,
        title: String?,
        description: String?,
        videoUri: Uri,
        onProgressUpdate: (percent: Int) -> Unit
    ): Result<ReelUploadResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to upload a reel."))
        }

        val fileSizeBytes = getFileSizeBytes(context, videoUri)
        if (fileSizeBytes > MAX_REEL_SIZE_BYTES) {
            return@withContext Result.failure(Exception("Video size exceeds 80 MB!"))
        }

        val durationMs = getVideoDurationMs(context, videoUri)
        if (durationMs > MAX_REEL_DURATION_MS) {
            return@withContext Result.failure(Exception("Video duration exceeds 3 minutes!"))
        }

        val mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4"
        val ext = if (mimeType.contains("quicktime", true) || mimeType.contains("mov", true)) "mov" else "mp4"

        val tempFile = prepareTempVideoFile(context, videoUri, ext)
            ?: return@withContext Result.failure(Exception("Could not read video file."))

        try {
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val pageIdPart = pageId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val titlePart = (title?.trim() ?: "My Reel").toRequestBody("text/plain".toMediaTypeOrNull())
            val descPart = (description?.trim() ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val hashtagsPart = "".toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryPart = "Entertainment".toRequestBody("text/plain".toMediaTypeOrNull())
            val privacyPart = "public".toRequestBody("text/plain".toMediaTypeOrNull())

            val requestFile = CountingRequestBody(tempFile, mimeType) { bytesWritten, totalBytes ->
                if (totalBytes > 0) {
                    val percent = ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 100)
                    onProgressUpdate(percent)
                }
            }

            val fileName = "reel_${System.currentTimeMillis()}.$ext"
            val videoPart = MultipartBody.Part.createFormData("video", fileName, requestFile)

            val response = vps2UploadService.uploadFullReelWorkflow(
                userId = uidPart,
                pageId = pageIdPart,
                title = titlePart,
                description = descPart,
                hashtags = hashtagsPart,
                category = categoryPart,
                linkUrl = null,
                privacy = privacyPart,
                video = videoPart,
                customThumb = null
            )

            tempFile.delete()

            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Upload failed on server."
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Reel upload error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun interactReel(reelId: Int, type: String): Result<ReelInteractionResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        try {
            val response = vps1Service.interactReel(
                action = "interact_reel",
                reelId = reelId,
                userId = userId,
                type = type
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to register $type on server."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyCreatorPage(): Result<CreatorPageDto?> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.success(null)
        }

        try {
            val response = vps1Service.getMyCreatorPage(action = "get_my_page", userId = userId)
            if (response.isSuccessful && response.body() != null && response.body()!!.hasPage) {
                Result.success(response.body()!!.page)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    suspend fun toggleFollowPage(pageId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to follow."))
        }

        try {
            val response = vps1Service.toggleFollowPage(pageId = pageId, userId = userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.isFollowing)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCreatorPageProfile(
        pageId: Int,
        pageName: String,
        handle: String,
        bio: String?,
        customLink: String?,
        avatarUri: Uri?
    ): Result<ApplyPageResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in."))
        }

        try {
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val pageIdPart = pageId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val namePart = pageName.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val handlePart = handle.trim().removePrefix("@").toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = bio?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val linkPart = customLink?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())

            var avatarPart: MultipartBody.Part? = null
            if (avatarUri != null) {
                val inputStream = context.contentResolver.openInputStream(avatarUri)
                val tempFile = File(context.cacheDir, "page_avatar_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                val reqFile = tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                avatarPart = MultipartBody.Part.createFormData("avatar", tempFile.name, reqFile)
            }

            val response = vps1Service.updateCreatorPageProfile(
                userId = uidPart,
                pageId = pageIdPart,
                pageName = namePart,
                handle = handlePart,
                bio = bioPart,
                customLink = linkPart,
                avatar = avatarPart
            )

            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Update failed"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update page error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 💬 COMMENTS SYSTEM (TikTok Style)
    // =========================================================================

    suspend fun getReelComments(reelId: Int): Result<List<ReelCommentDto>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 }
        try {
            val response = vps1Service.getReelComments(reelId = reelId, userId = userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.comments)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReelComment(reelId: Int, text: String, parentId: Int? = null): Result<ReelCommentDto> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to comment."))
        }

        try {
            val response = vps1Service.addReelComment(
                reelId = reelId,
                userId = userId,
                commentText = text.trim(),
                parentId = parentId
            )
            if (response.isSuccessful && response.body()?.comment != null) {
                Result.success(response.body()!!.comment!!)
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Failed to post comment"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleCommentLike(commentId: Int): Result<ToggleCommentLikeResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in."))
        }

        try {
            val response = vps1Service.toggleCommentLike(commentId = commentId, userId = userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to like comment"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🔁 REPOST SYSTEM
    // =========================================================================

    suspend fun toggleRepost(reelId: Int, caption: String? = null): Result<ToggleRepostResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to repost."))
        }

        try {
            val response = vps1Service.toggleRepost(reelId = reelId, userId = userId, caption = caption)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to repost"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🔖 SAVE / BOOKMARK REEL
    // =========================================================================

    suspend fun toggleSaveReel(reelId: Int): Result<ToggleSaveReelResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to save reels."))
        }

        try {
            val response = vps1Service.toggleSaveReel(reelId = reelId, userId = userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to save reel"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSavedReels(): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.success(emptyList())
        }

        try {
            val response = vps1Service.getSavedReels(userId = userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.reels)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 📤 SHARE TRACKING
    // =========================================================================

    suspend fun recordShare(reelId: Int, platform: String = "direct"): Result<RecordShareResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        try {
            val response = vps1Service.recordShare(reelId = reelId, userId = userId, platform = platform)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Share record failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileSizeBytes(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun getVideoDurationMs(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        } catch (_: Exception) {
            0L
        }
    }

    private fun prepareTempVideoFile(context: Context, fileUri: Uri, ext: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return null
            val tempFile = File(context.cacheDir, "upload_reel_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Temp video creation failed: ${e.message}")
            null
        }
    }
}
