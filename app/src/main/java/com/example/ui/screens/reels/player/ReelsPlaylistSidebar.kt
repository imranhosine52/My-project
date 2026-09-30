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
 * 🔲 আল্ট্রা-স্লিম 9:16 ভিডিও প্লেলিস্ট সাইডবার:
 * - চওড়া কমিয়ে ৫৬ ডিপি করা হয়েছে
 * - থাম্বনেলগুলো খাঁটি 9:16 অনুপাতে লম্বা
 * - রানিং ভিডিওতে অনেক চিকন (1.2dp) সাদা লাইন ও প্লে/পজ আইকন
 * - চাইনিজের বদলে ইংরেজি "Videos" লেখা
 */
@Composable
fun ReelsPlaylistSidebar(
    isOpen: Boolean,
    currentReel: UserReelDto,
    creatorReels: List<UserReelDto>,
    isPlaying: Boolean,
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
                .width(56.dp) // 🎯 সাইডবার আরও চিকন করা হলো
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.92f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 4.dp)
                // ডানে ড্র্যাগ করলে স্মুথলি বন্ধ হবে
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (delta > 15) {
                            onCloseSidebar()
                        }
                    },
                    orientation = Orientation.Horizontal
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =========================================================================
            // ১. ওপরে ইংরেজি হেডার (চাইনিজ লেখার বদলে "Videos" ও সংখ্যা)
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, top = 2.dp)
            ) {
                Text(
                    text = "Videos",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatVideosCount(creatorReels.size.toLong().coerceAtLeast(1L)),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // =========================================================================
            // ২. খাঁটি 9:16 সাইজের উল্লম্ব থাম্বনেল স্ট্রিপ
            // =========================================================================
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(
                    items = creatorReels,
                    key = { index, r -> "sidebar_item_${r.id}_$index" }
                ) { _, reelItem ->
                    val isCurrentPlaying = (reelItem.id == currentReel.id)

                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(9f / 16f) // 🎯 খাঁটি 9:16 টিকটক থাম্বনেল অনুপাত
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF141722))
                            .border(
                                width = if (isCurrentPlaying) 1.2.dp else 0.4.dp, // 🎯 সাদা লাইনটি অনেক চিকন (1.2dp) করা হলো
                                color = if (isCurrentPlaying) Color.White else Color(0xFF263346),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                runCatching {
                                    if (isCurrentPlaying) {
                                        onTogglePlayPause()
                                    } else {
                                        onSelectReel(reelItem)
                                    }
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

                        // রানিং ভিডিওতে সাদা প্লে/পজ আইকন
                        if (isCurrentPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.32f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatVideosCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}
