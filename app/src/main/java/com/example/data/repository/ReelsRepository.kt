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
        
        // 🎯 সাধারণ রিলসের লিমিট (৩ মিনিট / ৮০ এমবি)
        const val MAX_REEL_DURATION_MS = 180_000L
        const val MAX_REEL_SIZE_BYTES = 80L * 1024L * 1024L
        
        // 🎯 মিনি-ড্রামা সিরিজ পর্বের বর্ধিত লিমিট (১০ মিনিট / ২০০ এমবি)
        const val MAX_SERIES_DURATION_MS = 600_000L
        const val MAX_SERIES_SIZE_BYTES = 200L * 1024L * 1024L
    }

    private val followPrefs = context.getSharedPreferences(PREFS_FOLLOW_CACHE, Context.MODE_PRIVATE)

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    private fun getFollowKey(pageId: Long, userId: Int): String {
        return if (pageId > 0L) "page_$pageId" else "user_$userId"
    }

    private fun getLocalFollowedKeys(): Set<String> {
        return followPrefs.getStringSet(KEY_FOLLOWED_IDS, emptySet()) ?: emptySet()
    }

    fun isCreatorFollowed(pageId: Long, userId: Int): Boolean {
        val key = getFollowKey(pageId, userId)
        return getLocalFollowedKeys().contains(key)
    }

    fun isCreatorFollowed(pageId: Int, userId: Int): Boolean = isCreatorFollowed(pageId.toLong(), userId)

    private fun setLocalFollowState(pageId: Long, userId: Int, isFollowing: Boolean) {
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
    // 👑 ১. PUBLIC CREATOR PROFILE API
    // =========================================================================
    suspend fun getPublicCreatorProfile(pageId: Long): Result<PublicCreatorProfileDto> = withContext(Dispatchers.IO) {
        val viewerId = getCurrentUserId()
        try {
            val response = vps1Service.getPublicProfile(
                action = "get_public_profile",
                pageId = pageId,
                viewerId = viewerId
            )
            if (response.isSuccessful && response.body()?.profile != null) {
                val profile = response.body()!!.profile!!
                val isLocallyFollowed = isCreatorFollowed(profile.pageId, profile.userId)
                val effectiveFollowing = profile.isFollowing || isLocallyFollowed

                if (profile.isFollowing) {
                    setLocalFollowState(profile.pageId, profile.userId, true)
                }

                Result.success(profile.copy(isFollowing = effectiveFollowing))
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Profile not found"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPublicCreatorProfile error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 👤 ২. NEW: REGULAR USER PROFILE & FRIENDS REPOSITORY
    // =========================================================================
    suspend fun getUserRegularProfile(targetUserId: Int): Result<RegularUserProfileDto> = withContext(Dispatchers.IO) {
        val viewerId = getCurrentUserId()
        try {
            val response = vps1Service.getUserRegularProfile(
                action = "get_user_regular_profile",
                targetUserId = targetUserId,
                viewerId = viewerId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val profile = response.body()!!.profile
                if (profile != null) {
                    Result.success(profile)
                } else {
                    Result.failure(Exception("Regular user profile not found"))
                }
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Failed to fetch user profile"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserRegularProfile error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun toggleFriend(targetUserId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to add friends."))
        }

        try {
            val response = vps1Service.toggleFriend(
                action = "toggle_friend",
                userId = userId,
                friendId = targetUserId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!.isFriend)
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Failed to update friend status"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleFriend error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🌟 ৩. SUGGESTED CREATORS & PAGES API
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

                val resolvedPages = serverPages.map { page ->
                    val isLocallyFollowed = isCreatorFollowed(page.pageId.toLong(), page.userId)
                    val effectiveFollowing = page.isFollowing || isLocallyFollowed

                    if (page.isFollowing) {
                        setLocalFollowState(page.pageId.toLong(), page.userId, true)
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
    // 👑 ৪. REAL-TIME USER PROFILE & METRICS
    // =========================================================================
    suspend fun getUserProfileMetrics(targetUserId: Int): Result<UserProfileMetricsDto?> = withContext(Dispatchers.IO) {
        val viewerId = getCurrentUserId()
        try {
            val response = vps1Service.getUserProfileMetrics(
                action = "get_user_profile",
                targetUserId = targetUserId,
                pageId = targetUserId,
                userId = targetUserId,
                viewerId = viewerId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val profile = response.body()!!.effectiveProfile
                if (profile != null) {
                    val pId = profile.pageId?.toLong() ?: 0L
                    val isLocallyFollowed = isCreatorFollowed(pId, profile.userId)
                    val effectiveFollowing = profile.isFollowing || isLocallyFollowed

                    if (profile.isFollowing) {
                        setLocalFollowState(pId, profile.userId, true)
                    }

                    val updatedProfile = profile.copy(rawIsFollowing = effectiveFollowing)
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
    // 🖼️ ৫. AVATAR & COVER UPLOAD
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
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Avatar upload failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Avatar upload error: ${e.message}")
            Result.failure(e)
        }
    }

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
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Cover upload failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Cover upload error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🎯 ৬. রিয়েল-টাইম ফলো / আনফলো
    // =========================================================================
    suspend fun toggleFollowPage(pageId: Long, targetUserId: Int = 0): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to follow."))
        }

        val previousState = isCreatorFollowed(pageId, targetUserId)
        val optimisticNewState = !previousState
        setLocalFollowState(pageId, targetUserId, optimisticNewState)

        try {
            val response = vps1Service.toggleFollowPage(
                action = "toggle_follow_page",
                pageId = pageId,
                targetUserId = targetUserId,
                userId = userId
            )
            if (response.isSuccessful && response.body() != null) {
                val finalFollowState = response.body()!!.effectiveIsFollowing
                setLocalFollowState(pageId, targetUserId, finalFollowState)
                Result.success(finalFollowState)
            } else {
                setLocalFollowState(pageId, targetUserId, previousState)
                val errorMsg = response.errorBody()?.string() ?: "Failed to follow on server"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            setLocalFollowState(pageId, targetUserId, previousState)
            Result.failure(e)
        }
    }

    suspend fun toggleFollowPage(pageId: Int, targetUserId: Int = pageId): Result<Boolean> =
        toggleFollowPage(pageId.toLong(), targetUserId)

    // =========================================================================
    // 👤 ৭. CREATOR PAGE PROFILE UPDATE
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
    // 📺 ৮. CREATOR SERIES & PLAYLIST REPOSITORY
    // =========================================================================
    suspend fun createSeriesWorkflow(
        pageId: Long,
        title: String,
        description: String?,
        posterUri: Uri?,
        bannerUri: Uri?
    ): Result<CreatePlaylistResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to create a series."))

        try {
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val pageIdPart = pageId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val titlePart = title.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val descPart = description?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())

            var posterPart: MultipartBody.Part? = null
            if (posterUri != null) {
                val tempPoster = prepareCompressedImageFile(context, posterUri, "series_poster")
                if (tempPoster != null) {
                    val reqFile = tempPoster.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    posterPart = MultipartBody.Part.createFormData("poster", tempPoster.name, reqFile)
                }
            }

            var bannerPart: MultipartBody.Part? = null
            if (bannerUri != null) {
                val tempBanner = prepareCompressedImageFile(context, bannerUri, "series_banner")
                if (tempBanner != null) {
                    val reqFile = tempBanner.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    bannerPart = MultipartBody.Part.createFormData("banner", tempBanner.name, reqFile)
                }
            }

            val response = vps2UploadService.createSeriesWorkflow(
                userId = uidPart,
                pageId = pageIdPart,
                title = titlePart,
                description = descPart,
                poster = posterPart,
                banner = bannerPart
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Failed to create series"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createSeriesWorkflow error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createPlaylist(
        pageId: Long,
        title: String,
        description: String? = null,
        coverUrl: String? = null
    ): Result<CreatePlaylistResponse> = createSeriesWorkflow(
        pageId = pageId,
        title = title,
        description = description,
        posterUri = null,
        bannerUri = null
    )

    suspend fun createPlaylist(
        pageId: Int,
        title: String,
        description: String? = null,
        coverUrl: String? = null
    ): Result<CreatePlaylistResponse> = createPlaylist(pageId.toLong(), title, description, coverUrl)

    suspend fun getPlaylists(pageId: Long): Result<List<CreatorPlaylistDto>> = withContext(Dispatchers.IO) {
        try {
            val response = vps1Service.getPlaylists(action = "get_playlists", pageId = pageId)
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!.playlists)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylists error: ${e.message}")
            Result.success(emptyList())
        }
    }

    suspend fun getPlaylists(pageId: Int): Result<List<CreatorPlaylistDto>> = getPlaylists(pageId.toLong())

    suspend fun getPlaylistReels(playlistId: Int): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        try {
            val response = vps1Service.getPlaylistReels(action = "get_playlist_reels", playlistId = playlistId)
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val serverReels = response.body()!!.reels
                val resolvedReels = serverReels.map { reel ->
                    val isLocallyFollowed = isCreatorFollowed(reel.pageId.toLong(), reel.userId)
                    reel.copy(isFollowing = reel.isFollowing || isLocallyFollowed)
                }
                Result.success(resolvedReels)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistReels error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🎬 ৯. REELS FEED & WATCH ALGORITHM TRACKING
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
                    val isLocallyFollowed = isCreatorFollowed(reel.pageId.toLong(), reel.userId)
                    val effectiveIsFollowing = reel.isFollowing || isLocallyFollowed

                    if (reel.isFollowing) {
                        setLocalFollowState(reel.pageId.toLong(), reel.userId, true)
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
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.success(false)
        }
    }

    // =========================================================================
    // 🏷️ ১০. HASHTAG REELS & TRENDING HASHTAGS REPOSITORY
    // =========================================================================

    suspend fun getTrendingHashtags(): Result<List<TrendingHashtagDto>> = withContext(Dispatchers.IO) {
        try {
            val response = vps1Service.getTrendingHashtags(action = "get_trending_hashtags")
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val hashtags = response.body()!!.effectiveHashtags
                Result.success(hashtags)
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
                    val isLocallyFollowed = isCreatorFollowed(reel.pageId.toLong(), reel.userId)
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
                    val isLocallyFollowed = isCreatorFollowed(reel.pageId.toLong(), reel.userId)
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
    // 🚀 ১১. REEL UPLOAD WORKFLOW (ডাইনামিক লিমিট ভ্যালিডেশন সহ)
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
        val maxDuration = if (isSeries) MAX_SERIES_DURATION_MS else MAX_REEL_DURATION_MS
        val maxSizeBytes = if (isSeries) MAX_SERIES_SIZE_BYTES else MAX_REEL_SIZE_BYTES

        val fileSizeBytes = getFileSizeBytes(context, videoUri)
        if (fileSizeBytes > maxSizeBytes) {
            val limitMb = if (isSeries) 200 else 80
            return@withContext Result.failure(Exception("Video size exceeds ${limitMb} MB limit!"))
        }

        val durationMs = getVideoDurationMs(context, videoUri)
        if (durationMs > maxDuration) {
            val limitMin = if (isSeries) "10 minutes" else "3 minutes"
            return@withContext Result.failure(Exception("Video duration exceeds $limitMin limit!"))
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
    // ❤️ ১২. SOCIAL INTERACTIONS & COMMENTS
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
                val resized = if (width > height) {
                    Bitmap.createScaledBitmap(originalBitmap, maxDimension, (maxDimension / ratio).toInt().coerceAtLeast(1), true)
                } else {
                    Bitmap.createScaledBitmap(originalBitmap, (maxDimension * ratio).toInt().coerceAtLeast(1), maxDimension, true)
                }
                if (resized != originalBitmap) {
                    originalBitmap.recycle()
                }
                resized
            } else {
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()
            
            if (!scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
            
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
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
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
