package com.example.ui.screens.reels

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto

private val HeartPink = Color(0xFFFF2A4B)
private val ActionGreen = Color(0xFF00E676)
private val QualityCyan = Color(0xFF00E5FF)

@Composable
fun ReelsActionColumn(
    reel: UserReelDto,
    currentQuality: ReelVideoQuality,
    onAvatarClick: () -> Unit,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onQualityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // লাইক বাউন্স অ্যানিমেশন স্কেল
    val likeScale = remember { Animatable(1f) }

    LaunchedEffect(reel.isLiked) {
        if (reel.isLiked) {
            likeScale.animateTo(
                targetValue = 1.35f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
            likeScale.animateTo(1f, animationSpec = tween(120))
        }
    }

    Column(
        modifier = modifier.navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 👤 ১. ক্রিয়েটর অবতার + ফলো বাটন
        // =========================================================================
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .background(Color(0xFF1E2838))
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=00E676&color=000&bold=true")
                        .crossfade(true)
                        .build(),
                    contentDescription = reel.pageName,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            // যদি ফলো করা না থাকে তাহলে ছোট লাল/সবুজ '+' বাটন দেখাবে
            if (!reel.isFollowing) {
                Box(
                    modifier = Modifier
                        .offset(y = 5.dp)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(HeartPink)
                        .border(1.2.dp, Color.Black, CircleShape)
                        .clickable { onFollowClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Follow",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // =========================================================================
        // ❤️ ২. লাইক বাটন ও অ্যানিমেটেড কাউন্টার
        // =========================================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .scale(likeScale.value)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onLikeClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (reel.isLiked) HeartPink else Color.White,
                    modifier = Modifier.size(32.dp)
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
        // ↗️ ৩. শেয়ার বাটন ও শেয়ার কাউন্টার
        // =========================================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onShareClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = if (reel.sharesCount > 0) reel.sharesCount.toString() else "Share",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // =========================================================================
        // 🎛️ ৪. মাল্টি-কোয়ালিটি ভিডিও সুইচিং বাটন (720P / 480P / 360P)
        // =========================================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.clickable { onQualityClick() }
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.2.dp, QualityCyan.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.HighQuality,
                    contentDescription = "Change Quality",
                    tint = QualityCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = when (currentQuality) {
                    ReelVideoQuality.QUALITY_720P -> "720P"
                    ReelVideoQuality.QUALITY_480P -> "480P"
                    ReelVideoQuality.QUALITY_360P -> "360P"
                },
                color = QualityCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
