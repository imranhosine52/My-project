package com.example.ui.screens.reels.actions

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
private val StarGold = Color(0xFFFFD700)
private val TikTokRed = Color(0xFFFE2C55)

/**
 * 🎬 নতুন স্ক্রিনশটের হুবহু নিচের অংশ:
 * [উপরে]: ক্যাপশন ও হ্যাশট্যাগ
 * [নিচে বামে]: ক্রিয়েটর অ্যাভাটার + নাম + লাল Follow বাটন
 * [নিচে ডানে]: পাশাপাশি ৪টি অ্যাকশন আইকন (Like, Comment, Star/Save, Share) নিচে সংখ্যা সহ
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
        // =========================================================================
        // ১. ক্যাপশন ও হ্যাশট্যাগ লাইন (স্ক্রিনশটের মতো উপরে সাদা টেক্সট)
        // =========================================================================
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

        // =========================================================================
        // ২. নিচের মূল লাইন (বামে ক্রিয়েটর প্রোফাইল, ডানে অনুভূমিক অ্যাকশন আইকন)
        // =========================================================================
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
                // ছোট গোল অ্যাভাটার
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

                // ক্রিয়েটর নাম
                Text(
                    text = reel.pageName.ifBlank { reel.displayHandle },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenPageProfile() }
                )

                // লাল ক্যাপসুল Follow বাটন (স্ক্রিনশটের মতো)
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

            // 🔘 ডান পাশ: পাশাপাশি ৪টি অ্যাকশন আইকন ও নিচে সংখ্যা (স্ক্রিনশটের হুবহু)
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
                        text = formatCompactCount(reel.likesCount),
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
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatCompactCount(reel.commentsCount.toLong()),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ৩. স্টার / ফেভারিট (Save) + সংখ্যা
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { if (!isLoggedIn) onRequireLogin() else onSaveClick() }
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (isSaved) StarGold else Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatCompactCount(saveCount.toLong()),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ৪. শেয়ার ফরোয়ার্ড অ্যারো + সংখ্যা
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onShareClick() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatCompactCount(reel.sharesCount.toLong()),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
