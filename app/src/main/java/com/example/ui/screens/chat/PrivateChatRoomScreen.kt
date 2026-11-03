@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)

package com.example.ui.screens.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachFile
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.LocalPrivateMessageEntity
import com.example.ui.VipCrown3DIcon
import com.example.ui.viewmodel.PrivateChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 🎨 টেলিগ্রাম কালার প্যালেট
private val TelegramChatBg = Color(0xFF0E1621)
private val TelegramHeaderBg = Color(0xFF17212B)
private val TelegramBubbleSent = Color(0xFF2B5278)
private val TelegramBubbleReceived = Color(0xFF182533)
private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramOnlineGreen = Color(0xFF00E676)
private val TelegramMuted = Color(0xFF7E8D9F)
private val TelegramBlueTick = Color(0xFF53BDEB)
private val TelegramInputBg = Color(0xFF212D3B)

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun PrivateChatRoomScreen(
    viewModel: PrivateChatViewModel,
    myUserId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // কিবোর্ড যেন স্ক্রিন জাম্প না করে
    DisposableEffect(activity) {
        val originalMode = activity?.window?.attributes?.softInputMode ?: WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {
            activity?.window?.setSoftInputMode(originalMode)
        }
    }

    val chatState by viewModel.chatRoomUiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showMenuDropdown by remember { mutableStateOf(false) }

    // ব্যাক হ্যান্ডলার
    val handleExit: () -> Unit = {
        viewModel.closeChatRoom()
        onBackClick()
    }
    BackHandler { handleExit() }

    // নতুন মেসেজ আসলে নিচে স্মুথ অটো-স্ক্রোল
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            delay(100L)
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && chatState.messages.isNotEmpty()) {
            delay(100L)
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    // ইমেজ আপলোড লাউঞ্চার
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // নির্বাচিত ছবি পাঠানো
            viewModel.sendMessage(
                senderId = myUserId,
                receiverId = chatState.peerId,
                messageText = "",
                mediaUrl = uri.toString(),
                mediaType = "image"
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TelegramChatBg)
    ) {
        // =========================================================================
        // 🔝 ১. টেলিগ্রাম ফ্রস্টেড হেডার বার
        // =========================================================================
        Surface(
            color = TelegramHeaderBg,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = handleExit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // প্রোফাইল অবতার
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222E3A)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(chatState.peerAvatar ?: "https://ui-avatars.com/api/?name=${chatState.peerName}&background=1E293B&color=FACC15&bold=true")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = chatState.peerName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (chatState.isPeerOnline) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(TelegramOnlineGreen)
                                    .border(1.5.dp, TelegramHeaderBg, CircleShape)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = chatState.peerName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (chatState.isPeerVip) {
                                VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                            }
                        }

                        Text(
                            text = if (chatState.isPeerOnline) "online" else "last seen recently",
                            color = if (chatState.isPeerOnline) TelegramOnlineGreen else TelegramMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }

                // মেনু অপশন (Clear Chat ইত্যাদি)
                Box {
                    IconButton(
                        onClick = { showMenuDropdown = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenuDropdown,
                        onDismissRequest = { showMenuDropdown = false },
                        modifier = Modifier
                            .background(Color(0xFF1E2834))
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear Chat History", color = Color(0xFFFF5252), fontSize = 13.5.sp, fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenuDropdown = false
                                viewModel.clearChat(chatState.chatRoomId)
                            }
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 💬 ২. মেসেজ হিস্ট্রি লিস্ট
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
            items(
                items = chatState.messages,
                key = { it.id }
            ) { message ->
                val isMe = message.senderId == myUserId

                PrivateMessageBubble(
                    message = message,
                    isMe = isMe,
                    onImageClick = { previewImageUrl = it },
                    onSwipeToReply = { viewModel.setReplyingTo(message) }
                )
            }
        }

        // =========================================================================
        // ↩️ ৩. রিপ্লাই ব্যানার
        // =========================================================================
        AnimatedVisibility(
            visible = chatState.replyingToMessage != null,
            enter = expandVertically(tween(180)) + fadeIn(),
            exit = shrinkVertically(tween(180)) + fadeOut()
        ) {
            chatState.replyingToMessage?.let { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E2834))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.width(3.dp).height(26.dp).background(TelegramBlue))
                        Column {
                            Text(
                                text = if (target.senderId == myUserId) "You" else chatState.peerName,
                                color = TelegramBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = target.messageText.ifBlank { "Media" },
                                color = Color.White.copy(0.7f),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.setReplyingTo(null) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TelegramMuted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        // =========================================================================
        // ⌨️ ৪. টেলিগ্রাম স্লিম ইনপুট বার
        // =========================================================================
        Surface(
            color = TelegramChatBg,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ইনপুট ক্যাপসুল
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TelegramInputBg)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputText.isEmpty()) {
                            Text("Message...", color = TelegramMuted, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(TelegramBlue),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Default
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ছবি সিলেক্ট আইকন
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = "Attach Photo",
                        tint = TelegramMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { imagePickerLauncher.launch("image/*") }
                    )
                }

                // টেলিগ্রাম ব্লু সেন্ড বাটন
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue)
                        .clickable {
                            val textToSend = inputText.trim()
                            if (textToSend.isNotEmpty()) {
                                viewModel.sendMessage(
                                    senderId = myUserId,
                                    receiverId = chatState.peerId,
                                    messageText = textToSend
                                )
                                inputText = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
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

    // পূর্ণাঙ্গ ছবি প্রিভিউ ডায়ালগ
    previewImageUrl?.let { imgUrl ->
        Dialog(
            onDismissRequest = { previewImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.95f)),
                contentAlignment = Alignment.Center
            ) {
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

// =============================================================================
// 💬 ৫. টেলিগ্রাম মেসেজ বাবল ও টিক্স মার্কস কম্পোনেন্ট
// =============================================================================

@Composable
private fun PrivateMessageBubble(
    message: LocalPrivateMessageEntity,
    isMe: Boolean,
    onImageClick: (String) -> Unit,
    onSwipeToReply: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp, horizontal = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) TelegramBubbleSent else TelegramBubbleReceived,
            modifier = Modifier.widthIn(min = 60.dp, max = 280.dp)
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // ছবি মেসেজ
                if (!message.mediaUrl.isNullOrBlank() && message.mediaType.equals("image", ignoreCase = true)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF141A24))
                            .clickable { onImageClick(message.mediaUrl) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(message.mediaUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // টেক্সট এবং সময় ও টিক মার্ক রো
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.wrapContentSize()
                ) {
                    if (message.messageText.isNotBlank()) {
                        Text(
                            text = message.messageText,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    // সময় ও টিক্স (✓ / ✓✓)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(bottom = 1.dp)
                    ) {
                        Text(
                            text = message.timeFormatted.ifBlank { "Recent" },
                            color = TelegramMuted,
                            fontSize = 10.sp
                        )

                        // 🎯 ডেলিভারি টিক্স মার্ক
                        if (isMe) {
                            val tickColor = if (message.status == 3) TelegramBlueTick else TelegramMuted
                            val tickIcon = if (message.status >= 2) "✓✓" else "✓"

                            Text(
                                text = tickIcon,
                                color = tickColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
