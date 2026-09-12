package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * 💬 গ্লোবাল কমিউনিটি চ্যাট মডেল (আপডেটেড)
 */
data class ChatMessage(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "Fan",
    val senderAvatar: String? = null,
    val isVip: Boolean = false,
    val text: String = "",
    val imageUrl: String? = null,
    // 🎯 রিপ্লাই ফিচার সাপোর্ট
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    @ServerTimestamp
    val timestamp: Date? = null
)
