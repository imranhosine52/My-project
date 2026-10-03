package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * 💬 রিলস কমেন্ট ও নেস্টেড রিপ্লাই মডেল
 * (String এবং Int উভয়ের জন্য ১০০% ক্র্যাশ-প্রুফ সেইফ পার্সার)
 */
@JsonClass(generateAdapter = true)
data class ReelCommentDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "comment_id") val rawCommentId: Any? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "user_name") val userNameField: String? = null,
    @Json(name = "name") val nameField: String? = null,
    @Json(name = "user_avatar") val userAvatarField: String? = null,
    @Json(name = "avatar") val avatarField: String? = null,
    @Json(name = "comment_text") val commentTextField: String? = null,
    @Json(name = "comment") val commentField: String? = null,
    @Json(name = "text") val textField: String? = null,
    @Json(name = "likes_count") val rawLikesCount: Any? = 0,
    @Json(name = "likes") val rawLikes: Any? = 0,
    @Json(name = "is_liked") val rawIsLiked: Any? = false,
    @Json(name = "time_ago") val timeAgoField: String? = null,
    @Json(name = "created_at") val createdAtField: String? = null,
    @Json(name = "parent_id") val rawParentId: Any? = null,
    @Json(name = "replies") val repliesList: List<ReelCommentDto>? = emptyList()
) {
    val id: Int
        get() = (rawId as? Number)?.toInt()
            ?: rawId?.toString()?.toIntOrNull()
            ?: (rawCommentId as? Number)?.toInt()
            ?: rawCommentId?.toString()?.toIntOrNull()
            ?: 0

    val userId: Int
        get() = (rawUserId as? Number)?.toInt()
            ?: rawUserId?.toString()?.toIntOrNull()
            ?: 0

    val userName: String
        get() = userNameField?.takeIf { it.isNotBlank() }
            ?: nameField?.takeIf { it.isNotBlank() }
            ?: "User"

    val userAvatar: String?
        get() = userAvatarField?.takeIf { it.isNotBlank() }
            ?: avatarField?.takeIf { it.isNotBlank() }

    val commentText: String
        get() = commentTextField?.takeIf { it.isNotBlank() }
            ?: commentField?.takeIf { it.isNotBlank() }
            ?: textField?.takeIf { it.isNotBlank() }
            ?: ""

    val likesCount: Int
        get() = (rawLikesCount as? Number)?.toInt()
            ?: rawLikesCount?.toString()?.toIntOrNull()
            ?: (rawLikes as? Number)?.toInt()
            ?: rawLikes?.toString()?.toIntOrNull()
            ?: 0

    val timeAgo: String
        get() = timeAgoField ?: createdAtField ?: "Just now"

    val parentId: Int?
        get() = (rawParentId as? Number)?.toInt()
            ?: rawParentId?.toString()?.toIntOrNull()

    val replies: List<ReelCommentDto>
        get() = repliesList ?: emptyList()

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
 * (সার্ভার 'comments' অথবা 'data' যেকোনো নামে পাঠালেই অটো-ডিটেক্ট করবে)
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
