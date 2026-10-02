package com.example.data.repository.reels

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ReelsApiClient
import com.example.data.remote.ReelsApiService
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 📺 SeriesPlaylistRepository
 * মিনি-ড্রামা সিরিজ তৈরি, ৯:১৬ পোস্টার ও ১৬:৯ ব্যানার আপলোড এবং পর্ব তালিকা লোড করার হ্যান্ডলার।
 */
class SeriesPlaylistRepository(
    private val context: Context,
    private val vps1Service: ReelsApiService = ReelsApiClient.vps1Service,
    private val vps2UploadService: ReelsApiService = ReelsApiClient.vps2UploadService,
    private val authRepository: AuthRepository = AuthRepository(context),
    private val creatorProfileRepository: CreatorProfileRepository = CreatorProfileRepository(context, vps1Service, vps2UploadService, authRepository)
) {
    companion object {
        private const val TAG = "SeriesPlaylistRepo"
    }

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // =========================================================================
    // 🎬 ১. মিনি-ড্রামা সিরিজ / প্লেলিস্ট তৈরি (ডুয়েল ইমেজ পোস্টার ও ব্যানার সহ)
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
                val tempPoster = ReelMediaHelper.prepareCompressedImageFile(context, posterUri, "series_poster")
                if (tempPoster != null) {
                    val reqFile = tempPoster.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    posterPart = MultipartBody.Part.createFormData("poster", tempPoster.name, reqFile)
                }
            }

            var bannerPart: MultipartBody.Part? = null
            if (bannerUri != null) {
                val tempBanner = ReelMediaHelper.prepareCompressedImageFile(context, bannerUri, "series_banner")
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

    // =========================================================================
    // 📂 ২. ক্রিয়েটরের সমস্ত প্লেলিস্টের তালিকা লোড
    // =========================================================================
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

    // =========================================================================
    // ▶️ ৩. প্লেলিস্টের সমস্ত পর্ব ক্রমানুসারে লোড
    // =========================================================================
    suspend fun getPlaylistReels(playlistId: Int): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        try {
            val response = vps1Service.getPlaylistReels(action = "get_playlist_reels", playlistId = playlistId)
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
            Log.e(TAG, "getPlaylistReels error: ${e.message}")
            Result.failure(e)
        }
    }
}
