@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.reels.comments

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 🔲 একক কমেন্ট আইটেম:
 * (নিজের কমেন্টে সঠিক নাম/ছবি রেজলভার ও লং-প্রেস অপশন সহ)
 */
@Composable
fun CommentRowItem(
    comment: ReelCommentDto,
    currentUserId: Int = 0,
    currentUserName: String? = null,
    currentUserAvatar: String? = null,
    onLikeClick: () -> Unit,
    onReplyClick: (ReelCommentDto) -> Unit,
    onLongPressOwnComment: (ReelCommentDto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isLikedState by remember(comment.id, comment.isLiked) { mutableStateOf(comment.isLiked) }
    var likesCountState by remember(comment.id, comment.likesCount) { mutableIntStateOf(comment.likesCount) }
    var isRepliesExpanded by remember { mutableStateOf(false) }

    val replies = comment.repliesList

    // 🎯 কমেন্টটি বর্তমান লগইন করা ইউজারের নিজের কি না যাচাই
    val isOwnComment = (currentUserId > 0 && comment.userId == currentUserId) || comment.id < 0

    // 🎯 নিজের কমেন্ট হলে লোকাল নাম ও অবতার অগ্রাধিকার পাবে
    val displayName = if (isOwnComment && !currentUserName.isNullOrBlank()) {
        currentUserName
    } else {
        comment.effectiveUserName
    }

    val displayAvatar = if (isOwnComment && !currentUserAvatar.isNullOrBlank()) {
        currentUserAvatar
    } else {
        comment.effectiveAvatar
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = {
                    if (isOwnComment) {
                        onLongPressOwnComment(comment)
                    }
                }
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
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
                        .data(displayAvatar ?: "https://ui-avatars.com/api/?name=${displayName}&background=1E2434&color=fff")
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            // টেক্সট কনটেন্ট
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // ইউজারনেম ও টাইমস্ট্যাম্প
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = comment.timeAgo ?: "Just now",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // কমেন্ট টেক্সট
                Text(
                    text = comment.effectiveText,
                    color = Color(0xFFF1F5F9),
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                )

                // অ্যাকশন বাটন: "Reply"
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
                }
            }

            // ডানে লাইক হার্ট ও কাউন্টার
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
        // ২. নেস্টেড রিপ্লাই সেকশন (যেমন: ── View 2 more replies)
        // =========================================================================
        if (replies.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(start = 48.dp, top = 8.dp)
                    .clickable { isRepliesExpanded = !isRepliesExpanded }
            ) {
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
                        val isChildOwn = (currentUserId > 0 && reply.userId == currentUserId) || reply.id < 0

                        val childName = if (isChildOwn && !currentUserName.isNullOrBlank()) {
                            currentUserName
                        } else {
                            reply.effectiveUserName
                        }

                        val childAvatar = if (isChildOwn && !currentUserAvatar.isNullOrBlank()) {
                            currentUserAvatar
                        } else {
                            reply.effectiveAvatar
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                    onLongClick = {
                                        if (isChildOwn) {
                                            onLongPressOwnComment(reply)
                                        }
                                    }
                                ),
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
                                    model = childAvatar ?: "https://ui-avatars.com/api/?name=${childName}&background=1E2434&color=fff",
                                    contentDescription = childName,
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
                                        text = childName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = reply.timeAgo ?: "Just now",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = reply.effectiveText,
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
