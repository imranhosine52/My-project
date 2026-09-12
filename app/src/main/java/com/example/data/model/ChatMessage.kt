package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * 💬 গ্লোবাল কমিউনিটি চ্যাট মডেল
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
    @ServerTimestamp
    val timestamp: Date? = null
)
