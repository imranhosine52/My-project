@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.util.FirebaseChatManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun FloatingCommunityChatWidget(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String?,
    currentUserAvatar: String?,
    isVip: Boolean,
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
    var showEmojiPack by remember { mutableStateOf(false) }
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

    DisposableEffect(Unit) {
        onDispose {
            try { audioMediaPlayer.release() } catch (_: Exception) {}
            try {
                mediaRecorder?.release()
                tempAudioFile?.delete()
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isExpanded) {
            miniListState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMediaOrTextMessage() {
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
        showEmojiPack = false
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
            }
        }
    }

    Box(
        modifier = modifier
            .padding(bottom = 86.dp, end = 16.dp), // 👈 নেভিগেশন বারের ওপরে পারফেক্ট মার্জিন
        contentAlignment = Alignment.BottomEnd
    ) {
        // =========================================================================
        // 💬 ১. লম্বা সাইজ ও চিকন হেডারের মিনি চ্যাট উইন্ডো (উচ্চতা ৬২০dp)
        // =========================================================================
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(initialScale = 0.85f, animationSpec = tween(220)) + fadeIn() + slideInVertically { it / 4 },
            exit = scaleOut(targetScale = 0.85f, animationSpec = tween(200)) + fadeOut() + slideOutVertically { it / 4 }
        ) {
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .height(620.dp) // 👈 আপনার আঁকা নীল দাগ পর্যন্ত উঁচু ও লম্বা সাইজ
                    .padding(bottom = 48.dp)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF10141D),
                border = BorderStroke(1.2.dp, Color(0xFF232D3F))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 🔝 অতি স্লিম ও চিকন হেডার বার (উচ্চতা মাত্র ৩৮dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(Color(0xFF161E2C))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Text("DramaFlix Live Chat", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
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

                    // 💬 চ্যাট মেসেজ তালিকা (সোয়াইপ টু রিপ্লাই সহ)
                    LazyColumn(
                        state = miniListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages.takeLast(50), key = { it.id }) { msg ->
                            val isMe = msg.senderId == currentUserId
                            val hasImages = msg.imageUrls.isNotEmpty() || !msg.imageUrl.isNullOrBlank()
                            val hasVideo = !msg.videoUrl.isNullOrBlank()
                            val hasVoice = !msg.audioUrl.isNullOrBlank()

                            val offsetX = remember { Animatable(0f) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                    .pointerInput(msg.id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (offsetX.value > 45f) {
                                                    replyingToMessage = msg
                                                }
                                                coroutineScope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                            },
                                            onDragCancel = { coroutineScope.launch { offsetX.animateTo(0f) } },
                                            onHorizontalDrag = { _, dragAmount ->
                                                if (dragAmount > 0 || offsetX.value > 0) {
                                                    coroutineScope.launch { offsetX.snapTo((offsetX.value + dragAmount * 0.5f).coerceIn(0f, 70f)) }
                                                }
                                            }
                                        )
                                    },
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isMe) 12.dp else 2.dp,
                                        bottomEnd = if (isMe) 2.dp else 12.dp
                                    ),
                                    color = if (isMe) Color(0xFF2B5278) else Color(0xFF1B2330),
                                    modifier = Modifier.widthIn(min = 50.dp, max = 270.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                        if (!isMe) {
                                            Text(
                                                text = msg.senderName,
                                                color = if (msg.isOwner) Color(0xFFFFB300) else Color(0xFF5288C1),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }

                                        // রিপ্লাই ট্যাগ
                                        if (!msg.replyToName.isNullOrBlank()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x22000000))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(modifier = Modifier.width(2.5.dp).height(20.dp).background(Color(0xFF5288C1)))
                                                Column {
                                                    Text(msg.replyToName, color = Color(0xFF5288C1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text(msg.replyToText ?: "", color = Color.White.copy(0.8f), fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                        }

                                        // 🖼️ ছবি
                                        if (hasImages && !hasVideo) {
                                            val img = msg.imageUrls.firstOrNull() ?: msg.imageUrl!!
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { previewImageUrl = img }
                                            ) {
                                                AsyncImage(
                                                    model = img,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                        }

                                        // 🎬 ভিডিও (টিকটক ৯:১৬ ও ইউটিউব ১৬:৯ অটো সাইজ)
                                        if (hasVideo) {
                                            VideoMessageThumbnailBubble(
                                                videoUrl = msg.videoUrl!!,
                                                imageUrl = msg.imageUrl,
                                                onVideoClick = { previewVideoUrl = msg.videoUrl }
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                        }

                                        // 🎙️ পিওর মেইন চ্যাট স্টাইল ভয়েস প্লেয়ার (কোনো ডাবল ব্যাকগ্রাউন্ড বক্স ছাড়া)
                                        if (hasVoice) {
                                            val isVoicePlaying = (activePlayingAudioUrl == msg.audioUrl)
                                            WhatsAppVoicePlayer(
                                                senderName = msg.senderName,
                                                senderAvatar = msg.senderAvatar,
                                                durationSec = msg.mediaDurationSec,
                                                timeFormatted = formatMessageTime(msg.timestamp),
                                                isMe = isMe,
                                                isSeen = msg.isRead,
                                                isPlaying = isVoicePlaying,
                                                onPlayToggle = {
                                                    try {
                                                        if (isVoicePlaying && audioMediaPlayer.isPlaying) {
                                                            audioMediaPlayer.pause()
                                                            activePlayingAudioUrl = null
                                                        } else {
                                                            audioMediaPlayer.reset()
                                                            audioMediaPlayer.setDataSource(msg.audioUrl)
                                                            audioMediaPlayer.prepareAsync()
                                                            audioMediaPlayer.setOnPreparedListener {
                                                                audioMediaPlayer.start()
                                                                activePlayingAudioUrl = msg.audioUrl
                                                            }
                                                            audioMediaPlayer.setOnCompletionListener {
                                                                activePlayingAudioUrl = null
                                                            }
                                                        }
                                                    } catch (_: Exception) {}
                                                },
                                                onForwardClick = null
                                            )
                                        }

                                        // টেক্সট মেসেজ
                                        if (msg.text.isNotBlank()) {
                                            Text(
                                                text = msg.text,
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // সোয়াইপ টু রিপ্লাই প্রিভিউ ব্যানার
                    AnimatedVisibility(visible = replyingToMessage != null) {
                        replyingToMessage?.let { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF182230))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.width(2.5.dp).height(24.dp).background(Color(0xFF00E5FF)))
                                    Column {
                                        Text("Replying to ${target.senderName}", color = Color(0xFF00E5FF), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        Text(target.text.ifBlank { "Attachment" }, color = Color.White.copy(0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF8692A6), modifier = Modifier.size(16.dp).clickable { replyingToMessage = null })
                            }
                        }
                    }

                    // নির্বাচিত মিডিয়া প্রিভিউ বার
                    if (selectedImageUris.isNotEmpty() || selectedVideoUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF161F2E))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedVideoUri != null) "🎬 1 Video selected" else "📷 ${selectedImageUris.size} Photos selected",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp).clickable {
                                selectedImageUris = emptyList()
                                selectedVideoUri = null
                            })
                        }
                    }

                    // ইমোজি পপ-আপ
                    if (showEmojiPack) {
                        EmojiPackPopupCard(
                            onEmojiSelected = { emoji -> messageInput += emoji },
                            onClose = { showEmojiPack = false },
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    // =========================================================================
                    // ✍️ ২. "Compose your message..." ইনপুট বক্স
                    // =========================================================================
                    Surface(
                        color = Color(0xFF141A24),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E131C),
                            border = BorderStroke(1.dp, Color(0xFF222C3E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                if (isRecordingVoice) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(34.dp),
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
                                            Text("Cancel", color = Color(0xFFFF5252), fontSize = 11.5.sp, modifier = Modifier.clickable { cancelVoiceRecord() })
                                            Text("Send", color = Color(0xFF00E676), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { toggleVoiceRecord() })
                                        }
                                    }
                                } else {
                                    BasicTextField(
                                        value = messageInput,
                                        onValueChange = { messageInput = it },
                                        textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp, lineHeight = 16.sp),
                                        cursorBrush = SolidColor(Color(0xFF00E676)),
                                        singleLine = false,
                                        maxLines = 3,
                                        decorationBox = { inner ->
                                            if (messageInput.isEmpty() && selectedImageUris.isEmpty() && selectedVideoUri == null) {
                                                Text("Compose your message...", color = Color(0xFF717D94), fontSize = 12.5.sp)
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp, max = 56.dp)
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                                                contentDescription = "Emoji",
                                                tint = Color(0xFF8692A6),
                                                modifier = Modifier.size(18.dp).clickable { showEmojiPack = !showEmojiPack }
                                            )

                                            Icon(
                                                imageVector = Icons.Outlined.AttachFile,
                                                contentDescription = "Attach",
                                                tint = Color(0xFF8692A6),
                                                modifier = Modifier.size(18.dp).clickable { showAttachSheet = true }
                                            )

                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = "Voice",
                                                tint = Color(0xFF8692A6),
                                                modifier = Modifier.size(18.dp).clickable { toggleVoiceRecord() }
                                            )
                                        }

                                        IconButton(
                                            onClick = { sendMediaOrTextMessage() },
                                            enabled = messageInput.isNotBlank() || selectedImageUris.isNotEmpty() || selectedVideoUri != null,
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = if (messageInput.isNotBlank() || selectedImageUris.isNotEmpty() || selectedVideoUri != null) Color(0xFF00E676) else Color(0xFF384354),
                                                modifier = Modifier.size(16.dp)
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

        // =========================================================================
        // 🔘 ২. ছোট ও নিখুঁত স্পেসিংযুক্ত "Help?" ফ্লোটিং বাটন
        // =========================================================================
        Surface(
            modifier = Modifier
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(20.dp),
            color = if (isExpanded) Color(0xFFFF3B30) else Color(0xFF0084FF),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), // 👈 ছোট ও স্লিম সাইজ
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isExpanded) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Close", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.ChatBubble, contentDescription = "Help", tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Help?", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }

    // মিডিয়া অ্যাটাচমেন্ট মেনু
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = Color(0xFF161F2C)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select Media to Share", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = Color(0xFF2B374A), thickness = 0.8.dp)

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                        showAttachSheet = false
                        multiImagePicker.launch("image/*")
                    }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                    Text("Photos & Images", color = Color.White, fontSize = 13.5.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                        showAttachSheet = false
                        videoPicker.launch("video/*")
                    }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(22.dp))
                    Text("Video Clip (Max 50MB)", color = Color.White, fontSize = 13.5.sp)
                }
            }
        }
    }

    // ইমেজ প্রিভিউ
    previewImageUrl?.let { img ->
        Dialog(onDismissRequest = { previewImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.95f))) {
                AsyncImage(model = img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { previewImageUrl = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(0.6f))) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // ভিডিও প্লেয়ার
    previewVideoUrl?.let { vid ->
        ChatVideoPlayerDialog(
            videoUrl = vid,
            onDismiss = { previewVideoUrl = null }
        )
    }
}
