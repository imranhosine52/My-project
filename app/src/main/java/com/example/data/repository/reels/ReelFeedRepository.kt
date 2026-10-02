package com.example.data.repository.reels

import android.content.Context
import android.net.Uri
import android.util.Log
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
 * রিলস ফিড, টিকটক FYP স্কোরিং ইঞ্জিন, লাইক, কমেন্ট, শেয়ার, সেভ, হ্যাশট্যাগ ও ভিডিও আপলোড হ্যান্ডলার।
 */
class ReelFeedRepository(
    private val context: Context,
    private val vps1Service: ReelsApiService = ReelsApiClient.vps1Service,
    private val vps2UploadService: ReelsApiService = ReelsApiClient.vps2UploadService,
    private val authRepository: AuthRepository = AuthRepository(context),
    private val creatorProfileRepository: CreatorProfileRepository = CreatorProfileRepository(context, vps1Service, vps2UploadService, authRepository)
) {
    companion object {
        private const val TAG = "ReelFeedRepository"
    }

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // =========================================================================
    // 🌟 ১. রিলস ফিড লোড ও লোকাল ফলো স্টেট সিঙ্ক
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
    // ⏱️ ২. রিয়েল-টাইম টিকটক ওয়াচ স্কোরিং ট্র্যাকার
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
    // 🏷️ ৩. ট্রেন্ডিং হ্যাশট্যাগ ও হ্যাশট্যাগ রিলস এক্সপ্লোরার
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
    // 🚀 ৪. ভিডিও রিলস আপলোড ওয়ার্কফ্লো (৮০MB বনাম ২০০MB ডায়নামিক ভ্যালিডেশন)
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
    ): Result<ReelUploadResponse> = uploadReel(pageId.toLong(), title, description, playlistId, episodeNum, videoUri, onProgressUpdate)

    // =========================================================================
    // ❤️ ৫. সোশ্যাল ইন্টারঅ্যাকশন ও এনগেজমেন্ট
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

    // =========================================================================
    // 💬 ৬. কমেন্টস ও নেস্টেড রিপ্লাই হ্যান্ডলার
    // =========================================================================
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
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to comment."))

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
