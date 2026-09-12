package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ChatMessage(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "Fan",
    val senderEmail: String? = null,
    val senderAvatar: String? = null,
    val isVip: Boolean = false,
    val isOwner: Boolean = false,
    val text: String = "",
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val mediaDurationSec: Long = 0L,
    val viewsCount: Long = 1L,
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    val isRead: Boolean = false,
    val readBy: List<String> = emptyList(),
    val isPinned: Boolean = false,
    @ServerTimestamp
    val timestamp: Date? = null
)

/**
 * 👥 গ্রুপের মেম্বার ইনফো মডেল
 */
data class GroupMemberInfo(
    val userId: String = "",
    val userName: String = "Member",
    val userAvatar: String? = null,
    val userEmail: String? = null,
    val isOwner: Boolean = false,
    val isVip: Boolean = false,
    val joinedAt: Long = 0L,
    val lastActive: Long = 0L
)

/**
 * 📌 পিন করা মেসেজের মডেল
 */
data class PinnedMessageInfo(
    val messageId: String = "",
    val text: String = "",
    val senderName: String = "",
    val pinnedBy: String = "",
    val pinnedAt: Long = 0L
)

/**
 * 🚫 ব্লক করা ইউজারের মডেল
 */
data class BlockedUserInfo(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String? = null,
    val blockedAt: Long = 0L
)
