@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatMessage
import com.example.ui.VipCrown3DIcon
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.FirebaseChatManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    // ইউজারের তথ্য
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

    // ফুলস্ক্রিন ইমেজ প্রিভিউ ডায়ালগ স্টেট
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    // 📸 ইমেজ পিকার লাউঞ্চার
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // ⚡ ফায়ারবেস লাইভ মেসেজ পর্যবেক্ষণ
    val messagesList by produceState<List<ChatMessage>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveMessagesFlow().collect {
            value = it
        }
    }

    // নতুন মেসেজ এলে একদম নিচে স্ক্রোল করা
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    fun sendMessage() {
        if (isSending) return
        val text = messageText.trim()
        val imageUri = selectedImageUri

        if (text.isBlank() && imageUri == null) return

        isSending = true
        coroutineScope.launch {
            if (imageUri != null) {
                val success = FirebaseChatManager.uploadImageAndSendMessage(
                    context = context,
                    imageUri = imageUri,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderAvatar = currentUserAvatar,
                    isVip = isUserVip,
                    captionText = text
                )
                if (success) {
                    selectedImageUri = null
                    messageText = ""
                } else {
                    Toast.makeText(context, "Image upload failed. Try again.", Toast.LENGTH_SHORT).show()
                }
            } else {
                val success = FirebaseChatManager.sendTextMessage(
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderAvatar = currentUserAvatar,
                    isVip = isUserVip,
                    text = text
                )
                if (success) {
                    messageText = ""
                }
            }
            isSending = false
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C14))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. প্রিমিয়াম হেডার বার
            Surface(
                color = Color(0xFF121622),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                .background(Color(0xFF1E2433))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "DramaFlix Community",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                            }
                            Text(
                                text = "Live Discussion & Image Sharing",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    if (isUserVip) {
                        VipCrown3DIcon(modifier = Modifier.size(26.dp, 20.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E2433), thickness = 0.8.dp)

            // 💬 ২. চ্যাট মেসেজ লিস্ট
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
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(48.dp))
                            Text("No messages yet", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Say hi to everyone and start the discussion!", color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messagesList, key = { it.id }) { msg ->
                            val isMe = (msg.senderId == currentUserId)
                            ChatMessageBubble(
                                message = msg,
                                isMe = isMe,
                                onImageClick = { url -> previewImageUrl = url }
                            )
                        }
                    }
                }
            }

            // 🖼️ ৩. সিলেক্ট করা ছবি সেন্ড প্রিভিউ ব্যানার
            AnimatedVisibility(visible = selectedImageUri != null) {
                selectedImageUri?.let { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B28))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text("Image selected ready to send", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        IconButton(onClick = { selectedImageUri = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel image", tint = Color(0xFFFF5252))
                        }
                    }
                }
            }

            // ⌨️ ৪. বটম ইনপুট বার
            Surface(
                color = Color(0xFF121622),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 📸 ছবি সিলেক্ট বাটন
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C2233))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Send Image",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 📝 টেক্সট ইনপুট বক্স
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A2030))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (messageText.isEmpty() && selectedImageUri == null) {
                            Text("Type a message...", color = Color(0xFF64748B), fontSize = 13.5.sp)
                        } else if (messageText.isEmpty() && selectedImageUri != null) {
                            Text("Add caption (optional)...", color = Color(0xFF64748B), fontSize = 13.5.sp)
                        }

                        BasicTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(Color(0xFF00E5FF)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 🚀 সেন্ড বাটন
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF007AFF), Color(0xFF00D166))
                                )
                            )
                            .clickable(enabled = !isSending) { sendMessage() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 🖼️ ফুলস্ক্রিন ইমেজ প্রিভিউ পপ-আপ
        previewImageUrl?.let { fullUrl ->
            Dialog(
                onDismissRequest = { previewImageUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                ) {
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = "Full image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    IconButton(
                        onClick = { previewImageUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 💬 চ্যাট বাবল কম্পোনেন্ট (VIP ব্যাজ + ইমেজ প্রিভিউ সহ)
// =========================================================================
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onImageClick: (String) -> Unit
) {
    val timeFormatted = remember(message.timestamp) {
        if (message.timestamp != null) {
            SimpleDateFormat("hh:mm a", Locale.US).format(message.timestamp)
        } else {
            "Just now"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // অন্যের মেসেজে বাঁয়ে অবতার
        if (!isMe) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C3548)),
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
                    Text(
                        text = message.senderName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // অন্যের নামের সাথে VIP ক্রাউন
            if (!isMe) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = message.senderName,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (message.isVip) {
                        VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                    }
                }
            }

            // বাবল কনটেইনার
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isMe) 14.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 14.dp
                ),
                color = if (isMe) Color(0xFF007AFF) else Color(0xFF1E2435),
                border = if (isMe) null else BorderStroke(0.6.dp, Color(0xFF2E3850))
            ) {
                Column(modifier = Modifier.padding(if (message.imageUrl != null) 4.dp else 10.dp)) {
                    // শেয়ার করা ছবি
                    if (!message.imageUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onImageClick(message.imageUrl) }
                        ) {
                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = "Shared image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // ক্যাপশন বা টেক্সট
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = if (message.imageUrl != null) 6.dp else 0.dp, vertical = if (message.imageUrl != null) 6.dp else 0.dp)
                        )
                    }

                    // সময়
                    Text(
                        text = timeFormatted,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}
