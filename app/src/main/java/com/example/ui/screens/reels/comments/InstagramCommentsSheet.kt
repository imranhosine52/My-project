@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels.comments

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private val DeleteRed = Color(0xFFFF453A)
private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)

/**
 * 💬 ইনস্টাগ্রাম কমেন্ট বক্স:
 * (আসল নাম ও প্রোফাইল ছবি পারফেক্ট সিঙ্ক সহ)
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

    var selectedCommentForOptions by remember { mutableStateOf<ReelCommentDto?>(null) }
    var editingComment by remember { mutableStateOf<ReelCommentDto?>(null) }

    // ১. কমেন্টস ফেচ করা
    fun loadComments() {
        coroutineScope.launch {
            isLoading = true
            val result = repository.getReelComments(reelId)
            if (result.isSuccess) {
                val serverList = result.getOrDefault(emptyList()).distinctBy { it.id }
                val localAdded = commentsList.filter { it.id < 0 }
                commentsList.clear()
                commentsList.addAll(localAdded)
                commentsList.addAll(serverList.filter { srv -> localAdded.none { it.commentText == srv.commentText } })
            }
            isLoading = false
        }
    }

    LaunchedEffect(reelId) {
        loadComments()
    }

    // =========================================================================
    // ✍️ ২. কমেন্ট পোস্ট সাবমিশন হ্যান্ডলার (আসল নাম ও ছবি সরাসরি পাঠানো হচ্ছে)
    // =========================================================================
    fun executeSubmitComment() {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }

        val text = inputText.trim()
        if (text.isBlank() || isSubmitting) return

        if (editingComment != null) {
            val target = editingComment!!
            val targetId = target.id

            val topIndex = commentsList.indexOfFirst { it.id == targetId }
            if (topIndex != -1) {
                commentsList[topIndex] = commentsList[topIndex].copy(commentText = text)
            } else {
                for (i in 0 until commentsList.size) {
                    val p = commentsList[i]
                    if (p.repliesList.any { it.id == targetId }) {
                        val updatedReplies = p.repliesList.map {
                            if (it.id == targetId) it.copy(commentText = text) else it
                        }
                        commentsList[i] = p.copy(replies = updatedReplies)
                        break
                    }
                }
            }

            editingComment = null
            inputText = ""
            keyboardController?.hide()
            Toast.makeText(context, "Comment updated", Toast.LENGTH_SHORT).show()
            return
        }

        isSubmitting = true
        val parentTarget = replyingToComment
        val parentId = parentTarget?.id

        inputText = ""
        replyingToComment = null
        keyboardController?.hide()

        val tempId = -abs(System.currentTimeMillis().hashCode())
        val displayName = currentUserName.ifBlank { "User" }

        // 🎯 অপটিমিস্টিক কমেন্টে আপনার আসল নাম ও অবতার সেট করা
        val optimisticComment = ReelCommentDto(
            id = tempId,
            userId = currentUserId,
            userName = displayName,
            userAvatar = currentUserAvatar,
            commentText = text,
            rawLikesCount = 0,
            rawIsLiked = false,
            timeAgo = "Just now",
            parentId = parentId,
            replies = emptyList()
        )

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

        coroutineScope.launch {
            try {
                // 🎯 রিপোজিটরিতে আপনার আসল নাম ও ছবি পাঠানো হচ্ছে
                val res = repository.addReelComment(
                    reelId = reelId,
                    text = text,
                    parentId = parentId,
                    userName = displayName,
                    userAvatar = currentUserAvatar
                )
                if (res.isSuccess) {
                    val realComment = res.getOrNull()
                    if (realComment != null) {
                        val finalResolved = realComment.copy(
                            userName = if (realComment.userName.startsWith("User #") || realComment.userName == "User") displayName else realComment.userName,
                            userAvatar = realComment.userAvatar?.takeIf { it.isNotBlank() } ?: currentUserAvatar
                        )
                        if (parentId == null) {
                            val tempIndex = commentsList.indexOfFirst { it.id == tempId }
                            if (tempIndex != -1) {
                                commentsList[tempIndex] = finalResolved
                            }
                        } else {
                            val parentIndex = commentsList.indexOfFirst { it.id == parentId }
                            if (parentIndex != -1) {
                                val p = commentsList[parentIndex]
                                val updatedReplies = p.repliesList.map {
                                    if (it.id == tempId) finalResolved else it
                                }.distinctBy { it.id }
                                commentsList[parentIndex] = p.copy(replies = updatedReplies)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                isSubmitting = false
            }
        }
    }

    fun executeDeleteComment(target: ReelCommentDto) {
        val targetId = target.id
        val wasTopLevel = commentsList.removeAll { it.id == targetId }

        if (!wasTopLevel) {
            for (i in 0 until commentsList.size) {
                val p = commentsList[i]
                if (p.repliesList.any { it.id == targetId }) {
                    val filteredReplies = p.repliesList.filter { it.id != targetId }
                    commentsList[i] = p.copy(replies = filteredReplies)
                    break
                }
            }
        }

        selectedCommentForOptions = null
        Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F15))
            .imePadding()
    ) {
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
                        // 🎯 এখানে currentUserName এবং currentUserAvatar পাস করা হয়েছে
                        CommentRowItem(
                            comment = comment,
                            currentUserId = currentUserId,
                            currentUserName = currentUserName,
                            currentUserAvatar = currentUserAvatar,
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
                                    editingComment = null
                                    replyingToComment = target
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            },
                            onLongPressOwnComment = { target ->
                                selectedCommentForOptions = target
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = editingComment != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2838))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Editing your comment",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Edit",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            editingComment = null
                            inputText = ""
                        }
                )
            }
        }

        AnimatedVisibility(visible = replyingToComment != null && editingComment == null) {
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

        CommentInputField(
            currentUserAvatar = currentUserAvatar,
            currentUserName = currentUserName,
            targetCreatorName = targetCreatorName,
            inputText = inputText,
            onInputChange = { inputText = it },
            onSendClick = { executeSubmitComment() },
            onPickImageClick = onPickImageClick,
            onGifClick = onGifClick,
            isSubmitting = isSubmitting,
            focusRequester = focusRequester
        )
    }

    if (selectedCommentForOptions != null) {
        val target = selectedCommentForOptions!!

        ModalBottomSheet(
            onDismissRequest = { selectedCommentForOptions = null },
            containerColor = DarkCardBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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

                Text(
                    text = "Manage Comment",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF19202E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingComment = target
                            inputText = target.commentText
                            selectedCommentForOptions = null
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Edit comment",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF261214),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            executeDeleteComment(target)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = DeleteRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Delete comment",
                            color = DeleteRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
