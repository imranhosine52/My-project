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
    val imageUrls: List<String> = emptyList(), // 👈 একাধিক ছবির তালিকা
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val mediaDurationSec: Long = 0L,
    val viewsCount: Long = 1L,
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    val isRead: Boolean = false,
    val readBy: List<String> = emptyList(),
    val isPinned: Boolean = false, // 👈 পিন করা কিনা
    @ServerTimestamp
    val timestamp: Date? = null
)

// 🚫 ব্লক করা ইউজারের মডেল
data class BannedUser(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String? = null,
    val bannedAt: Long = System.currentTimeMillis(),
    val bannedBy: String = "Admin"
)

// 📌 পিন করা বার্তার মডেল
data class PinnedMessageInfo(
    val messageId: String = "",
    val text: String = "",
    val senderName: String = "",
    val pinnedAt: Long = System.currentTimeMillis()
)
