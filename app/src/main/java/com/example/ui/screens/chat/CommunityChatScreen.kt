@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)

package com.example.ui.screens.chat

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
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.PinnedMessageInfo
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.screens.chat.components.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.FirebaseChatManager
import com.example.util.LiveGroupStats
import com.example.util.UserChatStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

private val WhatsAppDarkBg = Color(0xFF0C1317)
private val WhatsAppBarBg = Color(0xFF1F2C34)

// 🌟 একাধিক ইউজারের অ্যাকশন টেক্সট ফরম্যাটার
private fun formatActiveActionsText(actions: List<UserChatStatus>): String {
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
    } else if (count == 2) {
        val u1 = actions[0].userName
        val u2 = actions[1].userName
        val allTyping = actions.all { it.action == "typing" }
        if (allTyping) "$u1 and $u2 are typing... ✍️"
        else "$u1 and $u2 are active in chat..."
    } else {
        val u1 = actions[0].userName
        val others = count - 1
        "$u1 and $others others are active... ✍️"
    }
}

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

    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val chatPrefs = remember { context.getSharedPreferences("play_drama_flix_chat_group_prefs", Context.MODE_PRIVATE) }
    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }

    val isUserLoggedIn = authState.isLoggedIn || authPrefs.getString("user_id", "").isNullOrBlank().not()
    var showAuthSheet by remember { mutableStateOf(false) }

    LaunchedEffect(isUserLoggedIn) {
        if (!isUserLoggedIn) {
            delay(300)
            showAuthSheet = true
        }
    }

    val currentUserEmail = remember(authState) { authPrefs.getString("user_email", "yheysifat@gmail.com") ?: "yheysifat@gmail.com" }
    val isCurrentUserOwner = remember(currentUserEmail) { FirebaseChatManager.isRootAdmin(currentUserEmail) }

    val currentUserId = remember(authState, isCurrentUserOwner) {
        if (isCurrentUserOwner) "owner_yheysifat"
        else {
            val savedId = authPrefs.getString("user_id", "")
            if (!savedId.isNullOrBlank()) savedId
            else {
                val existingGuest = authPrefs.getString("permanent_guest_id", "")
                if (!existingGuest.isNullOrBlank()) existingGuest
                else {
                    val newGId = "guest_${UUID.randomUUID().toString().take(8)}"
                    authPrefs.edit().putString("permanent_guest_id", newGId).apply()
                    newGId
                }
            }
        }
    }

    val currentUserName = remember(authState) { authPrefs.getString("user_name", "Hey Sifat YT") ?: "Hey Sifat YT" }
    val currentUserAvatar = remember(authState) { authPrefs.getString("user_avatar", null) }

    val isUserVip = remember(authState) {
        isCurrentUserOwner || authPrefs.getBoolean("is_vip", false) ||
        (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))
    }

    var isUserJoined by remember { mutableStateOf(chatPrefs.getBoolean("is_joined_group", isCurrentUserOwner)) }
    var isGroupMuted by remember { mutableStateOf(chatPrefs.getBoolean("is_group_muted", false)) }

    LaunchedEffect(currentUserId, currentUserEmail) {
        if (isCurrentUserOwner) {
            isUserJoined = true
            chatPrefs.edit().putBoolean("is_joined_group", true).apply()
            FirebaseChatManager.joinGroup(currentUserId, currentUserName, currentUserAvatar, currentUserEmail)
        } else {
            val alreadyJoined = FirebaseChatManager.isUserAlreadyJoined(currentUserId, currentUserEmail)
            if (alreadyJoined) {
                isUserJoined = true
                chatPrefs.edit().putBoolean("is_joined_group", true).apply()
            }
        }
    }

    var showGroupInfoScreen by remember { mutableStateOf(false) }
    var showTopDropDownMenu by remember { mutableStateOf(false) }

    var messageText by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }

    var typingStatusJob by remember { mutableStateOf<Job?>(null) }

    val selectedMessageIds = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedMessageIds.isNotEmpty()

    var uploadingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var uploadProgressPercent by remember { mutableIntStateOf(0) }
    var uploadSecondsLeft by remember { mutableLongStateOf(0L) }
    var isVideoUploadingActive by remember { mutableStateOf(false) }

    var showEmojiPackCard by remember { mutableStateOf(false) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableLongStateOf(0L) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }
    var recordingTimerJob by remember { mutableStateOf<Job?>(null) }

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
            try {
                mediaRecorder?.release()
                tempAudioFile?.delete()
                typingStatusJob?.cancel()
            } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedMessageIds.clear()
    }

    val liveStats by produceState(initialValue = LiveGroupStats(1, 1)) {
        FirebaseChatManager.getLiveGroupStatsFlow().collect { value = it }
    }

    val pinnedMessageInfo by produceState<PinnedMessageInfo?>(initialValue = null) {
        FirebaseChatManager.getLivePinnedMessageFlow().collect { value = it }
    }

    val isCurrentUserBlocked by produceState(initialValue = false) {
        FirebaseChatManager.isUserBlockedFlow(currentUserId).collect { value = it }
    }

    LaunchedEffect(currentUserId, isUserJoined) {
        if (isUserJoined) {
            FirebaseChatManager.subscribeToUserTopic(currentUserId)
            while (isUserJoined) {
                FirebaseChatManager.pingUserPresence(currentUserId, currentUserName, currentUserAvatar, currentUserEmail)
                delay(25000L)
            }
        }
    }

    val multiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris = (selectedImageUris + uris).distinct().take(10)
            selectedVideoUri = null
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "uploading_photo")
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedImageUris = emptyList()
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "uploading_video")
        }
    }

    val messagesList by produceState<List<ChatMessage>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveMessagesFlow().collect { value = it }
    }

    LaunchedEffect(messagesList) {
        if (messagesList.isNotEmpty()) {
            FirebaseChatManager.markMessagesAsRead(currentUserId, messagesList)
        }
    }

    val liveActiveActions by produceState<List<UserChatStatus>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveActiveActionUsersFlow(currentUserId).collect { value = it }
    }

    // =========================================================================
    // 🚀 স্মার্ট স্ক্রোলিং মেকানিজম (প্রথমবার কোনো স্ক্রোল অ্যানিমেশন ছাড়া নিচে নামবে)
    // =========================================================================
    var isFirstLoadDone by remember { mutableStateOf(false) }
    var previousMessageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(messagesList.size) {
        val currentCount = messagesList.size
        if (currentCount > 0 && !isSelectionMode) {
            if (!isFirstLoadDone) {
                // ⚡ প্রথমবার ওপেন করলে পলকের মধ্যে (ইনস্ট্যান্ট জাম্প) শেষের মেসেজ দেখাবে
                listState.scrollToItem(currentCount - 1)
                isFirstLoadDone = true
            } else if (currentCount > previousMessageCount) {
                // 💬 শুধু চ্যাট চলাকালীন নতুন মেসেজ আসলে নিচে স্মুথ স্ক্রোল করবে
                listState.animateScrollToItem(currentCount - 1)
            }
            previousMessageCount = currentCount
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messagesList.isNotEmpty() && !isSelectionMode) {
            listState.scrollToItem(messagesList.size - 1)
        }
    }

    val bottomInsetModifier = Modifier.windowInsetsPadding(
        if (isImeVisible) WindowInsets.ime
        else WindowInsets.navigationBars
    )

    // =========================================================================
    // 🎙️ ভয়েস রেকর্ডিং ফাংশনসমূহ
    // =========================================================================
    fun executeStartRecordingVoice() {
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

            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "recording")
        } catch (e: Exception) {
            Log.e("ChatVoice", "Recorder error: ${e.message}", e)
            Toast.makeText(context, "Could not record voice", Toast.LENGTH_SHORT).show()
            isRecordingVoice = false
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            executeStartRecordingVoice()
        } else {
            Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startRecordingVoice() {
        if (!isUserLoggedIn) {
            showAuthSheet = true
            return
        }
        if (isCurrentUserBlocked) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            executeStartRecordingVoice()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopAndSendVoice() {
        try {
            recordingTimerJob?.cancel()
            recordingTimerJob = null

            try { mediaRecorder?.stop() } catch (_: RuntimeException) {}
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVoice = false

            val file = tempAudioFile
            val duration = if (recordDurationSeconds < 1L) 1L else recordDurationSeconds

            if (file != null && file.exists() && file.length() > 0) {
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
                        FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
                    }
                }
            } else {
                tempAudioFile?.delete()
                FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
            }
        } catch (_: Exception) {
            isRecordingVoice = false
            isSending = false
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
        }
    }

    fun cancelVoiceRecording() {
        try {
            recordingTimerJob?.cancel()
            recordingTimerJob = null
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            mediaRecorder?.release()
            mediaRecorder = null
            tempAudioFile?.delete()
            isRecordingVoice = false
            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
        } catch (_: Exception) {}
    }

    fun sendMessage() {
        if (!isUserLoggedIn) {
            showAuthSheet = true
            return
        }
        if (isSending || isCurrentUserBlocked) return
        val textToSend = messageText.trim()
        val imageUris = selectedImageUris
        val videoUri = selectedVideoUri
        val replyTarget = replyingToMessage

        if (textToSend.isBlank() && imageUris.isEmpty() && videoUri == null) return

        messageText = ""
        selectedImageUris = emptyList()
        selectedVideoUri = null
        replyingToMessage = null
        showEmojiPackCard = false
        isSending = true

        typingStatusJob?.cancel()
        FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")

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
                } else if (imageUris.isNotEmpty()) {
                    FirebaseChatManager.uploadMultipleImagesAndSendMessage(
                        context = context,
                        imageUris = imageUris,
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
                FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhatsAppDarkBg)
    ) {
        // ১. ফিক্সড টপ হেডার
        Surface(
            color = WhatsAppBarBg,
            shadowElevation = 4.dp,
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

                Box {
                    IconButton(
                        onClick = { showTopDropDownMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showTopDropDownMenu,
                        onDismissRequest = { showTopDropDownMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF1E2834))
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Group Info", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2AABEE), modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showTopDropDownMenu = false
                                showGroupInfoScreen = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Share Group Link", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showTopDropDownMenu = false
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Join DramaFlix Community Group:\nhttps://playdramaflix.com/community")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Group Link"))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (isGroupMuted) "Unmute Notifications" else "Mute Notifications", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    if (isGroupMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showTopDropDownMenu = false
                                val newState = !isGroupMuted
                                isGroupMuted = newState
                                chatPrefs.edit().putBoolean("is_group_muted", newState).apply()
                                FirebaseChatManager.toggleGroupNotification(!newState)
                                Toast.makeText(context, if (newState) "🔕 Muted" else "🔔 Unmuted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // ২. পিনড মেসেজ ব্যানার
        pinnedMessageInfo?.let { pinInfo ->
            PinnedMessageBanner(
                pinnedInfo = pinInfo,
                canUnpin = isCurrentUserOwner,
                onBannerClick = { msgId ->
                    coroutineScope.launch {
                        val targetIdx = messagesList.indexOfFirst { it.id == msgId }
                        if (targetIdx != -1) {
                            listState.animateScrollToItem(targetIdx)
                        }
                    }
                },
                onUnpinClick = { msgId ->
                    coroutineScope.launch {
                        FirebaseChatManager.unpinMessage(msgId)
                    }
                }
            )
        }

        // ৩. মেসেজ লিস্ট
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messagesList, key = { it.id }) { msg ->
                val isMe = (msg.senderId == currentUserId)
                val isSelected = selectedMessageIds.contains(msg.id)

                WhatsAppMessageBubble(
                    message = msg,
                    isMe = isMe,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
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
                    onLongClick = {
                        if (isSelectionMode) {
                            if (isSelected) selectedMessageIds.remove(msg.id) else selectedMessageIds.add(msg.id)
                        } else {
                            selectedActionMessage = msg
                        }
                    },
                    onClick = {
                        if (isSelectionMode) {
                            if (isSelected) selectedMessageIds.remove(msg.id) else selectedMessageIds.add(msg.id)
                        }
                    },
                    onShareForward = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Voice note from ${msg.senderName} in DramaFlix Community")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Forward Voice"))
                    }
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

        // ৪. লাইভ টাইপিং ও অ্যাকশন বার
        AnimatedVisibility(
            visible = liveActiveActions.isNotEmpty(),
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
        ) {
            val actionText = formatActiveActionsText(liveActiveActions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JumpingDotsAnimation(dotColor = Color(0xFF00E676))
                Text(
                    text = actionText,
                    color = Color(0xFF00E676),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ৫. রিপ্লাই ব্যানার
        AnimatedVisibility(
            visible = replyingToMessage != null,
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(180))
        ) {
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

        // ৬. ইমোজি প্যাক
        AnimatedVisibility(
            visible = showEmojiPackCard,
            enter = expandVertically(tween(220)) + fadeIn(),
            exit = shrinkVertically(tween(220)) + fadeOut()
        ) {
            EmojiPackPopupCard(
                onEmojiSelected = { emoji -> messageText += emoji },
                onClose = { showEmojiPackCard = false },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // ৭. টাইপিং ইনপুট বার
        if (!isUserLoggedIn) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2834),
                border = BorderStroke(1.dp, Color(0xFF2AABEE).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .then(bottomInsetModifier)
                    .clickable { showAuthSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, tint = Color(0xFF2AABEE), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log in to join chat & send messages",
                        color = Color(0xFF2AABEE),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (isCurrentUserBlocked) {
            Surface(
                color = Color(0xFF261214),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .then(bottomInsetModifier)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                    Text(
                        text = "You are blocked by Admin from sending messages in this community group.",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            TelegramChatInputBar(
                isUserJoined = isUserJoined,
                isRecordingVoice = isRecordingVoice,
                recordDurationSeconds = recordDurationSeconds,
                messageText = messageText,
                currentUserAvatar = currentUserAvatar,
                currentUserName = currentUserName,
                selectedImageUris = selectedImageUris,
                selectedVideoUri = selectedVideoUri,
                isGroupMuted = isGroupMuted,
                isSending = isSending,
                onJoinGroupClick = {
                    isUserJoined = true
                    chatPrefs.edit().putBoolean("is_joined_group", true).apply()
                    FirebaseChatManager.joinGroup(currentUserId, currentUserName, currentUserAvatar, currentUserEmail)
                    Toast.makeText(context, "🎉 Joined DramaFlix Community!", Toast.LENGTH_SHORT).show()
                },
                onMessageTextChange = { newText ->
                    messageText = newText
                    if (newText.isNotBlank()) {
                        typingStatusJob?.cancel()
                        FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "typing")
                        typingStatusJob = coroutineScope.launch {
                            delay(3500L)
                            FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
                        }
                    } else {
                        typingStatusJob?.cancel()
                        FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
                    }
                },
                onToggleMuteClick = {
                    val newState = !isGroupMuted
                    isGroupMuted = newState
                    chatPrefs.edit().putBoolean("is_group_muted", newState).apply()
                    FirebaseChatManager.toggleGroupNotification(!newState)
                    Toast.makeText(context, if (newState) "🔕 Muted" else "🔔 Active", Toast.LENGTH_SHORT).show()
                },
                onEmojiPackToggle = { showEmojiPackCard = !showEmojiPackCard },
                onAttachClick = { showAttachMenu = true },
                onRemoveSingleImage = { uri -> selectedImageUris = selectedImageUris - uri },
                onClearSelectedMedia = {
                    selectedImageUris = emptyList()
                    selectedVideoUri = null
                    FirebaseChatManager.setUserActionStatus(currentUserId, currentUserName, "idle")
                },
                onStartVoiceRecord = { startRecordingVoice() },
                onCancelVoiceRecord = { cancelVoiceRecording() },
                onSendVoiceRecord = { stopAndSendVoice() },
                onSendMessage = { sendMessage() },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(bottomInsetModifier)
            )
        }
    }

    if (showAuthSheet) {
        AuthBottomSheetDialog(
            viewModel = viewModel,
            onDismiss = { showAuthSheet = false }
        )
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
                isCurrentUserOwner = isCurrentUserOwner,
                onToggleMute = {
                    val newState = !isGroupMuted
                    isGroupMuted = newState
                    chatPrefs.edit().putBoolean("is_group_muted", newState).apply()
                    FirebaseChatManager.toggleGroupNotification(!newState)
                },
                onLeaveGroup = {
                    isUserJoined = false
                    chatPrefs.edit().putBoolean("is_joined_group", false).apply()
                    FirebaseChatManager.leaveGroup(currentUserId, currentUserEmail)
                    showGroupInfoScreen = false
                    Toast.makeText(context, "You left the community group", Toast.LENGTH_SHORT).show()
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
                            multiImagePickerLauncher.launch("image/*")
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF2AABEE), modifier = Modifier.size(26.dp))
                    Column {
                        Text("Photos / Images (Multiple)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Select single or multiple photos together", color = Color(0xFF8696A0), fontSize = 11.5.sp)
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
        val isSenderNotOwner = msg.senderEmail != FirebaseChatManager.ROOT_ADMIN_EMAIL && msg.senderId != currentUserId

        ModalBottomSheet(
            onDismissRequest = { selectedActionMessage = null },
            containerColor = WhatsAppBarBg
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Message Options", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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

                if (isCurrentUserOwner) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            coroutineScope.launch {
                                FirebaseChatManager.pinMessage(msg, currentUserName)
                                Toast.makeText(context, "Message pinned to top!", Toast.LENGTH_SHORT).show()
                            }
                            selectedActionMessage = null
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, tint = Color(0xFFFFB300))
                        Text(if (msg.isPinned) "Re-pin Message" else "Pin Message to Top", color = Color(0xFFFFB300), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedMessageIds.add(msg.id)
                        selectedActionMessage = null
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color(0xFF00E676))
                    Text("Select Multiple Messages", color = Color.White, fontSize = 14.sp)
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

                if (isCurrentUserOwner && isSenderNotOwner) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            coroutineScope.launch {
                                FirebaseChatManager.kickUser(msg.senderId)
                                Toast.makeText(context, "${msg.senderName} kicked from group", Toast.LENGTH_SHORT).show()
                            }
                            selectedActionMessage = null
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFFF9800))
                        Text("Kick User (${msg.senderName})", color = Color(0xFFFF9800), fontSize = 14.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            coroutineScope.launch {
                                FirebaseChatManager.blockUser(msg.senderId, msg.senderName, msg.senderEmail)
                                Toast.makeText(context, "${msg.senderName} has been blocked!", Toast.LENGTH_SHORT).show()
                            }
                            selectedActionMessage = null
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF5252))
                        Text("Block & Ban User", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
