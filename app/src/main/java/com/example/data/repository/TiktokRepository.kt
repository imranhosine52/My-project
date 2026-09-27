package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class TiktokRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val authRepository: AuthRepository = AuthRepository(context, apiService)
) {
    companion object {
        private const val TAG = "TiktokRepository"
        const val MAX_REEL_DURATION_MS = 180_000L // ৩ মিনিট (১৮০ সেকেন্ড)
        const val MAX_REEL_SIZE_BYTES = 80L * 1024L * 1024L // ৮০ এমবি
    }

    // =========================================================================
    // 📄 ১. পেজ তৈরির আবেদন সাবমিট (Admin Approval)
    // =========================================================================
    suspend fun applyForCreatorPage(
        pageName: String,
        handle: String,
        bio: String?,
        avatarUri: Uri?
    ): Result<ApplyPageResponse> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }
        if (userId.isBlank() || userId == "0") {
            return@withContext Result.failure(Exception("Please log in to apply for a creator page."))
        }

        try {
            val uidPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val namePart = pageName.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val handlePart = handle.trim().removePrefix("@").toRequestBody("text/plain".toMediaTypeOrNull())
            val bioPart = bio?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())

            val avatarPart = avatarUri?.let { uri ->
                prepareFilePart(context, "avatar", uri)
            }

            val response = apiService.applyForCreatorPage(
                userId = uidPart,
                pageName = namePart,
                handle = handlePart,
                bio = bioPart,
                avatar = avatarPart
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to submit page application."
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Apply page error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🔍 ২. আমার পেজের বর্তমান স্ট্যাটাস চেক
    // =========================================================================
    suspend fun getMyCreatorPage(): Result<MyPageResponse> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }
        if (userId.isBlank() || userId == "0") {
            return@withContext Result.success(MyPageResponse(success = true, hasPage = false))
        }

        try {
            val response = apiService.getMyCreatorPage(userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(MyPageResponse(success = true, hasPage = false))
            }
        } catch (e: Exception) {
            Result.success(MyPageResponse(success = true, hasPage = false))
        }
    }

    // =========================================================================
    // 🎬 ৩. রিলস ভিডিও আপলোড (৩ মিনিট ও ৮০ এমবি ভ্যালিডেশন সহ)
    // =========================================================================
    suspend fun uploadReel(
        title: String,
        description: String?,
        videoUri: Uri
    ): Result<ReelUploadResponse> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }
        if (userId.isBlank() || userId == "0") {
            return@withContext Result.failure(Exception("Please log in to upload a reel."))
        }

        // ক) ভিডিওর সাইজ চেক (Max 80 MB)
        val fileSizeBytes = getFileSizeBytes(context, videoUri)
        if (fileSizeBytes > MAX_REEL_SIZE_BYTES) {
            return@withContext Result.failure(Exception("Video size exceeds the maximum limit of 80 MB!"))
        }

        // খ) ভিডিওর ডিউরেশন চেক (Max 3 Minutes)
        val durationMs = getVideoDurationMs(context, videoUri)
        if (durationMs > MAX_REEL_DURATION_MS) {
            return@withContext Result.failure(Exception("Video duration exceeds the maximum limit of 3 minutes!"))
        }

        try {
            val uidPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val titlePart = title.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val descPart = description?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())

            val videoPart = prepareFilePart(context, "video", videoUri)
                ?: return@withContext Result.failure(Exception("Could not read video file."))

            val response = apiService.uploadUserReel(
                userId = uidPart,
                title = titlePart,
                description = descPart,
                video = videoPart
            )

            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.body()?.message ?: "Upload failed on server."
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reel upload error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // ⏱️ ৪. ২৪ ঘণ্টার স্টোরি আপলোড
    // =========================================================================
    suspend fun uploadStory(
        caption: String?,
        mediaUri: Uri,
        isVideo: Boolean
    ): Result<StoryUploadResponse> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }
        if (userId.isBlank() || userId == "0") {
            return@withContext Result.failure(Exception("Please log in to share a story."))
        }

        try {
            val uidPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
            val captionPart = caption?.trim()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val typePart = (if (isVideo) "video" else "image").toRequestBody("text/plain".toMediaTypeOrNull())

            val filePart = prepareFilePart(context, "file", mediaUri)
                ?: return@withContext Result.failure(Exception("Could not read media file."))

            val response = apiService.uploadUserStory(
                userId = uidPart,
                caption = captionPart,
                mediaType = typePart,
                file = filePart
            )

            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to upload story."
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🌟 ৫. রিলস ফিড (For You & Following)
    // =========================================================================
    suspend fun getReelsFeed(tab: String = "for_you", page: Int = 1): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull()

        try {
            val response = apiService.getReelsFeed(tab = tab, userId = userId, page = page)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.reels)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get reels error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // ⏱️ ৬. সক্রিয় ২৪ ঘণ্টার স্টোরি লোড
    // =========================================================================
    suspend fun getActiveStories(): Result<List<UserStoryDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getActiveStories()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.stories)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    // =========================================================================
    // ❤️ ৭. রিলস লাইক / ভিউ / শেয়ার
    // =========================================================================
    suspend fun interactReel(reelId: Int, type: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0

        try {
            val response = apiService.interactReel(reelId = reelId, userId = userId, type = type)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.isLiked)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // ➕ ৮. পেজ ফলো / আনফলো
    // =========================================================================
    suspend fun toggleFollowPage(pageId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull()
        if (userId == null || userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to follow pages."))
        }

        try {
            val response = apiService.toggleFollowPage(pageId = pageId, userId = userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.isFollowing)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 🛠️ ইন্টারনাল ফাইল ও মিডিয়া হেল্পার ফাংশনসমূহ
    // =========================================================================

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

    private fun prepareFilePart(context: Context, partName: String, fileUri: Uri): MultipartBody.Part? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
            val inputStream = contentResolver.openInputStream(fileUri) ?: return null

            val ext = when {
                mimeType.contains("video", true) || mimeType.contains("mp4", true) -> "mp4"
                mimeType.contains("png", true) -> "png"
                else -> "jpg"
            }

            val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData(partName, tempFile.name, requestFile)
        } catch (e: Exception) {
            Log.e(TAG, "File part preparation error: ${e.message}")
            null
        }
    }
}
