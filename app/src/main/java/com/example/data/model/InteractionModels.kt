package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ViewIncrementRequest(
    @Json(name = "content_id") val contentId: Any
)

@JsonClass(generateAdapter = true)
data class ViewIncrementResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "content_id") val contentId: Any? = null,
    @Json(name = "total_views") val totalViews: Long? = null,
    @Json(name = "views") val views: Long? = null
) {
    val effectiveViews: Long get() = totalViews ?: views ?: 0L
}

@JsonClass(generateAdapter = true)
data class LikeToggleRequest(
    @Json(name = "content_id") val contentId: Any,
    @Json(name = "episode_id") val episodeId: Any? = null,
    @Json(name = "user_id") val userId: Any? = null
)

@JsonClass(generateAdapter = true)
data class LikeToggleResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_liked") val isLiked: Boolean? = null,
    @Json(name = "liked") val liked: Boolean? = null,
    @Json(name = "total_likes") val totalLikes: Long? = null,
    @Json(name = "likes") val likes: Long? = null
) {
    val effectiveIsLiked: Boolean get() = isLiked ?: liked ?: false
    val effectiveLikes: Long get() = totalLikes ?: likes ?: 0L
}

@JsonClass(generateAdapter = true)
data class InteractionStatusResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "content_id") val contentId: Any? = null,
    @Json(name = "views") val views: Long? = null,
    @Json(name = "total_views") val totalViews: Long? = null,
    @Json(name = "total_likes") val totalLikes: Long? = null,
    @Json(name = "likes") val likes: Long? = null,
    @Json(name = "is_liked") val isLiked: Boolean? = null,
    @Json(name = "liked") val liked: Boolean? = null,
    @Json(name = "total_comments") val totalComments: Int? = null
) {
    val effectiveViews: Long get() = totalViews ?: views ?: 0L
    val effectiveLikes: Long get() = totalLikes ?: likes ?: 0L
    val effectiveIsLiked: Boolean get() = isLiked ?: liked ?: false
}

@JsonClass(generateAdapter = true)
data class DramaApiComment(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "content_id") val rawContentId: Any? = null,
    @Json(name = "episode_id") val rawEpisodeId: Any? = null,
    @Json(name = "parent_id") val rawParentId: Any? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "user_avatar") val userAvatar: String? = null,
    @Json(name = "avatar") val fallbackAvatar: String? = null,
    @Json(name = "comment_text") val commentText: String = "",
    @Json(name = "likes_count") val rawLikesCount: Int? = null,
    @Json(name = "likes") val fallbackLikes: Int? = null,
    @Json(name = "shares_count") val rawSharesCount: Int? = null,
    @Json(name = "is_liked") val isLikedVal: Boolean? = null,
    @Json(name = "replies_count") val rawRepliesCount: Int? = null,
    @Json(name = "date_display") val dateDisplay: String? = null,
    @Json(name = "time_ago") val timeAgo: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "replies") val replies: List<DramaApiComment>? = null
) {
    val id: String get() = rawId?.toString() ?: "c_${System.currentTimeMillis()}"
    val displayName: String get() = userName?.takeIf { it.isNotBlank() } ?: "DramaFlix Fan"
    val avatarUrl: String? get() = userAvatar?.takeIf { it.isNotBlank() } ?: fallbackAvatar
    val likesCount: Int get() = rawLikesCount ?: fallbackLikes ?: 0
    val sharesCount: Int get() = rawSharesCount ?: 0
    val isLiked: Boolean get() = isLikedVal ?: false
    val repliesCount: Int get() = rawRepliesCount ?: replies?.size ?: 0
    val displayDate: String get() = dateDisplay?.takeIf { it.isNotBlank() } ?: timeAgo ?: createdAt?.take(10) ?: "Just now"
    val repliesList: List<DramaApiComment> get() = replies ?: emptyList()
}

typealias CommentItemDto = DramaApiComment

@JsonClass(generateAdapter = true)
data class CommentsListResponse(
    @Json(name = "status") val rawStatus: Any? = 200,
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "total_comments") val totalComments: Int? = null,
    @Json(name = "data") val data: List<DramaApiComment>? = null,
    @Json(name = "comments") val comments: List<DramaApiComment>? = null
) {
    val commentsList: List<DramaApiComment> get() = data ?: comments ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class AddCommentApiRequest(
    @Json(name = "content_id") val contentId: Any,
    @Json(name = "episode_id") val episodeId: Any? = null,
    @Json(name = "parent_id") val parentId: Any? = null,
    @Json(name = "user_id") val userId: Any? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "comment_text") val commentText: String
)

@JsonClass(generateAdapter = true)
data class AddCommentResponse(
    @Json(name = "status") val rawStatus: Any? = 201,
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: DramaApiComment? = null,
    @Json(name = "comment") val comment: DramaApiComment? = null
) {
    val commentItem: DramaApiComment? get() = data ?: comment
}

@JsonClass(generateAdapter = true)
data class CommentLikeApiRequest(
    @Json(name = "comment_id") val commentId: Any,
    @Json(name = "user_id") val userId: Any? = null
)

@JsonClass(generateAdapter = true)
data class CommentLikeApiResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val rawStatus: Any? = 200,
    @Json(name = "comment_id") val rawCommentId: Any? = null,
    @Json(name = "is_liked") val isLiked: Boolean? = null,
    @Json(name = "total_likes") val totalLikes: Int? = null
)

@JsonClass(generateAdapter = true)
data class CommentShareApiRequest(
    @Json(name = "comment_id") val commentId: Any,
    @Json(name = "user_id") val userId: Any? = null
)

@JsonClass(generateAdapter = true)
data class CommentShareApiResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val rawStatus: Any? = 200,
    @Json(name = "total_shares") val totalShares: Int? = null
)
