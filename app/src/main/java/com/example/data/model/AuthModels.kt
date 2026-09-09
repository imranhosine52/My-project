package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GoogleAuthRequest(
    @Json(name = "google_id") val googleId: String,
    @Json(name = "email") val email: String,
    @Json(name = "name") val name: String,
    @Json(name = "avatar") val avatar: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAuthResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "message") val message: String? = "Google Authentication successful!",
    @Json(name = "user") val user: UserProfileDto? = null,
    @Json(name = "token") val token: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthRegisterRequest(
    @Json(name = "name") val name: String,
    @Json(name = "email_or_phone") val emailOrPhone: String,
    @Json(name = "password") val password: String,
    @Json(name = "avatar") val avatar: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthLoginRequest(
    @Json(name = "email_or_phone") val emailOrPhone: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "message") val message: String? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "token") val token: String? = null,
    @Json(name = "user") val user: UserProfileDto? = null,
    @Json(name = "is_vip") val isVip: Boolean? = false
) {
    val userId: String get() = rawUserId?.toString() ?: user?.id ?: ""
}

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "account_id") val accountId: String? = null,
    @Json(name = "uid") val rawUid: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "role") val role: String? = "user",
    @Json(name = "plan") val plan: String? = "free",
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "profile_image") val profileImage: String? = null,
    @Json(name = "is_vip") val isVip: Boolean = false,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "vip_expiry") val vipExpiry: String? = null,
    @Json(name = "plan_expires_at") val planExpiresAt: String? = null,
    @Json(name = "vip_days_left") val vipDaysLeft: Int? = 0,
    @Json(name = "days_remaining") val daysRemaining: Int? = null,
    @Json(name = "has_biometric") val hasBiometric: Boolean? = false,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val id: String get() = rawId?.toString() ?: rawUserId?.toString() ?: ""
    val effectiveAccountId: String get() = accountId ?: rawUid ?: run {
        val numId = (rawId as? Number)?.toLong() ?: rawId?.toString()?.toLongOrNull() ?: 10000000L
        "${77000000L + (numId % 999999L)}"
    }
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: userName?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@") ?: phone ?: "PlayDramaFlix User"
    val displayContact: String get() = email ?: phone ?: "Signed in with Google"
    val effectiveAvatar: String? get() = avatarUrl ?: profileImage ?: avatar
    val effectiveDaysLeft: Int get() = daysRemaining ?: vipDaysLeft ?: 0
    val effectiveExpiry: String? get() = planExpiresAt ?: vipExpiry
    val effectivePlan: String get() = plan ?: (if (isVip) "vip" else "free")
}

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "message") val message: String? = null,
    @Json(name = "user") val user: UserProfileDto? = null
)
