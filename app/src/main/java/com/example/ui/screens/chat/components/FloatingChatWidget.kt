@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.chat.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatMessage
import com.example.data.model.GroupMemberInfo
import com.example.util.FirebaseChatManager
import com.example.util.UserChatStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private fun formatMiniActiveActionsText(actions: List<UserChatStatus>): String {
    if (actions.isEmpty()) return ""
    val count = actions.size
    return if (count == 1) {
        val user = actions[0]
        when (user.action) {
            "recording" -> "${user.userName} is recording voice 🎙️"
            "uploading_photo" -> "${user.userName} is sending photo 📷"
            "uploading_video" -> "${user.userName} is sending video 🎬"
            else -> "${user.userName} is typing... ✍️"
        }
    } else {
        val u1 = actions[0].userName
        val others = count - 1
        "$u1 and $others others are active... ✍️"
    }
}

@Composable
fun FloatingCommunityChatWidget(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String?,
    currentUserAvatar: String?,
    isVip: Boolean,
    isLoggedIn: Boolean = true,
    onRequireLogin: () -> Unit = {},
    onOpenFullScreenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val miniListState = rememberLazyListState()

    var isExpanded by remember { mutableStateOf(false) }
    var messageInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewVideoUrl by remember { mutableStateOf<String?>(null) }

    var activePlayingAudioUrl by remember { mutableStateOf<String?>(null) }
    val audioMediaPlayer = remember { MediaPlayer() }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableLongStateOf(0L) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }
    var recordingTimerJob by remember { mutableStateOf<Job?>(null) }
    var typingStatusJob by remember { mutableStateOf<Job?>(null) }

    val isImeVisible = WindowInsets.isImeVisible

    DisposableEffect(Unit) {
        onDispose {
            try { audioMediaPlayer.release() } catch (_: Exception) {}
            try {
                mediaRecorder?.release()
                tempAudioFile?.delete()
                typingStatusJob?.cancel()
            } catch (_: Exception) {}
        }
    }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris.take(6)
            selectedVideoUri = null
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedImageUris = emptyList()
        }
    }

    fun startRecording() {
        try {
            val audioFile = File(context.cacheDir, "mini_voice_${System.currentTimeMillis()}.m4a")
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
            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleVoiceRecord() {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }

        if (!isRecordingVoice) {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) startRecording()
            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            recordingTimerJob?.cancel()
            recordingTimerJob = null
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVoice = false

            val file = tempAudioFile
            val duration = if (recordDurationSeconds < 1L) 1L else recordDurationSeconds

            if (file != null && file.exists() && file.length() > 0) {
                isSending = true
                coroutineScope.launch {
                    try {
                        FirebaseChatManager.uploadVoiceAndSendMessage(
                            audioFile = file,
                            durationSeconds = duration,
                            senderId = currentUserId,
                            senderName = currentUserName,
                            senderEmail = currentUserEmail,
                            senderAvatar = currentUserAvatar,
                            isVip = isVip,
                            replyToMessage = replyingToMessage
                        )
                        replyingToMessage = null
                    } finally {
                        isSending = false
                    }
                }
            }
        }
    }

    fun cancelVoiceRecord() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        tempAudioFile?.delete()
        isRecordingVoice = false
    }

    val messages by produceState<List<ChatMessage>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveMessagesFlow().collect { value = it }
    }

    val groupMembersList by produceState<List<GroupMemberInfo>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveGroupMembersFlow().collect { value = it }
    }

    val liveMembersAvatarMap = remember(groupMembersList, currentUserAvatar) {
        val map = mutableMapOf<String, String?>()
        groupMembersList.forEach { m ->
            if (!m.userAvatar.isNullOrBlank()) {
                map[m.userId] = m.userAvatar
                m.userEmail?.let { map[it.lowercase()] = m.userAvatar }
            }
        }
        if (!currentUserAvatar.isNullOrBlank()) {
            map[currentUserId] = currentUserAvatar
            currentUserEmail?.let { map[it.lowercase()] = currentUserAvatar }
        }
        map
    }

    val liveActiveActions by produceState<List<UserChatStatus>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveActiveActionUsersFlow(currentUserId).collect { value = it }
    }

    // 🎯 চ্যাট ওপেন করলেই সর্বশেষ মেসেজ স্ক্রোল হবে
    LaunchedEffect(isExpanded) {
        if (isExpanded && messages.isNotEmpty()) {
            delay(120L)
            miniListState.scrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(messages.size) {
        if (isExpanded && messages.isNotEmpty()) {
            miniListState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendStickerOrGif(mediaUrl: String) {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }
        if (isSending) return
        showMediaPicker = false
        val replyTarget = replyingToMessage
        replyingToMessage = null

        coroutineScope.launch {
            FirebaseChatManager.sendStickerMessage(
                mediaUrl = mediaUrl,
                senderId = currentUserId,
                senderName = currentUserName,
                senderEmail = currentUserEmail,
                senderAvatar = currentUserAvatar,
                isVip = isVip,
                replyToMessage = replyTarget
            )
        }
    }

    fun sendMediaOrTextMessage() {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }
        if (isSending) return
        val text = messageInput.trim()
        val images = selectedImageUris
        val video = selectedVideoUri
        val replyTarget = replyingToMessage

        if (text.isBlank() && images.isEmpty() && video == null) return

        messageInput = ""
        selectedImageUris = emptyList()
        selectedVideoUri = null
        replyingToMessage = null
        showMediaPicker = false
        isSending = true

        coroutineScope.launch {
            try {
                if (video != null) {
                    FirebaseChatManager.uploadVideoWithProgressAndSendMessage(
                        context = context,
                        videoUri = video,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderEmail = currentUserEmail,
                        senderAvatar = currentUserAvatar,
                        isVip = isVip,
                        captionText = text,
                        replyToMessage = replyTarget,
                        onProgress = { _, _ -> },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                } else if (images.isNotEmpty()) {
                    FirebaseChatManager.uploadMultipleImagesAndSendMessage(
                        context = context,
                        imageUris = images,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderEmail = currentUserEmail,
                        senderAvatar = currentUserAvatar,
                        isVip = isVip,
                        captionText = text,
                        replyToMessage = replyTarget,
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                } else {
                    FirebaseChatManager.sendTextMessage(
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderEmail = currentUserEmail,
                        senderAvatar = currentUserAvatar,
                        isVip = isVip,
                        text = text,
                        replyToMessage = replyTarget
                    )
                }
            } finally {
                isSending = false
                FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
            }
        }
    }

    Column(
        modifier = modifier
            .windowInsetsPadding(if (isImeVisible) WindowInsets.ime else WindowInsets.navigationBars)
            .padding(
                bottom = if (isImeVisible) 4.dp else 46.dp,
                end = 12.dp
            ),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(initialScale = 0.85f, animationSpec = tween(220)) + fadeIn(),
            exit = scaleOut(targetScale = 0.85f, animationSpec = tween(200)) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .width(335.dp)
                    .height(495.dp)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF10141D),
                border = BorderStroke(1.dp, Color(0xFF232D3F))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(Color(0xFF161E2C))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Text("DramaFlix Live Chat", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isExpanded = false
                                    onOpenFullScreenChat()
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.OpenInFull, contentDescription = "Full Chat", tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                            }

                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF202A3C), thickness = 0.6.dp)

                    LazyColumn(
                        state = miniListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(messages.takeLast(70), key = { it.id }) { msg ->
                            val isMe = msg.senderId == currentUserId ||
                                    (!currentUserEmail.isNullOrBlank() && msg.senderEmail.equals(currentUserEmail, ignoreCase = true))

                            val effectiveAvatar = if (isMe) currentUserAvatar ?: liveMembersAvatarMap[currentUserId]
                            else msg.senderAvatar ?: liveMembersAvatarMap[msg.senderId] ?: msg.senderEmail?.let { liveMembersAvatarMap[it.lowercase()] }

                            val offsetX = remember { Animatable(0f) }

                            val candidateStickerUrl = msg.imageUrl ?: msg.imageUrls.firstOrNull() ?: msg.videoUrl
                            val isStickerMessage = msg.text.isBlank() &&
                                    msg.audioUrl.isNullOrBlank() &&
                                    !candidateStickerUrl.isNullOrBlank() &&
                                    (candidateStickerUrl.contains("tenor.com", ignoreCase = true) ||
                                     candidateStickerUrl.contains("giphy.com", ignoreCase = true) ||
                                     candidateStickerUrl.contains("/stickers/", ignoreCase = true) ||
                                     candidateStickerUrl.endsWith(".gif", ignoreCase = true) ||
                                     candidateStickerUrl.endsWith(".webp", ignoreCase = true) ||
                                     candidateStickerUrl.endsWith(".mp4", ignoreCase = true))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                    .pointerInput(msg.id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (offsetX.value > 40f) replyingToMessage = msg
                                                coroutineScope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                            },
                                            onDragCancel = { coroutineScope.launch { offsetX.animateTo(0f) } },
                                            onHorizontalDrag = { _, dragAmount ->
                                                if (dragAmount > 0 || offsetX.value > 0) {
                                                    coroutineScope.launch { offsetX.snapTo((offsetX.value + dragAmount * 0.5f).coerceIn(0f, 60f)) }
                                                }
                                            }
                                        )
                                    },
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (!isMe) {
                                    ChatUserAvatarCircle(
                                        avatarUrl = effectiveAvatar,
                                        userName = msg.senderName,
                                        modifier = Modifier.size(24.dp).padding(bottom = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                if (isStickerMessage && !candidateStickerUrl.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(135.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { previewImageUrl = candidateStickerUrl },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(candidateStickerUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Sticker",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                } else {
                                    val hasImages = msg.imageUrls.isNotEmpty() || !msg.imageUrl.isNullOrBlank()
                                    val hasVideo = !msg.videoUrl.isNullOrBlank()

                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 10.dp,
                                            topEnd = 10.dp,
                                            bottomStart = if (isMe) 10.dp else 2.dp,
                                            bottomEnd = if (isMe) 2.dp else 10.dp
                                        ),
                                        color = if (isMe) Color(0xFF2B5278) else Color(0xFF1B2330),
                                        modifier = Modifier.widthIn(min = 40.dp, max = 245.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
                                            if (!isMe) {
                                                Text(
                                                    text = msg.senderName,
                                                    color = if (msg.isOwner) Color(0xFFFFB300) else Color(0xFF5288C1),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(1.dp))
                                            }

                                            if (!msg.replyToName.isNullOrBlank()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0x22000000))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(modifier = Modifier.width(2.dp).height(16.dp).background(Color(0xFF5288C1)))
                                                    Column {
                                                        Text(msg.replyToName, color = Color(0xFF5288C1), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                        Text(msg.replyToText ?: "", color = Color.White.copy(0.8f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }

                                            if (msg.text.isNotBlank()) {
                                                Text(
                                                    text = msg.text,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isMe) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    ChatUserAvatarCircle(
                                        avatarUrl = effectiveAvatar,
                                        userName = msg.senderName,
                                        modifier = Modifier.size(24.dp).padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ইনপুট বার
                    Surface(
                        color = Color(0xFF141A24),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        if (!isLoggedIn) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0E131C),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRequireLogin() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Log in to send messages / চ্যাট করতে লগইন করুন",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0E131C),
                                border = BorderStroke(1.dp, Color(0xFF222C3E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                    BasicTextField(
                                        value = messageInput,
                                        onValueChange = { messageInput = it },
                                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp, lineHeight = 15.sp),
                                        cursorBrush = SolidColor(Color(0xFF00E676)),
                                        singleLine = false,
                                        maxLines = 3,
                                        decorationBox = { inner ->
                                            if (messageInput.isEmpty()) {
                                                Text("Compose your message...", color = Color(0xFF717D94), fontSize = 11.5.sp)
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp, max = 50.dp)
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                                                contentDescription = "Emoji, Stickers",
                                                tint = Color(0xFF8692A6),
                                                modifier = Modifier.size(17.dp).clickable { showMediaPicker = !showMediaPicker }
                                            )
                                        }

                                        IconButton(
                                            onClick = { sendMediaOrTextMessage() },
                                            enabled = messageInput.isNotBlank(),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = if (messageInput.isNotBlank()) Color(0xFF00E676) else Color(0xFF384354),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ফ্লোটিং চ্যাট বাটন
        if (!isImeVisible) {
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = !isExpanded }
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isExpanded) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = "Community Chat",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier
                            .size(34.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape)
                    )
                }
            }
        }
    }

    // =========================================================================
    // 🧸 স্টিকার ও ফটো প্রিভিউ ডায়ালগ (কালো স্ক্রিন ফিক্সড)
    // =========================================================================
    previewImageUrl?.let { mediaUrl ->
        val isVideoSticker = mediaUrl.endsWith(".mp4", true) || mediaUrl.endsWith(".webm", true)

        Dialog(
            onDismissRequest = { previewImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC080C14)) // 👈 কালো ব্ল্যাংক স্ক্রিনের বদলে ট্রান্সলুসেন্ট ব্যাকগ্রাউন্ড
                    .clickable { previewImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                // স্টিকার প্রিভিউ কনটেইনার
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideoSticker) {
                        MiniVideoStickerLoopPlayer(
                            videoUrl = mediaUrl,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(mediaUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Sticker",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                IconButton(
                    onClick = { previewImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.6f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    previewVideoUrl?.let { vid ->
        ChatVideoPlayerDialog(
            videoUrl = vid,
            onDismiss = { previewVideoUrl = null }
        )
    }
}

@Composable
private fun MiniVideoStickerLoopPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}
