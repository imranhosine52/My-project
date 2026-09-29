package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// =============================================================================
// 📥 ১. ইনবক্স কনভারসেশন মডেল (Server Spec 7 - VPS 2)
// =============================================================================
@JsonClass(generateAdapter = true)
data class InboxConversationsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "conversations") val conversations: List<DirectConversationItem> = emptyList(),
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class DirectConversationItem(
    @Json(name = "conversation_id") val conversationId: String = "",
    @Json(name = "other_user_id") val otherUserId: String = "",
    @Json(name = "other_user_name") val otherUserName: String = "User",
    @Json(name = "other_user_avatar") val otherUserAvatar: String? = null,
    @Json(name = "last_message") val lastMessage: String = "",
    @Json(name = "last_message_time") val lastMessageTime: String = "Just now",
    @Json(name = "unread_count") val unreadCount: Int = 0,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "is_system_notification") val isSystemNotification: Boolean = false,
    @Json(name = "activity_type") val activityType: String? = null // "follow", "comment", "mention", "chat"
)

// =============================================================================
// 💬 ২. ডিরেক্ট চ্যাট মেসেজ মডেল (Server Spec 7 - REST)
// =============================================================================
@JsonClass(generateAdapter = true)
data class ChatMessagesResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "conversation_id") val conversationId: String = "",
    @Json(name = "messages") val messages: List<DirectChatMessageDto> = emptyList(),
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class DirectChatMessageDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "conversation_id") val conversationId: String = "",
    @Json(name = "sender_id") val senderId: String = "",
    @Json(name = "sender_name") val senderName: String = "",
    @Json(name = "sender_avatar") val senderAvatar: String? = null,
    @Json(name = "text") val text: String = "",
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "image_urls") val imageUrls: List<String> = emptyList(),
    @Json(name = "video_url") val videoUrl: String? = null,
    @Json(name = "audio_url") val audioUrl: String? = null,
    @Json(name = "media_duration_sec") val mediaDurationSec: Long = 0L,
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "time_formatted") val timeFormatted: String? = "Just now",
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

// =============================================================================
// ⚡ ৩. WEBSOCKET রিয়েল-টাইম ফ্রেম মডেল (Server Spec 7 - wss://)
// =============================================================================
@JsonClass(generateAdapter = true)
data class WebSocketChatFrame(
    @Json(name = "type") val type: String = "message", // "message", "typing", "read", "presence"
    @Json(name = "conversation_id") val conversationId: String? = null,
    @Json(name = "sender_id") val senderId: String? = null,
    @Json(name = "recipient_id") val recipientId: String? = null,
    @Json(name = "sender_name") val senderName: String? = null,
    @Json(name = "sender_avatar") val senderAvatar: String? = null,
    @Json(name = "text") val text: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "image_urls") val imageUrls: List<String>? = null,
    @Json(name = "video_url") val videoUrl: String? = null,
    @Json(name = "audio_url") val audioUrl: String? = null,
    @Json(name = "media_duration_sec") val mediaDurationSec: Long? = null,
    @Json(name = "action") val action: String? = null, // "typing", "recording", "idle"
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

// =============================================================================
// 🔔 ৪. ইনবক্স অ্যাক্টিভিটি মডেল
// =============================================================================
@JsonClass(generateAdapter = true)
data class InboxActivityItem(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val iconType: String = "follow", // "follow", "comment", "system", "mention"
    val count: Int = 0,
    val timeAgo: String = "Recent",
    val avatarUrl: String? = null
)
