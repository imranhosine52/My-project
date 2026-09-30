package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

@JsonClass(generateAdapter = true)
data class PublicCreatorProfileResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "profile") val profile: PublicCreatorProfileDto? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileMetricsDto(
    @Json(name = "followers_count") val rawFollowersCount: Any? = 0L,
    @Json(name = "following_count") val rawFollowingCount: Any? = 0L,
    @Json(name = "likes_count") val rawLikesCount: Any? = 0L,
    @Json(name = "reels_count") val rawReelsCount: Any? = 0
) {
    val followersCount: Long get() = rawFollowersCount?.toString()?.toLongOrNull() ?: 0L
    val followingCount: Long get() = rawFollowingCount?.toString()?.toLongOrNull() ?: 0L
    val likesCount: Long get() = rawLikesCount?.toString()?.toLongOrNull() ?: 0L
    val reelsCount: Int get() = rawReelsCount?.toString()?.toIntOrNull() ?: 0

    val formattedFollowers: String get() = formatMetric(followersCount)
    val formattedFollowing: String get() = formatMetric(followingCount)
    val formattedLikes: String get() = formatMetric(likesCount)

    private fun formatMetric(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}

@JsonClass(generateAdapter = true)
data class PublicCreatorProfileDto(
    @Json(name = "page_id") val rawPageId: Any? = 0L,
    @Json(name = "user_id") val rawUserId: Any? = 0,
    @Json(name = "page_name") val pageName: String = "",
    @Json(name = "handle") val handle: String = "",
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "category") val category: String? = "Entertainment",
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "is_following") val rawIsFollowing: Any? = false,
    @Json(name = "metrics") val metrics: ProfileMetricsDto? = ProfileMetricsDto(),
    @Json(name = "total_playlists") val totalPlaylists: Int = 0,
    @Json(name = "playlists") val playlists: List<CreatorPlaylistDto> = emptyList(),
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList()
) {
    val pageId: Long get() = rawPageId?.toString()?.toLongOrNull() ?: 0L
    val userId: Int get() = rawUserId?.toString()?.toIntOrNull() ?: 0

    val isFollowing: Boolean
        get() = when (rawIsFollowing) {
            is Boolean -> rawIsFollowing
            is Number -> rawIsFollowing.toInt() == 1
            is String -> rawIsFollowing == "1" || rawIsFollowing.equals("true", true)
            else -> false
        }

    val displayHandle: String get() = if (handle.startsWith("@")) handle else "@$handle"
    val displayPageId: String get() = "ID: #$pageId"
}
