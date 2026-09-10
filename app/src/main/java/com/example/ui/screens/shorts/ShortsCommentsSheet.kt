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

private data class ParsedComment(
    val id: String,
    val userName: String,
    val userAvatar: String?,
    val text: String,
    val createdAt: String,
    val likesCount: Int,
    val repliesCount: Int,
    val sharesCount: Int,
    val isLiked: Boolean
)

// 🎯 আপনার DramaApiComment-এর সঠিক ফিল্ডগুলো থেকে ডেটা বের করার ইঞ্জিন
private fun extractCommentData(comment: Any): ParsedComment {
    val clazz = comment.javaClass

    fun getVal(vararg candidateNames: String): Any? {
        for (name in candidateNames) {
            val getterName = "get" + name.replaceFirstChar { it.uppercase() }
            try {
                val method = clazz.methods.find {
                    it.name.equals(getterName, ignoreCase = true) || it.name.equals(name, ignoreCase = true)
                }
                if (method != null && method.parameterCount == 0) {
                    val result = method.invoke(comment)
                    if (result != null) return result
                }
            } catch (_: Exception) {}

            try {
                val field = clazz.declaredFields.find { it.name.equals(name, ignoreCase = true) }
                if (field != null) {
                    field.isAccessible = true
                    val result = field.get(comment)
                    if (result != null) return result
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ১. আইডি (rawId = 41.0 কে 41 হিসেবে নিবে)
    val rawIdVal = getVal("rawId", "id", "commentId", "_id")
    val id = when (rawIdVal) {
        is Number -> rawIdVal.toLong().toString()
        is String -> rawIdVal.toDoubleOrNull()?.toLong()?.toString() ?: rawIdVal
        else -> ""
    }

    // ২. ইউজার নেম ও অ্যাভাটার
    val userName = getVal("userName", "authorName", "name", "user")?.toString() ?: "User"
    val userAvatar = getVal("userAvatar", "fallbackAvatar", "avatar", "photoUrl")?.toString()

    // ৩. 🎯 আসল কমেন্ট টেক্সট (আগে commentText মিসিং থাকায় পুরো অবজেক্ট দেখাত)
    val rawText = getVal("commentText", "text", "comment", "content", "message")?.toString()
    val text = if (!rawText.isNullOrBlank() && !rawText.startsWith("DramaApiComment(")) {
        rawText
    } else {
        // সেফটি ফলব্যাক: যদি অবজেক্ট আকারে থাকে তাহলে রেজেক্স দিয়ে আসল টেক্সট বের করে নিবে
        val regex = Regex("""commentText=([^,\)]+)""")
        regex.find(comment.toString())?.groupValues?.get(1) ?: "..."
    }

    // ৪. ২ নম্বর ছবির মতো তারিখ (যেমন: 10/09)
    val rawDate = getVal("dateDisplay", "timeAgo", "createdAt", "date")?.toString() ?: "Today"
    val createdAt = if (rawDate.contains("/")) {
        val parts = rawDate.split("/")
        if (parts.size >= 2) "${parts[0]}/${parts[1]}" else rawDate
    } else rawDate

    // ৫. লাইক, রিপ্লাই ও শেয়ার কাউন্ট
    val likesCount = when (val l = getVal("rawLikesCount", "likesCount", "fallbackLikes", "likes")) {
        is Number -> l.toInt()
        is String -> l.toDoubleOrNull()?.toInt() ?: 0
        else -> 0
    }

    val repliesCount = when (val r = getVal("rawRepliesCount", "repliesCount", "replies")) {
        is Number -> r.toInt()
        is String -> r.toDoubleOrNull()?.toInt() ?: 0
        else -> 0
    }

    val sharesCount = when (val s = getVal("rawSharesCount", "sharesCount", "shares")) {
        is Number -> s.toInt()
        is String -> s.toDoubleOrNull()?.toInt() ?: 0
        else -> 0
    }

    val isLiked = (getVal("isLikedVal", "isLiked", "liked") as? Boolean) ?: false

    return ParsedComment(
        id = id,
        userName = userName,
        userAvatar = userAvatar,
        text = text,
        createdAt = createdAt,
        likesCount = likesCount,
        repliesCount = repliesCount,
        sharesCount = sharesCount,
        isLiked = isLiked
    )
}

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
                // ডিফল্টভাবে স্ক্রিনের ৪৮% (নীল দাগ পর্যন্ত)
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
                // 💬 কমেন্ট লিস্ট
                // =============================================================
                Box(modifier = Modifier.weight(1f)) {
                    if (isFullScreen) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
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
                        if (comments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No comments yet. Be the first to comment!", color = Color(0xFF8E95A5), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                            ) {
                                items(comments) { comment ->
                                    CommentRowItem(
                                        comment = comment,
                                        onLike = onLikeComment,
                                        onReplyClick = {
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
                // ✍️ নিচের ইনপুট বার
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
                                val parentId = activeThreadComment?.let { extractCommentData(it).id }
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

// 🎯 ২ নম্বর ছবির হুবহু ডিজাইন: স্পেসড অ্যাকশন বাটন এবং আলাদা রো
@Composable
private fun CommentRowItem(
    comment: Any,
    onLike: (String) -> Unit,
    onReplyClick: () -> Unit,
    onShare: (String) -> Unit
) {
    val parsed = remember(comment) { extractCommentData(comment) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onReplyClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ১. অ্যাভাটার
            if (!parsed.userAvatar.isNullOrBlank()) {
                AsyncImage(
                    model = parsed.userAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF28303F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = parsed.userName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ২. নাম, কমেন্ট টেক্সট এবং ২ নম্বর ছবির মতো অ্যাকশন বার
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // নাম ও তারিখ
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = parsed.userName,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = parsed.createdAt,
                        color = Color(0xFF7E8695),
                        fontSize = 11.5.sp
                    )
                }

                // 🎯 মূল কমেন্ট টেক্সট (ক্লিন টেক্সট)
                Text(
                    text = parsed.text,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // =============================================================
                // 🔘 ২ নম্বর ছবির মতো সমান দূরত্বে লাইক, রিপ্লাই ও শেয়ার বাটন
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f) // পুরো লাইনে ছড়িয়ে থাকবে
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // 🎯 একটি অন্যটি থেকে পারফেক্ট দূরত্বে থাকবে
                ) {
                    // ❤️ লাইক
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clickable { onLike(parsed.id) }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (parsed.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (parsed.isLiked) Color(0xFFFF2A4B) else Color(0xFF8E95A5),
                            modifier = Modifier.size(17.dp)
                        )
                        if (parsed.likesCount > 0) {
                            Text(text = parsed.likesCount.toString(), color = Color(0xFF8E95A5), fontSize = 12.sp)
                        }
                    }

                    // 💬 রিপ্লাই
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clickable { onReplyClick() }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Reply",
                            tint = Color(0xFF8E95A5),
                            modifier = Modifier.size(17.dp)
                        )
                        if (parsed.repliesCount > 0) {
                            Text(text = parsed.repliesCount.toString(), color = Color(0xFF8E95A5), fontSize = 12.sp)
                        }
                    }

                    // ↗️ শেয়ার
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clickable { onShare(parsed.id) }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = Color(0xFF8E95A5),
                            modifier = Modifier.size(17.dp)
                        )
                        if (parsed.sharesCount > 0) {
                            Text(text = parsed.sharesCount.toString(), color = Color(0xFF8E95A5), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 🎯 প্রতিটি কমেন্টের মাঝে আলাদা ডিভাইডার লাইন
        HorizontalDivider(color = Color(0xFF1B202A), thickness = 0.7.dp)
    }
}
