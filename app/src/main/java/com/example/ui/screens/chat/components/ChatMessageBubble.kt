@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable // 👈 ফিক্সড: clickable ইমপোর্ট যোগ করা হয়েছে
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.ui.VipCrown3DIcon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun WhatsAppMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    activeAudioUrl: String?,
    onPlayAudio: (String) -> Unit,
    onSwipeToReply: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatted = remember(message.timestamp) {
        formatMessageTime(message.timestamp)
    }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX.value > 55f) onSwipeToReply()
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
        // ১ নম্বর ছবির মতো প্রেরকের গোল অবতার
        if (!isMe) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(getTelegramAvatarColor(message.senderName)),
                contentAlignment = Alignment.Center
            ) {
                if (!message.senderAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = message.senderAvatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = if (message.senderName.contains(" ")) {
                        val parts = message.senderName.split(" ")
                        "${parts[0].first()}${parts[1].first()}".uppercase()
                    } else {
                        message.senderName.take(1).uppercase()
                    }
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 305.dp)
        ) {
            // প্রেরকের নাম ও ওনার ব্যাজ
            if (!isMe && message.audioUrl.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = message.senderName,
                        color = if (message.isOwner) OwnerGold else getTelegramAvatarColor(message.senderName),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (message.isOwner) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = OwnerGold.copy(alpha = 0.2f),
                            border = BorderStroke(0.6.dp, OwnerGold)
                        ) {
                            Text(
                                text = "OWNER",
                                color = OwnerGold,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (message.isVip) {
                        VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                    }
                }
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

                    // রিপ্লাই কোট ব্লক
                    if (!message.replyToName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x28000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.width(3.dp).height(28.dp).background(Color(0xFF00A884)))
                                Column {
                                    Text(
                                        text = message.replyToName,
                                        color = Color(0xFF00A884),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = message.replyToText ?: "",
                                        color = Color.White.copy(0.8f),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // ছবি
                    if (!message.imageUrl.isNullOrBlank() && message.videoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(message.imageUrl) }
                        ) {
                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // ভিডিও বাবল
                    if (!message.videoUrl.isNullOrBlank()) {
                        VideoMessageThumbnailBubble(
                            videoUrl = message.videoUrl,
                            imageUrl = message.imageUrl,
                            onVideoClick = { onVideoClick(message.videoUrl) }
                        )
                    }

                    // 🎙️ ১ নম্বর ছবির হুবহু WhatsApp ভয়েস প্লেয়ার
                    if (!message.audioUrl.isNullOrBlank()) {
                        val isPlaying = (activeAudioUrl == message.audioUrl)
                        WhatsAppVoicePlayer(
                            senderName = message.senderName,
                            senderAvatar = message.senderAvatar,
                            durationSec = message.mediaDurationSec,
                            timeFormatted = timeFormatted,
                            isMe = isMe,
                            isPlaying = isPlaying,
                            onPlayToggle = { onPlayAudio(message.audioUrl) }
                        )
                    }

                    // টেক্সট মেসেজ
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // টাইম + ডাবল ব্লু টিক
                    if (message.audioUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 1.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = timeFormatted,
                                color = Color.White.copy(0.6f),
                                fontSize = 10.sp
                            )
                            if (isMe) {
                                Text(
                                    text = "✓✓",
                                    color = WhatsAppBlueTick,
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
}
