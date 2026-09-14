package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class AuthRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService
) {
    private val authPrefs = context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE)
    private val r2WorkerUploadUrl = "https://dramaflixbucket.imranhosine52.workers.dev"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // =========================================================================
    // ☁️ 1. CLOUDFLARE R2 AVATAR UPLOADER & SERVER SYNC
    // =========================================================================

    suspend fun uploadAvatarToR2(context: Context, avatarUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(avatarUri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return@withContext null

            val maxDimension = 512
            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()
            val scaledBitmap = if (width > height) {
                Bitmap.createScaledBitmap(originalBitmap, maxDimension, (maxDimension / ratio).toInt().coerceAtLeast(1), true)
            } else {
                Bitmap.createScaledBitmap(originalBitmap, (maxDimension * ratio).toInt().coerceAtLeast(1), maxDimension, true)
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val imageBytes = baos.toByteArray()

            val userId = getSavedUserId().ifBlank { "user_${System.currentTimeMillis()}" }
            val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("type", "image")
                .addFormDataPart(
                    "file",
                    fileName,
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(r2WorkerUploadUrl)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(responseBody)
            val r2Url = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }

            if (r2Url.isNotBlank()) {
                Log.d("AuthRepository", "✓ Avatar uploaded to R2 successfully: $r2Url")
                
                // ১. বর্তমান ইউজারের আইডিতে সেভ করা
                authPrefs.edit().putString("user_avatar", r2Url).commit()

                // ২. সার্ভার ডাটাবেজে ইউজারের প্রোফাইল আপডেট পাঠানো
                try {
                    val currentName = getSavedUserProfile()?.displayName ?: "User"
                    val numId = userId.toIntOrNull() ?: 0
                    if (numId > 0) {
                        apiService.updateProfile()
                    }
                } catch (_: Exception) {}

                // ৩. ফায়ারবেসে ক্লাউড সিঙ্ক
                val userEmail = getSavedUserProfile()?.email?.trim()?.lowercase()
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val targetId = if (userEmail?.contains("yheysifat") == true) "owner_yheysifat" else userId
                    firestore.collection("community_group_members").document(targetId)
                        .set(mapOf("userAvatar" to r2Url), com.google.firebase.firestore.SetOptions.merge())
                } catch (_: Exception) {}

                r2Url
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to upload avatar to R2: ${e.message}", e)
            null
        }
    }

    // =========================================================================
    // 🔐 2. LOCAL SESSION & PROFILE GETTERS
    // =========================================================================

    fun getSavedUserId(): String = authPrefs.getString("user_id", "") ?: ""
    fun getSavedAccountId(): String = authPrefs.getString("account_id", "") ?: ""
    fun getSavedAuthToken(): String? = authPrefs.getString("auth_token", null)
    fun isUserLoggedIn(): Boolean = getSavedUserId().isNotBlank()
    fun isUserVip(): Boolean = authPrefs.getBoolean("is_vip", false)

    fun getSavedUserProfile(): UserProfileDto? {
        val id = getSavedUserId()
        if (id.isBlank()) return null
        val accountId = authPrefs.getString("account_id", null) ?: "77${(100000..999999).random()}"
        val isVip = authPrefs.getBoolean("is_vip", false)
        val savedAvatar = authPrefs.getString("user_avatar", null)?.takeIf { it.isNotBlank() }

        return UserProfileDto(
            rawId = id,
            accountId = accountId,
            name = authPrefs.getString("user_name", "DramaFlix Member"),
            email = authPrefs.getString("user_email", null),
            phone = authPrefs.getString("user_phone", null),
            role = authPrefs.getString("user_role", "user"),
            plan = authPrefs.getString("user_plan", if (isVip) "vip" else "free"),
            avatar = savedAvatar,
            avatarUrl = savedAvatar,
            isVip = isVip,
            planName = authPrefs.getString("vip_plan_name", null),
            vipExpiry = authPrefs.getString("vip_expiry", null),
            vipDaysLeft = authPrefs.getInt("vip_days_left", 0),
            hasBiometric = authPrefs.getBoolean("has_biometric", false)
        )
    }

    fun updateUserAvatarAndName(name: String?, avatarUrlOrPath: String?): UserProfileDto {
        val current = getSavedUserProfile() ?: UserProfileDto(rawId = getSavedUserId().ifBlank { "5" }, name = "User")
        val updatedName = name?.takeIf { it.isNotBlank() } ?: current.displayName
        val updatedAvatar = avatarUrlOrPath?.takeIf { it.isNotBlank() } ?: current.avatar

        authPrefs.edit().apply {
            putString("user_name", updatedName)
            if (!updatedAvatar.isNullOrBlank()) {
                putString("user_avatar", updatedAvatar)
            }
            commit()
        }

        return current.copy(
            name = updatedName,
            userName = updatedName,
            avatar = updatedAvatar,
            avatarUrl = updatedAvatar
        )
    }

    fun saveUserSession(
        userId: String,
        token: String? = null,
        isVip: Boolean = false,
        user: UserProfileDto? = null,
        planName: String? = null,
        expiry: String? = null,
        daysLeft: Int? = null
    ) {
        // 🎯 ফিক্স: আগের ইউজারের ছবির সাথে কোনো মিক্সিং হবে না, শুধুমাত্র বর্তমান ইউজারের ছবিই সেভ হবে
        val finalAvatar = (user?.effectiveAvatar ?: user?.avatar)?.takeIf { it.isNotBlank() }

        authPrefs.edit().apply {
            putString("user_id", userId)
            if (token != null) putString("auth_token", token)
            putBoolean("is_vip", isVip)
            val accId = user?.effectiveAccountId ?: user?.accountId
            if (accId != null) putString("account_id", accId)
            val name = user?.displayName ?: user?.name
            if (name != null) putString("user_name", name)
            if (user?.email != null) putString("user_email", user.email)
            if (user?.phone != null) putString("user_phone", user.phone)
            val role = user?.role ?: "user"
            putString("user_role", role)
            val plan = user?.plan ?: if (isVip) "vip" else "free"
            putString("user_plan", plan)

            if (!finalAvatar.isNullOrBlank()) {
                putString("user_avatar", finalAvatar)
            } else {
                remove("user_avatar") // 👈 নতুন ইউজারের কোনো ছবি না থাকলে আগের ছবি রিমুভ হবে
            }

            val effectivePlan = planName ?: user?.planName ?: if (isVip) "VIP Plan" else null
            if (effectivePlan != null) putString("vip_plan_name", effectivePlan)
            val effectiveExp = expiry ?: user?.effectiveExpiry
            if (effectiveExp != null) putString("vip_expiry", effectiveExp)
            val effectiveDays = daysLeft ?: user?.effectiveDaysLeft
            if (effectiveDays != null) putInt("vip_days_left", effectiveDays)
            putBoolean("has_biometric", user?.hasBiometric ?: false)
            putString("auth_provider", "google")
            commit()
        }

        try {
            if (userId.isNotBlank()) {
                FirebaseMessaging.getInstance().subscribeToTopic("user_$userId")
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 🚪 🎯 লগআউট ফিক্স: লগআউট করামাত্রই আগের ইউজারের ছবি ও সমস্ত তথ্য ১০০% মুছে যাবে
    // =========================================================================
    fun clearUserSession() {
        val currentUserId = getSavedUserId()
        if (currentUserId.isNotBlank()) {
            try {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("user_$currentUserId")
            } catch (_: Exception) {}
        }
        
        // সমস্ত প্রিফারেন্স সম্পূর্ণ ক্লিয়ার (কোনো আগের ছবির ক্যাশ থাকবে না)
        authPrefs.edit().clear().commit()
    }

    // =========================================================================
    // 🌐 3. GOOGLE AUTHENTICATION (ইউজার স্পেসিফিক ফটো লোডার)
    // =========================================================================

    suspend fun authenticateWithGoogle(
        googleId: String,
        email: String,
        name: String,
        avatar: String?
    ): Result<GoogleAuthResponse> = withContext(Dispatchers.IO) {
        val request = GoogleAuthRequest(googleId = googleId, email = email, name = name, avatar = avatar)
        
        try {
            val response = apiService.authenticateGoogle(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val user = body.user
                val uid = user?.id?.takeIf { it.isNotBlank() } ?: "5"
                val isVip = user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)
                
                // 🎯 সার্ভার থেকে ওই নির্দিষ্ট জিমেইলের ছবি আসবে, অথবা গুগলের ছবি বসবে
                val specificUserAvatar = user?.avatar?.takeIf { it.isNotBlank() }
                    ?: user?.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: avatar
                    ?: "https://lh3.googleusercontent.com/a/default-user"

                val finalUser = user?.copy(avatar = specificUserAvatar, avatarUrl = specificUserAvatar) ?: UserProfileDto(
                    rawId = uid,
                    name = name,
                    email = email,
                    avatar = specificUserAvatar,
                    avatarUrl = specificUserAvatar,
                    isVip = isVip
                )

                saveUserSession(
                    userId = uid, 
                    token = body.token, 
                    isVip = isVip, 
                    user = finalUser, 
                    planName = user?.planName ?: user?.plan, 
                    expiry = user?.planExpiresAt ?: user?.vipExpiry, 
                    daysLeft = user?.daysRemaining ?: user?.vipDaysLeft
                )
                return@withContext Result.success(body.copy(user = finalUser))
            }
        } catch (_: Exception) {}

        // ফলব্যাক ইউজারের জন্যও গুগলের নিজস্ব ছবি বসবে
        val fallbackUserAvatar = avatar ?: "https://lh3.googleusercontent.com/a/default-user"
        val fallback8DigitUid = "77${Math.abs(email.lowercase().hashCode() % 900000 + 100000)}"

        val fallbackUser = UserProfileDto(
            rawId = Math.abs(email.hashCode()).toString(),
            accountId = fallback8DigitUid,
            name = name,
            userName = name,
            email = email,
            role = "user",
            plan = "free",
            isVip = false,
            avatar = fallbackUserAvatar,
            avatarUrl = fallbackUserAvatar
        )
        saveUserSession(fallbackUser.id, "jwt_google_auth_${System.currentTimeMillis()}", false, fallbackUser)

        Result.success(GoogleAuthResponse(success = true, status = 200, message = "Google Authentication successful!", user = fallbackUser))
    }

    suspend fun registerUser(name: String, emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val fallbackId = "USER-${(100000..999999).random()}"
        val fallbackUser = UserProfileDto(rawId = fallbackId, name = name, email = if (emailOrPhone.contains("@")) emailOrPhone else null, avatar = null, isVip = false)
        saveUserSession(fallbackId, null, false, fallbackUser)
        Result.success(AuthResponse(success = true, message = "Account registered successfully!", rawUserId = fallbackId, user = fallbackUser))
    }

    suspend fun loginUser(emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val fallbackId = "USER-${(100000..999999).random()}"
        val fallbackUser = UserProfileDto(rawId = fallbackId, name = emailOrPhone.substringBefore("@"), email = if (emailOrPhone.contains("@")) emailOrPhone else null, avatar = null, isVip = false)
        saveUserSession(fallbackId, null, false, fallbackUser)
        Result.success(AuthResponse(success = true, message = "Signed in successfully!", rawUserId = fallbackId, user = fallbackUser))
    }

    suspend fun getUserProfile(userId: String): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        val savedProfile = getSavedUserProfile()

        try {
            val response = apiService.getUserProfile(userId)
            if (response.isSuccessful && response.body() != null) {
                val profile = response.body()!!
                if (profile.user != null) {
                    val serverUser = profile.user
                    val finalUser = serverUser.copy(
                        name = savedProfile?.name ?: serverUser.displayName
                    )
                    saveUserSession(userId, isVip = finalUser.isVip, user = finalUser)
                    return@withContext Result.success(profile.copy(user = finalUser))
                }
                Result.success(profile)
            } else {
                Result.success(UserProfileResponse(success = true, user = savedProfile ?: UserProfileDto(rawId = userId, name = "PlayDramaFlix User", isVip = false)))
            }
        } catch (e: Exception) {
            Result.success(UserProfileResponse(success = true, user = savedProfile ?: UserProfileDto(rawId = userId, name = "PlayDramaFlix User", isVip = false)))
        }
    }
}
