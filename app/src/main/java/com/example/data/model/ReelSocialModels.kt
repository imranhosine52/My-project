package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 💬 রিলস কমেন্ট ও নেস্টেড রিপ্লাই মডেল
 * (UI-এর সমস্ত প্যারামিটার ও copy() মেথডের সাথে ১০০% সামঞ্জস্যপূর্ণ)
 */
@JsonClass(generateAdapter = true)
data class ReelCommentDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "user_name") val userName: String = "User",
    @Json(name = "user_avatar") val userAvatar: String? = null,
    @Json(name = "comment_text") val commentText: String = "",
    @Json(name = "likes_count") val rawLikesCount: Any? = 0,
    @Json(name = "is_liked") val rawIsLiked: Any? = false,
    @Json(name = "time_ago") val timeAgo: String? = "Just now",
    @Json(name = "parent_id") val parentId: Int? = null,
    @Json(name = "replies") val replies: List<ReelCommentDto> = emptyList(),

    // 🎯 পিএইচপি সার্ভারের ভিন্ন ফিল্ডের জন্য অটো-ফলব্যাক
    @Json(name = "comment") val altComment: String? = null,
    @Json(name = "text") val altText: String? = null,
    @Json(name = "name") val altName: String? = null,
    @Json(name = "avatar") val altAvatar: String? = null
) {
    val effectiveText: String 
        get() = commentText.ifBlank { altComment ?: altText ?: "" }

    val effectiveUserName: String 
        get() = userName.takeIf { it.isNotBlank() && it != "User" } ?: altName ?: "User"

    val effectiveAvatar: String? 
        get() = userAvatar ?: altAvatar

    val repliesList: List<ReelCommentDto> 
        get() = replies

    val likesCount: Int
        get() = (rawLikesCount as? Number)?.toInt()
            ?: rawLikesCount?.toString()?.toIntOrNull()
            ?: 0

    val isLiked: Boolean
        get() = when (rawIsLiked) {
            is Boolean -> rawIsLiked
            is Number -> rawIsLiked.toInt() == 1
            is String -> rawIsLiked == "1" || rawIsLiked.equals("true", ignoreCase = true)
            else -> false
        }
}

/**
 * 📡 কমেন্ট লিস্ট রেসপন্স
 * (সার্ভার 'comments' অথবা 'data' যেকোনো নামে পাঠালেই রিড করবে)
 */
@JsonClass(generateAdapter = true)
data class ReelCommentsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val rawTotal: Any? = 0,
    @Json(name = "comments") val comments: List<ReelCommentDto> = emptyList(),
    @Json(name = "data") val data: List<ReelCommentDto>? = null,
    @Json(name = "message") val message: String? = null
) {
    val total: Int
        get() = (rawTotal as? Number)?.toInt()
            ?: rawTotal?.toString()?.toIntOrNull()
            ?: effectiveComments.size

    val effectiveComments: List<ReelCommentDto>
        get() = comments.ifEmpty { data ?: emptyList() }
}

/**
 * নতুন কমেন্ট সাবমিট রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class AddReelCommentResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "comment") val comment: ReelCommentDto? = null,
    @Json(name = "data") val data: ReelCommentDto? = null
) {
    val effectiveComment: ReelCommentDto?
        get() = comment ?: data
}

/**
 * কমেন্ট লাইক টগল রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class ToggleCommentLikeResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_liked") val isLiked: Boolean = false,
    @Json(name = "likes_count") val likesCount: Int = 0
)

/**
 * 🔁 রিপোস্ট টগল রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class ToggleRepostResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_reposted") val isReposted: Boolean = false,
    @Json(name = "reposts_count") val repostsCount: Int = 0,
    @Json(name = "message") val message: String? = null
)

/**
 * 🔖 বুকমার্ক / সেভ টগল রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class ToggleSaveReelResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_saved") val isSaved: Boolean = false,
    @Json(name = "message") val message: String? = null
)

/**
 * 📤 শেয়ার ট্র্যাকিং রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class RecordShareResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "shares_count") val sharesCount: Int = 0,
    @Json(name = "message") val message: String? = null
)

/**
 * সেভ করা রিলসের লিস্ট রেসপন্স
 */
@JsonClass(generateAdapter = true)
data class SavedReelsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList(),
    @Json(name = "data") val data: List<UserReelDto>? = null
) {
    val effectiveReels: List<UserReelDto>
        get() = reels.ifEmpty { data ?: emptyList() }
}
