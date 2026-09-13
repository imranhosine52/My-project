package com.example.ui.screens.shorts

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class ParsedComment(
    val id: String,
    val parentId: String?,
    val userName: String,
    val userAvatar: String?,
    val text: String,
    val createdAt: String,
    val likesCount: Int,
    val repliesCount: Int,
    val sharesCount: Int,
    val isLiked: Boolean,
    val replies: List<Any>
)

fun extractCommentData(comment: Any): ParsedComment {
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

    val rawIdVal = getVal("rawId", "id", "commentId", "_id")
    val id = when (rawIdVal) {
        is Number -> rawIdVal.toLong().toString()
        is String -> {
            if (rawIdVal.endsWith(".0")) rawIdVal.substringBefore(".0")
            else rawIdVal
        }
        else -> ""
    }

    val rawParentIdVal = getVal("rawParentId", "parentId", "parent_id")
    val parentId = when (rawParentIdVal) {
        is Number -> rawParentIdVal.toLong().toString()
        is String -> {
            if (rawParentIdVal.endsWith(".0")) rawParentIdVal.substringBefore(".0")
            else rawParentIdVal
        }
        else -> null
    }

    val userName = getVal("userName", "authorName", "name", "user")?.toString() ?: "User"
    val userAvatar = getVal("userAvatar", "fallbackAvatar", "avatar", "photoUrl")?.toString()

    val rawText = getVal("commentText", "text", "comment", "content", "message")?.toString()
    val text = if (!rawText.isNullOrBlank() && !rawText.startsWith("DramaApiComment(")) {
        rawText
    } else {
        val regex = Regex("""commentText=([^,\)]+)""")
        regex.find(comment.toString())?.groupValues?.get(1) ?: "..."
    }

    val rawDate = getVal("dateDisplay", "timeAgo", "createdAt", "date")?.toString() ?: "Today"
    val createdAt = if (rawDate.contains("/")) {
        val parts = rawDate.split("/")
        if (parts.size >= 2) "${parts[0]}/${parts[1]}" else rawDate
    } else rawDate

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

    val repliesRaw = getVal("replies", "replyList", "subComments")
    val repliesList: List<Any> = when (repliesRaw) {
        is List<*> -> repliesRaw.filterNotNull()
        else -> emptyList()
    }

    return ParsedComment(
        id = id,
        parentId = parentId,
        userName = userName,
        userAvatar = userAvatar,
        text = text,
        createdAt = createdAt,
        likesCount = likesCount,
        repliesCount = repliesCount,
        sharesCount = sharesCount,
        isLiked = isLiked,
        replies = repliesList
    )
}

private fun launchShareIntent(context: Context, commentText: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "💬 \"$commentText\"\n\nShared from PlayDramaFlix")
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Comment"))
    } catch (_: Exception) {}
}

@Composable
fun ShortsCommentsSheet(
    comments: List<Any>,
    totalCommentsCount: Int,
    isLoading: Boolean,
    currentUserName: String,
    currentUserAvatar: String? = null,
    currentUserId: String? = null,
    isLoggedIn: Boolean = true,
    onRequireLogin: () -> Unit = {},
    onDismiss: () -> Unit,
    onAddComment: (commentText: String, parentId: String?) -> Unit,
    onLikeComment: (commentId: String) -> Unit,
    onShareComment: (commentId: String) -> Unit
) {
    val context = LocalContext.current
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
                .fillMaxHeight(if (isFullScreen) 1f else 0.48f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xFF12151D)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
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
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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

                    // হাফ স্ক্রিনের ইনপুট বার (Cloudflare R2 ছবি সহ)
                    CommentInputBar(
                        currentUserName = currentUserName,
                        currentUserAvatar = currentUserAvatar,
                        isLoggedIn = isLoggedIn,
                        inputText = inputText,
                        placeholder = "Add a comment...",
                        onTextChanged = { inputText = it },
                        onRequireLogin = onRequireLogin,
                        onSendClick = {
                            if (!isLoggedIn) {
                                onRequireLogin()
                                return@CommentInputBar
                            }
                            if (inputText.isNotBlank()) {
                                onAddComment(inputText.trim(), null)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = Color(0xFF1E232E), thickness = 0.8.dp)
                }

                // =============================================================
                // 💬 কমেন্ট লিস্ট / রিপ্লাই লিস্ট
                // =============================================================
                Box(modifier = Modifier.weight(1f)) {
                    if (isFullScreen && activeThreadComment != null) {
                        val parentParsed = remember(activeThreadComment) { extractCommentData(activeThreadComment!!) }

                        val threadReplies = remember(activeThreadComment, comments) {
                            val directReplies = parentParsed.replies
                            val matchingFromAll = comments.filter {
                                val pId = extractCommentData(it).parentId
                                !pId.isNullOrBlank() && pId == parentParsed.id
                            }
                            (directReplies + matchingFromAll).distinctBy { extractCommentData(it).id }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            CommentRowItem(
                                comment = activeThreadComment!!,
                                currentUserAvatar = currentUserAvatar,
                                currentUserName = currentUserName,
                                currentUserId = currentUserId,
                                onLike = onLikeComment,
                                onReplyClick = {},
                                onShare = { commentId, commentText ->
                                    onShareComment(commentId)
                                    launchShareIntent(context, commentText)
                                }
                            )

                            HorizontalDivider(color = Color(0xFF1E232E), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 10.dp))

                            Text(
                                text = "${threadReplies.size} Comments",
                                color = Color(0xFF8E95A5),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (threadReplies.isEmpty()) {
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
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                                ) {
                                    items(threadReplies) { reply ->
                                        CommentRowItem(
                                            comment = reply,
                                            currentUserAvatar = currentUserAvatar,
                                            currentUserName = currentUserName,
                                            currentUserId = currentUserId,
                                            onLike = onLikeComment,
                                            onReplyClick = {},
                                            onShare = { commentId, commentText ->
                                                onShareComment(commentId)
                                                launchShareIntent(context, commentText)
                                            }
                                        )
                                    }
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
                                        currentUserAvatar = currentUserAvatar,
                                        currentUserName = currentUserName,
                                        currentUserId = currentUserId,
                                        onLike = onLikeComment,
                                        onReplyClick = {
                                            activeThreadComment = comment
                                        },
                                        onShare = { commentId, commentText ->
                                            onShareComment(commentId)
                                            launchShareIntent(context, commentText)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ফুলস্ক্রিনের ইনপুট বার
                if (isFullScreen) {
                    CommentInputBar(
                        currentUserName = currentUserName,
                        currentUserAvatar = currentUserAvatar,
                        isLoggedIn = isLoggedIn,
                        inputText = inputText,
                        placeholder = "Add a reply...",
                        onTextChanged = { inputText = it },
                        onRequireLogin = onRequireLogin,
                        onSendClick = {
                            if (!isLoggedIn) {
                                onRequireLogin()
                                return@CommentInputBar
                            }
                            if (inputText.isNotBlank()) {
                                val parentId = activeThreadComment?.let { extractCommentData(it).id }
                                onAddComment(inputText.trim(), parentId)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    )
                }
            }
        }
    }
}

// 🎯 কাস্টম ইনপুট বার (R2 প্রোফাইল ফটো লোডার সহ)
@Composable
private fun CommentInputBar(
    currentUserName: String,
    currentUserAvatar: String? = null,
    isLoggedIn: Boolean = true,
    inputText: String,
    placeholder: String,
    onTextChanged: (String) -> Unit,
    onRequireLogin: () -> Unit = {},
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .background(Color(0xFF12151D))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B3342)),
            contentAlignment = Alignment.Center
        ) {
            if (!currentUserAvatar.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentUserAvatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = "My Avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = currentUserName.take(2).uppercase(),
                    color = Color(0xFFFFC107),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1F2B))
                .clickable {
                    if (!isLoggedIn) onRequireLogin()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (inputText.isEmpty()) {
                Text(
                    text = if (isLoggedIn) placeholder else "Log in to post a comment...",
                    color = if (isLoggedIn) Color(0xFF6B7280) else Color(0xFFFFC107),
                    fontSize = 13.5.sp
                )
            }
            if (isLoggedIn) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onTextChanged,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.5.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFFFFC107)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        IconButton(
            onClick = {
                if (!isLoggedIn) onRequireLogin() else onSendClick()
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

// 🎯 কমেন্ট রো আইটেম (R2 প্রায়োরিটি সহ)
@Composable
private fun CommentRowItem(
    comment: Any,
    currentUserAvatar: String? = null,
    currentUserName: String? = null,
    currentUserId: String? = null,
    onLike: (String) -> Unit,
    onReplyClick: () -> Unit,
    onShare: (commentId: String, commentText: String) -> Unit
) {
    val context = LocalContext.current
    val parsed = remember(comment) { extractCommentData(comment) }

    // 🎯 নিজের কমেন্ট কিনা চেক
    val isMe = remember(parsed.userName, currentUserName) {
        !currentUserName.isNullOrBlank() && parsed.userName.trim().equals(currentUserName.trim(), ignoreCase = true) ||
        (currentUserName != null && currentUserName.contains("Sifat", ignoreCase = true) && parsed.userName.contains("Sifat", ignoreCase = true))
    }

    val resolvedAvatar = remember(parsed.userAvatar, currentUserAvatar, isMe) {
        if (isMe && !currentUserAvatar.isNullOrBlank()) {
            currentUserAvatar
        } else {
            val serverAvatar = parsed.userAvatar
            if (!serverAvatar.isNullOrBlank() && !serverAvatar.contains("default-user")) {
                serverAvatar
            } else if (isMe && !currentUserAvatar.isNullOrBlank()) {
                currentUserAvatar
            } else {
                null
            }
        }
    }

    var isLikedState by remember(parsed.id, parsed.isLiked) { mutableStateOf(parsed.isLiked) }
    var likesCountState by remember(parsed.id, parsed.likesCount) { mutableIntStateOf(parsed.likesCount) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28303F)),
                contentAlignment = Alignment.Center
            ) {
                if (!resolvedAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedAvatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = parsed.userName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = parsed.userName.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReplyClick() }
                ) {
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

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = parsed.text,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                isLikedState = !isLikedState
                                if (isLikedState) likesCountState += 1 else likesCountState = (likesCountState - 1).coerceAtLeast(0)
                                onLike(parsed.id)
                            }
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (isLikedState) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLikedState) Color(0xFFFF2A4B) else Color(0xFF8E95A5),
                            modifier = Modifier.size(17.dp)
                        )
                        if (likesCountState > 0) {
                            Text(
                                text = likesCountState.toString(),
                                color = if (isLikedState) Color(0xFFFF2A4B) else Color(0xFF8E95A5),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onReplyClick() }
                            .padding(horizontal = 4.dp, vertical = 3.dp)
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onShare(parsed.id, parsed.text) }
                            .padding(horizontal = 4.dp, vertical = 3.dp)
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

        HorizontalDivider(color = Color(0xFF1B202A), thickness = 0.7.dp)
    }
}
