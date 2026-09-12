@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.ui.VipCrown3DIcon
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.FirebaseChatManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val TelegramDarkBg = Color(0xFF0F141C)
private val TelegramBarBg = Color(0xFF181F2B)
private val TelegramBorder = Color(0xFF283344)
private val TelegramBlue = Color(0xFF2AABEE)
private val OtherBubbleBg = Color(0xFF1E2638)

@Composable
fun CommunityChatScreen(
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val chatPrefs = remember { context.getSharedPreferences("play_drama_flix_chat_group_prefs", Context.MODE_PRIVATE) }
    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }

    val currentUserId = remember { authPrefs.getString("user_id", "")?.ifBlank { "guest_${UUID.randomUUID().toString().take(6)}" } ?: "guest" }
    val currentUserName = remember { authPrefs.getString("user_name", "Drama Fan") ?: "Drama Fan" }
    val currentUserAvatar = remember { authPrefs.getString("user_avatar", null) }
    val isUserVip = remember {
        authPrefs.getBoolean("is_vip", false) ||
        (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))
    }

    var isUserJoined by remember { mutableStateOf(chatPrefs.getBoolean("is_joined_group", false)) }
    var isGroupMuted by remember { mutableStateOf(chatPrefs.getBoolean("is_group_muted", false)) }

    var messageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableLongStateOf(0L) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }

    var showAttachMenu by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedActionMessage by remember { mutableStateOf<ChatMessage?>(null) }

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewVideoUrl by remember { mutableStateOf<String?>(null) }

    var activePlayingAudioUrl by remember { mutableStateOf<String?>(null) }
    val audioMediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try { audioMediaPlayer.release() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isSending = true
            coroutineScope.launch {
                val ok = FirebaseChatManager.uploadVideoAndSendMessage(
                    context = context,
                    videoUri = uri,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderAvatar = currentUserAvatar,
                    isVip = isUserVip,
                    replyToMessage = replyingToMessage,
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                )
                if (ok) replyingToMessage = null
                isSending = false
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val messagesList by produceState<List<ChatMessage>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveMessagesFlow().collect { value = it }
    }

    val liveActiveActions by produceState<List<com.example.util.UserChatStatus>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveActiveActionUsersFlow(currentUserId).collect { value = it }
    }

    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    LaunchedEffect(messageText) {
        if (messageText.isNotBlank()) {
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "typing")
        } else if (!isRecordingVoice) {
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordDurationSeconds = 0L
            while (isRecordingVoice) {
                delay(1000L)
                recordDurationSeconds++
            }
        }
    }

    fun startRecordingVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        try {
            val audioFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
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
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecordingVoice = true

            coroutineScope.launch {
                FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "recording")
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoice() {
        try {
            try {
                mediaRecorder?.stop()
            } catch (_: RuntimeException) {}
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVoice = false

            val file = tempAudioFile
            val duration = if (recordDurationSeconds < 1L) 1L else recordDurationSeconds
            if (file != null && file.exists()) {
                isSending = true
                coroutineScope.launch {
                    val ok = FirebaseChatManager.uploadVoiceAndSendMessage(
                        audioFile = file,
                        durationSeconds = duration,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderAvatar = currentUserAvatar,
                        isVip = isUserVip,
                        replyToMessage = replyingToMessage,
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                    )
                    if (ok) replyingToMessage = null
                    isSending = false
                }
            }
        } catch (_: Exception) {
            isRecordingVoice = false
        }
    }

    fun cancelVoiceRecording() {
        try {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            mediaRecorder?.release()
            mediaRecorder = null
            tempAudioFile?.delete()
            isRecordingVoice = false
            coroutineScope.launch {
                FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
            }
        } catch (_: Exception) {
            isRecordingVoice = false
        }
    }

    fun sendMessage() {
        if (isSending) return
        val text = messageText.trim()
        val imageUri = selectedImageUri
        val replyTarget = replyingToMessage

        if (text.isBlank() && imageUri == null) return

        isSending = true
        coroutineScope.launch {
            if (imageUri != null) {
                val ok = FirebaseChatManager.uploadImageAndSendMessage(
                    context = context,
                    imageUri = imageUri,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderAvatar = currentUserAvatar,
                    isVip = isUserVip,
                    captionText = text,
                    replyToMessage = replyTarget,
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                )
                if (ok) {
                    selectedImageUri = null
                    messageText = ""
                    replyingToMessage = null
                }
            } else {
                val ok = FirebaseChatManager.sendTextMessage(
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderAvatar = currentUserAvatar,
                    isVip = isUserVip,
                    text = text,
                    replyToMessage = replyTarget
                )
                if (ok) {
                    messageText = ""
                    replyingToMessage = null
                } else {
                    Toast.makeText(context, "Message send failed. Check connection.", Toast.LENGTH_SHORT).show()
                }
            }
            isSending = false
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TelegramDarkBg)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. টেলিগ্রাম স্টাইল এজ-টু-এজ টপ হেডার
            Surface(
                color = TelegramBarBg,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222B3D))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("DramaFlix Community", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF00E676)))
                            }
                            Text("Official Fans Discussion Channel", color = Color(0xFF909EB5), fontSize = 11.5.sp)
                        }
                    }

                    if (isUserVip) {
                        VipCrown3DIcon(modifier = Modifier.size(26.dp, 20.dp))
                    }
                }
            }

            HorizontalDivider(color = TelegramBorder, thickness = 0.8.dp)

            // 💬 ২. চ্যাট মেসেজ লিস্ট
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messagesList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("👋", fontSize = 36.sp)
                            Text("Welcome to Community Chat!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Join the group and start sharing thoughts, photos, audio & video.", color = Color(0xFF8E9BB2), fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messagesList, key = { it.id }) { msg ->
                            val isMe = (msg.senderId == currentUserId)
                            TelegramMessageBubble(
                                message = msg,
                                isMe = isMe,
                                activeAudioUrl = activePlayingAudioUrl,
                                onPlayAudio = { url ->
                                    try {
                                        if (activePlayingAudioUrl == url && audioMediaPlayer.isPlaying) {
                                            audioMediaPlayer.pause()
                                            activePlayingAudioUrl = null
                                        } else {
                                            audioMediaPlayer.reset()
                                            audioMediaPlayer.setDataSource(url)
                                            audioMediaPlayer.prepareAsync()
                                            audioMediaPlayer.setOnPreparedListener {
                                                audioMediaPlayer.start()
                                                activePlayingAudioUrl = url
                                            }
                                            audioMediaPlayer.setOnCompletionListener {
                                                activePlayingAudioUrl = null
                                            }
                                        }
                                    } catch (_: Exception) {}
                                },
                                onImageClick = { previewImageUrl = it },
                                onVideoClick = { previewVideoUrl = it },
                                onLongClick = { selectedActionMessage = msg }
                            )
                        }
                    }
                }
            }

            // 📡 ৩. লাইভ অ্যাকশন / টাইপিং বার
            AnimatedVisibility(visible = liveActiveActions.isNotEmpty()) {
                val actionUser = liveActiveActions.firstOrNull()
                if (actionUser != null) {
                    val actionText = when (actionUser.action) {
                        "recording" -> "${actionUser.userName} is recording voice note 🎙️"
                        "uploading_video" -> "${actionUser.userName} is sending video 🎬"
                        else -> "${actionUser.userName} is typing..."
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF141B26)).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JumpingDotsAnimation()
                        Text(actionText, color = TelegramBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ↩️ রিপ্লাই প্রিভিউ ব্যানার
            AnimatedVisibility(visible = replyingToMessage != null) {
                replyingToMessage?.let { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2838)).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                            Column {
                                Text("Replying to ${target.senderName}", color = TelegramBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(target.text.ifBlank { if (target.audioUrl != null) "🎤 Voice Message" else if (target.videoUrl != null) "🎬 Video" else "📷 Photo" }, color = Color.White.copy(0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // =============================================================
            // 🎯 ৪. সম্পূর্ণ ফিক্সড ইনপুট বার (বাটন আর কখনোই স্ক্রিনের বাইরে যাবে না)
            // =============================================================
            Surface(
                color = TelegramDarkBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp)
            ) {
                if (!isUserJoined) {
                    Button(
                        onClick = {
                            isUserJoined = true
                            chatPrefs.edit().putBoolean("is_joined_group", true).apply()
                            FirebaseChatManager.toggleGroupNotification(true)
                            Toast.makeText(context, "🎉 You joined the DramaFlix Community!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("JOIN COMMUNITY GROUP / জয়েন করুন", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Color(0xFF1A2230))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF2A4B)))
                            Text("Recording: ${recordDurationSeconds}s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { cancelVoiceRecording() }) {
                                Text("Cancel", color = Color(0xFFFF5252), fontSize = 13.sp)
                            }
                            Button(
                                onClick = { stopAndSendVoice() },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // প্রধান ক্যাপসুল বার
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(TelegramBarBg)
                                .border(0.8.dp, TelegramBorder, RoundedCornerShape(22.dp))
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF283446)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!currentUserAvatar.isNullOrBlank()) {
                                    AsyncImage(model = currentUserAvatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                } else {
                                    Text(currentUserName.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Icon(
                                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                                contentDescription = "Emoji",
                                tint = Color(0xFF8692A6),
                                modifier = Modifier.size(20.dp)
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (messageText.isEmpty()) {
                                    Text("Broadcast message...", color = Color(0xFF6B7A90), fontSize = 13.sp)
                                }
                                BasicTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                    cursorBrush = SolidColor(TelegramBlue),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Icon(
                                imageVector = if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = "Mute/Unmute",
                                tint = if (isGroupMuted) Color(0xFF8692A6) else Color(0xFFFFB300),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        val newState = !isGroupMuted
                                        isGroupMuted = newState
                                        chatPrefs.edit().putBoolean("is_group_muted", newState).apply()
                                        FirebaseChatManager.toggleGroupNotification(!newState)
                                        Toast.makeText(context, if (newState) "🔕 Notifications muted" else "🔔 Notifications active", Toast.LENGTH_SHORT).show()
                                    }
                            )

                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach",
                                tint = Color(0xFF8692A6),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showAttachMenu = true }
                            )
                        }

                        // ডানে নীল সেন্ড / মাইক বাটন (একদম স্ক্রিনের ভেতরে ফিক্সড থাকবে)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue)
                                .clickable {
                                    if (messageText.isNotBlank()) {
                                        sendMessage()
                                    } else {
                                        startRecordingVoice()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else if (messageText.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Outlined.Mic, contentDescription = "Record Voice", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }

        // 📎 অ্যাটাচমেন্ট মেনু
        if (showAttachMenu) {
            ModalBottomSheet(
                onDismissRequest = { showAttachMenu = false },
                containerColor = TelegramBarBg
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Share Media", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = TelegramBorder, thickness = 0.8.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                showAttachMenu = false
                                imagePickerLauncher.launch("image/*")
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(26.dp))
                        Column {
                            Text("Photo / Image", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Upload directly to Cloudflare R2", color = Color(0xFF8692A6), fontSize = 11.5.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                showAttachMenu = false
                                videoPickerLauncher.launch("video/*")
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(26.dp))
                        Column {
                            Text("Video File", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Up to 50 MB limit", color = Color(0xFF8692A6), fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // 📋 লং-প্রেস অপশন মেনু
        selectedActionMessage?.let { msg ->
            val isMyMsg = (msg.senderId == currentUserId)
            ModalBottomSheet(
                onDismissRequest = { selectedActionMessage = null },
                containerColor = TelegramBarBg
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Message Actions", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = TelegramBorder, thickness = 0.8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            replyingToMessage = msg
                            selectedActionMessage = null
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = TelegramBlue)
                        Text("Reply", color = Color.White, fontSize = 14.sp)
                    }

                    if (msg.text.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Message", msg.text))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                selectedActionMessage = null
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White)
                            Text("Copy Text", color = Color.White, fontSize = 14.sp)
                        }
                    }

                    if (isMyMsg) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                coroutineScope.launch { FirebaseChatManager.deleteMessage(msg.id) }
                                selectedActionMessage = null
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            Text("Delete Message", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 🎬 ভিডিও প্লেয়ার ডায়ালগ
        previewVideoUrl?.let { vidUrl ->
            Dialog(
                onDismissRequest = { previewVideoUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val exoPlayer = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(vidUrl))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } }, modifier = Modifier.fillMaxSize())
                    IconButton(
                        onClick = { previewVideoUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // 🖼️ ইমেজ ভিউয়ার ডায়ালগ
        previewImageUrl?.let { imgUrl ->
            Dialog(
                onDismissRequest = { previewImageUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.95f))) {
                    AsyncImage(model = imgUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    IconButton(
                        onClick = { previewImageUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 💬 মেসেজ বাবল (ভয়েস নোট ১০০% সুন্দরভাবে শো করবে)
// =========================================================================
@Composable
private fun TelegramMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    activeAudioUrl: String?,
    onPlayAudio: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val timeFormatted = remember(message.timestamp) {
        if (message.timestamp != null) {
            SimpleDateFormat("hh:mm a", Locale.US).format(message.timestamp)
        } else "Just now"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF283446)),
                contentAlignment = Alignment.Center
            ) {
                if (!message.senderAvatar.isNullOrBlank()) {
                    AsyncImage(model = message.senderAvatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Text(message.senderName.take(1).uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 285.dp)
        ) {
            if (!isMe) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                    Text(message.senderName, color = Color(0xFFA1ADC3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (message.isVip) VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 16.dp
                ),
                color = if (isMe) Color(0xFF2B5278) else OtherBubbleBg,
                border = BorderStroke(0.6.dp, Color(0xFF2E3A4E)),
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (!message.replyToName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(0.25f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("↩ ${message.replyToName}", color = TelegramBlue, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Text(message.replyToText ?: "", color = Color.White.copy(0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (!message.imageUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onImageClick(message.imageUrl) }
                        ) {
                            AsyncImage(model = message.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }

                    if (!message.videoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .clickable { onVideoClick(message.videoUrl) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(52.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(0.7f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            ) {
                                Text("🎬 Video", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }

                    // 🎙️ নিশ্চিতভাবে দৃশ্যমান ভয়েস বাবল
                    if (!message.audioUrl.isNullOrBlank()) {
                        val isPlaying = (activeAudioUrl == message.audioUrl)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            IconButton(
                                onClick = { onPlayAudio(message.audioUrl) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(TelegramBlue)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Voice Message", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${message.mediaDurationSec}s", color = Color.White.copy(0.7f), fontSize = 11.sp)
                            }

                            if (isPlaying) {
                                JumpingDotsAnimation()
                            }
                        }
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = timeFormatted,
                        color = Color.White.copy(0.6f),
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun JumpingDotsAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).scale(dot1Scale).clip(CircleShape).background(TelegramBlue))
        Box(modifier = Modifier.size(6.dp).scale(dot2Scale).clip(CircleShape).background(TelegramBlue))
        Box(modifier = Modifier.size(6.dp).scale(dot3Scale).clip(CircleShape).background(TelegramBlue))
    }
}
