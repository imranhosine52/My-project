package com.example.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val TelegramVoiceBlue = Color(0xFF5288C1) // 👈 স্ক্রিনশটের নীল প্লে বাটন
private val WaveformUnplayed = Color(0xFF6E8092)
private val WaveformPlayed = Color(0xFF8CA5BE)
private val TimestampGray = Color(0xFF8E9BA8)

@Composable
fun WhatsAppVoicePlayer(
    senderName: String,
    senderAvatar: String?,
    durationSec: Long,
    timeFormatted: String,
    isMe: Boolean,
    isSeen: Boolean = false,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onForwardClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sec = durationSec.coerceAtLeast(1L)
    val durationText = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // =========================================================================
        // 🎙️ স্ক্রিনশটের হুবহু টেলিগ্রাম ভয়েস প্লেয়ার বক্স
        // =========================================================================
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ১. বাঁয়ে উজ্জ্বল নীল গোল প্লে / পজ বাটন
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TelegramVoiceBlue)
                    .clickable { onPlayToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ২. সাউন্ড ওয়েভফর্ম + টাইমার ও টাইম রো
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // টেলিগ্রাম ওয়েভফর্ম বার্স
                TelegramVoiceWaveform(isPlaying = isPlaying)

                // নিচে সময় ও মেসেজ ডেলিভারি টাইম
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // অডিও সময় (00:02)
                    Text(
                        text = if (isPlaying) "Playing..." else durationText,
                        color = TimestampGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )

                    // মেসেজ পাঠানোর সময় ও টিক মার্ক (6:56 PM ✓✓)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = timeFormatted,
                            color = TimestampGray,
                            fontSize = 10.5.sp
                        )
                        if (isMe) {
                            Text(
                                text = if (isSeen) "✓✓" else "✓",
                                color = if (isSeen) WhatsAppBlueTick else TimestampGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ↗️ স্ক্রিনশটের বাবলের ডানপাশের গোল ফরোয়ার্ড বাটন
        // =========================================================================
        if (onForwardClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222E3A))
                    .clickable { onForwardClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Forward Voice",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 🌊 স্ক্রিনশটের হুবহু টেলিগ্রাম মার্জিত সাউন্ড ওয়েভফর্ম
 */
@Composable
fun TelegramVoiceWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "telegramWaveform")
    val heights = remember {
        listOf(4, 7, 10, 15, 12, 8, 14, 18, 22, 16, 12, 19, 14, 8, 11, 15, 10, 6, 9, 13, 7, 4)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        heights.forEachIndexed { index, baseHeight ->
            val animatedHeight by if (isPlaying) {
                infiniteTransition.animateFloat(
                    initialValue = 4f,
                    targetValue = baseHeight.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(260 + (index * 25), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$index"
                )
            } else {
                remember { mutableFloatStateOf(baseHeight.toFloat()) }
            }

            Box(
                modifier = Modifier
                    .width(2.2.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isPlaying && index % 2 == 0) WaveformPlayed else WaveformUnplayed)
            )
        }
    }
}
