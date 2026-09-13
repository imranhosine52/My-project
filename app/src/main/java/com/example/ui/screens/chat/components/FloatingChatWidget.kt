package com.example.ui.screens.chat.components

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.util.FirebaseChatManager
import kotlinx.coroutines.launch

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
    var isExpanded by remember { mutableStateOf(false) }
    var messageInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val miniListState = rememberLazyListState()

    val messages by produceState<List<ChatMessage>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveMessagesFlow().collect { value = it }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isExpanded) {
            miniListState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .padding(bottom = 70.dp, end = 16.dp), // 👈 বটম বারের ঠিক ওপরে অবস্থান করবে
        contentAlignment = Alignment.BottomEnd
    ) {
        // =========================================================================
        // 💬 ১. স্ক্রিনশটের মতো পপ-আপ মিনি চ্যাট উইন্ডো
        // =========================================================================
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(initialScale = 0.8f, animationSpec = tween(220)) + fadeIn() + slideInVertically { it / 3 },
            exit = scaleOut(targetScale = 0.8f, animationSpec = tween(200)) + fadeOut() + slideOutVertically { it / 3 }
        ) {
            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .height(440.dp)
                    .padding(bottom = 60.dp)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF141A24),
                border = BorderStroke(1.2.dp, Color(0xFF2A384C))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 🔝 উইন্ডো হেডার
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C2432))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Text(
                                text = "DramaFlix Live Chat",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // ⛶ ফুলস্ক্রিন বাটন
                            IconButton(
                                onClick = {
                                    isExpanded = false
                                    onOpenFullScreenChat()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.OpenInFull, contentDescription = "Full Chat", tint = Color(0xFF2AABEE), modifier = Modifier.size(16.dp))
                            }

                            // ✕ মিনিমাইজ বাটন
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8696A0), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF232D3F), thickness = 0.8.dp)

                    // 💬 লাইভ চ্যাট মেসেজ লিস্ট
                    LazyColumn(
                        state = miniListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages.takeLast(30), key = { it.id }) { msg ->
                            val isMe = msg.senderId == currentUserId

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isMe) Color(0xFF2B5278) else Color(0xFF1E2834),
                                    modifier = Modifier.widthIn(max = 240.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                        if (!isMe) {
                                            Text(
                                                text = msg.senderName,
                                                color = if (msg.isOwner) Color(0xFFFFB300) else Color(0xFF2AABEE),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = msg.text.ifBlank { if (msg.imageUrls.isNotEmpty() || msg.imageUrl != null) "📷 Photo" else if (msg.videoUrl != null) "🎬 Video" else "🎤 Voice note" },
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ✍️ টাইপিং ইনপুট বার
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C2432))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF141A24))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (messageInput.isEmpty()) {
                                Text("Type a message...", color = Color(0xFF8696A0), fontSize = 12.sp)
                            }
                            BasicTextField(
                                value = messageInput,
                                onValueChange = { messageInput = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color(0xFF00E676)),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (messageInput.isNotBlank()) {
                                        val text = messageInput.trim()
                                        messageInput = ""
                                        coroutineScope.launch {
                                            FirebaseChatManager.sendTextMessage(
                                                senderId = currentUserId,
                                                senderName = currentUserName,
                                                senderEmail = currentUserEmail,
                                                senderAvatar = currentUserAvatar,
                                                isVip = isVip,
                                                text = text
                                            )
                                        }
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        IconButton(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    val text = messageInput.trim()
                                    messageInput = ""
                                    coroutineScope.launch {
                                        FirebaseChatManager.sendTextMessage(
                                            senderId = currentUserId,
                                            senderName = currentUserName,
                                            senderEmail = currentUserEmail,
                                            senderAvatar = currentUserAvatar,
                                            isVip = isVip,
                                            text = text
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00D166))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🔵 ২. ভাসমান গোল চ্যাট বাবল (Floating Action Button)
        // =========================================================================
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(elevation = 10.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isExpanded) listOf(Color(0xFFFF5252), Color(0xFFFF1744))
                        else listOf(Color(0xFF007AFF), Color(0xFF00E676))
                    )
                )
                .clickable { isExpanded = !isExpanded },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.ChatBubble,
                contentDescription = "Live Chat",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
