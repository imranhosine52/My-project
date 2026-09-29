package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

// =============================================================================
// 🎛️ রিলস ভিডিওর কোয়ালিটি এনাম (Multi-Quality Switcher)
// =============================================================================
enum class ReelVideoQuality(val label: String, val key: String) {
    QUALITY_720P("720p HD", "720p"),
    QUALITY_480P("480p SD", "480p"),
    QUALITY_360P("360p Data Saver", "360p");

    companion object {
        fun fromKey(key: String): ReelVideoQuality {
            return when (key.lowercase()) {
                "360p" -> QUALITY_360P
                "480p" -> QUALITY_480P
                else -> QUALITY_720P
            }
        }
    }
}

// =============================================================================
// 👑 ১. REAL-TIME USER PROFILE & METRICS MODEL (Server Spec 1 - VPS 1)
// =============================================================================
@JsonClass(generateAdapter = true)
data class UserProfileMetricsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "profile") val profile: UserProfileMetricsDto? = null,
    @Json(name = "data") val data: UserProfileMetricsDto? = null,
    @Json(name = "message") val message: String? = null
) {
    val effectiveProfile: UserProfileMetricsDto? get() = profile ?: data
}

@JsonClass(generateAdapter = true)
data class UserProfileMetricsDto(
    @Json(name = "user_id") val rawUserId: Any? = 0,
    @Json(name = "name") val rawName: String? = null,
    @Json(name = "display_name") val displayNameField: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "is_vip") val isVip: Boolean = false,
    @Json(name = "has_page") val hasPage: Boolean = false,
    @Json(name = "page_id") val rawPageId: Any? = null,
    @Json(name = "page_name") val pageName: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "followers_count") val rawFollowersCount: Any? = 0L,
    @Json(name = "following_count") val rawFollowingCount: Any? = 0L,
    @Json(name = "total_likes_received") val rawTotalLikesReceived: Any? = 0L,
    @Json(name = "total_reels_count") val rawTotalReelsCount: Any? = 0,
    @Json(name = "is_following") val rawIsFollowing: Any? = false
) {
    val userId: Int get() = rawUserId?.toString()?.toIntOrNull() ?: 0
    val pageId: Int? get() = rawPageId?.toString()?.toIntOrNull()

    val effectiveAvatar: String? get() = avatar?.takeIf { it.isNotBlank() } ?: avatarUrl
    val effectiveCover: String? get() = cover?.takeIf { it.isNotBlank() } ?: coverUrl

    val followersCount: Long
        get() = rawFollowersCount?.toString()?.toLongOrNull() ?: 0L

    val followingCount: Long
        get() = rawFollowingCount?.toString()?.toLongOrNull() ?: 0L

    val totalLikesReceived: Long
        get() = rawTotalLikesReceived?.toString()?.toLongOrNull() ?: 0L

    val totalReelsCount: Int
        get() = rawTotalReelsCount?.toString()?.toIntOrNull() ?: 0

    val isFollowing: Boolean
        get() = when (rawIsFollowing) {
            is Boolean -> rawIsFollowing
            is Number -> rawIsFollowing.toInt() == 1
            is String -> rawIsFollowing == "1" || rawIsFollowing.equals("true", ignoreCase = true)
            else -> false
        }

    val displayHandle: String
        get() = when {
            handle.isNullOrBlank() -> "@user$userId"
            handle.startsWith("@") -> handle
            else -> "@$handle"
        }

    val displayName: String
        get() = rawName?.takeIf { it.isNotBlank() } 
            ?: displayNameField?.takeIf { it.isNotBlank() } 
            ?: pageName?.takeIf { it.isNotBlank() } 
            ?: "Creator"

    val formattedFollowers: String get() = formatCount(followersCount)
    val formattedFollowing: String get() = formatCount(followingCount)
    val formattedLikes: String get() = formatCount(totalLikesReceived)
    val formattedReelsCount: String get() = totalReelsCount.toString()

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}

// =============================================================================
// 📤 ২. AVATAR & COVER UPLOAD RESPONSE
// =============================================================================
@JsonClass(generateAdapter = true)
data class MediaUploadResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "cover_url") val coverUrl: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null
) {
    val effectiveUrl: String?
        get() = cover?.takeIf { it.isNotBlank() }
            ?: coverUrl?.takeIf { it.isNotBlank() }
            ?: avatar?.takeIf { it.isNotBlank() }
            ?: avatarUrl?.takeIf { it.isNotBlank() }
            ?: url?.takeIf { it.isNotBlank() }
            ?: mediaUrl?.takeIf { it.isNotBlank() }
            ?: imageUrl?.takeIf { it.isNotBlank() }
}

// =============================================================================
// 📄 ৩. ক্রিয়েটর পেজ মডেল
// =============================================================================
@JsonClass(generateAdapter = true)
data class CreatorPageDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "page_name") val pageName: String = "",
    @Json(name = "handle") val handle: String = "",
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "status") val status: String = "pending",
    @Json(name = "followers_count") val rawFollowersCount: Int? = 0,
    @Json(name = "following_count") val rawFollowingCount: Int? = 0,
    @Json(name = "total_likes") val rawTotalLikes: Long? = 0L,
    @Json(name = "total_views") val rawTotalViews: Long? = 0L,
    @Json(name = "custom_link") val customLink: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val isApproved: Boolean get() = status.equals("approved", ignoreCase = true)
    val isPending: Boolean get() = status.equals("pending", ignoreCase = true)
    val isRejected: Boolean get() = status.equals("rejected", ignoreCase = true)

    val displayHandle: String get() = if (handle.startsWith("@")) handle else "@$handle"
    val followersCount: Int get() = rawFollowersCount ?: 0
    val followingCount: Int get() = rawFollowingCount ?: 0
    val totalLikes: Long get() = rawTotalLikes ?: 0L
    val totalViews: Long get() = rawTotalViews ?: 0L

    val pageShareUrl: String 
        get() = customLink?.takeIf { it.isNotBlank() } 
            ?: "https://playdramaflix.com/page/${handle.removePrefix("@")}"

    val pageDeepLink: String 
        get() = "playdramaflix://page/$id"
}

@JsonClass(generateAdapter = true)
data class MyPageResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "has_page") val hasPage: Boolean = false,
    @Json(name = "page") val page: CreatorPageDto? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ApplyPageResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: String? = "pending",
    @Json(name = "page_id") val pageId: Int? = null,
    @Json(name = "message") val message: String? = null
)

// =============================================================================
// 🌟 ৪. নতুন সাজেস্টেড পেজ ও ফলো মডেল (get_suggested_pages API)
// =============================================================================
@JsonClass(generateAdapter = true)
data class SuggestedPageDto(
    @Json(name = "page_id") val pageId: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "page_name") val pageName: String = "",
    @Json(name = "handle") val handle: String = "",
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "category") val category: String? = "Entertainment",
    @Json(name = "followers_count") val rawFollowersCount: Any? = 0,
    @Json(name = "total_reels") val rawTotalReels: Any? = 0,
    @Json(name = "is_following") val rawIsFollowing: Any? = false
) {
    val followersCount: Long get() = rawFollowersCount?.toString()?.toLongOrNull() ?: 0L
    val totalReels: Int get() = rawTotalReels?.toString()?.toIntOrNull() ?: 0

    val isFollowing: Boolean
        get() = when (rawIsFollowing) {
            is Boolean -> rawIsFollowing
            is Number -> rawIsFollowing.toInt() == 1
            is String -> rawIsFollowing == "1" || rawIsFollowing.equals("true", ignoreCase = true)
            else -> false
        }

    val displayHandle: String get() = if (handle.startsWith("@")) handle else "@$handle"

    val formattedFollowers: String
        get() = when {
            followersCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", followersCount / 1_000_000.0)
            followersCount >= 1_000 -> String.format(Locale.US, "%.1fk", followersCount / 1_000.0)
            else -> "$followersCount"
        }
}

@JsonClass(generateAdapter = true)
data class SuggestedPagesResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "suggested_pages") val suggestedPages: List<SuggestedPageDto> = emptyList(),
    @Json(name = "pages") val pages: List<SuggestedPageDto>? = null,
    @Json(name = "message") val message: String? = null
) {
    val effectivePages: List<SuggestedPageDto> get() = suggestedPages.ifEmpty { pages ?: emptyList() }
}

// =============================================================================
// 🎬 ৫. রিলস / শর্টস ভিডিও মডেল
// =============================================================================
@JsonClass(generateAdapter = true)
data class UserReelDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "page_id") val pageId: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "page_name") val pageName: String = "Creator",
    @Json(name = "handle") val handle: String = "@creator",
    @Json(name = "page_avatar") val pageAvatar: String? = null,
    @Json(name = "title") val title: String? = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "hashtags") val hashtags: String? = null,
    @Json(name = "video_url") val videoUrl: String = "",
    @Json(name = "qualities") val qualities: Map<String, String>? = null,
    @Json(name = "thumb_url") val thumbUrl: String? = null,
    @Json(name = "duration_sec") val durationSec: Int = 15,
    @Json(name = "views_count") val rawViewsCount: Long? = 0L,
    @Json(name = "likes_count") val rawLikesCount: Long? = 0L,
    @Json(name = "comments_count") val rawCommentsCount: Int? = 0,
    @Json(name = "shares_count") val rawSharesCount: Int? = 0,
    @Json(name = "reposts_count") val rawRepostsCount: Int? = 0,
    @Json(name = "is_liked") val isLiked: Boolean = false,
    @Json(name = "is_saved") val isSaved: Boolean = false,
    @Json(name = "is_reposted") val isReposted: Boolean = false,
    @Json(name = "is_following") val isFollowing: Boolean = false
) {
    val rawVideoUrl: String get() = videoUrl
    val viewsCount: Long get() = rawViewsCount ?: 0L
    val likesCount: Long get() = rawLikesCount ?: 0L
    val commentsCount: Int get() = rawCommentsCount ?: 0
    val sharesCount: Int get() = rawSharesCount ?: 0
    val repostsCount: Int get() = rawRepostsCount ?: 0

    val displayHandle: String get() = if (handle.startsWith("@")) handle else "@$handle"
    val shareUrl: String get() = "https://playdramaflix.com/reel/$id"
    val customDeepLink: String get() = "playdramaflix://reel/$id"

    fun getVideoUrlForQuality(quality: ReelVideoQuality): String {
        if (!qualities.isNullOrEmpty()) {
            val direct = qualities[quality.key]
            if (!direct.isNullOrBlank()) return direct
        }

        if (videoUrl.isBlank()) return ""
        return when (quality) {
            ReelVideoQuality.QUALITY_720P -> {
                videoUrl.replace("video_480p.mp4", "video_720p.mp4")
                    .replace("video_360p.mp4", "video_720p.mp4")
            }
            ReelVideoQuality.QUALITY_480P -> {
                videoUrl.replace("video_720p.mp4", "video_480p.mp4")
                    .replace("video_360p.mp4", "video_480p.mp4")
            }
            ReelVideoQuality.QUALITY_360P -> {
                videoUrl.replace("video_720p.mp4", "video_360p.mp4")
                    .replace("video_480p.mp4", "video_360p.mp4")
            }
        }
    }

    val formattedLikes: String
        get() = when {
            likesCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", likesCount / 1_000_000.0)
            likesCount >= 1_000 -> String.format(Locale.US, "%.1fK", likesCount / 1_000.0)
            else -> likesCount.toString()
        }

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
}

@JsonClass(generateAdapter = true)
data class ReelsFeedResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReelUploadResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "reel_id") val reelId: Int? = null,
    @Json(name = "video_url") val videoUrl: String? = null,
    @Json(name = "thumb_url") val thumbUrl: String? = null,
    @Json(name = "duration_sec") val durationSec: Int? = 0,
    @Json(name = "message") val message: String? = null
)

// =============================================================================
// ⏱️ ৬. ২৪ ঘণ্টার স্টোরি মডেল
// =============================================================================
@JsonClass(generateAdapter = true)
data class UserStoryDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "page_id") val pageId: Int? = null,
    @Json(name = "user_name") val userName: String = "User",
    @Json(name = "user_avatar") val userAvatar: String? = null,
    @Json(name = "page_name") val pageName: String? = null,
    @Json(name = "media_type") val mediaType: String = "video",
    @Json(name = "media_url") val mediaUrl: String = "",
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val isVideo: Boolean get() = mediaType.equals("video", ignoreCase = true)
    val displayName: String get() = pageName?.takeIf { it.isNotBlank() } ?: userName
}

@JsonClass(generateAdapter = true)
data class StoriesFeedResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "stories") val stories: List<UserStoryDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StoryUploadResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null
)

// =============================================================================
// ➕ ৭. ইন্টারঅ্যাকশন ও ফলো রেসপন্স
// =============================================================================
@JsonClass(generateAdapter = true)
data class ReelLikeResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_liked") val isLiked: Boolean = false
)

typealias ReelInteractionResponse = ReelLikeResponse

@JsonClass(generateAdapter = true)
data class PageFollowResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_following") val isFollowing: Boolean = false,
    @Json(name = "following") val following: Boolean? = null,
    @Json(name = "followers_count") val followersCount: Long? = null
) {
    val effectiveIsFollowing: Boolean get() = following ?: isFollowing
}
