@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
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

// 🎨 টেলিগ্রাম ডার্ক থিম বাবল কালার
private val TelegramBubbleReceived = Color(0xFF18222D)
private val TelegramBubbleSent = Color(0xFF2B5278)
private val TelegramSenderNameColor = Color(0xFF5288C1)
private val TimestampMuted = Color(0xFF8E9BA8)
private val WhatsAppBlueTick = Color(0xFF53BDEB)

@Composable
fun WhatsAppMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    activeAudioUrl: String?,
    onPlayAudio: (String) -> Unit,
    onSwipeToReply: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit = {},
    onShareForward: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timeFormatted = remember(message.timestamp) {
        formatMessageTime(message.timestamp)
    }

    // 🎯 সিন হয়েছে কিনা তা চেক করা (সিন হলে ২টি নীল টিক, না হলে ১টি ধূসর টিক)
    val isSeen = remember(message.isRead, message.readBy, isMe) {
        message.isRead || message.readBy.any { it.isNotBlank() && it != message.senderId }
    }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // একাধিক ছবির তালিকা হ্যান্ডলিং
    val allImages = remember(message.imageUrls, message.imageUrl) {
        if (message.imageUrls.isNotEmpty()) message.imageUrls
        else if (!message.imageUrl.isNullOrBlank()) listOf(message.imageUrl)
        else emptyList()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0x332AABEE) else Color.Transparent)
            .padding(vertical = 2.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(message.id, isSelectionMode) {
                if (!isSelectionMode) {
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
                }
            },
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // সিলেকশন মোডে চেকমার্ক বক্স
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) TelegramBlue else Color(0x55000000))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // প্রেরকের অবতার
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
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isMe) 14.dp else 3.dp,
                    bottomEnd = if (isMe) 3.dp else 14.dp
                ),
                color = if (isMe) TelegramBubbleSent else TelegramBubbleReceived,
                modifier = Modifier.combinedClickable(
                    onClick = { if (isSelectionMode) onClick() },
                    onLongClick = onLongClick
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {

                    // 📌 পিনড মেসেজ ট্যাগ
                    if (message.isPinned) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Pinned",
                                color = Color(0xFFFFB300),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 👤 প্রেরকের নাম ও ওনার/ভিআইপি ব্যাজ
                    if (!isMe) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = message.senderName,
                                color = if (message.isOwner) OwnerGold else TelegramSenderNameColor,
                                fontSize = 12.5.sp,
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

                    // রিপ্লাই কোটেশন প্রিভিউ
                    if (!message.replyToName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x22000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.width(3.dp).height(28.dp).background(TelegramSenderNameColor))
                                Column {
                                    Text(
                                        text = message.replyToName,
                                        color = TelegramSenderNameColor,
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

                    // =============================================================
                    // 🖼️ মাল্টিপল ইমেজ কোলাজ (১টি, ২টি, ৩টি বা ৪+ ছবি)
                    // =============================================================
                    if (allImages.isNotEmpty() && message.videoUrl.isNullOrBlank()) {
                        ChatImageCollage(
                            images = allImages,
                            onImageClick = onImageClick
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // ভিডিও মেসেজ
                    if (!message.videoUrl.isNullOrBlank()) {
                        VideoMessageThumbnailBubble(
                            videoUrl = message.videoUrl,
                            imageUrl = message.imageUrl,
                            onVideoClick = { onVideoClick(message.videoUrl) }
                        )
                    }

                    // 🎙️ টেলিগ্রাম ভয়েস প্লেয়ার
                    if (!message.audioUrl.isNullOrBlank()) {
                        val isPlaying = (activeAudioUrl == message.audioUrl)
                        WhatsAppVoicePlayer(
                            senderName = message.senderName,
                            senderAvatar = message.senderAvatar,
                            durationSec = message.mediaDurationSec,
                            timeFormatted = timeFormatted,
                            isMe = isMe,
                            isSeen = isSeen,
                            isPlaying = isPlaying,
                            onPlayToggle = { onPlayAudio(message.audioUrl) },
                            onForwardClick = onShareForward
                        )
                    }

                    // 💬 টেক্সট মেসেজ ও টাইম/টিক
                    if (message.text.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = message.text,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 8.dp)
                            )

                            // মেসেজ ডেলিভারি/সিন টাইম ও টিক মার্ক
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = timeFormatted,
                                    color = TimestampMuted,
                                    fontSize = 10.5.sp
                                )
                                if (isMe) {
                                    Text(
                                        text = if (isSeen) "✓✓" else "✓",
                                        color = if (isSeen) WhatsAppBlueTick else TimestampMuted,
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
}

/**
 * 🖼️ ১ থেকে ৪+ ছবির স্মার্ট গ্রিড কোলাজ
 */
@Composable
fun ChatImageCollage(
    images: List<String>,
    onImageClick: (String) -> Unit
) {
    val count = images.size

    when (count) {
        1 -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(images[0]) }
            ) {
                AsyncImage(
                    model = images[0],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        2 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                images.forEach { imgUrl ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onImageClick(imgUrl) }
                    ) {
                        AsyncImage(
                            model = imgUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
        3 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onImageClick(images[0]) }
                ) {
                    AsyncImage(
                        model = images[0],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    images.drop(1).forEach { imgUrl ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onImageClick(imgUrl) }
                        ) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
        else -> { // ৪টি বা তার বেশি ছবি
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onImageClick(images[0]) }
                    ) {
                        AsyncImage(model = images[0], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onImageClick(images[1]) }
                    ) {
                        AsyncImage(model = images[1], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onImageClick(images[2]) }
                    ) {
                        AsyncImage(model = images[2], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onImageClick(images[3]) }
                    ) {
                        AsyncImage(model = images[3], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        // ৪টির বেশি হলে অবশিষ্ট ছবির সংখ্যা প্রদর্শন (+X)
                        if (count > 4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${count - 3}",
                                    color = Color.White,
                                    fontSize = 18.sp,
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
