@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ReelCommentDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)
private val HeartPink = Color(0xFFFF2A4B)
private val CyanAccent = Color(0xFF00E5FF)

private val QuickEmojis = listOf("❤️", "🔥", "😂", "👏", "😍", "🙌", "💯", "✨")

@Composable
fun ReelsCommentsSheet(
    reelId: Int,
    repository: ReelsRepository,
    isLoggedIn: Boolean = true,
    currentUserName: String = "User",
    currentUserAvatar: String? = null,
    onRequireLogin: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val currentUserId = remember { repository.getCurrentUserId() }

    // 🎯 রিয়েল-টাইম কমেন্ট লিস্ট (ডুপ্লিকেশন মুক্ত)
    val commentsList = remember { mutableStateListOf<ReelCommentDto>() }
    var isLoading by remember { mutableStateOf(true) }

    var inputText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<ReelCommentDto?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun loadComments() {
        coroutineScope.launch {
            isLoading = true
            val result = repository.getReelComments(reelId)
            if (result.isSuccess) {
                val serverList = result.getOrDefault(emptyList()).distinctBy { it.id }
                commentsList.clear()
                commentsList.addAll(serverList)
            }
            isLoading = false
        }
    }

    LaunchedEffect(reelId) {
        loadComments()
    }

    // =========================================================================
    // 💬 তাৎক্ষণিক রিয়েল-টাইম কমেন্ট পোস্টিং মেথড (এক ক্লিকে একটিই পোস্ট হবে)
    // =========================================================================
    fun executePostComment() {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }

        val text = inputText.trim()
        if (text.isBlank() || isSubmitting) return

        // 🔒 তাৎক্ষণিক লক ও ইনপুট ক্লিয়ার (ডাবল ক্লিক প্রতিরোধ)
        isSubmitting = true
        val parentTarget = replyingToComment
        val parentId = parentTarget?.id

        inputText = ""
        replyingToComment = null
        keyboardController?.hide()

        // ইউনিক নেগেটিভ আইডি তৈরি
        val tempId = -abs(System.nanoTime().hashCode())
        val optimisticComment = ReelCommentDto(
            id = tempId,
            userId = currentUserId,
            userName = currentUserName,
            userAvatar = currentUserAvatar,
            commentText = text,
            rawLikesCount = 0,
            rawIsLiked = false,
            timeAgo = "Just now",
            parentId = parentId,
            replies = emptyList()
        )

        // ২. তাত্ক্ষণিকভাবে UI-তে কমেন্ট দেখানো (০ সেকেন্ড ল্যাগ)
        if (parentId == null) {
            commentsList.add(0, optimisticComment)
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        } else {
            val parentIndex = commentsList.indexOfFirst { it.id == parentId }
            if (parentIndex != -1) {
                val p = commentsList[parentIndex]
                val updatedReplies = (p.repliesList + optimisticComment).distinctBy { it.id }
                commentsList[parentIndex] = p.copy(replies = updatedReplies)
            }
        }

        // ৩. ব্যাকগ্রাউন্ডে সার্ভারে রিকোয়েস্ট পাঠানো ও আসল আইডি দিয়ে রিপ্লেস করা
        coroutineScope.launch {
            try {
                val res = repository.addReelComment(reelId, text, parentId)
                if (res.isSuccess) {
                    val realComment = res.getOrNull()
                    if (realComment != null) {
                        if (parentId == null) {
                            val tempIndex = commentsList.indexOfFirst { it.id == tempId }
                            if (tempIndex != -1) {
                                commentsList[tempIndex] = realComment
                            }
                        } else {
                            val parentIndex = commentsList.indexOfFirst { it.id == parentId }
                            if (parentIndex != -1) {
                                val p = commentsList[parentIndex]
                                val updatedReplies = p.repliesList.map {
                                    if (it.id == tempId) realComment else it
                                }.distinctBy { it.id }
                                commentsList[parentIndex] = p.copy(replies = updatedReplies)
                            }
                        }
                    }
                } else {
                    // ফেইল করলে অপটিমিস্টিক কমেন্ট রিমুভ করা
                    if (parentId == null) {
                        commentsList.removeAll { it.id == tempId }
                    } else {
                        val parentIndex = commentsList.indexOfFirst { it.id == parentId }
                        if (parentIndex != -1) {
                            val p = commentsList[parentIndex]
                            commentsList[parentIndex] = p.copy(replies = p.repliesList.filter { it.id != tempId })
                        }
                    }
                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to post comment", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ড্র্যাগ হ্যান্ডেল বার
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

            // হেডার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${commentsList.size} comments",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderStrokeColor, thickness = 0.6.dp)

            // কমেন্ট লিস্ট
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading && commentsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
                    }
                } else if (commentsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No comments yet. Be the first to comment!", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(commentsList, key = { it.id }) { comment ->
                            SingleCommentItemWithReplies(
                                comment = comment,
                                onLikeClick = {
                                    if (!isLoggedIn) {
                                        onRequireLogin()
                                    } else {
                                        coroutineScope.launch {
                                            repository.toggleCommentLike(comment.id)
                                        }
                                    }
                                },
                                onReplyClick = {
                                    if (!isLoggedIn) {
                                        onRequireLogin()
                                    } else {
                                        replyingToComment = comment
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // রিপ্লাই ব্যানার
            AnimatedVisibility(visible = replyingToComment != null) {
                replyingToComment?.let { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2434))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Replying to ${target.userName}",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Reply",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { replyingToComment = null }
                        )
                    }
                }
            }

            // কুইক ইমোজি সারি
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10131B))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(QuickEmojis) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable {
                                if (!isLoggedIn) onRequireLogin()
                                else inputText += emoji
                            }
                            .padding(horizontal = 6.dp)
                    )
                }
            }

            // বটম ইনপুট বার
            Surface(
                color = Color(0xFF10131B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(Color(0xFF1A1F2C))
                            .border(0.8.dp, BorderStrokeColor, RoundedCornerShape(21.dp))
                            .clickable {
                                if (!isLoggedIn) onRequireLogin()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = if (!isLoggedIn) "Log in to comment..." else if (replyingToComment != null) "Add reply..." else "Add comment...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                enabled = isLoggedIn,
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(CyanAccent),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { executePostComment() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                    }

                    // 🎯 সেন্ড বাটন (ডাবল ক্লিক লক ও লগইন চেক সহ)
                    IconButton(
                        onClick = { executePostComment() },
                        enabled = !isSubmitting && (inputText.isNotBlank() || !isLoggedIn),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !isSubmitting) CyanAccent else Color(0xFF1E2434))
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank() && !isSubmitting) Color.Black else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleCommentItemWithReplies(
    comment: ReelCommentDto,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit
) {
    val context = LocalContext.current
    var isLikedState by remember(comment.id, comment.isLiked) { mutableStateOf(comment.isLiked) }
    var likesCountState by remember(comment.id, comment.likesCount) { mutableIntStateOf(comment.likesCount) }
    var isRepliesExpanded by remember { mutableStateOf(false) }

    val replies = comment.repliesList

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222838)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(comment.userAvatar ?: "https://ui-avatars.com/api/?name=${comment.userName}&background=222838&color=fff")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = comment.userName,
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = comment.timeAgo ?: "Just now",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = comment.commentText,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )

                Text(
                    text = "Reply",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onReplyClick() }
                        .padding(vertical = 2.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = 4.dp)
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
                        tint = if (isLikedState) HeartPink else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (likesCountState > 0) {
                    Text(
                        text = likesCountState.toString(),
                        color = if (isLikedState) HeartPink else TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (replies.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(start = 46.dp, top = 4.dp)
                    .clickable { isRepliesExpanded = !isRepliesExpanded }
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(1.dp)
                        .background(TextMuted)
                )
                Text(
                    text = if (isRepliesExpanded) "Hide replies" else "View ${replies.size} replies",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = isRepliesExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 46.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    replies.forEach { reply ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF222838)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = reply.userAvatar ?: "https://ui-avatars.com/api/?name=${reply.userName}&background=222838&color=fff",
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
                                    Text(reply.userName, color = TextMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Text(reply.timeAgo ?: "Just now", color = Color(0xFF64748B), fontSize = 10.5.sp)
                                }
                                Text(reply.commentText, color = Color.White, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
