package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        private const val PREFS_FOLLOW_CACHE = "reels_follow_cache_prefs"
        private const val KEY_FOLLOWED_IDS = "followed_creator_keys"
        const val MAX_REEL_DURATION_MS = 180_000L // ৩ মিনিট
        const val MAX_REEL_SIZE_BYTES = 80L * 1024L * 1024L // ৮০ এমবি
    }

    private val followPrefs = context.getSharedPreferences(PREFS_FOLLOW_CACHE, Context.MODE_PRIVATE)

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // =========================================================================
    // 🔒 ফলো পারসিস্টেন্স ক্যাশ হেল্পার ফাংশনসমূহ
    // =========================================================================
    private fun getFollowKey(pageId: Int, userId: Int): String {
        return if (pageId > 0) "page_$pageId" else "user_$userId"
    }

    private fun getLocalFollowedKeys(): Set<String> {
        return followPrefs.getStringSet(KEY_FOLLOWED_IDS, emptySet()) ?: emptySet()
    }

    fun isCreatorFollowed(pageId: Int, userId: Int): Boolean {
        val key = getFollowKey(pageId, userId)
        return getLocalFollowedKeys().contains(key)
    }

    private fun setLocalFollowState(pageId: Int, userId: Int, isFollowing: Boolean) {
        val key = getFollowKey(pageId, userId)
        val currentKeys = getLocalFollowedKeys().toMutableSet()
        if (isFollowing) {
            currentKeys.add(key)
        } else {
            currentKeys.remove(key)
        }
        followPrefs.edit().putStringSet(KEY_FOLLOWED_IDS, currentKeys).apply()
    }

    // =========================================================================
    // 🌟 ১. SUGGESTED CREATORS & PAGES API (Instagram/TikTok Style)
    // =========================================================================
    suspend fun getSuggestedPages(): Result<List<SuggestedPageDto>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        try {
            val response = vps1Service.getSuggestedPages(
                action = "get_suggested_pages",
                userId = userId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val serverPages = response.body()!!.effectivePages

                // সার্ভার ফলো স্টেট ও লোকাল ক্যাশ সিঙ্ক করা
                val resolvedPages = serverPages.map { page ->
                    val isLocallyFollowed = isCreatorFollowed(page.pageId, page.userId)
                    val effectiveFollowing = page.isFollowing || isLocallyFollowed

                    if (page.isFollowing) {
                        setLocalFollowState(page.pageId, page.userId, true)
                    }

                    page.copy(rawIsFollowing = effectiveFollowing)
                }

                Result.success(resolvedPages)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSuggestedPages error: ${e.message}")
            Result.success(emptyList())
        }
    }

    // =========================================================================
    // 👑 ২. REAL-TIME USER PROFILE & METRICS (Server Spec 1 - VPS 1)
    // =========================================================================
    suspend fun getUserProfileMetrics(targetUserId: Int): Result<UserProfileMetricsDto?> = withContext(Dispatchers.IO) {
        val viewerId = getCurrentUserId()
        try {
            val response = vps1Service.getUserProfileMetrics(
                action = "get_user_profile",
                targetUserId = targetUserId,
                viewerId = viewerId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val profile = response.body()!!.effectiveProfile
                if (profile != null) {
                    val isLocallyFollowed = isCreatorFollowed(profile.pageId ?: 0, profile.userId)
                    val effectiveFollowing = profile.isFollowing || isLocallyFollowed

                    if (profile.isFollowing) {
                        setLocalFollowState(profile.pageId ?: 0, profile.userId, true)
                    }

                    val updatedProfile = profile.copy(
                        rawIsFollowing = effectiveFollowing
                    )
                    Result.success(updatedProfile)
                } else {
                    Result.success(null)
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Failed to fetch profile metrics"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile metrics error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🖼️ ৩. AVATAR UPLOAD (Server Spec 2a - VPS 2 R2 Ingest)
    // =========================================================================
    suspend fun uploadUserAvatar(imageUri: Uri, fallbackUserId: Int = 0): Result<String> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 } ?: fallbackUserId
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to upload avatar."))
        }

        val tempFile = prepareCompressedImageFile(context, imageUri, "avatar")
            ?: return@withContext Result.failure(Exception("Could not read image file."))

        try {
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val requestFile = tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", tempFile.name, requestFile)

            val response = vps2UploadService.uploadUserAvatar(
                userId = uidPart,
                image = imagePart
            )

            tempFile.delete()

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val finalUrl = body.effectiveUrl ?: ""
                if (finalUrl.isNotBlank()) {
                    authRepository.updateUserAvatarAndName(null, finalUrl)
                    Result.success(finalUrl)
                } else {
                    Result.failure(Exception(body.message ?: "Avatar URL missing from response"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Avatar upload failed (HTTP ${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Avatar upload error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🖼️ ৪. COVER PHOTO UPLOAD (Server Spec 2b - VPS 2 R2 Ingest)
    // =========================================================================
    suspend fun uploadUserCover(imageUri: Uri, fallbackUserId: Int = 0): Result<String> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 } ?: fallbackUserId
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to upload cover photo."))
        }

        val tempFile = prepareCompressedImageFile(context, imageUri, "cover")
            ?: return@withContext Result.failure(Exception("Could not read image file."))

        try {
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val requestFile = tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", tempFile.name, requestFile)

            val response = vps2UploadService.uploadUserCover(
                userId = uidPart,
                image = imagePart
            )

            tempFile.delete()

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val finalUrl = body.effectiveUrl ?: ""
                if (finalUrl.isNotBlank()) {
                    Result.success(finalUrl)
                } else {
                    Result.failure(Exception(body.message ?: "Cover URL missing from server response"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Cover upload failed (HTTP ${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Cover upload error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🎯 ৫. রিয়েল-টাইম ফলো / আনফলো (পারসিস্টেন্ট ব্যাকএন্ড সিঙ্ক)
    // =========================================================================
    suspend fun toggleFollowPage(pageId: Int, targetUserId: Int = pageId): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to follow."))
        }

        val resolvedPageId = if (pageId > 0) pageId else targetUserId
        val resolvedTargetUserId = if (targetUserId > 0) targetUserId else pageId

        val previousState = isCreatorFollowed(resolvedPageId, resolvedTargetUserId)
        val optimisticNewState = !previousState
        setLocalFollowState(resolvedPageId, resolvedTargetUserId, optimisticNewState)

        try {
            val response = vps1Service.toggleFollowPage(
                action = "toggle_follow_page",
                pageId = resolvedPageId,
                targetUserId = resolvedTargetUserId,
                userId = userId
            )
            if (response.isSuccessful && response.body() != null) {
                val finalFollowState = response.body()!!.effectiveIsFollowing
                setLocalFollowState(resolvedPageId, resolvedTargetUserId, finalFollowState)
                Result.success(finalFollowState)
            } else {
                setLocalFollowState(resolvedPageId, resolvedTargetUserId, previousState)
                val errorMsg = response.errorBody()?.string() ?: "Failed to follow on server"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            setLocalFollowState(resolvedPageId, resolvedTargetUserId, previousState)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 👤 ৬. CREATOR PAGE PROFILE UPDATE
    // =========================================================================
    suspend fun updateCreatorPageProfile(
        pageId: Int,
        pageName: String,
        handle: String,
        bio: String?,
        customLink: String?,
        avatarUri: Uri?,
        fallbackUserId: Int = 0
    ): Result<ApplyPageResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 } ?: fallbackUserId
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in."))
        }

        try {
            val actionPart = "update_page".toRequestBody("text/plain".toMediaTypeOrNull())
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val pageIdPart = pageId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val namePart = pageName.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val handlePart = handle.trim().removePrefix("@").toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = bio?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val linkPart = customLink?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())

            var avatarPart: MultipartBody.Part? = null
            if (avatarUri != null) {
                val tempImg = prepareCompressedImageFile(context, avatarUri, "page_avatar")
                if (tempImg != null) {
                    val reqFile = tempImg.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    avatarPart = MultipartBody.Part.createFormData("avatar", tempImg.name, reqFile)
                }
            }

            val response = vps1Service.updateCreatorPageProfile(
                action = actionPart,
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
    // 🎬 ৭. REELS FEED
    // =========================================================================
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
                val serverReels = response.body()!!.reels

                val resolvedReels = serverReels.map { reel ->
                    val isLocallyFollowed = isCreatorFollowed(reel.pageId, reel.userId)
                    val effectiveIsFollowing = reel.isFollowing || isLocallyFollowed

                    if (reel.isFollowing) {
                        setLocalFollowState(reel.pageId, reel.userId, true)
                    }

                    reel.copy(isFollowing = effectiveIsFollowing)
                }

                val finalFeed = if (tab == "following") {
                    resolvedReels.filter { it.isFollowing }
                } else {
                    resolvedReels
                }

                Result.success(finalFeed)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feed exception: ${e.message}")
            Result.failure(e)
        }
    }

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

    // =========================================================================
    // 🚀 ৮. REEL UPLOAD WORKFLOW
    // =========================================================================
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

    // =========================================================================
    // ❤️ ৯. SOCIAL INTERACTIONS & COMMENTS
    // =========================================================================
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

    suspend fun toggleRepost(reelId: Int, caption: String? = null): Result<ToggleRepostResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to repost."))
        }

        try {
            val response = vps1Service.toggleRepost(
                action = "toggle_repost",
                reelId = reelId,
                userId = userId,
                caption = caption
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to repost"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleSaveReel(reelId: Int): Result<ToggleSaveReelResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to save reels."))
        }

        try {
            val response = vps1Service.toggleSaveReel(
                action = "toggle_save_reel",
                reelId = reelId,
                userId = userId
            )
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
            val response = vps1Service.getSavedReels(
                action = "get_saved_reels",
                userId = userId
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.reels)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordShare(reelId: Int, platform: String = "direct"): Result<RecordShareResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        try {
            val response = vps1Service.recordShare(
                action = "record_share",
                reelId = reelId,
                userId = userId,
                platform = platform
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Share record failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReelComments(reelId: Int): Result<List<ReelCommentDto>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 }
        try {
            val response = vps1Service.getReelComments(
                action = "get_comments",
                reelId = reelId,
                userId = userId
            )
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
                action = "add_comment",
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
            val response = vps1Service.toggleCommentLike(
                action = "toggle_comment_like",
                commentId = commentId,
                userId = userId
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to like comment"))
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
            val response = vps1Service.getMyCreatorPage(
                action = "get_my_page",
                userId = userId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.hasPage) {
                Result.success(response.body()!!.page)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    // =========================================================================
    // 🛠️ ফাইল ও ইমেজ হেল্পার ফাংশনসমূহ
    // =========================================================================
    private fun prepareCompressedImageFile(context: Context, uri: Uri, prefix: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val maxDimension = 1080
            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                if (width > height) {
                    Bitmap.createScaledBitmap(originalBitmap, maxDimension, (maxDimension / ratio).toInt().coerceAtLeast(1), true)
                } else {
                    Bitmap.createScaledBitmap(originalBitmap, (maxDimension * ratio).toInt().coerceAtLeast(1), maxDimension, true)
                }
            } else {
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Image compression error: ${e.message}")
            null
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
