package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 🔔 ১. রিয়েল সোশ্যাল অ্যাক্টিভিটি নোটিফিকেশন DTO
 * Endpoint: GET /tiktok-manager.php?action=get_social_activities&user_id={id}
 */
@JsonClass(generateAdapter = true)
data class SocialActivityDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "actor_id") val rawActorId: Any? = null,
    @Json(name = "actor_name") val rawActorName: String? = null,
    @Json(name = "actor_avatar") val actorAvatar: String? = null,
    @Json(name = "type") val type: String = "like", // "like", "comment", "follow", "save", "reply"
    @Json(name = "text") val text: String = "interacted with your profile.",
    @Json(name = "reel_id") val rawReelId: Any? = null,
    @Json(name = "thumb_url") val thumbUrl: String? = null,
    @Json(name = "time_ago") val customTimeAgo: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val actorId: Int get() = rawActorId?.toString()?.toIntOrNull() ?: 0
    val actorName: String get() = rawActorName?.takeIf { it.isNotBlank() } ?: "Someone"
    val reelId: Int? get() = rawReelId?.toString()?.toIntOrNull()
    val timeAgo: String get() = customTimeAgo ?: createdAt ?: "Recent"
}

@JsonClass(generateAdapter = true)
data class SocialActivitiesResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val rawTotal: Any? = 0,
    @Json(name = "activities") val activities: List<SocialActivityDto> = emptyList(),
    @Json(name = "data") val data: List<SocialActivityDto>? = null,
    @Json(name = "message") val message: String? = null
) {
    val totalCount: Int get() = (rawTotal as? Number)?.toInt() ?: rawTotal?.toString()?.toIntOrNull() ?: effectiveActivities.size
    val effectiveActivities: List<SocialActivityDto> get() = activities.ifEmpty { data ?: emptyList() }
}

/**
 * 👥 ২. পেন্ডিং ফ্রেন্ড রিকোয়েস্ট DTO (৩ নম্বর ছবি)
 * Endpoint: GET /tiktok-manager.php?action=get_friend_requests&user_id={id}
 */
@JsonClass(generateAdapter = true)
data class FriendRequestDto(
    @Json(name = "request_id") val rawRequestId: Any? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "name") val rawName: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "mutual_info") val mutualInfo: String? = null,
    @Json(name = "time_ago") val customTimeAgo: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false
) {
    val requestId: Int get() = rawRequestId?.toString()?.toIntOrNull() ?: 0
    val userId: Int get() = rawUserId?.toString()?.toIntOrNull() ?: 0
    val name: String get() = rawName?.takeIf { it.isNotBlank() } ?: "User"
    val timeAgo: String get() = customTimeAgo ?: "Recent"
}

@JsonClass(generateAdapter = true)
data class FriendRequestsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val rawTotal: Any? = 0, // 👈 "Requests (X)" ট্যাবের ডাইনামিক ব্যাজ সংখ্যা
    @Json(name = "requests") val requests: List<FriendRequestDto> = emptyList(),
    @Json(name = "data") val data: List<FriendRequestDto>? = null,
    @Json(name = "message") val message: String? = null
) {
    val totalCount: Int get() = (rawTotal as? Number)?.toInt() ?: rawTotal?.toString()?.toIntOrNull() ?: effectiveRequests.size
    val effectiveRequests: List<FriendRequestDto> get() = requests.ifEmpty { data ?: emptyList() }
}

/**
 * 🤝 ৩. একসেপ্টেড / কনফার্মড ফ্রেন্ডস DTO
 * Endpoint: GET /tiktok-manager.php?action=get_confirmed_friends&user_id={id}
 */
@JsonClass(generateAdapter = true)
data class ConfirmedFriendDto(
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "name") val rawName: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "handle") val handle: String? = null
) {
    val userId: Int get() = rawUserId?.toString()?.toIntOrNull() ?: 0
    val name: String get() = rawName?.takeIf { it.isNotBlank() } ?: "Friend"
    val displayHandle: String get() = handle?.takeIf { it.isNotBlank() } ?: "@user$userId"
}

@JsonClass(generateAdapter = true)
data class ConfirmedFriendsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "total") val rawTotal: Any? = 0,
    @Json(name = "friends") val friends: List<ConfirmedFriendDto> = emptyList(),
    @Json(name = "data") val data: List<ConfirmedFriendDto>? = null,
    @Json(name = "message") val message: String? = null
) {
    val totalCount: Int get() = (rawTotal as? Number)?.toInt() ?: rawTotal?.toString()?.toIntOrNull() ?: effectiveFriends.size
    val effectiveFriends: List<ConfirmedFriendDto> get() = friends.ifEmpty { data ?: emptyList() }
}

/**
 * ⚡ ৪. ফ্রেন্ড রিকোয়েস্ট হ্যান্ডলার রেসপন্স (Confirm / Delete)
 * Endpoint: POST /tiktok-manager.php?action=handle_friend_request
 */
@JsonClass(generateAdapter = true)
data class HandleFriendRequestResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: String? = "confirmed", // "confirmed", "deleted"
    @Json(name = "message") val message: String? = null
)
