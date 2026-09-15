package com.example.data.repository

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
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
    // 👑 VIP অ্যাক্টিভেশন লোকাল নোটিফিকেশন ডিসপ্যাচার
    // =========================================================================
    private fun showInstantVipActivatedNotification(planName: String = "VIP Pass") {
        try {
            val channelId = "high_importance_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_OPEN_VIP", true)
            }

            val requestCode = 9995
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("👑 VIP Pass Activated!")
                .setContentText("Congratulations! Your $planName is now active. Enjoy ad-free 1080p streaming!")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("🎉 Congratulations! Your $planName has been activated by the server. Enjoy 100% ad-free 1080p Full HD streaming and high-speed video downloads across all devices!")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setVibrate(longArrayOf(0, 250, 150, 250))
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(requestCode, notification)
            Log.d("AuthRepository", "✓ VIP Activation Notification displayed successfully.")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to show VIP notification: ${e.message}")
        }
    }

    // =========================================================================
    // ☁️ ১. CLOUDFLARE R2 AVATAR UPLOADER & SERVER SYNC
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
                
                authPrefs.edit().putString("user_avatar", r2Url).commit()

                try {
                    val currentName = getSavedUserProfile()?.displayName ?: "User"
                    val updatePayload = JSONObject().apply {
                        put("user_id", userId)
                        put("name", currentName)
                        put("avatar", r2Url)
                    }
                    val updateReq = Request.Builder()
                        .url("https://playdramaflix.com/api/v1/auth/profile")
                        .post(updatePayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                        .build()
                    val srvRes = httpClient.newCall(updateReq).execute()
                    srvRes.close()
                } catch (_: Exception) {}

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
    // 🔐 ২. LOCAL SESSION & PROFILE GETTERS
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
        val current = getSavedUserProfile() ?: UserProfileDto(rawId = getSavedUserId().ifBlank { "1" }, name = "User")
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
        daysLeft: Int? = null,
        triggerNotification: Boolean = false
    ) {
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
                remove("user_avatar")
            }

            val effectivePlan = planName ?: user?.planName ?: if (isVip) "VIP Pass" else null
            if (effectivePlan != null) putString("vip_plan_name", effectivePlan)
            val effectiveExp = expiry ?: user?.effectiveExpiry
            if (effectiveExp != null) putString("vip_expiry", effectiveExp)
            val effectiveDays = daysLeft ?: user?.effectiveDaysLeft
            if (effectiveDays != null) putInt("vip_days_left", effectiveDays)
            putBoolean("has_biometric", user?.hasBiometric ?: false)
            putString("auth_provider", "server")
            commit()
        }

        try {
            if (userId.isNotBlank()) {
                FirebaseMessaging.getInstance().subscribeToTopic("user_$userId")
            }
        } catch (_: Exception) {}

        // শুধুমাত্র সার্ভার অনুমোদিত VIP হলেই নোটিফিকেশন আসবে
        if (isVip && triggerNotification) {
            showInstantVipActivatedNotification(planName ?: user?.planName ?: "VIP Pass")
        }
    }

    fun clearUserSession() {
        val currentUserId = getSavedUserId()
        if (currentUserId.isNotBlank()) {
            try {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("user_$currentUserId")
            } catch (_: Exception) {}
        }
        authPrefs.edit().clear().commit()
    }

    // =========================================================================
    // 🌐 ৩. GOOGLE AUTHENTICATION (১০০% সার্ভার অনুমতিতে কাজ করবে)
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
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val body = response.body()!!
                val user = body.user
                val uid = user?.id?.takeIf { it.isNotBlank() } ?: "1"
                
                // 🎯 কঠোরভাবে সার্ভারের উত্তরের ওপর VIP স্ট্যাটাস নির্ভর করবে
                val isVip = user?.isVip == true || user?.plan.equals("vip", ignoreCase = true) || user?.plan.equals("premium", ignoreCase = true)
                
                val specificUserAvatar = user?.avatar?.takeIf { it.isNotBlank() }
                    ?: user?.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: avatar
                    ?: "https://ui-avatars.com/api/?name=${Uri.encode(name)}&background=1E293B&color=FACC15&bold=true"

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
                    daysLeft = user?.daysRemaining ?: user?.vipDaysLeft ?: 0,
                    triggerNotification = isVip
                )
                return@withContext Result.success(body.copy(user = finalUser))
            } else {
                val errorMsg = response.errorBody()?.string()?.let {
                    try { JSONObject(it).optString("message", "Google authentication failed on server.") } catch (_: Exception) { null }
                } ?: response.body()?.message ?: "Google login failed on server."
                return@withContext Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            // ❌ নেটওয়ার্ক এরর হলে কখনোই ইউজারকে ফ্রিতে VIP করা হবে না
            Log.e("AuthRepository", "Google sign-in server error: ${e.message}")
            return@withContext Result.failure(Exception("সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি: ${e.message}"))
        }
    }

    // =========================================================================
    // ✍️ ৪. EMAIL REGISTER (১০০% সার্ভার অনুমতিতে কাজ করবে)
    // =========================================================================
    suspend fun registerUser(name: String, emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val req = AuthRegisterRequest(name = name, emailOrPhone = emailOrPhone, password = password)
        try {
            val response = apiService.registerUser(req)
            val body = response.body()
            
            if (response.isSuccessful && body != null && body.success) {
                val user = body.user
                val uid = body.userId.ifBlank { user?.id ?: "" }
                val isVip = body.isVip == true || user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)

                saveUserSession(
                    userId = uid,
                    token = body.token,
                    isVip = isVip,
                    user = user,
                    planName = user?.planName ?: "VIP Pass",
                    expiry = user?.planExpiresAt ?: user?.vipExpiry,
                    daysLeft = user?.daysRemaining ?: if (isVip) 1 else 0,
                    triggerNotification = isVip
                )
                return@withContext Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string()?.let {
                    try { JSONObject(it).optString("message", "Registration failed.") } catch (_: Exception) { null }
                } ?: body?.message ?: "রেজিস্ট্রেশন সম্পন্ন করা সম্ভব হয়নি।"
                return@withContext Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            // ❌ কোনো অফলাইন ফেক VIP দেওয়া হবে না
            return@withContext Result.failure(Exception("সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি: ${e.message}"))
        }
    }

    // =========================================================================
    // 🔑 ৫. EMAIL LOGIN (১০০% সার্ভার অনুমতিতে কাজ করবে)
    // =========================================================================
    suspend fun loginUser(emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val req = AuthLoginRequest(emailOrPhone = emailOrPhone, password = password)
        try {
            val response = apiService.loginUser(req)
            val body = response.body()
            
            if (response.isSuccessful && body != null && body.success) {
                val user = body.user
                val uid = body.userId.ifBlank { user?.id ?: "" }
                val isVip = body.isVip == true || user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)

                saveUserSession(
                    userId = uid,
                    token = body.token,
                    isVip = isVip,
                    user = user,
                    planName = user?.planName,
                    expiry = user?.planExpiresAt,
                    daysLeft = user?.daysRemaining ?: if (isVip) 30 else 0,
                    triggerNotification = false
                )
                return@withContext Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string()?.let {
                    try { JSONObject(it).optString("message", "Invalid credentials.") } catch (_: Exception) { null }
                } ?: body?.message ?: "ভুল ইমেইল অথবা পাসওয়ার্ড।"
                return@withContext Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("সার্ভার এরর: ${e.message}"))
        }
    }

    // =========================================================================
    // 👤 ৬. GET USER PROFILE (সার্ভার থেকে ফ্রেশ ডাটা আনা)
    // =========================================================================
    suspend fun getUserProfile(userId: String): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        val savedProfile = getSavedUserProfile()

        try {
            val response = apiService.getUserProfile(userId)
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
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
