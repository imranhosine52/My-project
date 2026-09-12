@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)

package com.example.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.ui.VipCrown3DIcon
import com.example.ui.screens.chat.components.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.FirebaseChatManager
import com.example.util.LiveGroupStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

private val WhatsAppDarkBg = Color(0xFF0C1317)
private val WhatsAppBarBg = Color(0xFF1F2C34)

@Composable
fun CommunityChatScreen(
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val chatPrefs = remember { context.getSharedPreferences("play_drama_flix_chat_group_prefs", Context.MODE_PRIVATE) }
    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }

    val currentUserId = remember { authPrefs.getString("user_id", "")?.ifBlank { "guest_${UUID.randomUUID().toString().take(6)}" } ?: "guest" }
    val currentUserName = remember { authPrefs.getString("user_name", "Hey Sifat YT") ?: "Hey Sifat YT" }
    val currentUserEmail = remember { authPrefs.getString("user_email", "yheysifat@gmail.com") ?: "yheysifat@gmail.com" }
    val currentUserAvatar = remember { authPrefs.getString("user_avatar", null) }

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
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }

    var uploadingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var uploadProgressPercent by remember { mutableIntStateOf(0) }
    var uploadSecondsLeft by remember { mutableLongStateOf(0L) }
    var isVideoUploadingActive by remember { mutableStateOf(false) }

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

    // =========================================================================
    // 🎯 রিয়েল-টাইম কীবোর্ড হাইট ডিটেক্টর (নীল দাগের ভেতরের পারফেক্ট অফসেট)
    // =========================================================================
    var dynamicBottomOffsetDp by remember { mutableStateOf(0.dp) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect()
            view.getWindowVisibleDisplayFrame(r)
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val viewBottom = location[1] + view.height
            val coveredHeight = (viewBottom - r.bottom).coerceAtLeast(0)

            dynamicBottomOffsetDp = with(density) { coveredHeight.toDp() }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            try { audioMediaPlayer.release() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
        }
    }

    val liveStats by produceState(initialValue = LiveGroupStats(1, 1)) {
        FirebaseChatManager.getLiveGroupStatsFlow().collect { value = it }
    }

    LaunchedEffect(Unit) {
        while (true) {
            FirebaseChatManager.pingUserPresence(currentUserId, currentUserName, currentUserAvatar)
            delay(20000L)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            selectedVideoUri = null
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedImageUri = null
        }
    }

    val messagesList by produceState<List<ChatMessage>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveMessagesFlow().collect { value = it }
    }

    // 🎯 ইউজার মেসেজ দেখা মাত্রই সার্ভারে সিন (✓✓) স্ট্যাটাস আপডেট হওয়া
    LaunchedEffect(messagesList) {
        if (messagesList.isNotEmpty()) {
            FirebaseChatManager.markMessagesAsRead(currentUserId, messagesList)
        }
    }

    val liveActiveActions by produceState<List<com.example.util.UserChatStatus>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveActiveActionUsersFlow(currentUserId).collect { value = it }
    }

    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    LaunchedEffect(dynamicBottomOffsetDp) {
        if (dynamicBottomOffsetDp > 50.dp && messagesList.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    LaunchedEffect(messageText) {
        if (messageText.isNotBlank()) {
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "typing")
        } else {
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
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "recording")
        } catch (_: Exception) {
            Toast.makeText(context, "Could not record voice", Toast.LENGTH_SHORT).show()
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
                    try {
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

    fun cancelVoiceRecording() {
        try {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            mediaRecorder?.release()
            mediaRecorder = null
            tempAudioFile?.delete()
            isRecordingVoice = false
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
        } catch (_: Exception) {}
    }

    fun sendMessage() {
        if (isSending) return
        val textToSend = messageText.trim()
        val imageUri = selectedImageUri
        val videoUri = selectedVideoUri
        val replyTarget = replyingToMessage

        if (textToSend.isBlank() && imageUri == null && videoUri == null) return

        messageText = ""
        selectedImageUri = null
        selectedVideoUri = null
        replyingToMessage = null
        showEmojiPackCard = false
        isSending = true

        coroutineScope.launch {
            try {
                if (videoUri != null) {
                    uploadingVideoUri = videoUri
                    isVideoUploadingActive = true
                    uploadProgressPercent = 0
                    uploadSecondsLeft = 5L

                    FirebaseChatManager.uploadVideoWithProgressAndSendMessage(
                        context = context,
                        videoUri = videoUri,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderEmail = currentUserEmail,
                        senderAvatar = currentUserAvatar,
                        isVip = isUserVip,
                        captionText = textToSend,
                        replyToMessage = replyTarget,
                        onProgress = { pct, sec ->
                            uploadProgressPercent = pct
                            uploadSecondsLeft = sec
                        },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                    )
                    isVideoUploadingActive = false
                    uploadingVideoUri = null
                } else if (imageUri != null) {
                    FirebaseChatManager.uploadImageAndSendMessage(
                        context = context,
                        imageUri = imageUri,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderEmail = currentUserEmail,
                        senderAvatar = currentUserAvatar,
                        isVip = isUserVip,
                        captionText = textToSend,
                        replyToMessage = replyTarget,
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                    )
                } else {
                    val ok = FirebaseChatManager.sendTextMessage(
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderEmail = currentUserEmail,
                        senderAvatar = currentUserAvatar,
                        isVip = isUserVip,
                        text = textToSend,
                        replyToMessage = replyTarget
                    )
                    if (!ok) {
                        Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isSending = false
                focusManager.clearFocus()
            }
        }
    }

    // =========================================================================
    // 🌟 ফ্লোটিং ওভারলে লেআউট (নীল দাগের ভেতরের অবস্থানে নিখুঁতভাবে থাকবে)
    // =========================================================================
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WhatsAppDarkBg)
    ) {
        // ১. মেসেজের তালিকা (ফুল পেজ ব্যাকগ্রাউন্ড)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(
                top = 74.dp,
                bottom = 76.dp + (if (dynamicBottomOffsetDp > 50.dp) dynamicBottomOffsetDp else 0.dp)
            ),
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

        // ২. ফিক্সড টপ হেডার
        Surface(
            color = WhatsAppBarBg,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
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
                        .clickable { showGroupInfoScreen = true }
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
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

        // ৩. 🎯 টাইপিং বক্স (আপনার নীল দাগের ভেতরের পারফেক্ট পজিশন)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (dynamicBottomOffsetDp > 50.dp) {
                        // কীবোর্ডের ঠিক ৮ ডিপি উপরে নীল দাগের ভেতরে বসবে
                        Modifier.padding(bottom = dynamicBottomOffsetDp + 8.dp)
                    } else {
                        // কীবোর্ড বন্ধ থাকলে নিচের ন্যাভিগেশন বারের ওপর সুন্দরভাবে বসবে
                        Modifier.navigationBarsPadding().padding(bottom = 6.dp)
                    }
                )
        ) {
            AnimatedVisibility(visible = liveActiveActions.isNotEmpty()) {
                val actionUser = liveActiveActions.firstOrNull()
                if (actionUser != null) {
                    val actionText = when (actionUser.action) {
                        "recording" -> "${actionUser.userName} is recording audio 🎙️"
                        "uploading_video" -> "${actionUser.userName} is uploading video 🎬"
                        else -> "${actionUser.userName} is typing..."
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JumpingDotsAnimation()
                        Text(actionText, color = Color(0xFF00A884), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            AnimatedVisibility(visible = replyingToMessage != null) {
                replyingToMessage?.let { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E2834))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.width(3.dp).height(30.dp).background(Color(0xFF2AABEE)))
                            Column {
                                Text(target.senderName, color = Color(0xFF2AABEE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(target.text.ifBlank { if (target.audioUrl != null) "🎤 Voice Message" else if (target.videoUrl != null) "🎬 Video" else "📷 Photo" }, color = Color.White.copy(0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF8696A0), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (showEmojiPackCard) {
                EmojiPackPopupCard(
                    onEmojiSelected = { emoji -> messageText += emoji },
                    onClose = { showEmojiPackCard = false },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            TelegramChatInputBar(
                isUserJoined = isUserJoined,
                isRecordingVoice = isRecordingVoice,
                recordDurationSeconds = recordDurationSeconds,
                messageText = messageText,
                currentUserAvatar = currentUserAvatar,
                currentUserName = currentUserName,
                selectedImageUri = selectedImageUri,
                selectedVideoUri = selectedVideoUri,
                isGroupMuted = isGroupMuted,
                isSending = isSending,
                onJoinGroupClick = {
                    isUserJoined = true
                    chatPrefs.edit().putBoolean("is_joined_group", true).apply()
                    FirebaseChatManager.joinGroup(currentUserId, currentUserName, currentUserAvatar)
                    Toast.makeText(context, "🎉 Joined DramaFlix Community!", Toast.LENGTH_SHORT).show()
                },
                onMessageTextChange = { messageText = it },
                onToggleMuteClick = {
                    val newState = !isGroupMuted
                    isGroupMuted = newState
                    chatPrefs.edit().putBoolean("is_group_muted", newState).apply()
                    FirebaseChatManager.toggleGroupNotification(!newState)
                    Toast.makeText(context, if (newState) "🔕 Muted" else "🔔 Active", Toast.LENGTH_SHORT).show()
                },
                onEmojiPackToggle = { showEmojiPackCard = !showEmojiPackCard },
                onAttachClick = { showAttachMenu = true },
                onClearSelectedMedia = {
                    selectedImageUri = null
                    selectedVideoUri = null
                },
                onStartVoiceRecord = { startRecordingVoice() },
                onCancelVoiceRecord = { cancelVoiceRecording() },
                onSendVoiceRecord = { stopAndSendVoice() },
                onSendMessage = { sendMessage() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

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
                    FirebaseChatManager.leaveGroup(currentUserId)
                    showGroupInfoScreen = false
                },
                onBackClick = { showGroupInfoScreen = false },
                onImageClick = { previewImageUrl = it },
                onVideoClick = { previewVideoUrl = it }
            )
        }
    }

    if (showAttachMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAttachMenu = false },
            containerColor = WhatsAppBarBg
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Share Media", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = Color(0xFF2A3942), thickness = 0.8.dp)

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
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF2AABEE), modifier = Modifier.size(26.dp))
                    Column {
                        Text("Photo / Image", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Select photo & add caption before sending", color = Color(0xFF8696A0), fontSize = 11.5.sp)
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
                        Text("Select video (up to 50 MB) & add caption", color = Color(0xFF8696A0), fontSize = 11.5.sp)
                    }
                }
            }
        }
    }

    if (selectedActionMessage != null) {
        val msg = selectedActionMessage!!
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
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Color(0xFF2AABEE))
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

    previewVideoUrl?.let { vidUrl ->
        ChatVideoPlayerDialog(
            videoUrl = vidUrl,
            onDismiss = { previewVideoUrl = null }
        )
    }

    previewImageUrl?.let { imgUrl ->
        Dialog(onDismissRequest = { previewImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.95f))) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(14.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.6f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
