package com.example.data.model

import com.squareup.moshi.Json
import java.util.Date

/**
 * 📥 ১. ইনবক্স কনভারসেশন মডেল (1-on-1 Direct Chat)
 */
data class DirectConversationItem(
    val conversationId: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "User",
    val otherUserAvatar: String? = null,
    val lastMessage: String = "",
    val lastMessageTime: String = "Just now",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isSystemNotification: Boolean = false,
    val activityType: String? = null // "follow", "comment", "mention", "chat"
)

/**
 * 🔔 ২. ইনবক্স নোটিফিকেশন ও অ্যাক্টিভিটি মডেল
 */
data class InboxActivityItem(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val iconType: String = "follow", // "follow", "comment", "system", "mention"
    val count: Int = 0,
    val timeAgo: String = "Recent",
    val avatarUrl: String? = null
)
