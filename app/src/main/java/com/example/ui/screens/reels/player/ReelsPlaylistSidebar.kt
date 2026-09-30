package com.example.ui.screens.reels.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserReelDto
import java.util.Locale

/**
 * 🔲 ৪ নম্বর ছবির হুবহু প্লেলিস্ট সাইডবার:
 * - ওপরে মোট ভিডিও কাউন্ট (作品 281 / 1.7k)
 * - পেজের সব ভিডিওর থাম্বনেল
 * - যে ভিডিও চলছে সেটির ওপর সাদা বর্ডার এবং প্লে/পজ আইকন (ক্লিক করে সরাসরি প্লে/পজ)
 */
@Composable
fun ReelsPlaylistSidebar(
    isOpen: Boolean,
    currentReel: UserReelDto,
    creatorReels: List<UserReelDto>,
    isPlaying: Boolean, // 🎯 ভিডিও প্লে হচ্ছে নাকি পজ তা নির্ধারণ
    onTogglePlayPause: () -> Unit,
    onSelectReel: (UserReelDto) -> Unit,
    onCloseSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(tween(140)),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut(tween(140)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.90f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 6.dp)
                // 🎯 বাম থেকে ডানে (→) টান দিলে সাইডবার স্মুথলি বন্ধ হবে
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (delta > 18) {
                            onCloseSidebar()
                        }
                    },
                    orientation = Orientation.Horizontal
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =========================================================================
            // ১. ৪ নম্বর ছবির হুবহু ওপরে কাউন্ট (作品 281 / 1.7k)
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, top = 2.dp)
            ) {
                Text(
                    text = "作品",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatWorksCount(creatorReels.size.toLong().coerceAtLeast(1L)),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // =========================================================================
            // ২. উল্লম্ব থাম্বনেল স্ট্রিপ (৪ নম্বর ছবি)
            // =========================================================================
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                itemsIndexed(creatorReels, key = { _, r -> "sidebar_reel_${r.id}" }) { _, reelItem ->
                    val isCurrentPlaying = (reelItem.id == currentReel.id)

                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 74.dp)
                            .clip(RoundedCornerShape(3.5.dp))
                            .background(Color(0xFF141722))
                            .border(
                                width = if (isCurrentPlaying) 2.2.dp else 0.6.dp,
                                color = if (isCurrentPlaying) Color.White else Color(0xFF263346),
                                shape = RoundedCornerShape(3.5.dp)
                            )
                            .clickable {
                                if (isCurrentPlaying) {
                                    // 🎯 চলমান ভিডিও হলে থাম্বনেলে চাপ দিয়ে প্লে/পজ
                                    onTogglePlayPause()
                                } else {
                                    // 🎯 নতুন ভিডিও হলে স্যুইচ
                                    onSelectReel(reelItem)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(reelItem.thumbUrl?.takeIf { it.isNotBlank() } ?: reelItem.videoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = reelItem.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // 🎯 ৪ নম্বর ছবির হুবহু: রানিং ভিডিওতে সাদা প্লে অথবা পজ আইকন
                        if (isCurrentPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.38f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatWorksCount(count: Long): String {
    return when {
        count >= 10_000 -> String.format(Locale.US, "%.1fk", count / 1000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1000.0)
        else -> count.toString()
    }
}
