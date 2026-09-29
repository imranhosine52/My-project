package com.example.ui.screens.reels.actions

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserReelDto
import java.util.Locale

private val HeartRed = Color(0xFFFF2A4B)
private val BookmarkGold = Color(0xFFFACC15)

@Composable
fun InstagramActionColumn(
    reel: UserReelDto,
    isReposted: Boolean = false,
    isSaved: Boolean = false,
    repostCount: Int = 0,
    saveCount: Int = 0, // 🎯 রিয়েল সেভ কাউন্টার
    showRepost: Boolean = true,
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
                targetValue = 1.35f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
            likeScale.animateTo(1f, animationSpec = tween(120))
        }
    }

    Column(
        modifier = modifier.navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ❤️ ১. লাইক বাটন
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                    tint = if (reel.isLiked) HeartRed else Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (reel.likesCount > 0) formatActionCount(reel.likesCount) else "Likes",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 💬 ২. কমেন্ট বাবল
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onCommentClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = InstagramCommentIcon,
                    contentDescription = "Comments",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (reel.commentsCount > 0) formatActionCount(reel.commentsCount.toLong()) else "0",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 🔁 ৩. রিপোস্ট
        if (showRepost) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onRepostClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = InstagramRepostIcon,
                        contentDescription = "Repost",
                        tint = if (isReposted) Color(0xFF00E676) else Color.White,
                        modifier = Modifier.size(29.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (repostCount > 0) formatActionCount(repostCount.toLong()) else "0",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ✈️ ৪. শেয়ার
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onShareClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = InstagramPaperPlaneIcon,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(27.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (reel.sharesCount > 0) formatActionCount(reel.sharesCount.toLong()) else "0",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 🔖 ৫. বুকমার্ক / সেভ (🎯 ডামি viewsCount বাদ দিয়ে রিয়েল কাউন্টার)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSaveClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isSaved) BookmarkGold else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (saveCount > 0) formatActionCount(saveCount.toLong()) else if (isSaved) "1" else "Save",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private val InstagramCommentIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InstagramComment",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.65f, 13.58f)
        arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 20.5f)
        arcTo(8.48f, 8.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.64f, 19.36f)
        lineTo(3.5f, 20.5f)
        lineTo(4.64f, 16.36f)
        arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 20.65f, 13.58f)
        close()
    }.build()
}

private val InstagramRepostIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InstagramRepost",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.5f, 10.5f)
        lineTo(1.5f, 7.5f)
        lineTo(4.5f, 4.5f)
        moveTo(1.5f, 7.5f)
        horizontalLineTo(17.5f)
        arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.5f, 11.5f)
        verticalLineTo(12.5f)

        moveTo(19.5f, 13.5f)
        lineTo(22.5f, 16.5f)
        lineTo(19.5f, 19.5f)
        moveTo(22.5f, 16.5f)
        horizontalLineTo(6.5f)
        arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.5f, 12.5f)
        verticalLineTo(11.5f)
    }.build()
}

private val InstagramPaperPlaneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InstagramPaperPlane",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(22f, 2f)
        lineTo(11f, 13f)
        moveTo(22f, 2f)
        lineTo(15f, 22f)
        lineTo(11f, 13f)
        lineTo(2f, 9f)
        lineTo(22f, 2f)
        close()
    }.build()
}

private fun formatActionCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%,d", count)
        else -> count.toString()
    }
}
