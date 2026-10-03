package com.example.data.repository.reels

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ReelsApiClient
import com.example.data.remote.ReelsApiService
import com.example.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 👤 CreatorProfileRepository
 * ক্রিয়েটর পেজ, পাবলিক প্রোফাইল, ফলো স্টেট ও এফসিএম (FCM) টপিক সাবস্ক্রিপশন হ্যান্ডলার।
 */
class CreatorProfileRepository(
    private val context: Context,
    private val vps1Service: ReelsApiService = ReelsApiClient.vps1Service,
    private val vps2UploadService: ReelsApiService = ReelsApiClient.vps2UploadService,
    private val authRepository: AuthRepository = AuthRepository(context)
) {
    companion object {
        private const val TAG = "CreatorProfileRepo"
        private const val PREFS_FOLLOW_CACHE = "reels_user_scoped_follow_cache_v2"
    }

    private val followPrefs = context.getSharedPreferences(PREFS_FOLLOW_CACHE, Context.MODE_PRIVATE)

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // =========================================================================
    // 🔍 ইউজার-স্পেসিফিক লোকাল ফলো স্টেট (Account-Scoped Lock)
    // =========================================================================
    
    private fun getFollowKey(currentUserId: Int, pageId: Long): String {
        return "user_${currentUserId}_page_$pageId"
    }

    /**
     * 🎯 ফিক্সড: লগআউট অবস্থায় (currentUserId <= 0) থাকলে কখনোই Following হতে পারবে না
     */
    fun isCreatorFollowed(pageId: Long, targetUserId: Int = 0): Boolean {
        val currentUserId = getCurrentUserId()
        if (currentUserId <= 0) return false // 👈 লগআউট থাকলে সবসময় false!

        val key = getFollowKey(currentUserId, pageId)
        return followPrefs.getBoolean(key, false)
    }

    fun isCreatorFollowed(pageId: Int, targetUserId: Int = 0): Boolean = 
        isCreatorFollowed(pageId.toLong(), targetUserId)

    fun setLocalFollowState(pageId: Long, targetUserId: Int, isFollowing: Boolean) {
        val currentUserId = getCurrentUserId()
        if (currentUserId <= 0) return

        val key = getFollowKey(currentUserId, pageId)
        followPrefs.edit().putBoolean(key, isFollowing).apply()
    }

    // =========================================================================
    // 👑 ১. পাবলিক ক্রিয়েটর পেজ প্রোফাইল (VPS 1)
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

                // 🎯 লগআউট অবস্থায় থাকলে ফলো স্ট্যাটাস নিশ্চিতভাবে false
                val effectiveFollowing = if (viewerId <= 0) {
                    false
                } else {
                    profile.isFollowing
                }

                if (viewerId > 0) {
                    setLocalFollowState(profile.pageId, profile.userId, effectiveFollowing)
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
    // 👤 ২. সাধারণ ইউজার পাবলিক প্রোফাইল (VPS 1)
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

    // =========================================================================
    // 📊 ৩. লাইভ ইউজার প্রোফাইল মেট্রিক্স (VPS 1)
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
                    val effectiveFollowing = if (viewerId <= 0) false else profile.isFollowing

                    if (viewerId > 0 && pId > 0L) {
                        setLocalFollowState(pId, profile.userId, effectiveFollowing)
                    }

                    Result.success(profile.copy(rawIsFollowing = effectiveFollowing))
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
    // 🌟 ৪. সাজেস্টেড পেজ ও ক্রিয়েটর সাজেশন
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
                    val effectiveFollowing = if (userId <= 0) false else page.isFollowing
                    if (userId > 0) {
                        setLocalFollowState(page.pageId.toLong(), page.userId, effectiveFollowing)
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
    // 🎯 ৫. পেজ ফলো / আনফলো
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

                try {
                    val topicName = "page_$pageId"
                    if (finalFollowState) {
                        FirebaseMessaging.getInstance().subscribeToTopic(topicName)
                    } else {
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(topicName)
                    }
                } catch (_: Throwable) {}

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
    // 👤 ৬. পেজ প্রোফাইল আপডেট
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
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in."))

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
                val tempImg = ReelMediaHelper.prepareCompressedImageFile(context, avatarUri, "page_avatar")
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
            Result.failure(e)
        }
    }

    suspend fun getMyCreatorPage(): Result<CreatorPageDto?> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) return@withContext Result.success(null)

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
    // 🖼️ ৭. AVATAR & COVER UPLOAD
    // =========================================================================
    suspend fun uploadUserAvatar(imageUri: Uri, fallbackUserId: Int = 0): Result<String> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 } ?: fallbackUserId
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to upload avatar."))

        val tempFile = ReelMediaHelper.prepareCompressedImageFile(context, imageUri, "avatar")
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
                    Result.failure(Exception(body.message ?: "Avatar URL missing"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Avatar upload failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    suspend fun uploadUserCover(imageUri: Uri, fallbackUserId: Int = 0): Result<String> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId().takeIf { it > 0 } ?: fallbackUserId
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in to upload cover photo."))

        val tempFile = ReelMediaHelper.prepareCompressedImageFile(context, imageUri, "cover")
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
                    Result.failure(Exception(body.message ?: "Cover URL missing"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Cover upload failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }
}
