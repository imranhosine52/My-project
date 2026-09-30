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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserReelDto

/**
 * 🔲 স্ক্রিনশটের ডান পাশের উল্লম্ব ভিডিও প্লেলিস্ট সাইডবার:
 * - ওপরে মোট ভিডিও কাউন্ট (যেমন: 作品 281)
 * - পেজের সব ভিডিওর থাম্বনেল
 * - যে ভিডিও চলছে সেটির ওপর সাদা বর্ডার ও প্লে (▶) আইকন
 */
@Composable
fun ReelsPlaylistSidebar(
    isOpen: Boolean,
    currentReel: UserReelDto,
    creatorReels: List<UserReelDto>,
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
        ) + fadeIn(tween(150)),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth()
        ) {
            // ড্রপ-শ্যাডো বা ব্যাকড্রপ ক্লিকে বন্ধ হওয়া
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onCloseSidebar() }
            )

            // ডানপাশের মূল থাম্বনেল স্ট্রিপ
            Column(
                modifier = Modifier
                    .width(66.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(vertical = 10.dp)
                    // ডানে ড্র্যাগ করলে স্মুথলি বন্ধ হবে
                    .draggable(
                        state = rememberDraggableState { delta ->
                            if (delta > 20) {
                                onCloseSidebar()
                            }
                        },
                        orientation = Orientation.Horizontal
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ১. ওপরে মোট ভিডিও কাউন্ট (স্ক্রিনশটের মতো: 作品 281)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Videos",
                        color = Color(0xFF8E95A5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${creatorReels.size.coerceAtLeast(1)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ২. উল্লম্ব থাম্বনেল লিস্ট
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(creatorReels, key = { _, r -> "sidebar_reel_${r.id}" }) { _, reelItem ->
                        val isCurrentPlaying = (reelItem.id == currentReel.id)

                        Box(
                            modifier = Modifier
                                .size(width = 54.dp, height = 72.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF161A24))
                                .border(
                                    width = if (isCurrentPlaying) 2.dp else 0.5.dp,
                                    color = if (isCurrentPlaying) Color.White else Color(0xFF263346),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    onSelectReel(reelItem)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // থাম্বনেল ইমেজ
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(reelItem.thumbUrl?.takeIf { it.isNotBlank() } ?: reelItem.videoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = reelItem.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // 🎯 স্ক্রিনশটের হুবহু: যে ভিডিওটি প্লে হচ্ছে তার ওপর সাদা প্লে (▶) আইকন
                            if (isCurrentPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Playing",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
