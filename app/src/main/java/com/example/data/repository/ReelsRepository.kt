package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.CreatorPageDto
import com.example.data.model.MyPageResponse
import com.example.data.model.PageFollowResponse
import com.example.data.model.ReelInteractionResponse
import com.example.data.model.ReelUploadResponse
import com.example.data.model.UserReelDto
import com.example.data.remote.CountingRequestBody
import com.example.data.remote.ReelsApiClient
import com.example.data.remote.ReelsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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
        const val MAX_REEL_DURATION_MS = 180_000L // ৩ মিনিট (১৮০ সেকেন্ড)
        const val MAX_REEL_SIZE_BYTES = 80L * 1024L * 1024L // ৮০ মেগাবাইট
    }

    // =========================================================================
    // 🌟 ১. রিলস ফিড ফেচিং (VPS 1)
    // =========================================================================
    suspend fun getReelsFeed(tab: String = "for_you", page: Int = 1): Result<List<UserReelDto>> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull()
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
    // 🚀 ২. রিলস ভিডিও আপলোড (VPS 2 ট্রান্সকোডার ইঞ্জিন) - 500 ERROR ফিক্সড
    // =========================================================================
    suspend fun uploadReel(
        pageId: Int,
        title: String?,
        description: String?,
        videoUri: Uri,
        onProgressUpdate: (percent: Int) -> Unit
    ): Result<ReelUploadResponse> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull()
        if (userId == null || userId <= 0) {
            return@withContext Result.failure(Exception("Please log in to upload a reel."))
        }

        // ক) সাইজ চেক (Max 80 MB)
        val fileSizeBytes = getFileSizeBytes(context, videoUri)
        if (fileSizeBytes > MAX_REEL_SIZE_BYTES) {
            return@withContext Result.failure(Exception("Video size exceeds the maximum limit of 80 MB!"))
        }

        // খ) ডিউরেশন চেক (Max 3 Minutes)
        val durationMs = getVideoDurationMs(context, videoUri)
        if (durationMs > MAX_REEL_DURATION_MS) {
            return@withContext Result.failure(Exception("Video duration exceeds the maximum limit of 3 minutes!"))
        }

        // গ) আসল MIME Type ও ফাইল তৈরি
        val mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4"
        val ext = if (mimeType.contains("quicktime", true) || mimeType.contains("mov", true)) "mov" else "mp4"

        val tempFile = prepareTempVideoFile(context, videoUri, ext)
            ?: return@withContext Result.failure(Exception("Could not read video file from storage."))

        try {
            val uidPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val pageIdPart = pageId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val titlePart = (title?.trim() ?: "My Reel").toRequestBody("text/plain".toMediaTypeOrNull())
            val descPart = (description?.trim() ?: "").toRequestBody("text/plain".toMediaTypeOrNull())

            // লাইভ প্রোগ্রেস ট্র্যাকার রিকোয়েস্ট বডি
            val requestFile = CountingRequestBody(tempFile, mimeType) { bytesWritten, totalBytes ->
                if (totalBytes > 0) {
                    val percent = ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 100)
                    onProgressUpdate(percent)
                }
            }

            // 🎯 সার্ভার ফ্রেন্ডলি ফাইলনেম
            val fileName = "reel_${System.currentTimeMillis()}.$ext"
            val videoPart = MultipartBody.Part.createFormData("video", fileName, requestFile)

            // VPS 2 API Call
            val response = vps2UploadService.uploadReel(
                userId = uidPart,
                pageId = pageIdPart,
                title = titlePart,
                description = descPart,
                video = videoPart
            )

            tempFile.delete() // ক্যাশ ক্লিনআপ

            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!)
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: ""
                val errorMsg = if (errorBodyStr.isNotBlank()) {
                    errorBodyStr
                } else {
                    response.body()?.message ?: "Server Error (HTTP ${response.code()})"
                }
                Log.e(TAG, "VPS 2 Error [${response.code()}]: $errorMsg")
                Result.failure(Exception("VPS 2 Error: $errorMsg"))
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Reel upload error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // ❤️ ৩. লাইক, ভিউ ও শেয়ার ইন্টারঅ্যাকশন (VPS 1)
    // =========================================================================
    suspend fun interactReel(reelId: Int, type: String): Result<ReelInteractionResponse> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0

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

    // =========================================================================
    // 🔍 ৪. ক্রিয়েটর পেজ স্ট্যাটাস চেক (VPS 1)
    // =========================================================================
    suspend fun getMyCreatorPage(): Result<CreatorPageDto?> = withContext(Dispatchers.IO) {
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull()
        if (userId == null || userId <= 0) {
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
        val userId = authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull()
        if (userId == null || userId <= 0) {
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

    // =========================================================================
    // 🛠️ ফাইল ও মিডিয়া হেল্পার
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
