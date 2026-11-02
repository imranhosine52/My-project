package com.example.ui.screens.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun formatTimeDisplay(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * 🖐️ প্লেয়ার জেসচার ও অ্যানিমেশন ওভারলে (ভলিউম, ব্রাইটনেস, স্ক্রাবিং, ২x স্পিড ও ডাবল ট্যাপ)
 */
@Composable
fun PlayerGesturesOverlay(
    is2xActive: Boolean,
    isHorizontalScrubbing: Boolean,
    scrubTargetPositionMs: Long,
    totalDurationMs: Long,
    showLeftDoubleTapAnim: Boolean,
    showRightDoubleTapAnim: Boolean,
    showVolumeOverlay: Boolean,
    volumeFloat: Float,
    showBrightnessOverlay: Boolean,
    brightnessLevel: Float,
    screenWidthDp: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // ১. ⚡ ২X স্পিড ইন্ডিকেটর
        if (is2xActive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "2X Speed",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            blurRadius = 6f
                        )
                    )
                )
            }
        }

        // ২. ↔️ হরাইজন্টাল স্ক্রাবিং প্রিভিউ বক্স
        if (isHorizontalScrubbing) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = formatTimeDisplay(scrubTargetPositionMs),
                        color = Color(0xFF00E5FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = formatTimeDisplay(totalDurationMs),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val scrubFraction = if (totalDurationMs > 0) {
                    (scrubTargetPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .width(135.dp)
                        .height(2.8.dp)
                        .clip(RoundedCornerShape(1.4.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(scrubFraction)
                            .background(Color(0xFF00E5FF))
                    )
                }
            }
        }

        // ৩. ⏩ ডাবল ট্যাপ স্কিপ অ্যানিমেশন (+১০s ও -১০s)
        val doubleTapOffset = screenWidthDp * 0.25f

        AnimatedVisibility(
            visible = showLeftDoubleTapAnim,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -doubleTapOffset)
        ) {
            OttDoubleTapSkipBadge(isForward = false, skipSeconds = 10)
        }

        AnimatedVisibility(
            visible = showRightDoubleTapAnim,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = doubleTapOffset)
        ) {
            OttDoubleTapSkipBadge(isForward = true, skipSeconds = 10)
        }

        // ৪. 🔊 ভলিউম স্লাইডার ওভারলে
        if (showVolumeOverlay) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
            ) {
                Icon(
                    imageVector = if (volumeFloat <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Box(
                    modifier = Modifier
                        .width(85.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(volumeFloat.coerceIn(0f, 1f))
                            .background(Color(0xFF00E5FF))
                    )
                }
            }
        }

        // ৫. ☀️ ব্রাইটনেস স্লাইডার ওভারলে
        if (showBrightnessOverlay) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Brightness",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Box(
                    modifier = Modifier
                        .width(85.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(brightnessLevel.coerceIn(0f, 1f))
                            .background(Color(0xFF00E5FF))
                    )
                }
            }
        }
    }
}
