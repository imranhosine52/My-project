@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.example.util.LiveGroupStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// 🎨 ২ নম্বর ছবির হুবহু WhatsApp থিম কালার
private val WhatsAppDarkBg = Color(0xFF0C1317)
private val WhatsAppBarBg = Color(0xFF1F2C34)
private val WhatsAppSentBubble = Color(0xFF005C4B)     // 👈 WhatsApp Dark Green বাবল
private val WhatsAppReceivedBubble = Color(0xFF202C33) // 👈 WhatsApp Dark Slate বাবল
private val WhatsAppBlueTick = Color(0xFF53BDEB)       // 👈 WhatsApp Blue/Cyan টিক
private val TelegramBlue = Color(0xFF2AABEE)
private val PdFlixGreen = Color(0xFF00E676)
private val OwnerGold = Color(0xFFFFB300)

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
    val currentUserName = remember { authPrefs.getString("user_name", "Hey Sifat YT") ?: "Hey Sifat YT" }
    val currentUserEmail = remember { authPrefs.getString("user_email", "yheysifat@gmail.com") ?: "yheysifat@gmail.com" }
    val currentUserAvatar = remember { authPrefs.getString("user_avatar", null) }

    // 👑 ৩ নম্বর ছবির রুট এডমিন/ওনার চেক
    val isCurrentUserOwner = remember(currentUserEmail) { FirebaseChatManager.isRootAdmin(currentUserEmail) }
    val isUserVip = remember {
        isCurrentUserOwner || authPrefs.getBoolean("is_vip", false) ||
        (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))
    }

    var isUserJoined by remember { mutableStateOf(chatPrefs.getBoolean("is_joined_group", false)) }
    var isGroupMuted by remember { mutableStateOf(chatPrefs.getBoolean("is_group_muted", false)) }

    var showGroupInfoScreen by remember { mutableStateOf(false) }

    var messageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }

    // 🎬 ২ নম্বর ছবির মতো লাইভ ভিডিও আপলোড স্টেট
    var uploadingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var uploadProgressPercent by remember { mutableIntStateOf(0) }
    var uploadSecondsLeft by remember { mutableLongStateOf(0L) }
    var isVideoUploadingActive by remember { mutableStateOf(false) }

    // ইমোজি প্যাক কার্ড ওপেন স্টেট
    var showEmojiPackCard by remember { mutableStateOf(false) }

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

    // 📊 ১০০% লাইভ মেম্বার ও অনলাইন ট্র্যাকিং (No Fake Numbers)
    val liveStats by produceState(initialValue = LiveGroupStats(1, 1)) {
        FirebaseChatManager.getLiveGroupStatsFlow().collect { value = it }
    }

    LaunchedEffect(isUserJoined) {
        if (isUserJoined) {
            while (true) {
                FirebaseChatManager.pingUserPresence(currentUserId, currentUserName)
                delay(30000L)
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    // 🎬 ২ নম্বর ছবির মতো ৫০ MB ভিডিও আপলোড হ্যান্ডলার
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadingVideoUri = uri
            isVideoUploadingActive = true
            uploadProgressPercent = 0
            uploadSecondsLeft = 5L

            coroutineScope.launch {
                val ok = FirebaseChatManager.uploadVideoWithProgressAndSendMessage(
                    context = context,
                    videoUri = uri,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderEmail = currentUserEmail,
                    senderAvatar = currentUserAvatar,
                    isVip = isUserVip,
                    replyToMessage = replyingToMessage,
                    onProgress = { pct, sec ->
                        uploadProgressPercent = pct
                        uploadSecondsLeft = sec
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
                if (ok) replyingToMessage = null
                isVideoUploadingActive = false
                uploadingVideoUri = null
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoice() {
        try {
            try { mediaRecorder?.stop() } catch (_: RuntimeException) {}
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
                        senderEmail = currentUserEmail,
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
                    senderEmail = currentUserEmail,
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
                    senderEmail = currentUserEmail,
                    senderAvatar = currentUserAvatar,
                    isVip = isUserVip,
                    text = text,
                    replyToMessage = replyTarget
                )
                if (ok) {
                    messageText = ""
                    replyingToMessage = null
                } else {
                    Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
                }
            }
            isSending = false
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WhatsAppDarkBg)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. প্রিমিয়াম ফুলস্ক্রিন হেডার (কোনো কালো ফাঁকা গ্যাপ নেই)
            Surface(
                color = WhatsAppBarBg,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showGroupInfoScreen = true } // 👈 ২ নম্বর ছবির পেজ ওপেন
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("DramaFlix Community", color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF00E676)))
                            }
                            Text("${liveStats.totalMembers} members, ${liveStats.onlineMembers} online", color = Color(0xFF8696A0), fontSize = 11.5.sp)
                        }
                    }

                    if (isUserVip) {
                        VipCrown3DIcon(modifier = Modifier.size(26.dp, 20.dp).padding(end = 4.dp))
                    }
                }
            }

            // 💬 ২. চ্যাট মেসেজ লিস্ট
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messagesList, key = { it.id }) { msg ->
                        val isMe = (msg.senderId == currentUserId)
                        WhatsAppMessageBubble(
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
                            onSwipeToReply = { replyingToMessage = msg },
                            onImageClick = { previewImageUrl = it },
                            onVideoClick = { previewVideoUrl = it },
                            onLongClick = { selectedActionMessage = msg }
                        )
                    }

                    // 🎬 ২ নম্বর ছবির মতো লাইভ ভিডিও আপলোড প্রোগ্রেস বাবল
                    if (isVideoUploadingActive && uploadingVideoUri != null) {
                        item {
                            UploadingVideoBubble(
                                videoUri = uploadingVideoUri!!,
                                percent = uploadProgressPercent,
                                secondsLeft = uploadSecondsLeft,
                                onCancel = {
                                    isVideoUploadingActive = false
                                    uploadingVideoUri = null
                                }
                            )
                        }
                    }
                }
            }

            // 📡 ৩. লাইভ টাইপিং অ্যানিমেশন
            AnimatedVisibility(visible = liveActiveActions.isNotEmpty()) {
                val actionUser = liveActiveActions.firstOrNull()
                if (actionUser != null) {
                    val actionText = when (actionUser.action) {
                        "recording" -> "${actionUser.userName} is recording audio 🎙️"
                        "uploading_video" -> "${actionUser.userName} is uploading video 🎬"
                        else -> "${actionUser.userName} is typing..."
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF182229)).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JumpingDotsAnimation()
                        Text(actionText, color = Color(0xFF00A884), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ↩️ রিপ্লাই প্রিভিউ ব্যানার
            AnimatedVisibility(visible = replyingToMessage != null) {
                replyingToMessage?.let { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF182229)).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.width(3.dp).height(32.dp).background(Color(0xFF00A884)))
                            Column {
                                Text(target.senderName, color = Color(0xFF00A884), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(target.text.ifBlank { if (target.audioUrl != null) "🎤 Voice Message" else if (target.videoUrl != null) "🎬 Video" else "📷 Photo" }, color = Color.White.copy(0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF8696A0), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 🎯 ৪. সম্পূর্ণ ফিক্সড ইনপুট বার (টেলিগ্রাম স্টাইল)
            Surface(
                color = WhatsAppDarkBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp)
            ) {
                if (!isUserJoined) {
                    Button(
                        onClick = {
                            isUserJoined = true
                            chatPrefs.edit().putBoolean("is_joined_group", true).apply()
                            coroutineScope.launch {
                                FirebaseChatManager.joinGroup(currentUserId, currentUserName, currentUserAvatar)
                            }
                            Toast.makeText(context, "🎉 Joined DramaFlix Community!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
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
                            .background(Color(0xFF1F2C34))
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
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
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(WhatsAppBarBg)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // ১. বাঁয়ের গোল প্রোফাইল অবতার
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A3942)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!currentUserAvatar.isNullOrBlank()) {
                                    AsyncImage(model = currentUserAvatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                } else {
                                    Text(currentUserName.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // ২. ইমোজি আইকন (ক্লিক করলে রেডিমেড ইমোজি কার্ড ওপেন হবে)
                            Icon(
                                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                                contentDescription = "Emoji Pack",
                                tint = Color(0xFF8696A0),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { showEmojiPackCard = !showEmojiPackCard }
                            )

                            // ৩. টাইপিং বক্স
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (messageText.isEmpty()) {
                                    Text("Message...", color = Color(0xFF8696A0), fontSize = 14.sp)
                                }
                                BasicTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                    cursorBrush = SolidColor(Color(0xFF00A884)),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // ৪. নোটিফিকেশন ঘণ্টা
                            Icon(
                                imageVector = if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (isGroupMuted) Color(0xFF8696A0) else Color(0xFFFFB300),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        val newState = !isGroupMuted
                                        isGroupMuted = newState
                                        chatPrefs.edit().putBoolean("is_group_muted", newState).apply()
                                        FirebaseChatManager.toggleGroupNotification(!newState)
                                        Toast.makeText(context, if (newState) "🔕 Muted" else "🔔 Active", Toast.LENGTH_SHORT).show()
                                    }
                            )

                            // ৫. পেপারক্লিপ / অ্যাটাচমেন্ট
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = null,
                                tint = Color(0xFF8696A0),
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { showAttachMenu = true }
                            )
                        }

                        // ৬. সেন্ড / মাইক বাটন
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00A884))
                                .clickable {
                                    if (messageText.isNotBlank()) sendMessage()
                                    else startRecordingVoice()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else if (messageText.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Outlined.Mic, contentDescription = "Record", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 😊 রেডিমেড ইমোজি প্যাক কার্ড (WhatsApp Style)
        // =========================================================================
        if (showEmojiPackCard) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 54.dp, start = 8.dp, end = 8.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                    border = BorderStroke(1.dp, Color(0xFF2A3942)),
                    modifier = Modifier.fillMaxWidth().height(210.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ready-made Emoji Pack", color = Color(0xFF8696A0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showEmojiPackCard = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8696A0), modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val emojiList = listOf(
                            "👍", "❤️", "😂", "🔥", "🙏", "😍", "🥰", "👏", "🎉", "😮",
                            "😭", "🥺", "😎", "🥳", "✨", "💯", "😴", "🤔", "👀", "💔",
                            "💖", "🤝", "✌️", "🤞", "🫶", "🍿", "🎬", "☕", "🌹", "🚀"
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(emojiList) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            messageText += emoji
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 📱 ২ নম্বর ছবির হুবহু গ্রুপ ইনফো ও মেম্বার পেজ
        // =========================================================================
        if (showGroupInfoScreen) {
            Dialog(
                onDismissRequest = { showGroupInfoScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                GroupDetailsScreen(
                    messages = messagesList,
                    isGroupMuted = isGroupMuted,
                    stats = liveStats,
                    onToggleMute = {
                        val newState = !isGroupMuted
                        isGroupMuted = newState
                        chatPrefs.edit().putBoolean("is_group_muted", newState).apply()
                        FirebaseChatManager.toggleGroupNotification(!newState)
                    },
                    onLeaveGroup = {
                        isUserJoined = false
                        chatPrefs.edit().putBoolean("is_joined_group", false).apply()
                        coroutineScope.launch { FirebaseChatManager.leaveGroup(currentUserId) }
                        showGroupInfoScreen = false
                        Toast.makeText(context, "You left the community group", Toast.LENGTH_SHORT).show()
                    },
                    onBackClick = { showGroupInfoScreen = false },
                    onImageClick = { previewImageUrl = it },
                    onVideoClick = { previewVideoUrl = it }
                )
            }
        }

        // 📎 অ্যাটাচমেন্ট মেনু
        if (showAttachMenu) {
            ModalBottomSheet(
                onDismissRequest = { showAttachMenu = false },
                containerColor = WhatsAppBarBg
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Share Media", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color(0xFF2A3942), thickness = 0.8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            showAttachMenu = false
                            imagePickerLauncher.launch("image/*")
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(26.dp))
                        Column {
                            Text("Photo / Image", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Fast upload to Cloudflare R2", color = Color(0xFF8696A0), fontSize = 11.5.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            showAttachMenu = false
                            videoPickerLauncher.launch("video/*")
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(26.dp))
                        Column {
                            Text("Video File", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Up to 50 MB with live progress & thumbnail", color = Color(0xFF8696A0), fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // 📋 লং-প্রেস মেনু
        selectedActionMessage?.let { msg ->
            val canDelete = isCurrentUserOwner || (msg.senderId == currentUserId)
            ModalBottomSheet(
                onDismissRequest = { selectedActionMessage = null },
                containerColor = WhatsAppBarBg
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Message Actions", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color(0xFF2A3942), thickness = 0.8.dp)

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

                    if (canDelete) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                coroutineScope.launch { FirebaseChatManager.deleteMessage(msg.id) }
                                selectedActionMessage = null
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            Text(if (isCurrentUserOwner && msg.senderId != currentUserId) "Delete as Owner" else "Delete Message", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 🎬 ভিডিও প্লেয়ার
        previewVideoUrl?.let { vidUrl ->
            Dialog(onDismissRequest = { previewVideoUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
                    IconButton(onClick = { previewVideoUrl = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(0.6f))) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // 🖼️ ইমেজ ভিউয়ার
        previewImageUrl?.let { imgUrl ->
            Dialog(onDismissRequest = { previewImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.95f))) {
                    AsyncImage(model = imgUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    IconButton(onClick = { previewImageUrl = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(0.6f))) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 💬 ২ নম্বর ছবির হুবহু WhatsApp মেসেজ বাবল (Swipe-to-Reply সহ)
// =========================================================================
@Composable
private fun WhatsAppMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    activeAudioUrl: String?,
    onPlayAudio: (String) -> Unit,
    onSwipeToReply: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val timeFormatted = remember(message.timestamp) {
        if (message.timestamp != null) SimpleDateFormat("h:mm a", Locale.US).format(message.timestamp)
        else "Just now"
    }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX.value > 60f) onSwipeToReply()
                        coroutineScope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onDragCancel = { coroutineScope.launch { offsetX.animateTo(0f) } },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 0 || offsetX.value > 0) {
                            coroutineScope.launch { offsetX.snapTo((offsetX.value + dragAmount * 0.6f).coerceIn(0f, 90f)) }
                        }
                    }
                )
            },
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (!isMe) {
                Text(
                    text = message.senderName,
                    color = if (message.isOwner) OwnerGold else Color(0xFF53BDEB),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 10.dp,
                    topEnd = 10.dp,
                    bottomStart = if (isMe) 10.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 10.dp
                ),
                color = if (isMe) WhatsAppSentBubble else WhatsAppReceivedBubble,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {

                    if (!message.replyToName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x28000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.width(3.dp).height(28.dp).background(Color(0xFF00A884)))
                                Column {
                                    Text(message.replyToName, color = Color(0xFF00A884), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(message.replyToText ?: "", color = Color.White.copy(0.8f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (!message.imageUrl.isNullOrBlank() && message.videoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).clickable { onImageClick(message.imageUrl) }
                        ) {
                            AsyncImage(model = message.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }

                    // 🎬 ২ নম্বর ছবির মতো আসল ভিডিও থাম্বনেল
                    if (!message.videoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F2C34))
                                .clickable { onVideoClick(message.videoUrl) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!message.imageUrl.isNullOrBlank()) {
                                AsyncImage(model = message.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                AsyncVideoThumbnailLoader(videoUrl = message.videoUrl, modifier = Modifier.fillMaxSize())
                            }

                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.3f)))

                            Box(
                                modifier = Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    // 🎙️ ১ নম্বর ছবির হুবহু ভয়েস মেসেজ বাবল
                    if (!message.audioUrl.isNullOrBlank()) {
                        val isPlaying = (activeAudioUrl == message.audioUrl)
                        val durationText = String.format(Locale.US, "00:%02d", message.mediaDurationSec)

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF283848))
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0066FF))
                                    .clickable { onPlayAudio(message.audioUrl) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            VoiceWaveformVisualizer(isPlaying = isPlaying)

                            Text(durationText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(end = 4.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PdFlixGreen, modifier = Modifier.size(12.dp))
                                Text("P.D FLIX", color = PdFlixGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 1.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(timeFormatted, color = Color.White.copy(0.6f), fontSize = 10.sp)
                        if (isMe) {
                            Text("✓✓", color = WhatsAppBlueTick, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 🎬 লাইভ আপলোডিং ভিডিও বাবল
@Composable
fun UploadingVideoBubble(
    videoUri: Uri,
    percent: Int,
    secondsLeft: Long,
    onCancel: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(10.dp), color = WhatsAppSentBubble, modifier = Modifier.width(280.dp).height(240.dp)) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncVideoThumbnailLoader(videoUri = videoUri, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(16.dp).clickable { onCancel() })
                    Text("$percent% (${secondsLeft}s left)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AsyncVideoThumbnailLoader(
    videoUri: Uri? = null,
    videoUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(videoUri, videoUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoUri, videoUrl) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (videoUri != null) retriever.setDataSource(context, videoUri)
                else if (!videoUrl.isNullOrBlank()) retriever.setDataSource(videoUrl, HashMap())
                val frame = retriever.getFrameAtTime(1000000)
                retriever.release()
                bitmap = frame
            } catch (_: Exception) {}
        }
    }

    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = modifier.background(Color(0xFF1F2C34)))
    }
}

// 📱 ২ নম্বর ছবির হুবহু গ্রুপ ইনফো ও মেম্বার পেজ
@Composable
fun GroupDetailsScreen(
    messages: List<ChatMessage>,
    isGroupMuted: Boolean,
    stats: LiveGroupStats,
    onToggleMute: () -> Unit,
    onLeaveGroup: () -> Unit,
    onBackClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Media", "Files", "Voice", "Links")
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    val mediaItems = remember(messages) { messages.filter { !it.imageUrl.isNullOrBlank() || !it.videoUrl.isNullOrBlank() } }
    val voiceItems = remember(messages) { messages.filter { !it.audioUrl.isNullOrBlank() } }

    Box(modifier = Modifier.fillMaxSize().background(WhatsAppDarkBg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Group Info", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF1D2636)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(44.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("DramaFlix Community Group", color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.Bold)
                Text("${stats.totalMembers} members, ${stats.onlineMembers} online", color = Color(0xFF8692A6), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(modifier = Modifier.weight(1f).height(44.dp).clickable { onBackClick() }, shape = RoundedCornerShape(10.dp), color = Color(0xFF1B2433)) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Message", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(modifier = Modifier.weight(1f).height(44.dp).clickable { onToggleMute() }, shape = RoundedCornerShape(10.dp), color = Color(0xFF1B2433)) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isGroupMuted) "Unmute" else "Mute", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(modifier = Modifier.weight(1f).height(44.dp).clickable { showLeaveConfirmDialog = true }, shape = RoundedCornerShape(10.dp), color = Color(0xFF1B2433)) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFFF4D4F), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Leave", color = Color(0xFFFF4D4F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF4CAF50)), contentAlignment = Alignment.Center) {
                            Text("S", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Hey Sifat YT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("yheysifat@gmail.com • last seen recently", color = Color(0xFF8692A6), fontSize = 11.5.sp)
                        }
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF4A148C).copy(0.6f), border = BorderStroke(1.dp, Color(0xFF9C27B0))) {
                        Text("Owner", color = Color(0xFFE1BEE7), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs.size) { index ->
                    val tabName = tabs[index]
                    val isSelected = selectedTabIndex == index
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF223048) else Color.Transparent,
                        modifier = Modifier.clickable { selectedTabIndex = index }
                    ) {
                        Text(tabName, color = if (isSelected) TelegramBlue else Color(0xFF8692A6), fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTabIndex) {
                    0 -> {
                        if (mediaItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No shared media yet", color = Color(0xFF8692A6), fontSize = 13.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(mediaItems) { item ->
                                    Box(
                                        modifier = Modifier.aspectRatio(1f).background(Color(0xFF1E2838)).clickable {
                                            if (!item.videoUrl.isNullOrBlank()) onVideoClick(item.videoUrl)
                                            else if (!item.imageUrl.isNullOrBlank()) onImageClick(item.imageUrl)
                                        }
                                    ) {
                                        AsyncImage(model = item.imageUrl ?: item.videoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        if (!item.videoUrl.isNullOrBlank()) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).align(Alignment.Center))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        if (voiceItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No voice notes yet", color = Color(0xFF8692A6), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(voiceItems) { voice ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1C2432)).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp))
                                        Column {
                                            Text(voice.senderName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Voice Note • ${voice.mediaDurationSec}s", color = Color(0xFF8692A6), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No ${tabs[selectedTabIndex]} shared yet", color = Color(0xFF8692A6), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (showLeaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmDialog = false },
                containerColor = Color(0xFF1A2230),
                title = { Text("Leave Group?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to leave DramaFlix Community Group?", color = Color(0xFFCCD5E2), fontSize = 13.5.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveGroup()
                    }) { Text("Leave", color = Color(0xFFFF4D4F), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirmDialog = false }) { Text("Cancel", color = Color(0xFF8692A6)) }
                }
            )
        }
    }
}

// 🌊 অডিও সাউন্ড ওয়েভফর্ম
@Composable
fun VoiceWaveformVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = List(14) { index ->
        if (isPlaying) {
            val anim by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = (8..20).random().toFloat(),
                animationSpec = infiniteRepeatable(tween(300 + index * 30, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar_$index"
            )
            anim
        } else {
            remember { (4..16).random().toFloat() }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun JumpingDotsAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Scale by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1.1f, animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot1")
    val dot2Scale by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1.1f, animationSpec = infiniteRepeatable(tween(500, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot2")
    val dot3Scale by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1.1f, animationSpec = infiniteRepeatable(tween(500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot3")

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).scale(dot1Scale).clip(CircleShape).background(TelegramBlue))
        Box(modifier = Modifier.size(6.dp).scale(dot2Scale).clip(CircleShape).background(TelegramBlue))
        Box(modifier = Modifier.size(6.dp).scale(dot3Scale).clip(CircleShape).background(TelegramBlue))
    }
}
