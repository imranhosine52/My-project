package com.example.ui.screens.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ShortsCommentsSheet(
    comments: List<Any>,
    totalCommentsCount: Int,
    isLoading: Boolean,
    currentUserName: String,
    onDismiss: () -> Unit,
    onAddComment: (commentText: String, parentId: String?) -> Unit,
    onLikeComment: (commentId: String) -> Unit,
    onShareComment: (commentId: String) -> Unit
) {
    // নির্বাচিত কমেন্টের থ্রেড (৩ নম্বর ছবির মতো ফুল স্ক্রিন ভিউয়ের জন্য)
    var activeThreadComment by remember { mutableStateOf<Any?>(null) }
    var inputText by remember { mutableStateOf("") }

    val isFullScreen = activeThreadComment != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullScreen) Color.Black else Color.Black.copy(alpha = 0.40f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isFullScreen) onDismiss()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // 🎯 ডিফল্টভাবে ২ নম্বর ছবির নীল দাগ পর্যন্ত (৪৮%) এবং থ্রেডে ঢুকলে ফুল স্ক্রিন (১০০%)
                .fillMaxHeight(if (isFullScreen) 1f else 0.48f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xFF12151D)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // =============================================================
                // 🔝 হেডার
                // =============================================================
                if (isFullScreen) {
                    // ৩ নম্বর ছবির টপ বার: [<] এবং [:]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { activeThreadComment = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                        }
                    }
                } else {
                    // ২ নম্বর ছবির হেডার: 💬 Comments [count]             (X)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "💬", fontSize = 16.sp)
                            Text(
                                text = "Comments",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF232836))
                                    .padding(horizontal = 7.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = totalCommentsCount.toString(),
                                    color = Color(0xFF9099A8),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF232836))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFB0B7C3), modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1E232E), thickness = 0.8.dp)
                }

                // =============================================================
                // 💬 কমেন্ট লিস্ট অথবা ৩ নম্বর ছবির সিঙ্গেল কমেন্ট থ্রেড
                // =============================================================
                Box(modifier = Modifier.weight(1f)) {
                    if (isFullScreen) {
                        // ৩ নম্বর ছবির ফুল-স্ক্রিন রিপ্লাই পেজ
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            // প্যারেন্ট কমেন্ট কার্ড
                            CommentRowItem(
                                comment = activeThreadComment!!,
                                onLike = onLikeComment,
                                onReplyClick = {},
                                onShare = onShareComment
                            )

                            HorizontalDivider(color = Color(0xFF1E232E), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 12.dp))

                            Text(
                                text = "0 Comments",
                                color = Color(0xFF8E95A5),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // ৩ নম্বর ছবির মতো "No comments yet" সেন্টারে
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("💬", fontSize = 32.sp)
                                    Text(
                                        text = "No comments yet",
                                        color = Color(0xFF6B7280),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // ৪ নম্বর ছবির মতো মূল কমেন্ট লিস্ট
                        if (comments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No comments yet. Be the first to comment!", color = Color(0xFF8E95A5), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(comments) { comment ->
                                    CommentRowItem(
                                        comment = comment,
                                        onLike = onLikeComment,
                                        onReplyClick = {
                                            // 🎯 কমেন্টে ক্লিক করলে ৩ নম্বর ছবির মতো ফুল স্ক্রিন হবে
                                            activeThreadComment = comment
                                        },
                                        onShare = onShareComment
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // ✍️ নিচের ইনপুট বার (২ ও ৩ নম্বর ছবির ডিজাইন)
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12151D))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2B3342)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUserName.take(2).uppercase(),
                            color = Color(0xFFFFC107),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Add a comment...", color = Color(0xFF6B7280), fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1A1F2B),
                            unfocusedContainerColor = Color(0xFF1A1F2B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val parentId = (activeThreadComment as? com.example.data.model.CommentDto)?.id
                                onAddComment(inputText.trim(), parentId)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFC107))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// 🎯 ৪ নম্বর ছবির হুবহু কমেন্ট আইটেম লেআউট
@Composable
private fun CommentRowItem(
    comment: Any,
    onLike: (String) -> Unit,
    onReplyClick: () -> Unit,
    onShare: (String) -> Unit
) {
    val dto = comment as? com.example.data.model.CommentDto
    val commentId = dto?.id ?: ""
    val userName = dto?.userName ?: "User"
    val userAvatar = dto?.userAvatar
    val text = dto?.text ?: comment.toString()
    val createdAt = dto?.createdAt ?: "Today"
    val likesCount = dto?.likesCount ?: 0
    val repliesCount = dto?.repliesCount ?: 0
    val isLiked = dto?.isLiked ?: false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReplyClick() },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!userAvatar.isNullOrBlank()) {
            AsyncImage(
                model = userAvatar,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28303F)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // নাম ও তারিখ (একই লাইনে)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = userName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = createdAt,
                    color = Color(0xFF7E8695),
                    fontSize = 11.sp
                )
            }

            // কমেন্ট টেক্সট
            Text(
                text = text,
                color = Color(0xFFCBD5E1),
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            // ৪ নম্বর ছবির মতো লাইক, কমেন্ট ও শেয়ার বাটন রো
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // ❤️ লাইক
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onLike(commentId) }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF2A4B) else Color(0xFF8E95A5),
                        modifier = Modifier.size(16.dp)
                    )
                    if (likesCount > 0) {
                        Text(text = likesCount.toString(), color = Color(0xFF8E95A5), fontSize = 11.5.sp)
                    }
                }

                // 💬 রিপ্লাই (ক্লিক করলে ৩ নম্বর ছবির মতো ফুল স্ক্রিন পেজ ওপেন হবে)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onReplyClick() }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Reply",
                        tint = Color(0xFF8E95A5),
                        modifier = Modifier.size(16.dp)
                    )
                    if (repliesCount > 0) {
                        Text(text = repliesCount.toString(), color = Color(0xFF8E95A5), fontSize = 11.5.sp)
                    }
                }

                // ↗️ শেয়ার
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = Color(0xFF8E95A5),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onShare(commentId) }
                )
            }
        }
    }
}
