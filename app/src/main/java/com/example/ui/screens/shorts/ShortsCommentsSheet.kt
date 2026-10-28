@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.shorts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.DramaApiComment
import com.example.ui.screens.CommentVideoStickerPlayer
import com.example.ui.screens.SlateVoiceCommentPill
import com.example.ui.screens.chat.components.TelegramMediaPickerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private val sharedUploadClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

// 🎯 টাইপ-সেফ ও ক্র্যাশ-প্রুফ মডেল
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

// রিফ্লেকশন মুক্ত টাইপ-সেফ কনভার্টার
fun extractCommentData(comment: Any): ParsedComment {
    if (comment is DramaApiComment) {
        return ParsedComment(
            id = comment.id,
            parentId = comment.rawParentId?.toString(),
            userName = comment.displayName,
            userAvatar = comment.avatarUrl,
            text = comment.commentText,
            createdAt = comment.displayDate,
            likesCount = comment.likesCount,
            repliesCount = comment.repliesCount,
            sharesCount = comment.sharesCount,
            isLiked = comment.isLiked,
            replies = comment.repliesList
        )
    }

    val str = comment.toString()
    return ParsedComment(
        id = "c_${comment.hashCode()}",
        parentId = null,
        userName = "DramaFlix Viewer",
        userAvatar = null,
        text = str.takeIf { !it.startsWith("DramaApiComment") } ?: "...",
        createdAt = "Recent",
        likesCount = 0,
        repliesCount = 0,
        sharesCount = 0,
        isLiked = false,
        replies = emptyList()
    )
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
    onShareComment: (commentId: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var activeThreadComment by remember { mutableStateOf<Any?>(null) }
    var inputText by remember { mutableStateOf("") }
    var showMediaPicker by remember { mutableStateOf(false) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableLongStateOf(0L) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }
    var recordingTimerJob by remember { mutableStateOf<Job?>(null) }

    var activeVoiceAudioUrl by remember { mutableStateOf<String?>(null) }
    var currentVoicePositionMs by remember { mutableLongStateOf(0L) }
    val commentAudioPlayer = remember { MediaPlayer() }

    val isFullScreen = activeThreadComment != null

    LaunchedEffect(activeVoiceAudioUrl) {
        if (activeVoiceAudioUrl != null) {
            while (isActive && activeVoiceAudioUrl != null) {
                try {
                    if (commentAudioPlayer.isPlaying) {
                        currentVoicePositionMs = commentAudioPlayer.currentPosition.toLong()
                    }
                } catch (_: Exception) {}
                delay(200L)
            }
        } else {
            currentVoicePositionMs = 0L
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { commentAudioPlayer.release() } catch (_: Exception) {}
            try {
                mediaRecorder?.release()
                tempAudioFile?.delete()
                recordingTimerJob?.cancel()
            } catch (_: Exception) {}
        }
    }

    fun startVoiceRecording() {
        try {
            val audioFile = File(context.cacheDir, "shorts_voice_${System.currentTimeMillis()}.m4a")
            tempAudioFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32000)
                setAudioSamplingRate(22050)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecordingVoice = true
            recordDurationSeconds = 0L

            recordingTimerJob?.cancel()
            recordingTimerJob = coroutineScope.launch {
                while (isActive && isRecordingVoice) {
                    delay(1000L)
                    recordDurationSeconds++
                }
            }
        } catch (e: Exception) {
            isRecordingVoice = false
            Toast.makeText(context, "Could not start voice recording", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startVoiceRecording()
        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
    }

    fun toggleVoiceRecording(parentId: String? = null) {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }

        if (!isRecordingVoice) {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) startVoiceRecording()
            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            recordingTimerJob?.cancel()
            recordingTimerJob = null
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVoice = false

            val file = tempAudioFile
            if (file != null && file.exists() && file.length() > 0) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val reqBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("type", "audio")
                            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaTypeOrNull()))
                            .build()

                        val res = sharedUploadClient.newCall(
                            Request.Builder()
                                .url("https://dramaflixbucket.imranhosine52.workers.dev")
                                .post(reqBody)
                                .build()
                        ).execute()
                        file.delete()
                        val json = JSONObject(res.body?.string() ?: "")
                        val audioUrl = json.optString("mediaUrl").ifBlank { json.optString("imageUrl") }

                        if (audioUrl.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                onAddComment(audioUrl, parentId)
                                Toast.makeText(context, "Voice comment posted! 🎙️", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to upload audio comment", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        tempAudioFile?.delete()
        isRecordingVoice = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullScreen) Color.Black else Color.Black.copy(alpha = 0.45f))
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
                .fillMaxHeight(if (isFullScreen) 1f else 0.68f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF12151D)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 🔝 Header
                if (isFullScreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { activeThreadComment = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text("Replies", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.size(48.dp))
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
                                fontSize = 15.5.sp,
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

                    ShortsCommentInputBar(
                        currentUserName = currentUserName,
                        currentUserAvatar = currentUserAvatar,
                        isLoggedIn = isLoggedIn,
                        inputText = inputText,
                        isRecordingVoice = isRecordingVoice,
                        recordDurationSeconds = recordDurationSeconds,
                        placeholder = "Add a comment...",
                        onTextChanged = { inputText = it },
                        onOpenMediaPicker = { showMediaPicker = true },
                        onStartVoiceRecord = { toggleVoiceRecording(null) },
                        onCancelVoiceRecord = { cancelVoiceRecording() },
                        onSendVoiceRecord = { toggleVoiceRecording(null) },
                        onRequireLogin = onRequireLogin,
                        onSendClick = {
                            if (!isLoggedIn) {
                                onRequireLogin()
                                return@ShortsCommentInputBar
                            }
                            if (inputText.isNotBlank()) {
                                onAddComment(inputText.trim(), null)
                                inputText = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = Color(0xFF1E232E), thickness = 0.8.dp)
                }

                // 💬 Comments List
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
                            ShortsRichCommentRowItem(
                                comment = activeThreadComment!!,
                                currentUserAvatar = currentUserAvatar,
                                currentUserName = currentUserName,
                                currentUserId = currentUserId,
                                activeAudioUrl = activeVoiceAudioUrl,
                                currentPlaybackPositionMs = currentVoicePositionMs,
                                onPlayAudio = { audioUrl ->
                                    try {
                                        if (activeVoiceAudioUrl == audioUrl && commentAudioPlayer.isPlaying) {
                                            commentAudioPlayer.pause()
                                            activeVoiceAudioUrl = null
                                            currentVoicePositionMs = 0L
                                        } else {
                                            commentAudioPlayer.reset()
                                            commentAudioPlayer.setDataSource(audioUrl)
                                            commentAudioPlayer.prepareAsync()
                                            commentAudioPlayer.setOnPreparedListener {
                                                commentAudioPlayer.start()
                                                activeVoiceAudioUrl = audioUrl
                                            }
                                            commentAudioPlayer.setOnCompletionListener {
                                                activeVoiceAudioUrl = null
                                                currentVoicePositionMs = 0L
                                            }
                                        }
                                    } catch (_: Exception) {}
                                },
                                onLike = onLikeComment,
                                onReplyClick = {},
                                onShare = { commentId, commentText ->
                                    onShareComment(commentId)
                                    try {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "$commentText\n\nShared from PlayDramaFlix")
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Comment"))
                                    } catch (_: Exception) {}
                                },
                                onDeleteComment = onDeleteComment
                            )

                            HorizontalDivider(color = Color(0xFF1E232E), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 10.dp))

                            Text(
                                text = "${threadReplies.size} Replies",
                                color = Color(0xFF8E95A5),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                            ) {
                                items(threadReplies) { reply ->
                                    ShortsRichCommentRowItem(
                                        comment = reply,
                                        currentUserAvatar = currentUserAvatar,
                                        currentUserName = currentUserName,
                                        currentUserId = currentUserId,
                                        activeAudioUrl = activeVoiceAudioUrl,
                                        currentPlaybackPositionMs = currentVoicePositionMs,
                                        onPlayAudio = { audioUrl ->
                                            try {
                                                if (activeVoiceAudioUrl == audioUrl && commentAudioPlayer.isPlaying) {
                                                    commentAudioPlayer.pause()
                                                    activeVoiceAudioUrl = null
                                                    currentVoicePositionMs = 0L
                                                } else {
                                                    commentAudioPlayer.reset()
                                                    commentAudioPlayer.setDataSource(audioUrl)
                                                    commentAudioPlayer.prepareAsync()
                                                    commentAudioPlayer.setOnPreparedListener {
                                                        commentAudioPlayer.start()
                                                        activeVoiceAudioUrl = audioUrl
                                                    }
                                                    commentAudioPlayer.setOnCompletionListener {
                                                        activeVoiceAudioUrl = null
                                                        currentVoicePositionMs = 0L
                                                    }
                                                }
                                            } catch (_: Exception) {}
                                        },
                                        onLike = onLikeComment,
                                        onReplyClick = {},
                                        onShare = { commentId, commentText ->
                                            onShareComment(commentId)
                                            try {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "$commentText\n\nShared from PlayDramaFlix")
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Share Comment"))
                                            } catch (_: Exception) {}
                                        },
                                        onDeleteComment = onDeleteComment
                                    )
                                }
                            }
                        }
                    } else {
                        if (comments.isEmpty() && !isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No comments yet. Be the first to comment!", color = Color(0xFF8E95A5), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                            ) {
                                items(comments) { comment ->
                                    ShortsRichCommentRowItem(
                                        comment = comment,
                                        currentUserAvatar = currentUserAvatar,
                                        currentUserName = currentUserName,
                                        currentUserId = currentUserId,
                                        activeAudioUrl = activeVoiceAudioUrl,
                                        currentPlaybackPositionMs = currentVoicePositionMs,
                                        onPlayAudio = { audioUrl ->
                                            try {
                                                if (activeVoiceAudioUrl == audioUrl && commentAudioPlayer.isPlaying) {
                                                    commentAudioPlayer.pause()
                                                    activeVoiceAudioUrl = null
                                                    currentVoicePositionMs = 0L
                                                } else {
                                                    commentAudioPlayer.reset()
                                                    commentAudioPlayer.setDataSource(audioUrl)
                                                    commentAudioPlayer.prepareAsync()
                                                    commentAudioPlayer.setOnPreparedListener {
                                                        commentAudioPlayer.start()
                                                        activeVoiceAudioUrl = audioUrl
                                                    }
                                                    commentAudioPlayer.setOnCompletionListener {
                                                        activeVoiceAudioUrl = null
                                                        currentVoicePositionMs = 0L
                                                    }
                                                }
                                            } catch (_: Exception) {}
                                        },
                                        onLike = onLikeComment,
                                        onReplyClick = {
                                            activeThreadComment = comment
                                        },
                                        onShare = { commentId, commentText ->
                                            onShareComment(commentId)
                                            try {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "$commentText\n\nShared from PlayDramaFlix")
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Share Comment"))
                                            } catch (_: Exception) {}
                                        },
                                        onDeleteComment = onDeleteComment
                                    )
                                }
                            }
                        }
                    }
                }

                // Media Picker (Sticker / GIF)
                if (showMediaPicker) {
                    TelegramMediaPickerSheet(
                        onSendSticker = { stickerUrl ->
                            showMediaPicker = false
                            val parentId = activeThreadComment?.let { extractCommentData(it).id }
                            onAddComment(stickerUrl, parentId)
                        },
                        onSendGif = { gifUrl ->
                            showMediaPicker = false
                            val parentId = activeThreadComment?.let { extractCommentData(it).id }
                            onAddComment(gifUrl, parentId)
                        },
                        onSelectEmoji = { emoji ->
                            inputText += emoji
                        },
                        onClose = { showMediaPicker = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isFullScreen && !showMediaPicker) {
                    ShortsCommentInputBar(
                        currentUserName = currentUserName,
                        currentUserAvatar = currentUserAvatar,
                        isLoggedIn = isLoggedIn,
                        inputText = inputText,
                        isRecordingVoice = isRecordingVoice,
                        recordDurationSeconds = recordDurationSeconds,
                        placeholder = "Add a reply...",
                        onTextChanged = { inputText = it },
                        onOpenMediaPicker = { showMediaPicker = true },
                        onStartVoiceRecord = {
                            val parentId = activeThreadComment?.let { extractCommentData(it).id }
                            toggleVoiceRecording(parentId)
                        },
                        onCancelVoiceRecord = { cancelVoiceRecording() },
                        onSendVoiceRecord = {
                            val parentId = activeThreadComment?.let { extractCommentData(it).id }
                            toggleVoiceRecording(parentId)
                        },
                        onRequireLogin = onRequireLogin,
                        onSendClick = {
                            if (!isLoggedIn) {
                                onRequireLogin()
                                return@ShortsCommentInputBar
                            }
                            if (inputText.isNotBlank()) {
                                val parentId = activeThreadComment?.let { extractCommentData(it).id }
                                onAddComment(inputText.trim(), parentId)
                                inputText = ""
                                keyboardController?.hide()
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

@Composable
private fun ShortsCommentInputBar(
    currentUserName: String,
    currentUserAvatar: String? = null,
    isLoggedIn: Boolean = true,
    inputText: String,
    isRecordingVoice: Boolean = false,
    recordDurationSeconds: Long = 0L,
    placeholder: String = "Add a comment...",
    onTextChanged: (String) -> Unit,
    onOpenMediaPicker: () -> Unit = {},
    onStartVoiceRecord: () -> Unit = {},
    onCancelVoiceRecord: () -> Unit = {},
    onSendVoiceRecord: () -> Unit = {},
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
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

        if (isRecordingVoice) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E2834))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF2A4B)))
                    Text("Recording: ${recordDurationSeconds}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onCancelVoiceRecord() }
                    )
                    Text(
                        text = "Send",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSendVoiceRecord() }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1F2B))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                    contentDescription = "Emojis & Stickers",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            if (!isLoggedIn) onRequireLogin() else onOpenMediaPicker()
                        }
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (isLoggedIn) placeholder else "Log in to comment...",
                            color = if (isLoggedIn) Color(0xFF6B7280) else Color(0xFFFFC107),
                            fontSize = 13.sp
                        )
                    }
                    if (isLoggedIn) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = onTextChanged,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color(0xFFFFC107)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    if (!isLoggedIn) onRequireLogin()
                    else if (inputText.isNotBlank()) onSendClick()
                    else onStartVoiceRecord()
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFC107))
            ) {
                Icon(
                    imageVector = if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                    contentDescription = if (inputText.isNotBlank()) "Send" else "Record Voice",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ShortsRichCommentRowItem(
    comment: Any,
    currentUserAvatar: String? = null,
    currentUserName: String? = null,
    currentUserId: String? = null,
    activeAudioUrl: String? = null,
    currentPlaybackPositionMs: Long = 0L,
    onPlayAudio: (String) -> Unit = {},
    onLike: (String) -> Unit,
    onReplyClick: () -> Unit,
    onShare: (commentId: String, commentText: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val parsed = remember(comment) { extractCommentData(comment) }
    val text = parsed.text

    var showMenuDropdown by remember { mutableStateOf(false) }

    val isVoiceComment = text.endsWith(".m4a", true) || text.endsWith(".mp3", true) || text.contains("/audio/", true)
    val isVideoSticker = (text.endsWith(".mp4", true) || text.endsWith(".webm", true) || text.contains("vid_", true)) && !isVoiceComment
    val isImageSticker = (text.contains("tenor.com", true) || text.contains("giphy.com", true) ||
            text.contains("/stickers/", true) || text.endsWith(".webp", true) || text.endsWith(".gif", true) || text.endsWith(".png", true)) && !isVoiceComment && !isVideoSticker

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
            } else null
        }
    }

    var isLikedState by remember(parsed.id, parsed.isLiked) { mutableStateOf(parsed.isLiked) }
    var likesCountState by remember(parsed.id, parsed.likesCount) { mutableIntStateOf(parsed.likesCount) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
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
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
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

                if (isVideoSticker) {
                    key(parsed.id, text) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            CommentVideoStickerPlayer(videoUrl = text, modifier = Modifier.fillMaxSize())
                        }
                    }
                } else if (isImageSticker) {
                    key(parsed.id, text) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(text).crossfade(true).build(),
                                contentDescription = "Sticker",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                } else if (isVoiceComment) {
                    val isPlaying = (activeAudioUrl == text)
                    SlateVoiceCommentPill(
                        audioUrl = text,
                        isPlaying = isPlaying,
                        currentPlaybackPositionMs = currentPlaybackPositionMs,
                        onPlayToggle = { onPlayAudio(text) },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = text,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                isLikedState = !isLikedState
                                if (isLikedState) likesCountState += 1 else likesCountState = (likesCountState - 1).coerceAtLeast(0)
                                onLike(parsed.id)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isLikedState) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLikedState) Color(0xFFFF2A4B) else Color(0xFF8E95A5),
                            modifier = Modifier.size(16.dp)
                        )
                        if (likesCountState > 0) {
                            Text(
                                text = likesCountState.toString(),
                                color = if (isLikedState) Color(0xFFFF2A4B) else Color(0xFF8E95A5),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onReplyClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Reply",
                            tint = Color(0xFF8E95A5),
                            modifier = Modifier.size(16.dp)
                        )
                        if (parsed.repliesCount > 0) {
                            Text(text = parsed.repliesCount.toString(), color = Color(0xFF8E95A5), fontSize = 11.5.sp)
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenuDropdown = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color(0xFF8E95A5),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false },
                            modifier = Modifier
                                .background(Color(0xFF1E2834))
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    showMenuDropdown = false
                                    onShare(parsed.id, parsed.text)
                                }
                            )

                            if (isMe) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        onDeleteComment(parsed.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1B202A), thickness = 0.7.dp)
    }
}
