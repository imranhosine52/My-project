package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 👥 রেগুলার ইউজারের ফ্রেন্ড আইটেম DTO
 */
@JsonClass(generateAdapter = true)
data class RegularUserFriendDto(
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "name") val rawName: String? = null,
    @Json(name = "avatar") val avatar: String? = null
) {
    val userId: Int get() = rawUserId?.toString()?.toIntOrNull() ?: 0
    val displayName: String get() = rawName?.takeIf { it.isNotBlank() } ?: "Friend"
}

/**
 * 📊 রেগুলার ইউজারের আসল মেট্রিক্স DTO (Friends, Following, Saved Videos)
 */
@JsonClass(generateAdapter = true)
data class RegularUserMetricsDto(
    @Json(name = "friends_count") val rawFriendsCount: Any? = 0,
    @Json(name = "following_count") val rawFollowingCount: Any? = 0,
    @Json(name = "saved_count") val rawSavedCount: Any? = 0
) {
    val friendsCount: Int get() = rawFriendsCount?.toString()?.toIntOrNull() ?: 0
    val followingCount: Int get() = rawFollowingCount?.toString()?.toIntOrNull() ?: 0
    val savedCount: Int get() = rawSavedCount?.toString()?.toIntOrNull() ?: 0

    val formattedFriends: String get() = formatMetric(friendsCount.toLong())
    val formattedFollowing: String get() = formatMetric(followingCount.toLong())
    val formattedSaved: String get() = formatMetric(savedCount.toLong())

    private fun formatMetric(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}

/**
 * 👤 রেগুলার ইউজারের সম্পূর্ণ প্রোফাইল মডেল
 */
@JsonClass(generateAdapter = true)
data class RegularUserProfileDto(
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "account_id") val accountId: String? = null,
    @Json(name = "name") val rawName: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "is_vip") val rawIsVip: Any? = false,
    @Json(name = "is_friend") val rawIsFriend: Any? = false,
    @Json(name = "metrics") val metrics: RegularUserMetricsDto? = RegularUserMetricsDto(),
    @Json(name = "friends") val friends: List<RegularUserFriendDto> = emptyList(),
    @Json(name = "saved_reels") val savedReels: List<UserReelDto> = emptyList()
) {
    val userId: Int get() = rawUserId?.toString()?.toIntOrNull() ?: 0
    val displayName: String get() = rawName?.takeIf { it.isNotBlank() } ?: "Drama Viewer"
    val displayAccountId: String get() = accountId?.takeIf { it.isNotBlank() } ?: "#${85000000 + userId}"

    val isVip: Boolean
        get() = when (rawIsVip) {
            is Boolean -> rawIsVip
            is Number -> rawIsVip.toInt() == 1
            is String -> rawIsVip == "1" || rawIsVip.equals("true", ignoreCase = true)
            else -> false
        }

    val isFriend: Boolean
        get() = when (rawIsFriend) {
            is Boolean -> rawIsFriend
            is Number -> rawIsFriend.toInt() == 1
            is String -> rawIsFriend == "1" || rawIsFriend.equals("true", ignoreCase = true)
            else -> false
        }
}

/**
 * 📡 রেগুলার ইউজার প্রোফাইল এপিআই রেসপন্স
 * Endpoint: GET /tiktok-manager.php?action=get_user_regular_profile&user_id={id}&viewer_id={viewer_id}
 */
@JsonClass(generateAdapter = true)
data class RegularUserProfileResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "profile") val profile: RegularUserProfileDto? = null,
    @Json(name = "message") val message: String? = null
)

/**
 * 🤝 ফ্রেন্ড রিকোয়েস্ট / টগল এপিআই রেসপন্স
 * Endpoint: POST /tiktok-manager.php?action=toggle_friend
 */
@JsonClass(generateAdapter = true)
data class ToggleFriendResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_friend") val isFriend: Boolean = false,
    @Json(name = "friends_count") val friendsCount: Int? = null,
    @Json(name = "message") val message: String? = null
)
