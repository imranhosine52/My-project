package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

// =============================================================================
// 🎛️ রিলস ভিডিওর কোয়ালিটি এনাম (Multi-Quality Switcher)
// =============================================================================
enum class ReelVideoQuality(val label: String, val fileSuffix: String) {
    QUALITY_720P("720p HD", "video_720p.mp4"),
    QUALITY_480P("480p SD", "video_480p.mp4"),
    QUALITY_360P("360p Data Saver", "video_360p.mp4");

    companion object {
        fun fromSuffix(url: String): ReelVideoQuality {
            return when {
                url.contains("video_360p.mp4") -> QUALITY_360P
                url.contains("video_480p.mp4") -> QUALITY_480P
                else -> QUALITY_720P
            }
        }
    }
}

// =============================================================================
// 📄 ১. ফেসবুক স্টাইল ক্রিয়েটর পেজ মডেল
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
    @Json(name = "status") val status: String = "pending", // 'pending', 'approved', 'rejected'
    @Json(name = "followers_count") val rawFollowersCount: Int? = 0,
    @Json(name = "total_views") val rawTotalViews: Long? = 0L,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val isApproved: Boolean get() = status.equals("approved", ignoreCase = true)
    val isPending: Boolean get() = status.equals("pending", ignoreCase = true)
    val isRejected: Boolean get() = status.equals("rejected", ignoreCase = true)

    val displayHandle: String get() = if (handle.startsWith("@")) handle else "@$handle"
    val followersCount: Int get() = rawFollowersCount ?: 0
    val totalViews: Long get() = rawTotalViews ?: 0L

    val formattedFollowers: String
        get() = when {
            followersCount >= 1_000_000 -> String.format(Locale.US, "%.1fM", followersCount / 1_000_000.0)
            followersCount >= 1_000 -> String.format(Locale.US, "%.1fK", followersCount / 1_000.0)
            else -> followersCount.toString()
        }
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
// 🎬 ২. রিলস / শর্টস ভিডিও মডেল (Multi-Quality Switcher সহ)
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
    @Json(name = "video_url") val videoUrl: String = "",
    @Json(name = "thumb_url") val thumbUrl: String? = null,
    @Json(name = "duration_sec") val durationSec: Int = 15,
    @Json(name = "views_count") val rawViewsCount: Long? = 0L,
    @Json(name = "likes_count") val rawLikesCount: Long? = 0L,
    @Json(name = "comments_count") val rawCommentsCount: Int? = 0,
    @Json(name = "shares_count") val rawSharesCount: Int? = 0,
    @Json(name = "is_liked") val isLiked: Boolean = false,
    @Json(name = "is_following") val isFollowing: Boolean = false
) {
    val viewsCount: Long get() = rawViewsCount ?: 0L
    val likesCount: Long get() = rawLikesCount ?: 0L
    val commentsCount: Int get() = rawCommentsCount ?: 0
    val sharesCount: Int get() = rawSharesCount ?: 0

    val displayHandle: String get() = if (handle.startsWith("@")) handle else "@$handle"

    /**
     * 🎯 MULTI-QUALITY SWITCHER LOGIC
     * সার্ভার URL থেকে 720p / 480p / 360p ডাইনামিক রূপান্তর
     */
    fun getVideoUrlForQuality(quality: ReelVideoQuality): String {
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
// ⏱️ ৩. ২৪ ঘণ্টার স্টোরি মডেল
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
// ➕ ৪. ইন্টারঅ্যাকশন ও ফলো রেসপন্স
// =============================================================================
@JsonClass(generateAdapter = true)
data class ReelInteractionResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "is_liked") val isLiked: Boolean? = false,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class PageFollowResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_following") val isFollowing: Boolean = false
)
