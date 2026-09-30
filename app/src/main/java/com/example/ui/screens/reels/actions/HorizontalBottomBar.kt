package com.example.ui.screens.reels.actions

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserReelDto
import java.util.Locale

private val HeartRed = Color(0xFFFF2A4B)
private val TikTokRed = Color(0xFFFE2C55)

/**
 * 🎬 ১ নম্বর ছবির নিচের অংশ (শুধুমাত্র সাইডবার ওপেন থাকলে দেখাবে):
 * [উপরে]: ক্যাপশন ও হ্যাশট্যাগ
 * [নিচে বামে]: ক্রিয়েটর অ্যাভাটার + নাম + লাল Follow বাটন
 * [নিচে ডানে]: আগের ৪টি মূল আইকন পাশাপাশি (Heart, Comment, Bookmark, Share) নিচে সংখ্যা সহ
 */
@Composable
fun HorizontalBottomBar(
    reel: UserReelDto,
    annotatedCaption: AnnotatedString,
    isSaved: Boolean,
    saveCount: Int,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onToggleLike: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onFollowClick: () -> Unit,
    onOpenPageProfile: () -> Unit,
    onHashtagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val likeScale = remember { Animatable(1f) }

    LaunchedEffect(reel.isLiked) {
        if (reel.isLiked) {
            likeScale.animateTo(1.3f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            likeScale.animateTo(1f, tween(100))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ১. ক্যাপশন ও হ্যাশট্যাগ লাইন
        if (annotatedCaption.text.isNotBlank()) {
            ClickableText(
                text = annotatedCaption,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onClick = { offset ->
                    annotatedCaption.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            onHashtagClick(annotation.item)
                        }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ২. নিচের লাইন: বামে প্রোফাইল + ডানে মূল ৪টি আইকন পাশাপাশি
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 👤 বাম পাশ: অ্যাভাটার + নাম + লাল Follow বাটন
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { onOpenPageProfile() }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=222838&color=fff")
                            .crossfade(true)
                            .build(),
                        contentDescription = reel.pageName,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = reel.pageName.ifBlank { reel.displayHandle },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenPageProfile() }
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (reel.isFollowing) Color(0xFF262C38) else TikTokRed,
                    modifier = Modifier.clickable {
                        if (!isLoggedIn) onRequireLogin() else onFollowClick()
                    }
                ) {
                    Text(
                        text = if (reel.isFollowing) "Following" else "Follow",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 🔘 ডান পাশ: আপনার আগের সেই মূল ৪টি আইকনই রাখা হয়েছে (কোনো আইকন চেঞ্জ করা হয়নি)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ১. লাইক হার্ট + সংখ্যা
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (!isLoggedIn) onRequireLogin() else onToggleLike() }
                ) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (reel.isLiked) HeartRed else Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(likeScale.value)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (reel.likesCount > 0) formatCompactCount(reel.likesCount) else "0",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ২. কমেন্ট বাবল + সংখ্যা
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onCommentClick() }
                ) {
                    Icon(
                        imageVector = InstagramCommentIcon,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (reel.commentsCount > 0) formatCompactCount(reel.commentsCount.toLong()) else "0",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ৩. 🎯 আপনার আগের আসল বুকমার্ক রিবন আইকন (স্টার বাদ দেওয়া হয়েছে)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { if (!isLoggedIn) onRequireLogin() else onSaveClick() }
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else InstagramBookmarkIcon,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (saveCount > 0) formatCompactCount(saveCount.toLong()) else if (isSaved) "1" else "0",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ৪. পেপার প্লেন সেন্ড/শেয়ার আইকন
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onShareClick() }
                ) {
                    Icon(
                        imageVector = InstagramSendPlaneIcon,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (reel.sharesCount > 0) formatCompactCount(reel.sharesCount.toLong()) else "0",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// 🔣 আগের আসল কমেন্ট স্পিচ বাবল ভেক্টর
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

// 🔣 আগের আসল পেপার প্লেন সেন্ড ভেক্টর
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

// 🔣 আগের আসল আউটলাইন বুকমার্ক রিবন ভেক্টর
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

private fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
