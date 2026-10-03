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

// 🎨 নীল অ্যাকসেন্ট (কোনো লাল রঙ নেই)
private val ActiveCyan = Color(0xFF00E5FF)
private val SidebarDarkBg = Color(0xFF080B10)
private val ItemBorderColor = Color(0xFF1E2638)

/**
 * 🔲 আল্ট্রা-স্লিম ৪২dp ভিডিও প্লেলিস্ট সাইডবার:
 * - ভিডিও ফ্রেমের সম্পূর্ণ বাইরে ডানপাশে ডকড থাকবে।
 * - থাম্বনেলগুলো খাঁটি 9:16 অনুপাতে লম্বা।
 * - সক্রিয় ভিডিওতে চিকন সায়ান ব্লু বর্ডার ও প্লে/পজ নির্দেশক।
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
                dampingRatio = Spring.DampingRatioNoBouncy,
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
                .width(42.dp) // 🎯 ৪২dp আল্ট্রা-স্লিম প্রস্থ
                .fillMaxHeight()
                .background(SidebarDarkBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 4.dp)
                // ডানে ড্র্যাগ করলে স্মুথলি সাইডবার বন্ধ হবে
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (delta > 12) {
                            onCloseSidebar()
                        }
                    },
                    orientation = Orientation.Horizontal
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =========================================================================
            // ১. শীর্ষে হেডার: "Videos" ও সংখ্যা
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, top = 2.dp)
            ) {
                Text(
                    text = "Videos",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatVideosCount(creatorReels.size.toLong().coerceAtLeast(1L)),
                    color = Color.White,
                    fontSize = 10.5.sp,
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
                contentPadding = PaddingValues(bottom = 6.dp)
            ) {
                itemsIndexed(
                    items = creatorReels,
                    key = { index, r -> "sidebar_item_${r.id}_$index" }
                ) { _, reelItem ->
                    val isCurrentPlaying = (reelItem.id == currentReel.id)

                    Box(
                        modifier = Modifier
                            .width(36.dp) // 🎯 ৩৬dp প্রস্থ
                            .aspectRatio(9f / 16f) // 🎯 খাঁটি 9:16 টিকটক থাম্বনেল রেশিও
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF141722))
                            .border(
                                width = if (isCurrentPlaying) 1.2.dp else 0.4.dp,
                                color = if (isCurrentPlaying) ActiveCyan else ItemBorderColor, // 🎯 সায়ান ব্লু বর্ডার
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

                        // সক্রিয় ভিডিওতে প্লে/পজ নির্দেশক
                        if (isCurrentPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
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
