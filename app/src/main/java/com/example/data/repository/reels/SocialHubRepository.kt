package com.example.data.repository.reels

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ReelsApiClient
import com.example.data.remote.ReelsApiService
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🔔 SocialHubRepository
 * ফ্রেন্ড রিকোয়েস্ট, ফ্রেন্ডলিস্ট, অ্যাড ফ্রেন্ড/আনফ্রেন্ড এবং লাইভ সোশ্যাল অ্যাক্টিভিটি নোটিফিকেশন হ্যান্ডলার।
 */
class SocialHubRepository(
    private val context: Context,
    private val vps1Service: ReelsApiService = ReelsApiClient.vps1Service,
    private val authRepository: AuthRepository = AuthRepository(context)
) {
    companion object {
        private const val TAG = "SocialHubRepository"
    }

    fun getCurrentUserId(): Int {
        return authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // =========================================================================
    // 🤝 ১. ফ্রেন্ড যোগ / আনফ্রেন্ড টগল
    // =========================================================================
    suspend fun toggleFriend(targetUserId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.failure(Exception("Please log in first."))
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
    // 🔔 ২. আসল সোশ্যাল অ্যাক্টিভিটি নোটিফিকেশন ফিড
    // =========================================================================
    suspend fun getSocialActivities(): Result<SocialActivitiesResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.success(
                SocialActivitiesResponse(success = true, rawTotal = 0, activities = emptyList())
            )
        }

        try {
            val response = vps1Service.getSocialActivities(
                action = "get_social_activities",
                userId = userId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!)
            } else {
                Result.success(
                    SocialActivitiesResponse(success = true, rawTotal = 0, activities = emptyList())
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSocialActivities error: ${e.message}")
            Result.success(
                SocialActivitiesResponse(success = true, rawTotal = 0, activities = emptyList())
            )
        }
    }

    // =========================================================================
    // 📩 ৩. পেন্ডিং ফ্রেন্ড রিকোয়েস্ট তালিকা
    // =========================================================================
    suspend fun getFriendRequests(): Result<FriendRequestsResponse> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) {
            return@withContext Result.success(
                FriendRequestsResponse(success = true, rawTotal = 0, requests = emptyList())
            )
        }

        try {
            val response = vps1Service.getFriendRequests(
                action = "get_friend_requests",
                userId = userId
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!)
            } else {
                Result.success(
                    FriendRequestsResponse(success = true, rawTotal = 0, requests = emptyList())
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFriendRequests error: ${e.message}")
            Result.success(
                FriendRequestsResponse(success = true, rawTotal = 0, requests = emptyList())
            )
        }
    }

    // =========================================================================
    // ⚡ ৪. ফ্রেন্ড রিকোয়েস্ট অ্যাকশন (Confirm / Delete)
    // =========================================================================
    suspend fun handleFriendRequest(requestId: Int, cmd: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId <= 0) return@withContext Result.failure(Exception("Please log in."))

        try {
            val response = vps1Service.handleFriendRequest(
                action = "handle_friend_request",
                requestId = requestId,
                userId = userId,
                cmd = cmd
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(true)
            } else {
                val err = response.errorBody()?.string() ?: response.body()?.message ?: "Action failed"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleFriendRequest error: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // 👥 ৫. কনফার্মড ফ্রেন্ডস তালিকা (টার্গেট ইউজার আইডি সহ)
    // =========================================================================
    suspend fun getConfirmedFriends(targetUserId: Int? = null): Result<List<ConfirmedFriendDto>> = withContext(Dispatchers.IO) {
        val uid = targetUserId ?: getCurrentUserId()
        if (uid <= 0) return@withContext Result.success(emptyList())

        try {
            val response = vps1Service.getConfirmedFriends(
                action = "get_confirmed_friends",
                userId = uid
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!.effectiveFriends)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConfirmedFriends error: ${e.message}")
            Result.success(emptyList())
        }
    }
}
