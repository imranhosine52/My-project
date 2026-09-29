package com.example.ui.screens.reels.comments

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReelCommentDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.launch
import kotlin.math.abs

private val TextMuted = Color(0xFF8692A6)
private val InstagramBlue = Color(0xFF0095F6)

/**
 * 💬 ১ নম্বর ছবির সম্পূর্ণ ইনস্টাগ্রাম কমেন্ট বক্স কন্টেইনার:
 * (কমেন্ট লিস্ট + নেস্টেড থ্রেড + কুইক ইমোজি স্ট্রিপ + ক্যাপসুল ইনপুট ফিল্ড)
 */
@Composable
fun InstagramCommentsSheet(
    reelId: Int,
    targetCreatorName: String,
    repository: ReelsRepository,
    isLoggedIn: Boolean = true,
    currentUserName: String = "User",
    currentUserAvatar: String? = null,
    onRequireLogin: () -> Unit = {},
    onPickImageClick: () -> Unit = {},
    onGifClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val currentUserId = remember { repository.getCurrentUserId() }

    val commentsList = remember { mutableStateListOf<ReelCommentDto>() }
    var isLoading by remember { mutableStateOf(true) }

    var inputText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<ReelCommentDto?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // ১. সার্ভার থেকে কমেন্টস ফেচ করা
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

    // ২. অপটিমিস্টিক কমেন্ট পোস্টিং (জিরো-ল্যাগ ইনস্ট্যান্ট শো)
    fun executePostComment() {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }

        val text = inputText.trim()
        if (text.isBlank() || isSubmitting) return

        isSubmitting = true
        val parentTarget = replyingToComment
        val parentId = parentTarget?.id

        inputText = ""
        replyingToComment = null
        keyboardController?.hide()

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

        // UI-তে ইনস্ট্যান্ট যুক্ত করা
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

        // ব্যাকগ্রাউন্ডে সার্ভারে সেন্ড
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
                    // নেটওয়ার্ক ফেইল হলে অপটিমিস্টিক কমেন্ট রোলব্যাক
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F15))
            .imePadding()
    ) {
        // =========================================================================
        // 📜 ৩. কমেন্ট স্ক্রোলিং লিস্ট (১ নম্বর ছবি)
        // =========================================================================
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && commentsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                }
            } else if (commentsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No comments yet. Start the conversation!", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(commentsList, key = { it.id }) { comment ->
                        CommentRowItem(
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
                            onReplyClick = { target ->
                                if (!isLoggedIn) {
                                    onRequireLogin()
                                } else {
                                    replyingToComment = target
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // =========================================================================
        // ↩️ ৪. রিপ্লাই ইন্ডিকেটর ব্যানার
        // =========================================================================
        AnimatedVisibility(visible = replyingToComment != null) {
            replyingToComment?.let { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141722))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Replying to ${target.userName}",
                        color = InstagramBlue,
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

        // =========================================================================
        // 😊 ৫. কুইক ইমোজি রো (১ নম্বর ছবি)
        // =========================================================================
        QuickEmojiRow(
            onEmojiClick = { emoji ->
                if (!isLoggedIn) {
                    onRequireLogin()
                } else {
                    inputText += emoji
                    focusRequester.requestFocus()
                }
            }
        )

        // =========================================================================
        // ⌨️ ৬. বটম ক্যাপসুল ইনপুট বার (১ নম্বর ছবি)
        // =========================================================================
        CommentInputField(
            currentUserAvatar = currentUserAvatar,
            currentUserName = currentUserName,
            targetCreatorName = targetCreatorName,
            inputText = inputText,
            onInputChange = { inputText = it },
            onSendClick = { executePostComment() },
            onPickImageClick = onPickImageClick,
            onGifClick = onGifClick,
            isSubmitting = isSubmitting,
            focusRequester = focusRequester
        )
    }
}
