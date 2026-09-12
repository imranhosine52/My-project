package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ChatMessage(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "Fan",
    val senderAvatar: String? = null,
    val isVip: Boolean = false,
    val text: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val mediaDurationSec: Long = 0L, // 👈 ফায়ারবেসের সাথে মিল রেখে Long করা হলো
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    @ServerTimestamp
    val timestamp: Date? = null
)
