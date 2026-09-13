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
    // ☁️ 1. CLOUDFLARE R2 AVATAR UPLOADER & CLOUD SYNC
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
                // ১. ডিস্কে স্থায়ীভাবে সেভ করা
                authPrefs.edit().putString("user_avatar", r2Url).commit()

                // ২. ক্লাউড ফায়ারবেসেও স্থায়ীভাবে ব্যাকআপ সেভ করা (যাতে লগআউট-লগইন করলেও ফিরে আসে)
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
        val updatedAvatar = avatarUrlOrPath?.takeIf { it.isNotBlank() }
            ?: authPrefs.getString("user_avatar", null)?.takeIf { it.isNotBlank() }
            ?: current.avatar

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
        val existingAvatar = authPrefs.getString("user_avatar", null)?.takeIf { it.isNotBlank() }
        val incomingAvatar = (user?.effectiveAvatar ?: user?.avatar)?.takeIf { it.isNotBlank() }

        val finalAvatar = when {
            incomingAvatar != null && (incomingAvatar.contains("workers.dev") || incomingAvatar.contains("dramaflixbucket")) -> incomingAvatar
            existingAvatar != null -> existingAvatar
            else -> incomingAvatar
        }

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

    fun clearUserSession() {
        val currentUserId = getSavedUserId()
        if (currentUserId.isNotBlank()) {
            try {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("user_$currentUserId")
            } catch (_: Exception) {}
        }
        // লগআউট করলেও শুধুমাত্র সেশন কিগুলো ক্লিয়ার করা হয়
        authPrefs.edit().apply {
            remove("user_id")
            remove("auth_token")
            remove("is_vip")
            commit()
        }
    }

    // =========================================================================
    // 🌐 3. REMOTE AUTHENTICATION (WITH CLOUD AVATAR RECOVERY)
    // =========================================================================

    /**
     * 🎯 ক্লাউড ফায়ারবেস থেকে পূর্ববর্তী R2 অবতার রিকভার করা (লগইন করার সময়)
     */
    private suspend fun recoverCloudR2Avatar(email: String, userId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                // ১. ওনার চেক
                if (email.contains("yheysifat", ignoreCase = true)) {
                    val doc = firestore.collection("community_group_members").document("owner_yheysifat").get().await()
                    val avatar = doc.getString("userAvatar")
                    if (!avatar.isNullOrBlank() && (avatar.contains("workers.dev") || avatar.contains("dramaflixbucket"))) {
                        return@withContext avatar
                    }
                }
                // ২. ইউজার আইডি দিয়ে চেক
                if (userId.isNotBlank()) {
                    val doc = firestore.collection("community_group_members").document(userId).get().await()
                    val avatar = doc.getString("userAvatar")
                    if (!avatar.isNullOrBlank() && (avatar.contains("workers.dev") || avatar.contains("dramaflixbucket"))) {
                        return@withContext avatar
                    }
                }
                // ৩. ইমেইল দিয়ে চেক
                val query = firestore.collection("community_group_members")
                    .whereEqualTo("userEmail", email.trim().lowercase())
                    .limit(1)
                    .get()
                    .await()
                if (!query.isEmpty) {
                    val avatar = query.documents[0].getString("userAvatar")
                    if (!avatar.isNullOrBlank() && (avatar.contains("workers.dev") || avatar.contains("dramaflixbucket"))) {
                        return@withContext avatar
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun authenticateWithGoogle(
        googleId: String,
        email: String,
        name: String,
        avatar: String?
    ): Result<GoogleAuthResponse> = withContext(Dispatchers.IO) {
        val request = GoogleAuthRequest(googleId = googleId, email = email, name = name, avatar = avatar)
        
        // 🚀 লগইন করার সাথে সাথে ক্লাউড থেকে পূর্ববর্তী R2 ছবি রিকভার করা
        val recoveredR2Avatar = recoverCloudR2Avatar(email, googleId)
        val localSavedAvatar = authPrefs.getString("user_avatar", null)?.takeIf { it.isNotBlank() }
        val finalAvatarToUse = recoveredR2Avatar ?: localSavedAvatar ?: avatar ?: "https://lh3.googleusercontent.com/a/default-user"

        try {
            val response = apiService.authenticateGoogle(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val user = body.user
                val uid = user?.id?.takeIf { it.isNotBlank() } ?: "5"
                val isVip = user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)
                
                val finalUser = user?.copy(avatar = finalAvatarToUse, avatarUrl = finalAvatarToUse) ?: UserProfileDto(
                    rawId = uid,
                    name = name,
                    email = email,
                    avatar = finalAvatarToUse,
                    avatarUrl = finalAvatarToUse,
                    isVip = isVip
                )

                saveUserSession(uid, body.token, isVip, finalUser, user?.planName ?: user?.plan, user?.planExpiresAt ?: user?.vipExpiry, user?.daysRemaining ?: user?.vipDaysLeft)
                return@withContext Result.success(body.copy(user = finalUser))
            }
        } catch (_: Exception) {}

        val existingAccountId = if (authPrefs.getString("user_email", null) == email) authPrefs.getString("account_id", null) else null
        val fallback8DigitUid = existingAccountId?.takeIf { it.isNotBlank() } ?: "77${Math.abs(email.lowercase().hashCode() % 900000 + 100000)}"

        val fallbackUser = UserProfileDto(
            rawId = 5,
            accountId = fallback8DigitUid,
            name = name,
            userName = name,
            email = email,
            role = "user",
            plan = "free",
            isVip = false,
            avatar = finalAvatarToUse,
            avatarUrl = finalAvatarToUse
        )
        saveUserSession("5", "jwt_google_auth_${System.currentTimeMillis()}", false, fallbackUser)

        Result.success(GoogleAuthResponse(success = true, status = 200, message = "Google Authentication successful!", user = fallbackUser))
    }

    suspend fun registerUser(name: String, emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val fallbackId = "USER-${(100000..999999).random()}"
        val savedAvatar = authPrefs.getString("user_avatar", null)
        val fallbackUser = UserProfileDto(rawId = fallbackId, name = name, email = if (emailOrPhone.contains("@")) emailOrPhone else null, avatar = savedAvatar, isVip = false)
        saveUserSession(fallbackId, null, false, fallbackUser)
        Result.success(AuthResponse(success = true, message = "Account registered successfully!", rawUserId = fallbackId, user = fallbackUser))
    }

    suspend fun loginUser(emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val fallbackId = "USER-${(100000..999999).random()}"
        val savedAvatar = authPrefs.getString("user_avatar", null)
        val fallbackUser = UserProfileDto(rawId = fallbackId, name = emailOrPhone.substringBefore("@"), email = if (emailOrPhone.contains("@")) emailOrPhone else null, avatar = savedAvatar, isVip = false)
        saveUserSession(fallbackId, null, false, fallbackUser)
        Result.success(AuthResponse(success = true, message = "Signed in successfully!", rawUserId = fallbackId, user = fallbackUser))
    }

    suspend fun getUserProfile(userId: String): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        val savedProfile = getSavedUserProfile()
        val persistentR2Avatar = authPrefs.getString("user_avatar", null)?.takeIf { it.isNotBlank() }

        try {
            val response = apiService.getUserProfile(userId)
            if (response.isSuccessful && response.body() != null) {
                val profile = response.body()!!
                if (profile.user != null) {
                    val serverUser = profile.user
                    val resolvedAvatar = persistentR2Avatar
                        ?: savedProfile?.avatar?.takeIf { it.isNotBlank() }
                        ?: serverUser.effectiveAvatar

                    val finalUser = serverUser.copy(
                        avatar = resolvedAvatar,
                        avatarUrl = resolvedAvatar,
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
