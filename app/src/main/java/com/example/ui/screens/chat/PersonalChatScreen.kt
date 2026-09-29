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
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.text.style.TextOverflow // 👈 ফিক্সড ইমপোর্ট
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatMessage
import com.example.ui.screens.chat.components.ChatImageCollage
import com.example.ui.screens.chat.components.WhatsAppVoicePlayer
import com.example.util.FirebaseChatManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    val conversationChannelId = remember(myUserId, recipientUserId) {
        val sorted = listOf(myUserId, recipientUserId).sorted()
        "direct_${sorted[0]}_${sorted[1]}"
    }

    var inputText by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableLongStateOf(0L) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }
    var recordingTimerJob by remember { mutableStateOf<Job?>(null) }

    var activeAudioUrl by remember { mutableStateOf<String?>(null) }
    val audioPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try { audioPlayer.release() } catch (_: Exception) {}
            try {
                mediaRecorder?.release()
                tempAudioFile?.delete()
                recordingTimerJob?.cancel()
            } catch (_: Exception) {}
        }
    }

    val messagesList by produceState<List<ChatMessage>>(initialValue = emptyList(), conversationChannelId) {
        getDirectChatMessagesFlow(conversationChannelId).collect { value = it }
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
                        sendDirectVoiceMessage(
                            conversationId = conversationChannelId,
                            audioFile = file,
                            durationSec = duration,
                            senderId = myUserId,
                            senderName = myUserName,
                            senderAvatar = myUserAvatar
                        )
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

    fun sendMessage() {
        val text = inputText.trim()
        val images = selectedImageUris

        if (text.isBlank() && images.isEmpty()) return

        inputText = ""
        selectedImageUris = emptyList()
        isSending = true

        coroutineScope.launch {
            try {
                if (images.isNotEmpty()) {
                    sendDirectImageMessage(
                        context = context,
                        conversationId = conversationChannelId,
                        imageUris = images,
                        senderId = myUserId,
                        senderName = myUserName,
                        senderAvatar = myUserAvatar,
                        caption = text
                    )
                } else {
                    sendDirectTextMessage(
                        conversationId = conversationChannelId,
                        senderId = myUserId,
                        senderName = myUserName,
                        senderAvatar = myUserAvatar,
                        text = text
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
        // 🔝 ১. পার্সোনাল চ্যাট হেডার বার
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
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222B38)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(recipientUserAvatar ?: "https://ui-avatars.com/api/?name=$recipientUserName&background=00E676&color=000")
                                .crossfade(true)
                                .build(),
                            contentDescription = recipientUserName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column {
                        Text(
                            text = recipientUserName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "online",
                            color = ActionGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { Toast.makeText(context, "Voice call started", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Call, contentDescription = "Voice Call", tint = Color.White)
                    }

                    IconButton(
                        onClick = { Toast.makeText(context, "Video call started", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Videocam, contentDescription = "Video Call", tint = Color.White)
                    }
                }
            }
        }

        // =========================================================================
        // 💬 ২. মেসেজ বাবল লিস্ট
        // =========================================================================
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
                                    timeFormatted = formatDmTime(msg.timestamp),
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
                                        text = formatDmTime(msg.timestamp) + if (isMe) " ✓✓" else "",
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

        // =========================================================================
        // ✍️ ৩. ইনপুট বার
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
                                onValueChange = { inputText = it },
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

private fun getDirectChatMessagesFlow(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
    val firestore = FirebaseFirestore.getInstance()
    val listener = firestore.collection("direct_conversations")
        .document(conversationId)
        .collection("messages")
        .orderBy("timestamp", Query.Direction.ASCENDING)
        .limitToLast(200)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val list = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
            }
            trySend(list)
        }
    awaitClose { listener.remove() }
}

private suspend fun sendDirectTextMessage(
    conversationId: String,
    senderId: String,
    senderName: String,
    senderAvatar: String?,
    text: String
) = withContext(Dispatchers.IO) {
    try {
        val firestore = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "senderAvatar" to senderAvatar,
            "text" to text.trim(),
            "imageUrl" to null,
            "imageUrls" to emptyList<String>(),
            "videoUrl" to null,
            "audioUrl" to null,
            "mediaDurationSec" to 0L,
            "isRead" to false,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("direct_conversations")
            .document(conversationId)
            .collection("messages")
            .add(data)
            .await()
    } catch (_: Exception) {}
}

private suspend fun sendDirectVoiceMessage(
    conversationId: String,
    audioFile: File,
    durationSec: Long,
    senderId: String,
    senderName: String,
    senderAvatar: String?
) = withContext(Dispatchers.IO) {
    try {
        val firestore = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "senderAvatar" to senderAvatar,
            "text" to "",
            "audioUrl" to "https://playdramaflix.com/audio/voice_${System.currentTimeMillis()}.m4a",
            "mediaDurationSec" to durationSec,
            "isRead" to false,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("direct_conversations")
            .document(conversationId)
            .collection("messages")
            .add(data)
            .await()
    } catch (_: Exception) {}
}

private suspend fun sendDirectImageMessage(
    context: Context,
    conversationId: String,
    imageUris: List<Uri>,
    senderId: String,
    senderName: String,
    senderAvatar: String?,
    caption: String
) = withContext(Dispatchers.IO) {
    try {
        val firestore = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "senderAvatar" to senderAvatar,
            "text" to caption.trim(),
            "imageUrl" to imageUris.firstOrNull()?.toString(),
            "imageUrls" to imageUris.map { it.toString() },
            "isRead" to false,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("direct_conversations")
            .document(conversationId)
            .collection("messages")
            .add(data)
            .await()
    } catch (_: Exception) {}
}

private fun formatDmTime(date: Date?): String {
    return if (date != null) {
        SimpleDateFormat("h:mm a", Locale.US).format(date)
    } else "Just now"
}
