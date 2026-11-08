package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.DramaStatsEntity
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import com.example.util.AppAnalyticsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class InteractionRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val authRepository: AuthRepository = AuthRepository(context, apiService)
) {
    private val dramaStatsDao = database.dramaStatsDao()
    private val viewsCachePrefs = context.getSharedPreferences("play_drama_flix_views_24h_cache", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "InteractionRepo"
        // ⏱️ একই ডিভাইস থেকে বারবার ভিউ স্প্যাম রোধে ১৫ মিনিটের সেশন ইন্টারভাল
        private const val VIEW_COOLDOWN_MS = 15 * 60 * 1000L
    }

    // =========================================================================
    // 📊 1. LOCAL ROOM STATS & OBSERVABLES
    // =========================================================================

    fun getDramaStatsFlow(slug: String): Flow<DramaStatsEntity?> = dramaStatsDao.getStatsFlow(slug)

    suspend fun getOrCreateDramaStats(slug: String, initialLikes: Int, initialViews: Long): DramaStatsEntity = withContext(Dispatchers.IO) {
        val existing = dramaStatsDao.getStats(slug)
        if (existing != null) {
            existing
        } else {
            val newStats = DramaStatsEntity(
                slug = slug,
                likesCount = if (initialLikes > 0) initialLikes else 0,
                isLiked = false,
                viewsCount = if (initialViews > 0) initialViews else 0L,
                sharesCount = 0
            )
            dramaStatsDao.insertOrUpdate(newStats)
            newStats
        }
    }

    suspend fun recordOrganicView(slug: String, initialViews: Long = 0L) = withContext(Dispatchers.IO) {
        try {
            val existing = dramaStatsDao.getStats(slug)
            if (existing != null) {
                dramaStatsDao.incrementViews(slug)
            } else {
                val newStats = DramaStatsEntity(
                    slug = slug,
                    likesCount = 0,
                    isLiked = false,
                    viewsCount = (if (initialViews > 0) initialViews else 0L) + 1,
                    sharesCount = 0
                )
                dramaStatsDao.insertOrUpdate(newStats)
            }

            try {
                apiService.recordView(slug)
            } catch (e: Exception) {
                Log.d(TAG, "Server view record offline sync: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording organic view", e)
        }
    }

    suspend fun toggleOrganicLike(slug: String, initialLikes: Int = 0): DramaStatsEntity = withContext(Dispatchers.IO) {
        val existing = dramaStatsDao.getStats(slug)
        val currentLiked = existing?.isLiked ?: false
        val currentLikes = existing?.likesCount ?: initialLikes
        val newLiked = !currentLiked
        val newLikes = if (newLiked) currentLikes + 1 else (currentLikes - 1).coerceAtLeast(0)
        val currentViews = existing?.viewsCount ?: 0L

        val updated = DramaStatsEntity(
            slug = slug,
            likesCount = newLikes,
            isLiked = newLiked,
            viewsCount = currentViews,
            sharesCount = existing?.sharesCount ?: 0,
            lastUpdated = System.currentTimeMillis()
        )
        dramaStatsDao.insertOrUpdate(updated)

        try {
            apiService.toggleLike(slug, mapOf("liked" to newLiked))
        } catch (e: Exception) {
            Log.d(TAG, "Server like toggle offline sync: ${e.message}")
        }
        updated
    }

    // =========================================================================
    // ⏱️ 2. লাইভ ভিডিও ভিউ ও সেশন ট্র্যাকিং ইঞ্জিন
    // =========================================================================

    fun shouldRecord24hView(contentId: Any): Boolean {
        val idStr = contentId.toString()
        val lastRecordedTime = viewsCachePrefs.getLong("last_view_time_$idStr", 0L)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastRecordedTime) >= VIEW_COOLDOWN_MS
    }

    fun mark24hViewRecorded(contentId: Any, updatedViews: Long = 0L) {
        val idStr = contentId.toString()
        val currentTime = System.currentTimeMillis()
        viewsCachePrefs.edit().apply {
            putLong("last_view_time_$idStr", currentTime)
            if (updatedViews > 0L) {
                putLong("cached_views_$idStr", updatedViews)
            }
            apply()
        }
    }

    fun getCached24hViews(contentId: Any): Long {
        val idStr = contentId.toString()
        return viewsCachePrefs.getLong("cached_views_$idStr", 0L)
    }

    suspend fun recordVideoInteractionView(contentId: Any, force: Boolean = false): Result<ViewIncrementResponse> = withContext(Dispatchers.IO) {
        val canRecord = force || shouldRecord24hView(contentId)

        if (!canRecord) {
            val cachedCount = getCached24hViews(contentId)
            return@withContext Result.success(
                ViewIncrementResponse(
                    success = true,
                    contentId = contentId,
                    totalViews = if (cachedCount > 0) cachedCount else null,
                    views = if (cachedCount > 0) cachedCount else null
                )
            )
        }

        try {
            val response = apiService.recordVideoView(ViewIncrementRequest(contentId = contentId))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val effective = body.effectiveViews
                mark24hViewRecorded(contentId, effective)
                Result.success(body)
            } else {
                val prev = getCached24hViews(contentId)
                Result.success(ViewIncrementResponse(success = true, contentId = contentId, totalViews = prev, views = prev))
            }
        } catch (e: Exception) {
            val prev = getCached24hViews(contentId)
            Result.success(ViewIncrementResponse(success = true, contentId = contentId, totalViews = prev, views = prev))
        }
    }

    // =========================================================================
    // ❤️ 3. REMOTE LIKE, REACTION & ENGAGEMENT APIS
    // =========================================================================

    suspend fun toggleInteractionLike(contentId: Any, episodeId: Any? = null): Result<LikeToggleResponse> = withContext(Dispatchers.IO) {
        val uid = authRepository.getSavedUserId().takeIf { it.isNotBlank() }
        try {
            val response = apiService.toggleInteractionLike(
                LikeToggleRequest(
                    contentId = contentId,
                    episodeId = episodeId,
                    userId = uid
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // লোকাল Room ডেটাবেজে লাইক কাউন্ট সিঙ্ক করা
                val slugKey = contentId.toString()
                dramaStatsDao.updateLike(slugKey, body.effectiveIsLiked, body.effectiveLikes.toInt())
                Result.success(body)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: Failed to toggle like on server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 🌟 টেলিগ্রাম স্টাইল মাল্টি-ইমোজি রিঅ্যাকশন টগল
     */
    suspend fun toggleReaction(
        contentId: Any,
        reaction: String
    ): Result<ReactionToggleResponse> = withContext(Dispatchers.IO) {
        val savedUid = authRepository.getSavedUserId().takeIf { it.isNotBlank() }
        try {
            val response = apiService.toggleReaction(
                ReactionToggleRequest(
                    contentId = contentId,
                    reaction = reaction,
                    userId = savedUid
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: Failed to toggle reaction"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 📊 লাইভ স্ট্যাটাস ফেচিং (এরর হলেও আগের ডেটা ধরে রাখবে, ০ দিয়ে মুছবে না)
     */
    suspend fun fetchInteractionStatus(contentId: Any, episodeId: Any? = null): Result<InteractionStatusResponse> = withContext(Dispatchers.IO) {
        val savedUid = authRepository.getSavedUserId().takeIf { it.isNotBlank() }
        try {
            val response = apiService.getInteractionStatus(
                contentId = contentId, 
                episodeId = episodeId,
                userId = savedUid
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.effectiveViews > 0) {
                    viewsCachePrefs.edit().putLong("cached_views_${contentId}", body.effectiveViews).apply()
                }
                Result.success(body)
            } else {
                // সার্ভার এরর হলে ফেলিউর হিসেবে পাঠানো যাতে বর্তমান স্টেট মুছে না যায়
                Result.failure(Exception("HTTP ${response.code()}: Failed to fetch interaction status"))
            }
        } catch (e: Exception) {
            // নেটওয়ার্ক টাইমআউট হলে ফেলিউর রিটার্ন করা যাতে ইউজার ইন্টারফেস ০ না হয়ে যায়
            Result.failure(e)
        }
    }

    // =========================================================================
    // 💬 4. কমেন্ট তালিকা ও কমেন্ট যোগ করার মেথড
    // =========================================================================

    suspend fun fetchCommentsList(
        contentId: Any,
        episodeId: Any? = null,
        userId: Any? = null
    ): Result<List<DramaApiComment>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getComments(contentId = contentId, episodeId = episodeId, userId = userId)
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.commentsList
                return@withContext Result.success(list)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCommentsList v1 error: ${e.message}")
        }

        try {
            val ajaxResponse = apiService.getCommentsAjax(contentId = contentId, episodeId = episodeId)
            if (ajaxResponse.isSuccessful && ajaxResponse.body() != null) {
                return@withContext Result.success(ajaxResponse.body()!!.commentsList)
            }
        } catch (_: Exception) {}

        Result.failure(Exception("Failed to load comments from server"))
    }

    suspend fun postNewComment(
        contentId: Any,
        episodeId: Any? = null,
        parentId: Any? = null,
        commentText: String,
        authorName: String? = null,
        userId: Any? = null,
        authorAvatar: String? = null
    ): Result<DramaApiComment> = withContext(Dispatchers.IO) {
        val savedProfile = authRepository.getSavedUserProfile()
        val savedUid = userId ?: savedProfile?.id?.takeIf { it.isNotBlank() }
        val name = authorName?.takeIf { it.isNotBlank() } ?: savedProfile?.displayName ?: "DramaFlix Viewer"
        val avatar = authorAvatar?.takeIf { it.isNotBlank() } 
            ?: savedProfile?.avatar?.takeIf { it.isNotBlank() } 
            ?: "https://ui-avatars.com/api/?name=${name}&background=1E293B&color=FACC15&bold=true"

        val request = AddCommentApiRequest(
            contentId = contentId,
            episodeId = episodeId,
            parentId = parentId,
            userId = savedUid,
            userName = name,
            commentText = commentText
        )

        try {
            val response = apiService.postComment(request)
            if (response.isSuccessful && response.body()?.commentItem != null) {
                val item = response.body()!!.commentItem!!
                val finalItem = if (item.userAvatar.isNullOrBlank() && !avatar.isNullOrBlank()) {
                    item.copy(userAvatar = avatar, fallbackAvatar = avatar)
                } else item
                return@withContext Result.success(finalItem)
            } else {
                Log.w(TAG, "postNewComment server code: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "postNewComment error: ${e.message}")
        }

        try {
            val ajaxResponse = apiService.postCommentAjax(
                action = "add_comment",
                contentId = contentId,
                episodeId = episodeId,
                parentId = parentId,
                userId = savedUid,
                userName = name,
                commentText = commentText
            )
            if (ajaxResponse.isSuccessful && ajaxResponse.body()?.commentItem != null) {
                val item = ajaxResponse.body()!!.commentItem!!
                val finalItem = if (item.userAvatar.isNullOrBlank() && !avatar.isNullOrBlank()) {
                    item.copy(userAvatar = avatar, fallbackAvatar = avatar)
                } else item
                return@withContext Result.success(finalItem)
            }
        } catch (_: Exception) {}

        Result.failure(Exception("Failed to post comment to server."))
    }

    suspend fun toggleCommentLike(commentId: Any, userId: Any? = null): Result<CommentLikeApiResponse> = withContext(Dispatchers.IO) {
        val savedUid = userId ?: authRepository.getSavedUserId().takeIf { it.isNotBlank() }
        try {
            val response = apiService.toggleCommentLike(CommentLikeApiRequest(commentId = commentId, userId = savedUid))
            if (response.isSuccessful && response.body() != null) {
                return@withContext Result.success(response.body()!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "toggleCommentLike error: ${e.message}")
        }
        Result.success(CommentLikeApiResponse(success = true, isLiked = true, totalLikes = 1))
    }

    suspend fun recordCommentShare(commentId: Any, userId: Any? = null): Result<CommentShareApiResponse> = withContext(Dispatchers.IO) {
        val savedUid = userId ?: authRepository.getSavedUserId().takeIf { it.isNotBlank() }
        try {
            val response = apiService.recordCommentShare(CommentShareApiRequest(commentId = commentId, userId = savedUid))
            if (response.isSuccessful && response.body() != null) {
                return@withContext Result.success(response.body()!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "recordCommentShare error: ${e.message}")
        }
        Result.success(CommentShareApiResponse(success = true, totalShares = 1))
    }

    // =========================================================================
    // 👤 5. USER ACTIVITY (LIKES & COMMENTS SUMMARY)
    // =========================================================================

    suspend fun getUserActivity(userId: String): Result<UserActivityResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUserActivity(userId = userId, type = "all")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val directResponse = apiService.getUserActivityDirect(userId = userId, type = "all")
                if (directResponse.isSuccessful && directResponse.body() != null) {
                    Result.success(directResponse.body()!!)
                } else {
                    Result.failure(Exception("HTTP ${response.code()}: Failed to fetch user activity"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user activity: ${e.message}", e)
            Result.failure(e)
        }
    }
}
