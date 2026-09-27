package com.example.ui.screens.player.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContentItemDto
import com.example.ui.theme.GoldVip
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlayerHeaderSection(
    content: ContentItemDto,
    shortTitle: String,
    viewsCount: Long,
    likesCount: Long,
    isLiked: Boolean,
    isInWatchlist: Boolean,
    isDescriptionExpanded: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleDescription: () -> Unit,
    onLikeClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onServerIconClick: () -> Unit,
    onSwipeDownFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // 🎯 স্মুথ ইলাস্টিক ড্র্যাগ অ্যানিমেশন স্টেট
    val pullDownOffsetY = remember { Animatable(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // টানার সময় হালকা নিচে নেমে দৃশ্যমান রেসপন্স দিবে
                translationY = pullDownOffsetY.value
            }
            // 🎯 টাইটেল ধরে টান দিলে স্মুথ ড্র্যাগ হ্যান্ডলিং
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (pullDownOffsetY.value > 30f) {
                                onSwipeDownFullscreen() // স্মুথভাবে ফুলস্ক্রিন কল
                            }
                            // বাউন্স হয়ে আগের জায়গায় মসৃণভাবে ফিরে আসবে
                            pullDownOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            pullDownOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0 || pullDownOffsetY.value > 0) {
                            change.consume()
                            coroutineScope.launch {
                                // 🎯 ইলাস্টিক রেজিস্ট্যান্স (টানলে মাখনের মতো মসৃণ মুভ হবে)
                                val newOffset = (pullDownOffsetY.value + dragAmount * 0.45f).coerceIn(0f, 50f)
                                pullDownOffsetY.snapTo(newOffset)
                            }
                        }
                    }
                )
            }
    ) {
        // ১. ড্রামা টাইটেল ও Pre / Next বাটন
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = shortTitle,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF161A23),
                    border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                    modifier = Modifier
                        .size(width = 54.dp, height = 30.dp)
                        .clickable { onPreviousClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Pre",
                            color = Color(0xFFB0B7C6),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF161A23),
                    border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                    modifier = Modifier
                        .size(width = 54.dp, height = 30.dp)
                        .clickable { onNextClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Next",
                            color = Color(0xFFB0B7C6),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ২. মেটাডাটা ও অ্যাকশন আইকন রো (ভিউ, লাইক, বুকমার্ক ও সার্ভার সুইচ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = content.releaseYear.ifBlank { "2026" }, 
                    color = Color(0xFF8E95A5), 
                    fontSize = 11.5.sp, 
                    fontWeight = FontWeight.Medium
                )
                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(13.dp))
                Text(
                    text = if (content.rating > 0) String.format(Locale.US, "%.1f", content.rating) else "8.9", 
                    color = GoldVip, 
                    fontSize = 11.5.sp, 
                    fontWeight = FontWeight.Bold
                )
                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                Text(
                    text = if (isDescriptionExpanded) "less" else "...more",
                    color = TealAccent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onToggleDescription() }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ভিউজ কাউন্টার
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Views", tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                    Text(formatCount(viewsCount), color = Color(0xFFCCD0DB), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // লাইক বাটন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onLikeClick() }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = formatCount(likesCount), 
                        color = if (isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2), 
                        fontSize = 11.5.sp, 
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // বুকমার্ক আইকন
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (isInWatchlist) TealAccent else Color(0xFFADB3C2),
                    modifier = Modifier.size(16.dp).clickable { onWatchlistClick() }
                )

                // 🎯 সার্ভার সুইচ আইকন
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF161E2E))
                        .clickable { onServerIconClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ৩. এক্সপ্যান্ডেবল ডেসক্রিপশন
        if (isDescriptionExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                    color = Color(0xFFCCD0DB),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> "$count"
        else -> "0"
    }
}
