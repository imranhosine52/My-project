package com.example.data.repository.reels

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.manager.ReelInteractionGuard
import com.example.data.model.*
import com.example.data.remote.CountingRequestBody
import com.example.data.remote.ReelsApiClient
import com.example.data.remote.ReelsApiService
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 🎬 ReelFeedRepository
 * -----------------------------------------------------------------------------
 * ১. ২৪ ঘণ্টার কঠোর ভিউ গার্ড ও সার্ভার সিঙ্ক ইঞ্জিন (এক ভিজিটর = ২৪ ঘণ্টায় ১ ভিউ)।
 * ২. রিলস ফিড লোডিং ও ফলো স্টেট রেজলভার।
 * ৩. টিকটক অ্যালগরিদম ওয়াচ টাইম স্কোরিং ট্র্যাকার।
 * ৪. সার্চ, ট্রেন্ডিং হ্যাশট্যাগ ও ট্রান্সকোডার আপলোড ওয়ার্কফ্লো (VPS 2)।
 * ৫. লাইক, সেভ, শেয়ার ও নেস্টেড কমেন্টস হ্যান্ডলার।
 */
class ReelFeedRepository(
    private val context: Context,
    private val vps1Service: ReelsApiService = ReelsApiClient.vps1Service,
    private val vps2UploadService: ReelsApiService = ReelsApiClient.vps2UploadService,
    private val authRepository: AuthRepository = AuthRepository(context),
    private val creatorProfileRepository: CreatorProfileRepository = CreatorProfileRepository(
        context,
        vps1Service,
        vps2UploadService,
        authRepository
    )
) {
    companion object {
        private const val TAG = "ReelFeedRepository"
    }

    // 🛡️ ২৪ ঘণ্টার লোকাল ভিউ গার্ড সিঙ্গেলটন
    private val interactionGuard = ReelInteractionGuard.getInstance(context)

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // =========================================================================
    // 👁️ ১. ২৪ ঘণ্টার ভিউ ট্র্যাকার ও সার্ভার সিঙ্ক (Zero Fake/Loop Views)
    // =========================================================================

    /**
     * ভিডিও দেখলে কল হবে:
     * - ২৪ ঘণ্টার মধ্যে আগে দেখা হয়ে থাকলে সাথে সাথে false দিয়ে বের হয়ে যাবে (সার্ভারে রিকোয়েস্ট যাবে না)।
     * - ২৪ ঘণ্টার পর প্রথম ভিউ হলে এটি সার্ভারে পাঠাবে এবং লোকাল মেমোরিতে ২৪ ঘণ্টার জন্য লক করবে।
     */
    suspend fun recordReelViewLocal(reelId: Int): Boolean = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()

        // ১. ২৪ ঘণ্টার এলিজিবিলিটি চেক এবং লোকাল লক
        val isEligible = interactionGuard.recordLocalViewIfEligible(reelId, userId)
        if (!isEligible) {
            return@withContext false
        }

        // ২. নতুন জেনুইন ভিউ সার্ভারে পাঠানো
        try {
            val response = vps1Service.interactReel(
                action = "interact_reel",
                reelId = reelId,
                userId = userId,
                type = "view"
            )
            if (response.isSuccessful) {
                interactionGuard.markSyncCompleted(listOf(reelId.toString()))
                Log.d(TAG, "✓ View synced to database for Reel #$reelId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to immediately sync view for reel #$reelId: ${e.message}")
        }

        // ৩. পূর্বে অফলাইনে জমে থাকা কোনো ভিউ থাকলে তা সিঙ্ক করা
        if (interactionGuard.isBatchSyncDue()) {
            syncPendingViewsToServer()
        }

        true
    }

    /**
     * 🚀 পূর্বে জমে থাকা সমস্ত পেন্ডিং ভিউ সার্ভারে সিঙ্ক করার ফলব্যাক মেথড
     */
    suspend fun syncPendingViewsToServer(): Result<Int> = withContext(Dispatchers.IO) {
        val pendingReelIds = interactionGuard.getPendingViewReelIds()
        if (pendingReelIds.isEmpty()) {
            return@withContext Result.success(0)
        }

        val userId = getCurrentUserId()
        val successfullySyncedIds = mutableSetOf<String>()

        Log.i(TAG, "⚡ Syncing ${pendingReelIds.size} pending views to server...")

        try {
            for (reelIdStr in pendingReelIds) {
                val reelId = reelIdStr.toIntOrNull() ?: continue
                try {
                    val response = vps1Service.interactReel(
                        action = "interact_reel",
                        reelId = reelId,
                        userId = userId,
                        type = "view"
                    )
                    if (response.isSuccessful) {
                        successfullySyncedIds.add(reelIdStr)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync view for reel #$reelId: ${e.message}")
                }
            }

            if (successfullySyncedIds.isNotEmpty()) {
                interactionGuard.markSyncCompleted(successfullySyncedIds)
            }

            Result.success(successfullySyncedIds.size)
        } catch (e: Exception) {
            Log.e(TAG, "Batch view sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🌟 ২. রিলস ফিড লোড ও লোকাল ফলো স্টেট সিঙ্ক
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
                    val isLocallyFollowed = creatorProfileRepository.isCreatorFollowed(reel.pageId.toLong(), reel.userId)
                    val effectiveIsFollowing = reel.isFollowing || isLocallyFollowed

                    if (reel.isFollowing) {
                        creatorProfileRepository.setLocalFollowState(reel.pageId.toLong(), reel.userId, true)
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

    // =========================================================================
    // ⏱️ ৩. রিয়েল-টাইম টিকটক ওয়াচ স্কোরিং ট্র্যাকার
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
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.success(false)
        }
    }

    // =========================================================================
    // 🏷️ ৪. ট্রেন্ডিং হ্যাশট্যাগ ও সার্চ এক্সপ্লোরার
    // =========================================================================
    suspend fun getTrendingHashtags(): Result<List<TrendingHashtagDto>> = withContext(Dispatchers.IO) {
        try {
            val response = vps1Service.getTrendingHashtags(action = "get_trending_hashtags")
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!.effectiveHashtags)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTrendingHashtags error: ${e.message}")
            Result.success(emptyList())
        }
    }

    suspend fun getHashtagReels(tag: String, page: Int = 1): Result<HashtagDetailResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 }
        val cleanTag = tag.trim().removePrefix("#")

        try {
            val response = vps1Service.getHashtagReels(
                action = "get_hashtag_reels",
                tag = cleanTag,
                page = page,
                userId = userId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val body = response.body()!!
                val resolvedReels = body.reels.map { reel ->
                    val isLocallyFollowed = creatorProfileRepository.isCreatorFollowed(reel.pageId.toLong(), reel.userId)
                    reel.copy(isFollowing = reel.isFollowing || isLocallyFollowed)
                }
                Result.success(body.copy(reels = resolvedReels))
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Failed to fetch hashtag reels"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHashtagReels error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun searchReelsWithCategory(
        query: String? = null,
        category: String? = null,
        page: Int = 1
    ): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 }
        val cleanQuery = query?.trim()?.takeIf { it.isNotBlank() }
        val cleanCategory = category?.trim()?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }

        try {
            val response = vps1Service.searchReels(
                action = "search_reels",
                query = cleanQuery,
                category = cleanCategory,
                page = page,
                userId = userId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val serverReels = response.body()!!.reels
                val resolvedReels = serverReels.map { reel ->
                    val isLocallyFollowed = creatorProfileRepository.isCreatorFollowed(reel.pageId.toLong(), reel.userId)
                    reel.copy(isFollowing = reel.isFollowing || isLocallyFollowed)
                }
                Result.success(resolvedReels)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchReelsWithCategory error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🚀 ৫. ভিডিও রিলস আপলোড ওয়ার্কফ্লো (VPS 2 Transcoder Engine)
    // =========================================================================
    suspend fun uploadReel(
        pageId: Long,
        title: String?,
        description: String?,
        playlistId: Int? = null,
        episodeNum: Int = 1,
        videoUri: Uri,
        onProgressUpdate: (percent: Int) -> Unit
    ): Result<ReelUploadResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to upload."))

        val isSeries = (playlistId != null && playlistId > 0)
        val maxDuration = if (isSeries) ReelMediaHelper.MAX_SERIES_DURATION_MS else ReelMediaHelper.MAX_REEL_DURATION_MS
        val maxSizeBytes = if (isSeries) ReelMediaHelper.MAX_SERIES_SIZE_BYTES else ReelMediaHelper.MAX_REEL_SIZE_BYTES

        val fileSizeBytes = ReelMediaHelper.getFileSizeBytes(context, videoUri)
        if (fileSizeBytes > maxSizeBytes) {
            val limitMb = if (isSeries) 200 else 80
            return@withContext Result.failure(Exception("Video size exceeds ${limitMb} MB limit!"))
        }

        val durationMs = ReelMediaHelper.getVideoDurationMs(context, videoUri)
        if (durationMs > maxDuration) {
            val limitMin = if (isSeries) "10 minutes" else "3 minutes"
            return@withContext Result.failure(Exception("Video duration exceeds $limitMin limit!"))
        }

        val mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4"
        val ext = if (mimeType.contains("quicktime", true) || mimeType.contains("mov", true)) "mov" else "mp4"

        val tempFile = ReelMediaHelper.prepareTempVideoFile(context, videoUri, ext)
            ?: return@withContext Result.failure(Exception("Could not read video file."))

        try {
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val pageIdPart = pageId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val titlePart = (title?.trim() ?: "My Reel").toRequestBody("text/plain".toMediaTypeOrNull())
            val descPart = (description?.trim() ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val hashtagsPart = "".toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryPart = (if (isSeries) "Drama" else "Entertainment").toRequestBody("text/plain".toMediaTypeOrNull())
            val privacyPart = "public".toRequestBody("text/plain".toMediaTypeOrNull())

            val playlistIdPart = playlistId?.takeIf { it > 0 }?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val episodeNumPart = episodeNum.coerceAtLeast(1).toString().toRequestBody("text/plain".toMediaTypeOrNull())

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
                playlistId = playlistIdPart,
                episodeNum = episodeNumPart,
                video = videoPart,
                customThumb = null
            )

            tempFile.delete()

            if (response.isSuccessful && response.body()?.success == true) {
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

    suspend fun uploadReel(
        pageId: Int,
        title: String?,
        description: String?,
        playlistId: Int? = null,
        episodeNum: Int = 1,
        videoUri: Uri,
        onProgressUpdate: (percent: Int) -> Unit
    ): Result<ReelUploadResponse> = uploadReel(
        pageId.toLong(),
        title,
        description,
        playlistId,
        episodeNum,
        videoUri,
        onProgressUpdate
    )

    // =========================================================================
    // ❤️ ৬. সোশ্যাল ইন্টারঅ্যাকশন (লাইক, সেভ, রিপোস্ট, কমেন্টস)
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
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to repost."))

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
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to save reels."))

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

    suspend fun getSavedReels(targetUserId: Int? = null): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        val uid = targetUserId ?: getCurrentUserId()
        if (uid <= 0) return@withContext Result.success(emptyList())

        try {
            val response = vps1Service.getSavedReels(
                action = "get_saved_reels",
                userId = uid
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
                Result.success(response.body()!!.effectiveComments)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReelComment(
        reelId: Int,
        text: String,
        parentId: Int? = null,
        userName: String? = null,
        userAvatar: String? = null
    ): Result<ReelCommentDto> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to comment."))

        val savedProfile = authRepository.getSavedUserProfile()
        val finalName = userName?.takeIf { it.isNotBlank() }
            ?: savedProfile?.displayName
            ?: savedProfile?.name
            ?: "User"

        val finalAvatar = userAvatar?.takeIf { it.isNotBlank() }
            ?: savedProfile?.avatar
            ?: savedProfile?.effectiveAvatar

        try {
            val response = vps1Service.addReelComment(
                action = "add_comment",
                reelId = reelId,
                userId = userId,
                userName = finalName,
                name = finalName,
                userAvatar = finalAvatar,
                avatar = finalAvatar,
                commentText = text.trim(),
                parentId = parentId
            )

            val body = response.body()
            val comment = body?.effectiveComment ?: body?.comment

            if (response.isSuccessful && comment != null) {
                val resolved = comment.copy(
                    userName = if (comment.userName.isBlank() || comment.userName.startsWith("User #") || comment.userName == "User") finalName else comment.userName,
                    userAvatar = comment.userAvatar?.takeIf { it.isNotBlank() } ?: finalAvatar
                )
                Result.success(resolved)
            } else {
                val err = response.errorBody()?.string() ?: body?.message ?: "Failed to post comment"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "addReelComment error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun toggleCommentLike(commentId: Int): Result<ToggleCommentLikeResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in."))

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
}
