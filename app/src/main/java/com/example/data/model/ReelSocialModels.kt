package com.example.data.model

import com.squareup.moshi.Json
import java.util.Locale

/**
 * 💬 রিলস কমেন্ট ও নেস্টেড রিপ্লাই মডেল
 */
data class ReelCommentDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "user_name") val userName: String = "User",
    @Json(name = "user_avatar") val userAvatar: String? = null,
    @Json(name = "comment_text") val commentText: String = "",
    @Json(name = "likes_count") val rawLikesCount: Int? = 0,
    @Json(name = "is_liked") val rawIsLiked: Any? = false,
    @Json(name = "time_ago") val timeAgo: String? = "Just now",
    @Json(name = "parent_id") val parentId: Int? = null,
    @Json(name = "replies") val replies: List<ReelCommentDto>? = emptyList()
) {
    val likesCount: Int get() = rawLikesCount ?: 0
    val repliesList: List<ReelCommentDto> get() = replies ?: emptyList()

    val isLiked: Boolean
        get() = when (rawIsLiked) {
            is Boolean -> rawIsLiked
            is Number -> rawIsLiked.toInt() == 1
            is String -> rawIsLiked == "1" || rawIsLiked.equals("true", ignoreCase = true)
            else -> false
        }
}

/**
 * কমেন্ট লিস্ট রেসপন্স
 */
data class ReelCommentsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "comments") val comments: List<ReelCommentDto> = emptyList()
)

/**
 * নতুন কমেন্ট সাবমিট রেসপন্স
 */
data class AddReelCommentResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "comment") val comment: ReelCommentDto? = null
)

/**
 * কমেন্ট লাইক টগল রেসপন্স
 */
data class ToggleCommentLikeResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_liked") val isLiked: Boolean = false,
    @Json(name = "likes_count") val likesCount: Int = 0
)

/**
 * 🔁 রিপোস্ট টগল রেসপন্স
 */
data class ToggleRepostResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_reposted") val isReposted: Boolean = false,
    @Json(name = "reposts_count") val repostsCount: Int = 0,
    @Json(name = "message") val message: String? = null
)

/**
 * 🔖 বুকমার্ক / সেভ টগল রেসপন্স
 */
data class ToggleSaveReelResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_saved") val isSaved: Boolean = false,
    @Json(name = "message") val message: String? = null
)

/**
 * 📤 শেয়ার ট্র্যাকিং রেসপন্স
 */
data class RecordShareResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "shares_count") val sharesCount: Int = 0,
    @Json(name = "message") val message: String? = null
)

/**
 * সেভ করা রিলসের লিস্ট রেসপন্স
 */
data class SavedReelsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList()
)
