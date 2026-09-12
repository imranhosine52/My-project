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
    val videoUrl: String? = null,    // 🎬 ভিডিও লিংক (সর্বোচ্চ ৫০ এমবি)
    val audioUrl: String? = null,    // 🎙️ ভয়েস মেসেজ লিংক
    val mediaDurationSec: Int = 0,   // অডিও/ভিডিওর দৈর্ঘ্য (সেকেন্ডে)
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    @ServerTimestamp
    val timestamp: Date? = null
)
