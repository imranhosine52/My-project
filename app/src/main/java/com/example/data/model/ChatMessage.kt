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
    val isOwner: Boolean = false, // 👑 ওনার/অ্যাডমিন ফ্ল্যাগ
    val text: String = "",
    val imageUrl: String? = null, // সিঙ্গেল ইমেজ (ব্যাকওয়ার্ড কম্প্যাটিবিলিটি)
    val imageUrls: List<String> = emptyList(), // 🖼️ একাধিক ইমেজের তালিকা
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val mediaDurationSec: Long = 0L,
    val viewsCount: Long = 1L,
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    val isRead: Boolean = false, // সিন হয়েছে কিনা
    val readBy: List<String> = emptyList(),
    val isPinned: Boolean = false, // 📌 মেসেজ পিন্ড কিনা
    @ServerTimestamp
    val timestamp: Date? = null
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
