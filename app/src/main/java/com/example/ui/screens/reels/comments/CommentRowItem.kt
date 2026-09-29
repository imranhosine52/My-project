package com.example.ui.screens.reels.comments

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.example.data.model.ReelCommentDto

private val TextMuted = Color(0xFF8692A6)
private val HeartRed = Color(0xFFFF2A4B)

/**
 * 🔲 ১ নম্বর ছবির হুবহু একক কমেন্ট আইটেম:
 * (ইউজার অ্যাভাটার + ইউজারনেম + টাইম '5w' + টেক্সট + "Reply" + "See translation" + ডানে লাইক ও কাউন্টার + নেস্টেড থ্রেড)
 */
@Composable
fun CommentRowItem(
    comment: ReelCommentDto,
    onLikeClick: () -> Unit,
    onReplyClick: (ReelCommentDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isLikedState by remember(comment.id, comment.isLiked) { mutableStateOf(comment.isLiked) }
    var likesCountState by remember(comment.id, comment.likesCount) { mutableIntStateOf(comment.likesCount) }
    var isRepliesExpanded by remember { mutableStateOf(false) }
    var isTranslated by remember { mutableStateOf(false) }

    val replies = comment.repliesList

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // =========================================================================
        // ১. মূল কমেন্ট রো (অ্যাভাটার + নাম/টাইম + কমেন্ট টেক্সট + ডানে লাইক হার্ট)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ইউজারের গোল অ্যাভাটার
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2434)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(comment.userAvatar ?: "https://ui-avatars.com/api/?name=${comment.userName}&background=1E2434&color=fff")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            // টেক্সট কনটেন্ট
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // ইউজারনেম ও টাইমস্ট্যাম্প (যেমন: evdokiya_kurch  5w)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = comment.userName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = comment.timeAgo ?: "5w",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // কমেন্ট টেক্সট
                Text(
                    text = if (isTranslated) "${comment.commentText} (Translated)" else comment.commentText,
                    color = Color(0xFFF1F5F9),
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                )

                // অ্যাকশন বাটন: "Reply" এবং "See translation" (১ নম্বর ছবি)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Reply",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onReplyClick(comment) }
                    )

                    Text(
                        text = if (isTranslated) "See original" else "See translation",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { isTranslated = !isTranslated }
                    )
                }
            }

            // ডানে লাইক হার্ট ও কাউন্টার (১ নম্বর ছবি)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            ) {
                IconButton(
                    onClick = {
                        isLikedState = !isLikedState
                        likesCountState += if (isLikedState) 1 else -1
                        onLikeClick()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isLikedState) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLikedState) HeartRed else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (likesCountState > 0) {
                    Text(
                        text = likesCountState.toString(),
                        color = if (isLikedState) HeartRed else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // =========================================================================
        // ২. নেস্টেড রিপ্লাই সেকশন (যেমন: ── View 4 more replies)
        // =========================================================================
        if (replies.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(start = 48.dp, top = 8.dp)
                    .clickable { isRepliesExpanded = !isRepliesExpanded }
            ) {
                // ১ নম্বর ছবির মতো হরাইজন্টাল ড্যাশ লাইন
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(TextMuted.copy(alpha = 0.6f))
                )
                Text(
                    text = if (isRepliesExpanded) "Hide replies" else "View ${replies.size} more replies",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // চাইল্ড রিপ্লাইগুলো এক্সপ্যান্ড হওয়া
            AnimatedVisibility(visible = isRepliesExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    replies.forEach { reply ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2434)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = reply.userAvatar ?: "https://ui-avatars.com/api/?name=${reply.userName}&background=1E2434&color=fff",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = reply.userName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = reply.timeAgo ?: "1w",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = reply.commentText,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
