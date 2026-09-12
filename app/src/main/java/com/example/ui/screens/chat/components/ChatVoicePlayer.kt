package com.example.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 🎙️ ২ নম্বর ছবির হুবহু ভয়েস প্লেয়ার বাবল ডিজাইন (Blue + Slate Gray + P.D FLIX)
 */
@Composable
fun ChatVoicePlayerBubble(
    durationSec: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationText = String.format(Locale.US, "00:%02d", durationSec.coerceAtLeast(1L))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        // ২ নম্বর ছবির হুবহু সেগমেন্টেড ক্যাপসুল (বাঁয়ে নীল ও ডানে স্লেট-গ্রে)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF005CE6), // Vibrant Blue
                            Color(0xFF0066FF),
                            Color(0xFF5F6E84), // Slate Gray
                            Color(0xFF677890)
                        )
                    )
                )
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // নীল প্লে / পজ সার্কেল বাটন
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3385FF))
                    .clickable { onPlayToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // হোয়াইট সাউন্ড ওয়েভফর্ম
            VoiceWaveformVisualizer(isPlaying = isPlaying)

            // টাইমার (00:01)
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ২ নম্বর ছবির মতো P.D FLIX ব্র্যান্ডিং লোগো
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(end = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = PdFlixGreen,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "P.D FLIX",
                color = PdFlixGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * 🌊 অডিও সাউন্ড ওয়েভফর্ম ভিজ্যুয়ালাইজার
 */
@Composable
fun VoiceWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveformAnim")
    val heights = List(12) { index ->
        if (isPlaying) {
            val anim by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = (8..20).random().toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(280 + index * 25, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            anim
        } else {
            remember { (4..16).random().toFloat() }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(18.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
        }
    }
}
