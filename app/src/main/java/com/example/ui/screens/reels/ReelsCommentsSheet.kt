@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ReelCommentDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.launch

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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var commentsList by remember { mutableStateOf<List<ReelCommentDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var inputText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<ReelCommentDto?>(null) }
    var isPosting by remember { mutableStateOf(false) }

    // কমেন্ট ফেচ করা
    fun loadComments() {
        coroutineScope.launch {
            isLoading = true
            val result = repository.getReelComments(reelId)
            commentsList = result.getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(reelId) {
        loadComments()
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
                .fillMaxHeight(0.72f) // স্ক্রিনের ৭২% উচ্চতা
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ড্র্যাগ হ্যান্ডেল
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

            // =========================================================================
            // 🔝 ১. হেডার রো (টোটাল কমেন্ট কাউন্ট + ক্লোজ বাটন)
            // =========================================================================
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

            // =========================================================================
            // 💬 ২. স্ক্রোলযোগ্য কমেন্ট লিস্ট
            // =========================================================================
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(commentsList, key = { it.id }) { comment ->
                            SingleCommentItemWithReplies(
                                comment = comment,
                                onLikeClick = {
                                    coroutineScope.launch {
                                        repository.toggleCommentLike(comment.id)
                                    }
                                },
                                onReplyClick = {
                                    replyingToComment = comment
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            )
                        }
                    }
                }
            }

            // রিপ্লাই ব্যানার (যদি কোনো কমেন্টে রিপ্লাই সিলেক্ট করা থাকে)
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

            // কুইক ইমোজি রো
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
                            .clickable { inputText += emoji }
                            .padding(horizontal = 6.dp)
                    )
                }
            }

            // =========================================================================
            // ✍️ ৩. ফিক্সড বটম টেক্সট ইনপুট বার
            // =========================================================================
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
                                    text = if (replyingToComment != null) "Add reply..." else "Add comment...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(CyanAccent),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (inputText.isNotBlank() && !isPosting) {
                                            isPosting = true
                                            val text = inputText.trim()
                                            val pId = replyingToComment?.id
                                            coroutineScope.launch {
                                                val res = repository.addReelComment(reelId, text, pId)
                                                if (res.isSuccess) {
                                                    inputText = ""
                                                    replyingToComment = null
                                                    keyboardController?.hide()
                                                    loadComments()
                                                }
                                                isPosting = false
                                            }
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                    }

                    // সেন্ড বাটন
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isPosting) {
                                isPosting = true
                                val text = inputText.trim()
                                val pId = replyingToComment?.id
                                coroutineScope.launch {
                                    val res = repository.addReelComment(reelId, text, pId)
                                    if (res.isSuccess) {
                                        inputText = ""
                                        replyingToComment = null
                                        keyboardController?.hide()
                                        loadComments()
                                    }
                                    isPosting = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isPosting,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) CyanAccent else Color(0xFF1E2434))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.Black else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 💬 একক কমেন্ট এবং কলাপসিবল রিপ্লাই অ্যাকর্ডিয়ন
// =============================================================================
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
            // অবতার
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

            // কমেন্ট বডি
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

                // Reply বাটন
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

            // কমেন্ট লাইক বাটন
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

        // =========================================================================
        // 📂 কলাপসিবল "View replies" অ্যাকর্ডিয়ন (TikTok Style)
        // =========================================================================
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
