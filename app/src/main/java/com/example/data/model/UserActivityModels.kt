package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserActivityResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "user_avatar") val userAvatar: String? = null,
    @Json(name = "likes_count") val likesCount: Int? = 0,
    @Json(name = "comments_count") val commentsCount: Int? = 0,
    @Json(name = "likes") val likes: List<UserLikedActivityDto>? = emptyList(),
    @Json(name = "comments") val comments: List<UserCommentActivityDto>? = emptyList()
) {
    val likesList: List<UserLikedActivityDto> get() = likes ?: emptyList()
    val commentsList: List<UserCommentActivityDto> get() = comments ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class UserLikedActivityDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "badge_tag") val badgeTag: String? = null,
    @Json(name = "duration") val duration: String? = "01:20",
    @Json(name = "total_likes") val totalLikes: Int? = 0,
    @Json(name = "total_comments") val totalComments: Int? = 0,
    @Json(name = "total_shares") val totalShares: Int? = 0,
    @Json(name = "liked_date") val likedDate: String? = "Recent"
) {
    val idString: String get() = rawId?.toString() ?: slug
    val effectiveBadge: String get() = badgeTag?.takeIf { it.isNotBlank() } ?: "🎬 What to Watch"
    val effectiveDuration: String get() = duration?.takeIf { it.isNotBlank() } ?: "01:20"
}

@JsonClass(generateAdapter = true)
data class UserCommentActivityDto(
    @Json(name = "comment_id") val rawCommentId: Any? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "user_avatar") val userAvatar: String? = null,
    @Json(name = "comment_text") val commentText: String = "",
    @Json(name = "comment_date") val commentDate: String? = "Recent",
    @Json(name = "comment_likes") val commentLikes: Int? = 0,
    @Json(name = "comment_shares") val commentShares: Int? = 0,
    @Json(name = "post") val post: ActivityPostSummaryDto? = null
) {
    val commentIdString: String get() = rawCommentId?.toString() ?: ""
}

@JsonClass(generateAdapter = true)
data class ActivityPostSummaryDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "badge_tag") val badgeTag: String? = null,
    @Json(name = "total_likes") val totalLikes: Int? = 0,
    @Json(name = "total_comments") val totalComments: Int? = 0,
    @Json(name = "total_shares") val totalShares: Int? = 0
) {
    val idString: String get() = rawId?.toString() ?: slug
    val effectiveBadge: String get() = badgeTag?.takeIf { it.isNotBlank() } ?: "🎬 What to Watch"
}
