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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 🎙️ ক্লিন ও মডার্ন ভয়েস মেসেজ বাবল কম্পোনেন্ট
 */
@Composable
fun ChatVoicePlayerBubble(
    durationSec: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationText = String.format(Locale.US, "00:%02d", durationSec)

    Row(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // নীল রঙের প্লে/পজ বাটন
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(TelegramBlue)
                .clickable { onPlayToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // সাউন্ড ওয়েভফর্ম
        VoiceWaveformVisualizer(isPlaying = isPlaying)

        // রানিং টাইমার
        Text(
            text = durationText,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
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
    val heights = List(14) { index ->
        if (isPlaying) {
            val anim by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = (8..20).random().toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(300 + index * 30, easing = FastOutSlowInEasing),
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(20.dp)
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
