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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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

// 🎨 কালার থিম: প্রিমিয়াম মিডনাইট গ্রাফাইট + চকচকে সাদা কনট্রাস্ট
private val MidnightBg = Color(0xFF0D1017)
private val CardSurfaceDark = Color(0xFF161C26)
private val MyBubbleColor = Color(0xFF0066FF)
private val AccentCyan = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00D166)

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

    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }
    val currentUserId = remember { authPrefs.getString("user_id", "")?.ifBlank { "guest_${UUID.randomUUID().toString().take(6)}" } ?: "guest" }
    val currentUserName = remember { authPrefs.getString("user_name", "Drama Fan") ?: "Drama Fan" }
    val currentUserAvatar = remember { authPrefs.getString("user_avatar", null) }
    val isUserVip = remember {
        authPrefs.getBoolean("is_vip", false) ||
        (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))
    }

    var messageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }

    // ভয়েস রেকর্ডিং স্টেট
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }

    // রিপ্লাই ও লং-প্রেস মেনু
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedActionMessage by remember { mutableStateOf<ChatMessage?>(null) }

    // ফুলস্ক্রিন প্রিভিউ (ইমেজ ও ভিডিও)
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewVideoUrl by remember { mutableStateOf<String?>(null) }

    // অডিও প্লেব্যাক স্টেট (যেকোনো একটি অডিও প্লে হবে)
    var activePlayingAudioUrl by remember { mutableStateOf<String?>(null) }
    val audioMediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            audioMediaPlayer.release()
            mediaRecorder?.release()
        }
    }

    // 📸 ইমেজ পিকার
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    // 🎬 ভিডিও পিকার (৫০ MB পর্যন্ত)
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

    // 🎙️ মাইক পারমিশন
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Microphone permission needed to record voice", Toast.LENGTH_SHORT).show()
        }
    }

    // ⚡ ফায়ারবেস লাইভ মেসেজ
    val messagesList by produceState<List<ChatMessage>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveMessagesFlow().collect { value = it }
    }

    // 📡 অপর পাশে কে কী করছে (লাইভ টাইপিং / ভয়েস / ভিডিও)
    val liveActiveActions by produceState<List<com.example.util.UserChatStatus>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveActiveActionUsersFlow(currentUserId).collect { value = it }
    }

    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    // টাইপিং স্ট্যাটাস ট্র্যাকার
    LaunchedEffect(messageText) {
        if (messageText.isNotBlank()) {
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "typing")
        } else if (!isRecordingVoice) {
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
        }
    }

    // ভয়েস রেকর্ড টাইমার
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordDurationSeconds = 0
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
            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoice() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVoice = false

            val file = tempAudioFile
            val duration = recordDurationSeconds
            if (file != null && file.exists() && duration >= 1) {
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
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
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
            mediaRecorder?.stop()
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
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
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
                }
            }
            isSending = false
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBg)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. প্রিমিয়াম ফুলস্ক্রিন হেডার (Edge-to-Edge Status Bar সহ)
            Surface(
                color = Color(0xEB161C26),
                shadowElevation = 10.dp,
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222B3D))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(19.dp))
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "DramaFlix Community",
                                    color = Color.White,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentGreen)
                                )
                            }
                            Text(
                                text = "Live Discussion • Voice & Video (50MB)",
                                color = AccentCyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isUserVip) {
                        VipCrown3DIcon(modifier = Modifier.size(28.dp, 22.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF202838), thickness = 0.8.dp)

            // 💬 ২. চ্যাট মেসেজ স্ক্রিন
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messagesList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = Color(0xFF3B4860), modifier = Modifier.size(48.dp))
                            Text("No messages yet", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Be the first to share your thoughts, photos, voice or video!", color = Color(0xFF8E9BB2), fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messagesList, key = { it.id }) { msg ->
                            val isMe = (msg.senderId == currentUserId)
                            ChatMessageBubble(
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

            // 📡 ৩. লাইভ টাইপিং ও অ্যাকশন অ্যানিমেশন ব্যানার
            AnimatedVisibility(visible = liveActiveActions.isNotEmpty()) {
                val actionUser = liveActiveActions.firstOrNull()
                if (actionUser != null) {
                    val actionText = when (actionUser.action) {
                        "recording" -> "${actionUser.userName} is recording voice"
                        "uploading_video" -> "${actionUser.userName} is sending a video"
                        else -> "${actionUser.userName} is typing"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF131B2A))
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JumpingDotsAnimation()
                        Text(
                            text = actionText,
                            color = AccentCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ↩️ রিপ্লাই প্রিভিউ
            AnimatedVisibility(visible = replyingToMessage != null) {
                replyingToMessage?.let { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A2336))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Column {
                                Text(
                                    text = "Replying to ${target.senderName}",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = target.text.ifBlank { if (target.videoUrl != null) "🎬 Video" else if (target.audioUrl != null) "🎤 Voice" else "📷 Photo" },
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 🖼️ ইমেজ সিলেক্টেড ব্যানার
            AnimatedVisibility(visible = selectedImageUri != null) {
                selectedImageUri?.let { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161C28))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text("Image ready to send", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = { selectedImageUri = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF5252))
                        }
                    }
                }
            }

            // ⌨️ ৪. বটম ইনপুট ও ভয়েস রেকর্ডিং বার
            Surface(
                color = Color(0xFF161C26),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                if (isRecordingVoice) {
                    // 🎙️ লাইভ ভয়েস রেকর্ডিং মোড
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF2A4B))
                            )
                            Text(
                                text = "Recording: ${recordDurationSeconds}s",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { cancelVoiceRecording() }) {
                                Text("Cancel", color = Color(0xFFFF5252), fontSize = 13.sp)
                            }
                            Button(
                                onClick = { stopAndSendVoice() },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // 📝 সাধারণ মেসেজ + মিডিয়া বাটন
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 📸 ছবি
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF222B3D))
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Photo", tint = AccentCyan, modifier = Modifier.size(20.dp))
                        }

                        // 🎬 ভিডিও (৫০ MB)
                        IconButton(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF222B3D))
                        ) {
                            Icon(Icons.Outlined.Videocam, contentDescription = "Video", tint = AccentGreen, modifier = Modifier.size(22.dp))
                        }

                        // ইনপুট বক্স
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF202736))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (messageText.isEmpty() && selectedImageUri == null) {
                                Text("Say something in community...", color = Color(0xFF8692A6), fontSize = 13.5.sp)
                            }
                            BasicTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(AccentCyan),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // যদি টেক্সট থাকে তাহলে সেন্ড বাটন, না থাকলে ভয়েস রেকর্ড বাটন
                        if (messageText.isNotBlank() || selectedImageUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MyBubbleColor)
                                    .clickable(enabled = !isSending) { sendMessage() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            // 🎙️ মাইক বাটন
                            IconButton(
                                onClick = { startRecordingVoice() },
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF222B3D))
                            ) {
                                Icon(Icons.Outlined.Mic, contentDescription = "Voice", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }

        // 📋 ৫. লং-প্রেস মেনু
        selectedActionMessage?.let { msg ->
            val isMyMsg = (msg.senderId == currentUserId)
            ModalBottomSheet(
                onDismissRequest = { selectedActionMessage = null },
                containerColor = Color(0xFF181F2C)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Options", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color(0xFF283448), thickness = 0.8.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                replyingToMessage = msg
                                selectedActionMessage = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = AccentCyan)
                        Text("Reply", color = Color.White, fontSize = 14.sp)
                    }

                    if (msg.text.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Message", msg.text))
                                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                    selectedActionMessage = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White)
                            Text("Copy Text", color = Color.White, fontSize = 14.sp)
                        }
                    }

                    if (isMyMsg) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch { FirebaseChatManager.deleteMessage(msg.id) }
                                    selectedActionMessage = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF4D4F))
                            Text("Delete for everyone", color = Color(0xFFFF4D4F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 🎬 ৬. ফুলস্ক্রিন ভিডিও প্লেয়ার পপ-আপ
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
                DisposableEffect(Unit) {
                    onDispose { exoPlayer.release() }
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { previewVideoUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // 🖼️ ৭. ফুলস্ক্রিন ইমেজ পপ-আপ
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
// 💬 চ্যাট বাবল কম্পোনেন্ট (ভিডিও, অডিও ও ইমেজের আল্ট্রা-ক্লিন ডিজাইন)
// =========================================================================
@Composable
private fun ChatMessageBubble(
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
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF263246)),
                contentAlignment = Alignment.Center
            ) {
                if (!message.senderAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = message.senderAvatar,
                        contentDescription = message.senderName,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(message.senderName.take(1).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 285.dp)
        ) {
            if (!isMe) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Text(message.senderName, color = Color(0xFFA1ADC3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (message.isVip) {
                        VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 3.dp,
                    bottomEnd = if (isMe) 3.dp else 16.dp
                ),
                color = if (isMe) MyBubbleColor else CardSurfaceDark,
                border = if (isMe) null else BorderStroke(0.8.dp, Color(0xFF28344A)),
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {

                    // ↩️ রিপ্লাই বক্স
                    if (!message.replyToName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(0.25f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("↩ ${message.replyToName}", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(message.replyToText ?: "", color = Color.White.copy(0.85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 🖼️ ছবি
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

                    // 🎬 ৫০ MB ভিডিও বাবল
                    if (!message.videoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F141E))
                                .clickable { onVideoClick(message.videoUrl) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play Video", tint = AccentGreen, modifier = Modifier.size(52.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(0.7f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            ) {
                                Text("🎬 Video", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }

                    // 🎙️ অডিও প্লেয়ার বাবল
                    if (!message.audioUrl.isNullOrBlank()) {
                        val isThisPlaying = (activeAudioUrl == message.audioUrl)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = { onPlayAudio(message.audioUrl) },
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(if (isMe) Color.White else AccentGreen)
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text("Voice Note", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${message.mediaDurationSec}s", color = Color.White.copy(0.7f), fontSize = 10.sp)
                            }

                            // অ্যানিমেটেড ওয়েভফর্ম
                            if (isThisPlaying) {
                                JumpingDotsAnimation()
                            }
                        }
                    }

                    // টেক্সট মেসেজ
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
                        color = Color.White.copy(0.65f),
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}

// =========================================================================
// 🌟 ৩-ডট লাইভ জাম্পিং অ্যানিমেশন (Typing Indicator)
// =========================================================================
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
        Box(modifier = Modifier.size(6.dp).scale(dot1Scale).clip(CircleShape).background(AccentCyan))
        Box(modifier = Modifier.size(6.dp).scale(dot2Scale).clip(CircleShape).background(AccentCyan))
        Box(modifier = Modifier.size(6.dp).scale(dot3Scale).clip(CircleShape).background(AccentCyan))
    }
}
