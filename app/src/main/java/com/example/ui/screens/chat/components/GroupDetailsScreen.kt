@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.chat.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.BlockedUserInfo
import com.example.data.model.ChatMessage
import com.example.data.model.GroupMemberInfo
import com.example.ui.VipCrown3DIcon
import com.example.ui.screens.CommentVideoStickerPlayer
import com.example.util.FirebaseChatManager
import com.example.util.LiveGroupStats
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// 🎨 টেলিগ্রাম অফিসিয়াল ডার্ক কালারস
private val TgBgDark = Color(0xFF0E1621)
private val TgHeaderBg = Color(0xFF17212B)
private val TgAccentBlue = Color(0xFF2AABEE)
private val TgOnlineGreen = Color(0xFF00E676)
private val TgMutedText = Color(0xFF7E8D9F)
private val TgDividerColor = Color(0xFF1F2B3A)
private val TgCardBg = Color(0xFF17212B)

/**
 * 🕒 রিয়েল-টাইম নির্ভুল লাস্ট সিন ফরম্যাটার (টেলিগ্রাম অ্যালগরিদম)
 */
private fun formatTelegramLastSeen(lastActiveMillis: Long): String {
    if (lastActiveMillis <= 0L) return "last seen recently"
    val diff = System.currentTimeMillis() - lastActiveMillis
    if (diff < 0L) return "online"

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 3 -> "online"
        minutes < 60 -> "last seen ${minutes}m ago"
        hours < 24 -> {
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(lastActiveMillis))
            "last seen today at $timeStr"
        }
        days < 2 -> {
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(lastActiveMillis))
            "last seen yesterday at $timeStr"
        }
        days < 7 -> "last seen $days days ago"
        else -> {
            val dateStr = SimpleDateFormat("dd/MM/yy", Locale.US).format(Date(lastActiveMillis))
            "last seen on $dateStr"
        }
    }
}

@Composable
fun GroupDetailsScreen(
    messages: List<ChatMessage>,
    isGroupMuted: Boolean,
    stats: LiveGroupStats,
    isCurrentUserOwner: Boolean = false,
    onToggleMute: () -> Unit,
    onLeaveGroup: () -> Unit,
    onBackClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }
    val myUserId = remember { authPrefs.getString("user_id", "") ?: "" }
    val isMyVip = remember {
        authPrefs.getBoolean("is_vip", false) ||
        (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))
    }

    val tabs = remember(isCurrentUserOwner) {
        if (isCurrentUserOwner) listOf("Members", "Media", "Stickers", "Files", "Voice", "Links", "Blocked 🚫")
        else listOf("Members", "Media", "Stickers", "Files", "Voice", "Links")
    }
    var selectedTab by remember { mutableStateOf("Members") }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 🧸 ১. স্টিকার ও মিডিয়া ফিল্টারিং
    val stickerItems = remember(messages) {
        messages.filter { msg ->
            val u = msg.imageUrl ?: msg.imageUrls.firstOrNull() ?: msg.videoUrl
            msg.text.isBlank() && msg.audioUrl.isNullOrBlank() && !u.isNullOrBlank() && (
                u.contains("tenor.com", true) || u.contains("giphy.com", true) ||
                u.contains("/stickers/", true) || u.contains("stk_", true) ||
                u.contains("gif_", true) || u.contains("emo_", true) ||
                u.contains("vid_", true) ||
                u.endsWith(".gif", true) || u.endsWith(".webp", true) ||
                u.endsWith(".png", true) || u.endsWith(".jpg", true) ||
                u.endsWith(".mp4", true) || u.endsWith(".webm", true)
            )
        }.reversed()
    }

    val mediaItems = remember(messages, stickerItems) {
        val stickerSet = stickerItems.map { it.id }.toSet()
        messages.filter { msg ->
            msg.id !in stickerSet && (msg.imageUrls.isNotEmpty() || !msg.imageUrl.isNullOrBlank() || !msg.videoUrl.isNullOrBlank())
        }.reversed()
    }

    val voiceItems = remember(messages) { messages.filter { !it.audioUrl.isNullOrBlank() }.reversed() }

    val sharedLinks = remember(messages) {
        val linkList = mutableListOf<SharedLinkItem>()
        val urlPattern = Regex("""(https?://[^\s]+|www\.[^\s]+)""")
        for (msg in messages) {
            val matches = urlPattern.findAll(msg.text)
            for (match in matches) {
                linkList.add(
                    SharedLinkItem(
                        url = match.value,
                        senderName = msg.senderName,
                        timeFormatted = formatMessageTime(msg.timestamp)
                    )
                )
            }
        }
        linkList.reversed()
    }

    val groupMembers by produceState<List<GroupMemberInfo>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveGroupMembersFlow().collect { value = it }
    }

    val blockedUsers by produceState<List<BlockedUserInfo>>(initialValue = emptyList()) {
        if (isCurrentUserOwner) {
            FirebaseChatManager.getLiveBlockedUsersFlow().collect { value = it }
        } else {
            value = emptyList()
        }
    }

    val accurateMemberCount = if (groupMembers.isNotEmpty()) groupMembers.size else stats.totalMembers

    // 🎯 কোলাপ্সিং ট্রানজিশন ডিটেকশন (স্ক্রোল করলে টপ বারের টাইটেল ও ব্যাকগ্রাউন্ড ফেড-ইন হবে)
    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 180
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "group_vip_shine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 350f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val goldenVipShineBorder = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD700),
            Color(0xFFFFF9C4),
            Color(0xFFFF9100),
            Color(0xFFFFD700)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 140f, 140f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TgBgDark)
    ) {
        // =========================================================================
        // 📜 মূল স্ক্রোলযোগ্য কন্টেন্ট (যা হেডারসহ মসৃণভাবে উপরে উঠে যাবে)
        // =========================================================================
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ১. উপরে স্ট্যাটাস বারের নিচে পর্যাপ্ত স্পেস যেন হেডার সুন্দর দেখায়
            item {
                Spacer(modifier = Modifier.statusBarsPadding().height(48.dp))
            }

            // ২. টেলিগ্রাম স্টাইল বড় গ্রুপ হেডার (Avatar, Title, Subtitle)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2AABEE), Color(0xFF2283B8))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "DramaFlix Community Group",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$accurateMemberCount members, ${stats.onlineMembers} online",
                        color = TgMutedText,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // ৩. আধুনিক টেলিগ্রাম কুইক অ্যাকশন বাটনসমূহ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelegramActionButton(
                            icon = Icons.Default.ChatBubble,
                            text = "Message",
                            iconColor = TgAccentBlue,
                            onClick = onBackClick,
                            modifier = Modifier.weight(1f)
                        )

                        TelegramActionButton(
                            icon = if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            text = if (isGroupMuted) "Unmute" else "Mute",
                            iconColor = if (isGroupMuted) Color(0xFFFFB300) else Color.White,
                            onClick = onToggleMute,
                            modifier = Modifier.weight(1f)
                        )

                        TelegramActionButton(
                            icon = Icons.Default.ExitToApp,
                            text = "Leave",
                            iconColor = Color(0xFFFF4D4F),
                            onClick = { showLeaveConfirmDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // ৪. গ্রুপ ডেসক্রিপশন / ওনার ইনফো কার্ড
            item {
                Surface(
                    color = TgCardBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("S", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Hey Sifat YT", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                    Text("Group Creator & Lead Admin", color = TgMutedText, fontSize = 11.5.sp)
                                }
                            }

                            Text(
                                text = "owner",
                                color = Color(0xFFFFD700),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = TgDividerColor, thickness = 0.6.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Official community of DramaFlix. Discuss your favourite Asian dramas, request episodes, and connect with fellow fans!",
                            color = Color(0xFFD0D7DE),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // ৫. স্টিকি ট্যাব বার (টেলিগ্রাম স্টাইল আধুনিক পিল বাটন)
            stickyHeader {
                Surface(
                    color = TgBgDark.copy(alpha = 0.96f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(tabs) { tabName ->
                                val isSelected = (selectedTab == tabName)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) TgAccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { selectedTab = tabName }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tabName,
                                        color = if (isSelected) TgAccentBlue else TgMutedText,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = TgDividerColor, thickness = 0.8.dp)
                    }
                }
            }

            // =========================================================================
            // 👥 ৬. নির্বাচিত ট্যাবের কনটেন্ট
            // =========================================================================
            when (selectedTab) {
                "Members" -> {
                    if (groupMembers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = TgAccentBlue, strokeWidth = 2.5.dp)
                            }
                        }
                    } else {
                        // টেলিগ্রামের মতো ক্লিন ইউনিফায়েড কার্ডের ভেতর মেম্বারদের লিস্ট
                        item {
                            Surface(
                                color = TgCardBg,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    groupMembers.forEachIndexed { index, member ->
                                        val now = System.currentTimeMillis()
                                        val isOnline = member.isOwner || (now - member.lastActive < 180_000L)
                                        val lastSeenText = if (isOnline) "online" else formatTelegramLastSeen(member.lastActive)

                                        val isMemberVip = remember(member, myUserId, isMyVip) {
                                            member.isOwner || member.isVip ||
                                            (member.userId == myUserId && isMyVip) ||
                                            member.userName.contains("VIP", ignoreCase = true)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { }
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                // 👤 অবতার ও অনলাইন ডট
                                                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .then(
                                                                if (isMemberVip || member.isOwner) {
                                                                    Modifier
                                                                        .border(width = 1.8.dp, brush = goldenVipShineBorder, shape = CircleShape)
                                                                        .padding(2.dp)
                                                                } else {
                                                                    Modifier.padding(0.5.dp)
                                                                }
                                                            )
                                                            .clip(CircleShape)
                                                            .background(getTelegramAvatarColor(member.userName)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (!member.userAvatar.isNullOrBlank()) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .data(member.userAvatar)
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Text(
                                                                text = member.userName.take(1).uppercase(),
                                                                color = Color.White,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    // 🟢 অনলাইন হলে উজ্জ্বল সবুজ ডট
                                                    if (isOnline) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(11.dp)
                                                                .clip(CircleShape)
                                                                .background(TgOnlineGreen)
                                                                .border(2.dp, TgCardBg, CircleShape)
                                                                .align(Alignment.BottomEnd)
                                                        )
                                                    }
                                                }

                                                // 📝 নাম ও স্ট্যাটাস
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = member.userName,
                                                            color = Color.White,
                                                            fontSize = 14.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        if (member.isOwner) {
                                                            Text(
                                                                text = "owner",
                                                                color = Color(0xFFFFD700),
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        } else if (isMemberVip) {
                                                            VipCrown3DIcon(modifier = Modifier.size(15.dp, 11.dp))
                                                        }
                                                    }

                                                    Text(
                                                        text = lastSeenText,
                                                        color = if (isOnline) TgOnlineGreen else TgMutedText,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                }
                                            }

                                            // ওনারদের জন্য অ্যাকশন বাটন
                                            if (isCurrentUserOwner && !member.isOwner) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                FirebaseChatManager.kickUser(member.userId)
                                                                Toast.makeText(context, "${member.userName} kicked", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.PersonRemove, contentDescription = "Kick", tint = Color(0xFFFF9800), modifier = Modifier.size(17.dp))
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                FirebaseChatManager.blockUser(member.userId, member.userName, member.userEmail)
                                                                Toast.makeText(context, "${member.userName} blocked", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Block, contentDescription = "Block", tint = Color(0xFFFF5252), modifier = Modifier.size(17.dp))
                                                    }
                                                }
                                            }
                                        }

                                        if (index < groupMembers.size - 1) {
                                            HorizontalDivider(
                                                color = TgDividerColor.copy(alpha = 0.5f),
                                                thickness = 0.6.dp,
                                                modifier = Modifier.padding(start = 68.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Media" -> {
                    if (mediaItems.isEmpty()) {
                        item {
                            EmptyTabPlaceholder("No shared photos or videos yet")
                        }
                    } else {
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxWidth().height(420.dp)
                            ) {
                                items(mediaItems) { item ->
                                    val firstImg = item.imageUrls.firstOrNull() ?: item.imageUrl
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .background(Color(0xFF1E2838))
                                            .clickable {
                                                if (!item.videoUrl.isNullOrBlank()) onVideoClick(item.videoUrl)
                                                else if (!firstImg.isNullOrBlank()) onImageClick(firstImg)
                                            }
                                    ) {
                                        AsyncImage(
                                            model = firstImg ?: item.videoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (!item.videoUrl.isNullOrBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp).align(Alignment.Center)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Stickers" -> {
                    if (stickerItems.isEmpty()) {
                        item {
                            EmptyTabPlaceholder("No stickers shared in group yet")
                        }
                    } else {
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().height(420.dp)
                            ) {
                                items(stickerItems, key = { it.id }) { item ->
                                    val stickerUrl = item.imageUrl ?: item.imageUrls.firstOrNull() ?: item.videoUrl
                                    if (!stickerUrl.isNullOrBlank()) {
                                        val isVideo = stickerUrl.endsWith(".mp4", true) || stickerUrl.endsWith(".webm", true) || stickerUrl.contains("vid_", true)

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onImageClick(stickerUrl) }
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isVideo) {
                                                CommentVideoStickerPlayer(
                                                    videoUrl = stickerUrl,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                                )
                                            } else {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(stickerUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Sticker",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Voice" -> {
                    if (voiceItems.isEmpty()) {
                        item {
                            EmptyTabPlaceholder("No voice notes yet")
                        }
                    } else {
                        items(voiceItems) { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TgCardBg)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = TgAccentBlue, modifier = Modifier.size(34.dp))
                                Column {
                                    Text(voice.senderName, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                    Text("Voice Note • ${voice.mediaDurationSec}s", color = TgMutedText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                "Links" -> {
                    if (sharedLinks.isEmpty()) {
                        item {
                            EmptyTabPlaceholder("No shared links in this group")
                        }
                    } else {
                        items(sharedLinks) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TgCardBg)
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(TgAccentBlue.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = TgAccentBlue, modifier = Modifier.size(20.dp))
                                    }

                                    Column {
                                        Text(
                                            text = item.url,
                                            color = TgAccentBlue,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Shared by ${item.senderName} • ${item.timeFormatted}",
                                            color = TgMutedText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open",
                                    tint = TgMutedText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                "Blocked 🚫" -> {
                    if (!isCurrentUserOwner) {
                        item { EmptyTabPlaceholder("Access restricted") }
                    } else if (blockedUsers.isEmpty()) {
                        item { EmptyTabPlaceholder("No blocked users in this group") }
                    } else {
                        items(blockedUsers, key = { it.userId }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TgCardBg)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFF5252)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Block, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(user.userName, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        Text(user.userEmail ?: "ID: ${user.userId}", color = TgMutedText, fontSize = 11.sp, maxLines = 1)
                                    }
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            FirebaseChatManager.unblockUser(user.userId)
                                            Toast.makeText(context, "${user.userName} unblocked", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Unblock", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🔝 টেলিগ্রাম ট্রু ফ্লোটিং টপ অ্যাপ বার (স্ক্রোল ফেড-ইন এনিমেশনসহ)
        // =========================================================================
        Surface(
            color = if (isScrolledPastHeader) TgHeaderBg.copy(alpha = 0.96f) else Color.Transparent,
            shadowElevation = if (isScrolledPastHeader) 4.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                AnimatedVisibility(
                    visible = isScrolledPastHeader,
                    enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically { it / 2 },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(TgAccentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = "DramaFlix Community Group",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$accurateMemberCount members",
                                color = TgMutedText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // লিভ গ্রুপ ডায়ালগ
        if (showLeaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmDialog = false },
                containerColor = Color(0xFF1A2230),
                title = { Text("Leave Group?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to leave DramaFlix Community Group?", color = Color(0xFFCCD5E2), fontSize = 13.5.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveGroup()
                    }) {
                        Text("Leave", color = Color(0xFFFF4D4F), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirmDialog = false }) {
                        Text("Cancel", color = TgMutedText)
                    }
                }
            )
        }
    }
}

/**
 * 🔘 টেলিগ্রামের মতো আধুনিক ক্যাপসুল অ্যাকশন বাটন
 */
@Composable
private fun TelegramActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = TgCardBg
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyTabPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TgMutedText, fontSize = 13.sp)
    }
}
