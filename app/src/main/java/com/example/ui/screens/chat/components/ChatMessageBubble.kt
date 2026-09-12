@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens.chat.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
private val LinkColor = Color(0xFF53BDEB)

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
    val context = LocalContext.current
    val timeFormatted = remember(message.timestamp) {
        formatMessageTime(message.timestamp)
    }

    val isSeen = remember(message.isRead, message.readBy, isMe) {
        message.isRead || message.readBy.any { it.isNotBlank() && it != message.senderId }
    }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val allImages = remember(message.imageUrls, message.imageUrl) {
        if (message.imageUrls.isNotEmpty()) message.imageUrls
        else if (!message.imageUrl.isNullOrBlank()) listOf(message.imageUrl)
        else emptyList()
    }

    // 🔗 ক্লিকেবল লিংক জেনারেটর
    val annotatedMessageText = remember(message.text) {
        buildAnnotatedString {
            val raw = message.text
            val urlPattern = Regex("""(https?://[^\s]+|www\.[^\s]+)""")
            var lastIndex = 0

            urlPattern.findAll(raw).forEach { matchResult ->
                val start = matchResult.range.first
                val end = matchResult.range.last + 1

                if (start > lastIndex) {
                    append(raw.substring(lastIndex, start))
                }

                val matchedUrl = matchResult.value
                val fullUrl = if (matchedUrl.startsWith("http://") || matchedUrl.startsWith("https://")) matchedUrl else "https://$matchedUrl"

                pushStringAnnotation(tag = "URL", annotation = fullUrl)
                withStyle(
                    style = SpanStyle(
                        color = LinkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(matchedUrl)
                }
                pop()
                lastIndex = end
            }

            if (lastIndex < raw.length) {
                append(raw.substring(lastIndex))
            }
        }
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

        // =========================================================================
        // 🌟 ডায়নামিক সাইজের বাবল (ছোট মেসেজে ছোট, বড় মেসেজে বড়)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isMe) 14.dp else 3.dp,
                bottomEnd = if (isMe) 3.dp else 14.dp
            ),
            color = if (isMe) TelegramBubbleSent else TelegramBubbleReceived,
            modifier = Modifier
                .widthIn(min = 40.dp, max = 300.dp)
                .combinedClickable(
                    onClick = { if (isSelectionMode) onClick() },
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {

                // 📌 পিনড মেসেজ ট্যাগ
                if (message.isPinned) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Pinned",
                            color = Color(0xFFFFB300),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 👤 প্রেরকের নাম
                if (!isMe) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = message.senderName,
                            color = if (message.isOwner) OwnerGold else TelegramSenderNameColor,
                            fontSize = 12.sp,
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
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else if (message.isVip) {
                            VipCrown3DIcon(modifier = Modifier.size(15.dp, 11.dp))
                        }
                    }
                }

                // রিপ্লাই কোট
                if (!message.replyToName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22000000))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(2.5.dp).height(24.dp).background(TelegramSenderNameColor))
                            Column {
                                Text(
                                    text = message.replyToName,
                                    color = TelegramSenderNameColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = message.replyToText ?: "",
                                    color = Color.White.copy(0.8f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // 🖼️ মাল্টিপল ইমেজ কোলাজ
                if (allImages.isNotEmpty() && message.videoUrl.isNullOrBlank()) {
                    ChatImageCollage(
                        images = allImages,
                        onImageClick = onImageClick
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // ভিডিও মেসেজ
                if (!message.videoUrl.isNullOrBlank()) {
                    VideoMessageThumbnailBubble(
                        videoUrl = message.videoUrl,
                        imageUrl = message.imageUrl,
                        onVideoClick = { onVideoClick(message.videoUrl) }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // 🎙️ ভয়েস প্লেয়ার
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

                // 💬 টেক্সট মেসেজ ও ডায়নামিক টাইম/টিক
                if (message.text.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        ClickableText(
                            text = annotatedMessageText,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 14.5.sp,
                                lineHeight = 19.sp
                            ),
                            onClick = { offset ->
                                val urlAnnotation = annotatedMessageText.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()
                                if (urlAnnotation != null) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlAnnotation.item)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    if (isSelectionMode) onClick()
                                }
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // টাইম ও টিক মার্ক
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(bottom = 1.dp)
                        ) {
                            Text(
                                text = timeFormatted,
                                color = TimestampMuted,
                                fontSize = 10.sp
                            )
                            if (isMe) {
                                Text(
                                    text = if (isSeen) "✓✓" else "✓",
                                    color = if (isSeen) WhatsAppBlueTick else TimestampMuted,
                                    fontSize = 10.5.sp,
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
                modifier = Modifier.fillMaxWidth().height(150.dp),
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
                modifier = Modifier.fillMaxWidth().height(170.dp),
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
        else -> {
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
                        AsyncImage(
                            model = images[0],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onImageClick(images[1]) }
                    ) {
                        AsyncImage(
                            model = images[1],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
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
                        AsyncImage(
                            model = images[2],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onImageClick(images[3]) }
                    ) {
                        AsyncImage(
                            model = images[3],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
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
