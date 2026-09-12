package com.example.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale

@Composable
fun WhatsAppVoicePlayer(
    senderName: String,
    senderAvatar: String?,
    durationSec: Long,
    timeFormatted: String,
    isMe: Boolean,
    isSeen: Boolean = false, // 👈 সিন ফ্ল্যাগ
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sec = durationSec.coerceAtLeast(1L)
    val durationText = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getTelegramAvatarColor(senderName)),
                contentAlignment = Alignment.Center
            ) {
                if (!senderAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = senderAvatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = senderName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color(0xFF53BDEB),
                modifier = Modifier
                    .size(15.dp)
                    .align(Alignment.BottomEnd)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "Play/Pause",
            tint = Color(0xFF8696A0),
            modifier = Modifier
                .size(34.dp)
                .clickable { onPlayToggle() }
        )

        Spacer(modifier = Modifier.width(4.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF53BDEB))
                )
                Spacer(modifier = Modifier.width(4.dp))
                WhatsAppVoiceWaveform(isPlaying = isPlaying)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = durationText,
                    color = Color(0xFF8696A0),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = timeFormatted,
                        color = Color(0xFF8696A0),
                        fontSize = 10.sp
                    )
                    // 🎯 ভয়েস মেসেজের ক্ষেত্রে টিক মার্ক
                    if (isMe) {
                        Text(
                            text = if (isSeen) "✓✓" else "✓",
                            color = if (isSeen) WhatsAppBlueTick else Color(0xFF8696A0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsAppVoiceWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveformAnim")
    val heights = List(18) { index ->
        if (isPlaying) {
            val anim by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = (6..22).random().toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(240 + index * 20, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            anim
        } else {
            remember { (4..18).random().toFloat() }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(20.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.2.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF8696A0))
            )
        }
    }
}
