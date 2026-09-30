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

/**
 * 🎯 ১ নম্বর ছবির হুবহু অ্যাকশন কলাম (Repost সম্পূর্ণ বাদ দেওয়া হয়েছে):
 * [১. Heart/Like] -> [২. Comment Bubble] -> [৩. Instagram Direct Share] -> [৪. Bookmark Ribbon]
 */
@Composable
fun InstagramActionColumn(
    reel: UserReelDto,
    isSaved: Boolean = false,
    saveCount: Int = 0,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // ❤️ ১. লাইক বাটন (১ নম্বর ছবির মতো হার্ট + সংখ্যা যেমন 15.4K)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(42.dp)
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
                    modifier = Modifier.size(31.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (reel.likesCount > 0) formatActionCount(reel.likesCount) else "0",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // =========================================================================
        // 💬 ২. কমেন্ট বাবল (১ নম্বর ছবির মতো গোল স্পিচ বাবল যেমন 150)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onCommentClick, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = InstagramCommentIcon,
                    contentDescription = "Comments",
                    tint = Color.White,
                    modifier = Modifier.size(29.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (reel.commentsCount > 0) formatActionCount(reel.commentsCount.toLong()) else "0",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // =========================================================================
        // ✈️ ৩. ইনস্টাগ্রাম ডিরেক্ট পেপার প্লেন সেন্ড আইকন (১ নম্বর ছবির হুবহু যেমন 4,617)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onShareClick, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = InstagramSendPlaneIcon,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (reel.sharesCount > 0) formatActionCount(reel.sharesCount.toLong()) else "0",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // =========================================================================
        // 🔖 ৪. বুকমার্ক / সেভ রিবন আইকন (১ নম্বর ছবির হুবহু যেমন 2,692)
        // =========================================================================
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSaveClick, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else InstagramBookmarkIcon,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(29.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (saveCount > 0) formatActionCount(saveCount.toLong()) else if (isSaved) "1" else "0",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// 🔣 ১ নম্বর ছবির হুবহু কমেন্ট স্পিচ বাবল
private val InstagramCommentIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InstagramComment",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.1f,
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

// 🔣 ১ নম্বর ছবির হুবহু পেপার প্লেন সেন্ড আইকন (Instagram Direct)
private val InstagramSendPlaneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InstagramSendPlane",
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

// 🔣 ১ নম্বর ছবির হুবহু আউটলাইন বুকমার্ক রিবন
private val InstagramBookmarkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InstagramBookmark",
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
        moveTo(6f, 3f)
        horizontalLineTo(18f)
        arcTo(2f, 2f, 0f, false, true, 20f, 5f)
        verticalLineTo(21f)
        lineTo(12f, 16.5f)
        lineTo(4f, 21f)
        verticalLineTo(5f)
        arcTo(2f, 2f, 0f, false, true, 6f, 3f)
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
