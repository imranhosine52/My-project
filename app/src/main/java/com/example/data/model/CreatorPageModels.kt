package com.example.data.model

import com.squareup.moshi.Json
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
// 📄 ১. ফেসবুক স্টাইল ক্রিয়েটর পেজ মডেল (ইউনিক পেজ লিংক সহ)
// =============================================================================
data class CreatorPageDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "user_id") val userId: Int = 0,
    @Json(name = "page_name") val pageName: String = "",
    @Json(name = "handle") val handle: String = "",
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "status") val status: String = "pending", // 'pending', 'approved', 'rejected'
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

    // 🎯 প্রতিটি পেজের নিজস্ব ইউনিক ওয়েবসাইট ও ডিপ-লিঙ্ক
    val pageShareUrl: String 
        get() = customLink?.takeIf { it.isNotBlank() } 
            ?: "https://playdramaflix.com/page/${handle.removePrefix("@")}"

    val pageDeepLink: String 
        get() = "playdramaflix://page/$id"

    val formattedFollowers: String
        get() = when {
            followersCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", followersCount / 1_000_000.0)
            followersCount >= 1_000 -> String.format(Locale.US, "%.1fK", followersCount / 1_000.0)
            else -> followersCount.toString()
        }
}

data class MyPageResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "has_page") val hasPage: Boolean = false,
    @Json(name = "page") val page: CreatorPageDto? = null,
    @Json(name = "message") val message: String? = null
)

data class ApplyPageResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: String? = "pending",
    @Json(name = "page_id") val pageId: Int? = null,
    @Json(name = "message") val message: String? = null
)

// =============================================================================
// 🎬 ২. রিলস / শর্টস ভিডিও মডেল (ইউনিক ভিডিও লিংক ও Multi-Quality সহ)
// =============================================================================
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
    @Json(name = "qualities") val qualities: Map<String, String>? = null, // {"720p": "...", "480p": "...", "360p": "..."}
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

    // 🎯 প্রতিটি রিলস পোস্টের জন্য ইউনিক ডিপ-লিঙ্ক
    val shareUrl: String 
        get() = "https://playdramaflix.com/reel/$id"

    val customDeepLink: String 
        get() = "playdramaflix://reel/$id"

    /**
     * 🎯 MULTI-QUALITY SWITCHER LOGIC
     * সার্ভার URL থেকে 720p / 480p / 360p ডাইনামিক রূপান্তর
     */
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

data class ReelsFeedResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "reels") val reels: List<UserReelDto> = emptyList()
)

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
// ⏱️ ৩. ২৪ ঘণ্টার স্টোরি মডেল
// =============================================================================
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

data class StoriesFeedResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "stories") val stories: List<UserStoryDto> = emptyList()
)

data class StoryUploadResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null
)

// =============================================================================
// ➕ ৪. ইন্টারঅ্যাকশন ও ফলো রেসপন্স
// =============================================================================
data class ReelLikeResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_liked") val isLiked: Boolean = false
)

typealias ReelInteractionResponse = ReelLikeResponse

data class PageFollowResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_following") val isFollowing: Boolean = false
)
