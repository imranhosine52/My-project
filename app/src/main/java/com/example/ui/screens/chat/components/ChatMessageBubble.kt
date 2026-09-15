@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.chat.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatMessage
import com.example.ui.VipCrown3DIcon
import com.example.util.FirebaseChatManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 🎨 টেলিগ্রাম ডার্ক থিম কালারসমূহ
private val TelegramBubbleReceived = Color(0xFF18222D)
private val TelegramBubbleSent = Color(0xFF2B5278)
private val TelegramSenderNameColor = Color(0xFF5288C1)
private val TimestampMuted = Color(0xFF8E9BA8)
private val LinkColor = Color(0xFF53BDEB)
private val GoldenOwnerText = Color(0xFFFFD700)

@Composable
fun WhatsAppMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    currentUserAvatar: String? = null,
    avatarMap: Map<String, String?> = emptyMap(),
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
    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }

    val isDeviceOwner = remember {
        val email = authPrefs.getString("user_email", null)
        val name = authPrefs.getString("user_name", "") ?: ""
        FirebaseChatManager.isRootAdmin(email) || name.contains("Hey Sifat", ignoreCase = true) || name.contains("Sifat", ignoreCase = true)
    }

    val isDeviceVip = remember {
        val plan = authPrefs.getString("user_plan", "free")?.lowercase() ?: "free"
        authPrefs.getBoolean("is_vip", false) || plan == "vip" || plan == "premium" || isDeviceOwner
    }

    val isMessageOwner = remember(message, isMe, isDeviceOwner) {
        if (isMe) {
            isDeviceOwner || message.isOwner
        } else {
            message.isOwner ||
            FirebaseChatManager.isRootAdmin(message.senderEmail) ||
            message.senderName.contains("Hey Sifat", ignoreCase = true) ||
            message.senderName.contains("Owner", ignoreCase = true) ||
            message.senderName.contains("Admin", ignoreCase = true) ||
            message.senderId == "owner_yheysifat"
        }
    }

    val isMessageVip = remember(message, isMe, isDeviceVip, isMessageOwner) {
        if (isMe) {
            isDeviceVip || isMessageOwner || message.isVip
        } else {
            isMessageOwner ||
            message.isVip ||
            message.senderName.contains("VIP", ignoreCase = true)
        }
    }

    val timeFormatted = remember(message.timestamp) {
        formatMessageTime(message.timestamp)
    }

    val isSeen = remember(message.isRead, message.readBy, isMe) {
        message.isRead || message.readBy.any { it.isNotBlank() && it != message.senderId }
    }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val effectiveAvatar = remember(message.senderAvatar, currentUserAvatar, avatarMap, isMe) {
        if (isMe) {
            currentUserAvatar?.takeIf { it.isNotBlank() }
                ?: message.senderAvatar?.takeIf { it.isNotBlank() }
                ?: avatarMap[message.senderId]
        } else {
            message.senderAvatar?.takeIf { it.isNotBlank() }
                ?: avatarMap[message.senderId]
                ?: message.senderEmail?.let { avatarMap[it.lowercase()] }
        }
    }

    val allImages = remember(message.imageUrls, message.imageUrl) {
        if (message.imageUrls.isNotEmpty()) message.imageUrls
        else if (!message.imageUrl.isNullOrBlank()) listOf(message.imageUrl)
        else emptyList()
    }

    val isPureStickerOrGif = remember(message.text, message.imageUrl, message.imageUrls, message.videoUrl, message.audioUrl) {
        message.text.isBlank() &&
        message.audioUrl.isNullOrBlank() &&
        (
            (!message.imageUrl.isNullOrBlank() || message.imageUrls.size == 1) &&
            ((message.imageUrl ?: message.imageUrls.firstOrNull() ?: "").let { url ->
                url.contains("tenor.com", ignoreCase = true) ||
                url.contains("giphy.com", ignoreCase = true) ||
                url.contains("/stickers/", ignoreCase = true) ||
                url.contains("stk_", ignoreCase = true) ||
                url.contains("vid_", ignoreCase = true) ||
                url.contains("gif_", ignoreCase = true) ||
                url.contains("emo_", ignoreCase = true) ||
                url.endsWith(".gif", ignoreCase = true) ||
                url.endsWith(".webp", ignoreCase = true) ||
                url.endsWith(".mp4", ignoreCase = true) ||
                url.endsWith(".webm", ignoreCase = true)
            })
            || (!message.videoUrl.isNullOrBlank() && (
                message.videoUrl.contains("/stickers/", ignoreCase = true) ||
                message.videoUrl.contains("vid_", ignoreCase = true)
            ))
        )
    }

    val singleStickerUrl = remember(isPureStickerOrGif, message.imageUrl, message.imageUrls, message.videoUrl) {
        if (isPureStickerOrGif) {
            message.imageUrl ?: message.imageUrls.firstOrNull() ?: message.videoUrl
        } else null
    }

    val isVideoSticker = remember(singleStickerUrl) {
        val u = singleStickerUrl.orEmpty()
        u.endsWith(".mp4", ignoreCase = true) ||
        u.endsWith(".webm", ignoreCase = true) ||
        u.contains("vid_", ignoreCase = true)
    }

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
            .padding(vertical = 2.dp, horizontal = 4.dp)
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

        // 👤 ১. অন্য ইউজারের প্রোফাইল পিকচার
        if (!isMe) {
            ChatUserAvatarCircle(
                avatarUrl = effectiveAvatar,
                userName = message.senderName,
                isVip = isMessageVip,
                isOwner = isMessageOwner,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // 🧸 ২. মেসেজ বাবল
        if (isPureStickerOrGif && !singleStickerUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { if (isSelectionMode) onClick() else onImageClick(singleStickerUrl) },
                        onLongClick = onLongClick
                    )
                    .padding(2.dp)
            ) {
                if (isVideoSticker) {
                    SeamlessVideoStickerPlayer(
                        videoUrl = singleStickerUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(singleStickerUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Sticker",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = timeFormatted,
                            color = Color.White,
                            fontSize = 9.sp
                        )
                        if (isMe) {
                            Text(
                                text = if (isSeen) "✓✓" else "✓",
                                color = if (isSeen) WhatsAppBlueTick else Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // 💬 সাধারণ টেক্সট বা মিডিয়া বাবল
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isMe) 14.dp else 3.dp,
                    bottomEnd = if (isMe) 3.dp else 14.dp
                ),
                color = if (isMe) TelegramBubbleSent else TelegramBubbleReceived,
                modifier = Modifier
                    .widthIn(min = 50.dp, max = 285.dp)
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

                    if (message.isPinned) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = Color(0xFFFFB300), modifier = Modifier.size(11.dp))
                            Text("Pinned Message", color = Color(0xFFFFB300), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 👑 ৩. ওনারের মেসেজের ওপর ব্যাকগ্রাউন্ড/আইকন ছাড়া শুধু ছোট করে 'owner' লেখা
                    if (isMessageOwner) {
                        Text(
                            text = "owner",
                            color = GoldenOwnerText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }

                    // 👤 অন্য ইউজারের নাম ও ভিআইপি ক্রাউন
                    if (!isMe) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = message.senderName,
                                color = if (isMessageOwner) GoldenOwnerText else TelegramSenderNameColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (isMessageVip && !isMessageOwner) {
                                VipCrown3DIcon(modifier = Modifier.size(15.dp, 11.dp))
                            }
                        }
                    }

                    // ↩️ রিপ্লাই ব্যানার
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

                    // 🖼️ ইমেজ কোলাজ
                    if (allImages.isNotEmpty() && message.videoUrl.isNullOrBlank()) {
                        ChatImageCollage(images = allImages, onImageClick = onImageClick)
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    // 🎬 ভিডিও মেসেজ
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
                        val isAudioPlaying = (activeAudioUrl == message.audioUrl)
                        WhatsAppVoicePlayer(
                            senderName = message.senderName,
                            senderAvatar = effectiveAvatar,
                            durationSec = message.mediaDurationSec,
                            timeFormatted = timeFormatted,
                            isMe = isMe,
                            isSeen = isSeen,
                            isPlaying = isAudioPlaying,
                            onPlayToggle = { onPlayAudio(message.audioUrl) },
                            onForwardClick = onShareForward
                        )
                    }

                    // 💬 টেক্সট মেসেজ ও টাইম/টিক
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
                                    fontSize = 14.sp,
                                    lineHeight = 18.5.sp
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

        // 👤 ৪. নিজের প্রোফাইল পিকচার (ডানে)
        if (isMe) {
            Spacer(modifier = Modifier.width(6.dp))
            ChatUserAvatarCircle(
                avatarUrl = effectiveAvatar,
                userName = message.senderName,
                isVip = isMessageVip,
                isOwner = isMessageOwner,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * 🎥 টেলিগ্রাম স্টাইলের অটো-লুপিং মিউটেড ভিডিও স্টিকার প্লেয়ার
 */
@Composable
private fun SeamlessVideoStickerPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

/**
 * 🌟 VIP গোল্ডেন শিমার বর্ডার ও অ্যানিমেটেড ক্রাউন ব্যাজসহ অবতার সার্কেল
 */
@Composable
fun ChatUserAvatarCircle(
    avatarUrl: String?,
    userName: String,
    isVip: Boolean = false,
    isOwner: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "vip_avatar_shine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 350f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_gold_shimmer"
    )

    val vipPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vip_crown_pulse"
    )

    val goldenShineBorder = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD700),
            Color(0xFFFFFFFF),
            Color(0xFFFF9100),
            Color(0xFFFFD700)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 140f, 140f)
    )

    Box(
        modifier = modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .then(
                    if (isVip || isOwner) {
                        Modifier
                            .border(width = 2.dp, brush = goldenShineBorder, shape = CircleShape)
                            .padding(2.5.dp)
                    } else {
                        Modifier
                            .border(0.8.dp, Color(0x33FFFFFF), CircleShape)
                            .padding(1.dp)
                    }
                )
                .clip(CircleShape)
                .background(getTelegramAvatarColor(userName)),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = userName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initial = if (userName.contains(" ")) {
                    val parts = userName.split(" ")
                    "${parts[0].first()}${parts[1].first()}".uppercase()
                } else {
                    userName.take(1).uppercase()
                }
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isVip && !isOwner) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .scale(vipPulseScale)
            ) {
                VipCrown3DIcon(
                    modifier = Modifier.size(width = 16.dp, height = 13.dp)
                )
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
                    .height(190.dp)
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
                modifier = Modifier.fillMaxWidth().height(145.dp),
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
                modifier = Modifier.fillMaxWidth().height(165.dp),
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
                    .height(195.dp),
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
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${count - 3}",
                                    color = Color.White,
                                    fontSize = 17.sp,
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
