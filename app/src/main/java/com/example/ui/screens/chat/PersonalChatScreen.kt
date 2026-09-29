@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)

package com.example.ui.screens.chat

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachFile
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.DirectChatMessageDto
import com.example.data.model.UserProfileMetricsDto
import com.example.data.repository.ChatRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.chat.components.ChatImageCollage
import com.example.ui.screens.chat.components.WhatsAppVoicePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val DarkBg = Color(0xFF0C1317)
private val BarBg = Color(0xFF1F2C34)
private val BubbleSent = Color(0xFF005C4B)
private val BubbleReceived = Color(0xFF202C33)
private val ActionGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8692A6)

@Composable
fun PersonalChatScreen(
    myUserId: String,
    myUserName: String,
    myUserAvatar: String?,
    recipientUserId: String,
    recipientUserName: String,
    recipientUserAvatar: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val chatRepository = remember { ChatRepository(context) }
    val reelsRepository = remember { ReelsRepository(context) }

    // 🎯 "Creator" নামের পরিবর্তে সার্ভার থেকে আসল প্রোফাইল ও নাম ফেচ করার স্টেট
    var liveRecipientProfile by remember { mutableStateOf<UserProfileMetricsDto?>(null) }

    LaunchedEffect(recipientUserId) {
        val targetIdInt = recipientUserId.filter { it.isDigit() }.toIntOrNull() ?: 0
        if (targetIdInt > 0) {
            val res = reelsRepository.getUserProfileMetrics(targetIdInt)
            if (res.isSuccess) {
                liveRecipientProfile = res.getOrNull()
            }
        }
    }

    // আসল নাম ও অবতার নির্ধারণ (কখনোই শুধু "Creator" দেখাবে না)
    val displayRecipientName = remember(liveRecipientProfile, recipientUserName) {
        liveRecipientProfile?.displayName?.takeIf { it.isNotBlank() && !it.equals("Creator", ignoreCase = true) }
            ?: liveRecipientProfile?.pageName?.takeIf { it.isNotBlank() }
            ?: recipientUserName.takeIf { it.isNotBlank() && !it.equals("Creator", ignoreCase = true) }
            ?: liveRecipientProfile?.displayHandle
            ?: "DramaFlix Member"
    }

    val displayRecipientAvatar = remember(liveRecipientProfile, recipientUserAvatar) {
        liveRecipientProfile?.effectiveAvatar?.takeIf { it.isNotBlank() }
            ?: recipientUserAvatar?.takeIf { it.isNotBlank() }
    }

    // ইউনিক কনভারসেশন চ্যানেল আইডি
    val conversationChannelId = remember(myUserId, recipientUserId) {
        val sorted = listOf(myUserId, recipientUserId).sorted()
        "direct_${sorted[0]}_${sorted[1]}"
    }

    val messagesList = remember { mutableStateListOf<DirectChatMessageDto>() }
    var isLoadingHistory by remember { mutableStateOf(true) }
    var isRecipientTyping by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }

    // ভয়েস নোট ও অডিও প্লেয়ার স্টেট
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableLongStateOf(0L) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }
    var recordingTimerJob by remember { mutableStateOf<Job?>(null) }
    var typingTimerJob by remember { mutableStateOf<Job?>(null) }

    var activeAudioUrl by remember { mutableStateOf<String?>(null) }
    val audioPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try { audioPlayer.release() } catch (_: Exception) {}
            try {
                mediaRecorder?.release()
                tempAudioFile?.delete()
                recordingTimerJob?.cancel()
                typingTimerJob?.cancel()
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // 🌐 ১. VPS 2 REST API থেকে পূর্বের মেসেজ হিস্ট্রি লোড
    // =========================================================================
    LaunchedEffect(conversationChannelId) {
        chatRepository.connectLiveSocket()
        val historyResult = chatRepository.getChatMessages(conversationChannelId)
        if (historyResult.isSuccess) {
            messagesList.clear()
            messagesList.addAll(historyResult.getOrDefault(emptyList()))
        }
        isLoadingHistory = false
    }

    // =========================================================================
    // ⚡ ২. লাইভ WebSocket ইনকামিং মেসেজ রিসিভার (Server Spec 7)
    // =========================================================================
    LaunchedEffect(conversationChannelId) {
        chatRepository.incomingLiveMessages.collect { incomingMsg ->
            if (incomingMsg.conversationId == conversationChannelId ||
                incomingMsg.senderId == recipientUserId) {
                if (messagesList.none { it.id == incomingMsg.id }) {
                    messagesList.add(incomingMsg)
                    listState.animateScrollToItem((messagesList.size - 1).coerceAtLeast(0))
                }
            }
        }
    }

    // ⚡ ৩. লাইভ টাইপিং ইন্ডিকেটর রিসিভার
    LaunchedEffect(conversationChannelId) {
        chatRepository.incomingLiveFrames.collect { frame ->
            if (frame.type == "typing" && frame.senderId == recipientUserId) {
                isRecipientTyping = (frame.action == "typing")
            }
        }
    }

    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris.take(6)
        }
    }

    // =========================================================================
    // 🎙️ ৪. ভয়েস রেকর্ডিং ইঞ্জিন
    // =========================================================================
    fun executeStartVoice() {
        try {
            val audioFile = File(context.cacheDir, "dm_voice_${System.currentTimeMillis()}.m4a")
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
            Toast.makeText(context, "Cannot record audio", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) executeStartVoice()
        else Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    fun startVoiceRecording() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) executeStartVoice() else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopAndSendVoice() {
        try {
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
                        val uploadResult = chatRepository.uploadVoiceNote(file)
                        if (uploadResult.isSuccess) {
                            val audioUrl = uploadResult.getOrNull()
                            val localMsg = DirectChatMessageDto(
                                id = "local_${System.currentTimeMillis()}",
                                conversationId = conversationChannelId,
                                senderId = myUserId,
                                senderName = myUserName,
                                senderAvatar = myUserAvatar,
                                text = "",
                                audioUrl = audioUrl,
                                mediaDurationSec = duration,
                                isRead = false,
                                timestamp = System.currentTimeMillis()
                            )
                            messagesList.add(localMsg)

                            // WebSocket দিয়ে ফ্রেম প্রেরণ
                            chatRepository.sendDirectTextMessage(
                                conversationId = conversationChannelId,
                                recipientId = recipientUserId,
                                text = audioUrl ?: "",
                                senderName = myUserName,
                                senderAvatar = myUserAvatar
                            )
                        }
                    } finally {
                        isSending = false
                    }
                }
            }
        } catch (_: Exception) {
            isRecordingVoice = false
            isSending = false
        }
    }

    fun cancelVoice() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        tempAudioFile?.delete()
        isRecordingVoice = false
    }

    // =========================================================================
    // 💬 ৫. মেসেজ ও মিডিয়া সেন্ডার মেথড (WebSocket + VPS 2)
    // =========================================================================
    fun sendMessage() {
        val text = inputText.trim()
        val images = selectedImageUris

        if (text.isBlank() && images.isEmpty()) return

        inputText = ""
        selectedImageUris = emptyList()
        isSending = true
        chatRepository.sendTypingStatus(conversationChannelId, recipientUserId, false)

        coroutineScope.launch {
            try {
                if (images.isNotEmpty()) {
                    for (uri in images) {
                        val imgResult = chatRepository.uploadChatImage(uri)
                        if (imgResult.isSuccess) {
                            val imgUrl = imgResult.getOrNull()
                            val localMsg = DirectChatMessageDto(
                                id = "local_${System.currentTimeMillis()}",
                                conversationId = conversationChannelId,
                                senderId = myUserId,
                                senderName = myUserName,
                                senderAvatar = myUserAvatar,
                                text = text,
                                imageUrl = imgUrl,
                                imageUrls = listOfNotNull(imgUrl),
                                isRead = false,
                                timestamp = System.currentTimeMillis()
                            )
                            messagesList.add(localMsg)

                            chatRepository.sendDirectTextMessage(
                                conversationId = conversationChannelId,
                                recipientId = recipientUserId,
                                text = imgUrl ?: "",
                                senderName = myUserName,
                                senderAvatar = myUserAvatar
                            )
                        }
                    }
                } else {
                    val localMsg = DirectChatMessageDto(
                        id = "local_${System.currentTimeMillis()}",
                        conversationId = conversationChannelId,
                        senderId = myUserId,
                        senderName = myUserName,
                        senderAvatar = myUserAvatar,
                        text = text,
                        isRead = false,
                        timestamp = System.currentTimeMillis()
                    )
                    messagesList.add(localMsg)

                    chatRepository.sendDirectTextMessage(
                        conversationId = conversationChannelId,
                        recipientId = recipientUserId,
                        text = text,
                        senderName = myUserName,
                        senderAvatar = myUserAvatar
                    )
                }
            } finally {
                isSending = false
            }
        }
    }

    val isImeVisible = WindowInsets.isImeVisible

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // =========================================================================
        // 🔝 হেডার বার (🎯 কল আইকন দুটি সম্পূর্ণরূপে অপসারিত)
        // =========================================================================
        Surface(
            color = BarBg,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222B38)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!displayRecipientAvatar.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(displayRecipientAvatar)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayRecipientName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = displayRecipientName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayRecipientName,
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isRecipientTyping) "typing..." else "online",
                        color = if (isRecipientTyping) Color(0xFF00E5FF) else ActionGreen,
                        fontSize = 11.5.sp,
                        fontWeight = if (isRecipientTyping) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // =========================================================================
        // 💬 মেসেজ বাবল লিস্ট
        // =========================================================================
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoadingHistory && messagesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.5.dp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messagesList, key = { it.id }) { msg ->
                        val isMe = (msg.senderId == myUserId)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isMe) 12.dp else 2.dp,
                                    bottomEnd = if (isMe) 2.dp else 12.dp
                                ),
                                color = if (isMe) BubbleSent else BubbleReceived,
                                modifier = Modifier.widthIn(min = 50.dp, max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                    if (msg.imageUrls.isNotEmpty()) {
                                        ChatImageCollage(images = msg.imageUrls, onImageClick = {})
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }

                                    if (!msg.audioUrl.isNullOrBlank()) {
                                        val isVoicePlaying = (activeAudioUrl == msg.audioUrl)
                                        WhatsAppVoicePlayer(
                                            senderName = msg.senderName,
                                            senderAvatar = msg.senderAvatar,
                                            durationSec = msg.mediaDurationSec,
                                            timeFormatted = formatDmTime(Date(msg.timestamp)),
                                            isMe = isMe,
                                            isSeen = msg.isRead,
                                            isPlaying = isVoicePlaying,
                                            onPlayToggle = {
                                                try {
                                                    if (isVoicePlaying && audioPlayer.isPlaying) {
                                                        audioPlayer.pause()
                                                        activeAudioUrl = null
                                                    } else {
                                                        audioPlayer.reset()
                                                        audioPlayer.setDataSource(msg.audioUrl)
                                                        audioPlayer.prepareAsync()
                                                        audioPlayer.setOnPreparedListener {
                                                            audioPlayer.start()
                                                            activeAudioUrl = msg.audioUrl
                                                        }
                                                        audioPlayer.setOnCompletionListener {
                                                            activeAudioUrl = null
                                                        }
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        )
                                    }

                                    if (msg.text.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = msg.text,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = formatDmTime(Date(msg.timestamp)) + if (isMe) " ✓✓" else "",
                                                color = TextMuted,
                                                fontSize = 10.sp
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
        // ✍️ ইনপুট বার
        // =========================================================================
        Surface(
            color = BarBg,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(if (isImeVisible) WindowInsets.ime else WindowInsets.navigationBars)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF141D24))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Recording: ${recordDurationSeconds}s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Cancel", color = Color(0xFFFF5252), fontSize = 13.sp, modifier = Modifier.clickable { cancelVoice() })
                            Text("Send", color = ActionGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { stopAndSendVoice() })
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 100.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF141D24))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                            contentDescription = "Emoji",
                            tint = TextMuted,
                            modifier = Modifier.size(22.dp)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (inputText.isEmpty() && selectedImageUris.isEmpty()) {
                                Text("Message...", color = TextMuted, fontSize = 14.sp)
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = {
                                    inputText = it
                                    chatRepository.sendTypingStatus(conversationChannelId, recipientUserId, true)
                                    typingTimerJob?.cancel()
                                    typingTimerJob = coroutineScope.launch {
                                        delay(2500L)
                                        chatRepository.sendTypingStatus(conversationChannelId, recipientUserId, false)
                                    }
                                },
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(ActionGreen),
                                singleLine = false,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Attach",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { multiImagePicker.launch("image/*") }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() || selectedImageUris.isNotEmpty()) sendMessage()
                            else startVoiceRecording()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ActionGreen)
                    ) {
                        Icon(
                            imageVector = if (inputText.isNotBlank() || selectedImageUris.isNotEmpty()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDmTime(date: Date?): String {
    return if (date != null) {
        SimpleDateFormat("h:mm a", Locale.US).format(date)
    } else "Just now"
}
