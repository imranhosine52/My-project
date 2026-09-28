package com.example.ui.screens.reels

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserReelDto

private val HeartPink = Color(0xFFFF2A4B)
private val RepostGreen = Color(0xFF00E676)
private val BookmarkGold = Color(0xFFFACC15)

@Composable
fun ReelsActionColumn(
    reel: UserReelDto,
    isReposted: Boolean = false,
    isSaved: Boolean = false,
    repostCount: Int = 0,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onRepostClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val likeScale = remember { Animatable(1f) }

    LaunchedEffect(reel.isLiked) {
        if (reel.isLiked) {
            likeScale.animateTo(
                targetValue = 1.3f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
            likeScale.animateTo(1f, animationSpec = tween(100))
        }
    }

    Column(
        modifier = modifier.navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =========================================================================
        // ❤️ ১. লাইক বাটন
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .scale(likeScale.value)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onLikeClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (reel.isLiked) HeartPink else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = reel.formattedLikes,
                color = if (reel.isLiked) HeartPink else Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // =========================================================================
        // 💬 ২. কমেন্ট বাটন (ট্যাপে TikTok Bottom Sheet ওপেন হবে)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onCommentClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comments",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = if (reel.commentsCount > 0) reel.commentsCount.toString() else "0",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // =========================================================================
        // 🔁 ৩. রিপোস্ট বাটন (TikTok Style Green Toggle)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onRepostClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isReposted) Icons.Default.Repeat else Icons.Outlined.Repeat,
                    contentDescription = "Repost",
                    tint = if (isReposted) RepostGreen else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = if (repostCount > 0) repostCount.toString() else "Repost",
                color = if (isReposted) RepostGreen else Color.White,
                fontSize = 10.5.sp,
                fontWeight = if (isReposted) FontWeight.Bold else FontWeight.Medium
            )
        }

        // =========================================================================
        // 🔖 ৪. বুকমার্ক / সেভ বাটন (Golden-Yellow Toggle)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSaveClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isSaved) BookmarkGold else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "Save",
                color = if (isSaved) BookmarkGold else Color.White,
                fontSize = 10.5.sp,
                fontWeight = if (isSaved) FontWeight.Bold else FontWeight.Medium
            )
        }

        // =========================================================================
        // ↗️ ৫. শেয়ার বাটন (কাউন্টার ও ট্র্যাকিং সহ)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onShareClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = if (reel.sharesCount > 0) reel.sharesCount.toString() else "Share",
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
