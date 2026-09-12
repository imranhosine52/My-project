@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.chat.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border // 👈 এই ইমপোর্টটি যোগ করা হয়েছে
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BlockedUserInfo
import com.example.data.model.ChatMessage
import com.example.data.model.GroupMemberInfo
import com.example.ui.VipCrown3DIcon
import com.example.util.FirebaseChatManager
import com.example.util.LiveGroupStats
import kotlinx.coroutines.launch

data class SharedLinkItem(
    val url: String,
    val senderName: String,
    val timeFormatted: String
)

@Composable
fun GroupDetailsScreen(
    messages: List<ChatMessage>,
    isGroupMuted: Boolean,
    stats: LiveGroupStats,
    isCurrentUserOwner: Boolean = false, // 👑 ওনার/অ্যাডমিন চেক
    onToggleMute: () -> Unit,
    onLeaveGroup: () -> Unit,
    onBackClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    // 🌟 Members ট্যাব যুক্ত এবং Blocked ট্যাব ১০০% শুধু ওনারের জন্য
    val tabs = remember(isCurrentUserOwner) {
        if (isCurrentUserOwner) listOf("Members", "Media", "Files", "Voice", "Links", "Blocked 🚫")
        else listOf("Members", "Media", "Files", "Voice", "Links")
    }

    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    val mediaItems = remember(messages) {
        messages.filter { it.imageUrls.isNotEmpty() || !it.imageUrl.isNullOrBlank() || !it.videoUrl.isNullOrBlank() }
    }
    val voiceItems = remember(messages) { messages.filter { !it.audioUrl.isNullOrBlank() } }

    // 🔗 চ্যাটের সব শেয়ার্ড লিংক
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

    // 👥 গ্রুপের সকল মেম্বারদের লাইভ তালিকা
    val groupMembers by produceState<List<GroupMemberInfo>>(initialValue = emptyList()) {
        FirebaseChatManager.getLiveGroupMembersFlow().collect { value = it }
    }

    // 🚫 লাইভ ব্লকড ইউজার তালিকা (শুধুমাত্র ওনারের জন্য)
    val blockedUsers by produceState<List<BlockedUserInfo>>(initialValue = emptyList()) {
        if (isCurrentUserOwner) {
            FirebaseChatManager.getLiveBlockedUsersFlow().collect { value = it }
        } else {
            value = emptyList()
        }
    }

    val currentTabName = tabs.getOrElse(selectedTabIndex) { "Members" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WhatsAppDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ১. টপ বার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Group Info", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            // ২. গ্রুপ লোগো, নাম ও লাইভ মেম্বার সংখ্যা
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1D2636)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(44.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "DramaFlix Community Group",
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${stats.totalMembers} members, ${stats.onlineMembers} online",
                    color = Color(0xFF8692A6),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // অ্যাকশন বাটনসমূহ
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onBackClick() },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1B2433)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Message", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onToggleMute() },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1B2433)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isGroupMuted) "Unmute" else "Mute", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { showLeaveConfirmDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1B2433)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFFF4D4F), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Leave", color = Color(0xFFFF4D4F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            // ৩. 🔒 ওনার প্রোফাইল কার্ড (জিমেইল সম্পূর্ণ হাইড)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("S", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Hey Sifat YT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Group Creator & Lead Admin", color = Color(0xFF8692A6), fontSize = 11.5.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4A148C).copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color(0xFF9C27B0))
                    ) {
                        Text(
                            text = "Owner",
                            color = Color(0xFFE1BEE7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            // ৪. ট্যাব বার
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs.size) { index ->
                    val tabName = tabs[index]
                    val isSelected = selectedTabIndex == index
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF223048) else Color.Transparent,
                        modifier = Modifier.clickable { selectedTabIndex = index }
                    ) {
                        Text(
                            text = tabName,
                            color = if (isSelected) TelegramBlue else Color(0xFF8692A6),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            // ৫. ডায়নামিক ট্যাব কনটেন্ট
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTabName) {
                    // =============================================================
                    // 👥 MEMBERS TAB (গ্রুপের সব মেম্বার তালিকা)
                    // =============================================================
                    "Members" -> {
                        val displayMembers = remember(groupMembers) {
                            if (groupMembers.isNotEmpty()) groupMembers
                            else listOf(
                                GroupMemberInfo(userId = "1", userName = "Hey Sifat YT", isOwner = true, isVip = true),
                                GroupMemberInfo(userId = "2", userName = "PlayDramaFlix Fan", isOwner = false, isVip = false)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayMembers, key = { it.userId }) { member ->
                                val now = System.currentTimeMillis()
                                val isOnline = member.isOwner || (now - member.lastActive < 180_000L)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1C2432))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // অবতার + অনলাইন ডট
                                        Box(modifier = Modifier.size(42.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(getTelegramAvatarColor(member.userName)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!member.userAvatar.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = member.userAvatar,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Text(
                                                        text = member.userName.take(1).uppercase(),
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // অনলাইন গ্রিন ডট
                                            if (isOnline) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(11.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF00E676))
                                                        .border(1.5.dp, WhatsAppDarkBg, CircleShape)
                                                        .align(Alignment.BottomEnd)
                                                )
                                            }
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = member.userName,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (member.isOwner) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = OwnerGold.copy(alpha = 0.2f),
                                                        border = BorderStroke(0.6.dp, OwnerGold)
                                                    ) {
                                                        Text("OWNER", color = OwnerGold, fontSize = 7.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                    }
                                                } else if (member.isVip) {
                                                    VipCrown3DIcon(modifier = Modifier.size(15.dp, 11.dp))
                                                }
                                            }
                                            Text(
                                                text = if (isOnline) "online" else "last seen recently",
                                                color = if (isOnline) Color(0xFF00E676) else Color(0xFF8692A6),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // 👑 ওনারের জন্য মেম্বার রিমুভ / ব্লক অপশন
                                    if (isCurrentUserOwner && !member.isOwner) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        FirebaseChatManager.kickUser(member.userId)
                                                        Toast.makeText(context, "${member.userName} kicked", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.PersonRemove, contentDescription = "Kick", tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
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
                                                Icon(Icons.Default.Block, contentDescription = "Block", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 🖼️ MEDIA TAB
                    // =============================================================
                    "Media" -> {
                        if (mediaItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No shared media yet", color = Color(0xFF8692A6), fontSize = 13.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize()
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

                    // =============================================================
                    // 📁 FILES TAB
                    // =============================================================
                    "Files" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No files shared yet", color = Color(0xFF8692A6), fontSize = 13.sp)
                        }
                    }

                    // =============================================================
                    // 🎙️ VOICE TAB
                    // =============================================================
                    "Voice" -> {
                        if (voiceItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No voice notes yet", color = Color(0xFF8692A6), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(voiceItems) { voice ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1C2432))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp))
                                        Column {
                                            Text(voice.senderName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Voice Note • ${voice.mediaDurationSec}s", color = Color(0xFF8692A6), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 🔗 LINKS TAB
                    // =============================================================
                    "Links" -> {
                        if (sharedLinks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No shared links in this group", color = Color(0xFF8692A6), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sharedLinks) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1C2432))
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
                                                    .background(TelegramBlue.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Link, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                            }

                                            Column {
                                                Text(
                                                    text = item.url,
                                                    color = TelegramBlue,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Shared by ${item.senderName} • ${item.timeFormatted}",
                                                    color = Color(0xFF8692A6),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open",
                                            tint = Color(0xFF8692A6),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 🚫 BLOCKED TAB (শুধুমাত্র ওনারের অ্যাকাউন্টে দৃশ্যমান)
                    // =============================================================
                    "Blocked 🚫" -> {
                        if (!isCurrentUserOwner) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Access restricted", color = Color(0xFF8692A6), fontSize = 13.sp)
                            }
                        } else if (blockedUsers.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No blocked users in this group", color = Color(0xFF8692A6), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(blockedUsers, key = { it.userId }) { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1C2432))
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
                                                Text(user.userEmail ?: "ID: ${user.userId}", color = Color(0xFF8692A6), fontSize = 11.sp, maxLines = 1)
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
                        Text("Cancel", color = Color(0xFF8696A0))
                    }
                }
            )
        }
    }
}
