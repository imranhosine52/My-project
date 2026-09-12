package com.example.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

// 🎨 ২ নম্বর ছবির হুবহু WhatsApp ও Telegram কালার থিম
val WhatsAppDarkBg = Color(0xFF0C1317)
val WhatsAppBarBg = Color(0xFF1F2C34)
val WhatsAppSentBubble = Color(0xFF005C4B)     // 👈 sent message bubble
val WhatsAppReceivedBubble = Color(0xFF202C33) // 👈 received message bubble
val WhatsAppBlueTick = Color(0xFF53BDEB)       // 👈 double cyan checkmarks
val TelegramBlue = Color(0xFF2AABEE)
val PdFlixGreen = Color(0xFF00E676)
val OwnerGold = Color(0xFFFFB300)

/**
 * 🎨 ১ নম্বর ছবির মতো নাম অনুসারে টেলিগ্রাম অবতার কালার জেনারেটর
 */
fun getTelegramAvatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFFA695E7), // Purple (JH স্টাইল)
        Color(0xFF7BC862), // Green (M স্টাইল)
        Color(0xFFE17076), // Red
        Color(0xFFFAA774), // Orange
        Color(0xFF6EC9CB), // Cyan
        Color(0xFF65AADD), // Blue
        Color(0xFFEE7AAE)  // Pink
    )
    val index = Math.abs(name.hashCode()) % colors.size
    return colors[index]
}

/**
 * 🕒 মেসেজের সময় সুন্দরভাবে ফরম্যাট করার ফাংশন
 */
fun formatMessageTime(timestamp: Date?): String {
    return if (timestamp != null) {
        SimpleDateFormat("h:mm a", Locale.US).format(timestamp)
    } else {
        "Just now"
    }
}

/**
 * 🌟 ৩-ডট লাইভ জাম্পিং টাইপিং অ্যানিমেশন
 */
@Composable
fun JumpingDotsAnimation(
    dotColor: Color = Color(0xFF00A884)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsTransition")
    
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).scale(dot1Scale).clip(CircleShape).background(dotColor))
        Box(modifier = Modifier.size(6.dp).scale(dot2Scale).clip(CircleShape).background(dotColor))
        Box(modifier = Modifier.size(6.dp).scale(dot3Scale).clip(CircleShape).background(dotColor))
    }
}
