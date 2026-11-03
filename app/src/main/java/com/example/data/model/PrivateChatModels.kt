package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// =============================================================================
// 📥 ১. ইনবক্স তালিকা মডেল (Inbox List Models)
// =============================================================================

@JsonClass(generateAdapter = true)
data class InboxResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 200,
    @Json(name = "total_chats") val totalChats: Int = 0,
    @Json(name = "inbox") val inbox: List<InboxItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class InboxItemDto(
    @Json(name = "chat_room_id") val chatRoomId: String = "",
    @Json(name = "peer_id") val peerId: String = "",
    @Json(name = "account_id") val accountId: String? = null, // 👈 ৮ ডিজিটের ইউনিক অ্যাকাউন্ট আইডি
    @Json(name = "peer_name") val peerName: String = "User",
    @Json(name = "peer_avatar") val peerAvatar: String? = null,
    @Json(name = "is_vip") val isVip: Boolean = false,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "last_message") val lastMessage: String = "",
    @Json(name = "last_media_type") val lastMediaType: String = "text",
    @Json(name = "unread_count") val unreadCount: Int = 0,
    @Json(name = "is_pinned") val isPinned: Boolean = false,
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "time_formatted") val timeFormatted: String = ""
) {
    val displayAvatar: String
        get() = peerAvatar?.takeIf { it.isNotBlank() }
            ?: "https://ui-avatars.com/api/?name=${peerName}&background=1E293B&color=FACC15&bold=true"

    val isVoiceMessage: Boolean
        get() = lastMediaType.equals("audio", ignoreCase = true)

    val isImageMessage: Boolean
        get() = lastMediaType.equals("image", ignoreCase = true)

    val isVideoMessage: Boolean
        get() = lastMediaType.equals("video", ignoreCase = true)

    val isStickerMessage: Boolean
        get() = lastMediaType.equals("sticker", ignoreCase = true)
}

// =============================================================================
// 💬 ২. মেসেজ হিস্ট্রি ও ডেল্টা সিঙ্ক মডেল (Private Messages Models)
// =============================================================================

@JsonClass(generateAdapter = true)
data class PrivateMessagesResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 200,
    @Json(name = "count") val count: Int = 0,
    @Json(name = "messages") val messages: List<PrivateMessageDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PrivateMessageDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "chat_room_id") val chatRoomId: String = "",
    @Json(name = "sender_id") val senderId: String = "",
    @Json(name = "receiver_id") val receiverId: String = "",
    @Json(name = "message_text") val messageText: String = "",
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "media_type") val mediaType: String = "text",
    @Json(name = "media_duration_sec") val mediaDurationSec: Int = 0,
    @Json(name = "reply_to_id") val replyToId: String? = null,
    @Json(name = "status") val status: Int = 1, // 1 = Sent (✓), 2 = Delivered (✓✓), 3 = Seen (Blue ✓✓)
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "time_formatted") val timeFormatted: String = ""
) {
    val isSent: Boolean get() = status >= 1
    val isDelivered: Boolean get() = status >= 2
    val isSeen: Boolean get() = status == 3

    val isText: Boolean get() = mediaType.equals("text", ignoreCase = true)
    val isImage: Boolean get() = mediaType.equals("image", ignoreCase = true)
    val isAudio: Boolean get() = mediaType.equals("audio", ignoreCase = true)
    val isVideo: Boolean get() = mediaType.equals("video", ignoreCase = true)
    val isSticker: Boolean get() = mediaType.equals("sticker", ignoreCase = true)
}

// =============================================================================
// 🚀 ৩. মেসেজ পাঠানো এবং রিড রিকোয়েস্ট মডেল (Payload Requests)
// =============================================================================

@JsonClass(generateAdapter = true)
data class SendPrivateMessageRequest(
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "receiver_id") val receiverId: String,
    @Json(name = "message_text") val messageText: String = "",
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "media_type") val mediaType: String = "text",
    @Json(name = "media_duration_sec") val mediaDurationSec: Int = 0,
    @Json(name = "reply_to_id") val replyToId: String? = null
)

@JsonClass(generateAdapter = true)
data class SendPrivateMessageResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 201,
    @Json(name = "message") val message: String = "Message sent successfully!",
    @Json(name = "data") val data: PrivateMessageDto? = null
)

@JsonClass(generateAdapter = true)
data class MarkSeenRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "peer_id") val peerId: String
)

@JsonClass(generateAdapter = true)
data class GenericChatActionResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 200,
    @Json(name = "message") val message: String = ""
)

// =============================================================================
// 🔍 ৪. ৮-ডিজিট আইডি দিয়ে ইউজার সার্চ রেসপন্স মডেল (User Search Models)
// =============================================================================

@JsonClass(generateAdapter = true)
data class UserSearchResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int = 200,
    @Json(name = "message") val message: String? = null,
    @Json(name = "user") val user: SearchedUserDto? = null
)

@JsonClass(generateAdapter = true)
data class SearchedUserDto(
    @Json(name = "peer_id") val peerId: String = "",
    @Json(name = "account_id") val accountId: String = "", // 👈 ৮ ডিজিটের আইডি
    @Json(name = "peer_name") val peerName: String = "User",
    @Json(name = "peer_avatar") val peerAvatar: String? = null,
    @Json(name = "is_vip") val isVip: Boolean = false,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "bio") val bio: String? = null
) {
    val displayAvatar: String
        get() = peerAvatar?.takeIf { it.isNotBlank() }
            ?: "https://ui-avatars.com/api/?name=${peerName}&background=1E293B&color=FACC15&bold=true"
}
