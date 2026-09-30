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
data class PublicPlaylistSummaryDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = "",
    @Json(name = "cover_url") val coverUrl: String? = "",
    @Json(name = "total_episodes") val totalEpisodes: Int = 1,
    @Json(name = "total_views") val totalViews: Long = 0L
)

@JsonClass(generateAdapter = true)
data class PublicReelSummaryDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String? = "",
    @Json(name = "video_url") val videoUrl: String = "",
    @Json(name = "thumb_url") val thumbUrl: String? = null,
    @Json(name = "duration_sec") val durationSec: Int = 15,
    @Json(name = "views_count") val viewsCount: Long = 0L,
    @Json(name = "likes_count") val likesCount: Long = 0L,
    @Json(name = "playlist_id") val playlistId: Int? = null,
    @Json(name = "episode_num") val episodeNum: Int? = 1
) {
    val formattedViews: String
        get() = when {
            viewsCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", viewsCount / 1_000_000.0)
            viewsCount >= 1_000 -> String.format(Locale.US, "%.1fK", viewsCount / 1_000.0)
            else -> viewsCount.toString()
        }

    val formattedDuration: String
        get() {
            val m = durationSec / 60
            val s = durationSec % 60
            return String.format(Locale.US, "%02d:%02d", m, s)
        }

    fun toUserReelDto(pageName: String, handle: String, avatar: String?): UserReelDto {
        return UserReelDto(
            id = id,
            title = title ?: "",
            videoUrl = videoUrl,
            thumbUrl = thumbUrl,
            durationSec = durationSec,
            rawViewsCount = viewsCount,
            rawLikesCount = likesCount,
            playlistId = playlistId,
            rawEpisodeNum = episodeNum ?: 1,
            pageName = pageName,
            handle = handle,
            pageAvatar = avatar
        )
    }
}

@JsonClass(generateAdapter = true)
data class PublicCreatorProfileDto(
    @Json(name = "page_id") val pageId: Long = 0L,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "page_name") val pageName: String = "Creator",
    @Json(name = "handle") val handle: String = "@creator",
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "category") val category: String? = "Entertainment",
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "is_following") val isFollowing: Boolean = false,
    
    // 🎯 আপনার লাইভ API-এর রুট লেভেল ফিল্ডসমূহ
    @Json(name = "followers_count") val followersCount: Long = 0L,
    @Json(name = "following_count") val followingCount: Long = 0L,
    @Json(name = "likes_count") val likesCount: Long = 0L,
    @Json(name = "reels_count") val reelsCount: Int = 0,
    
    @Json(name = "total_playlists") val totalPlaylists: Int = 0,
    @Json(name = "playlists") val playlists: List<PublicPlaylistSummaryDto> = emptyList(),
    @Json(name = "reels") val reels: List<PublicReelSummaryDto> = emptyList()
) {
    val displayHandle: String get() = if (handle.startsWith("@")) handle else "@$handle"

    val formattedFollowers: String get() = formatCount(followersCount)
    val formattedFollowing: String get() = formatCount(followingCount)
    val formattedLikes: String get() = formatCount(likesCount)

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
