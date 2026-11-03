@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.LocalInboxEntity
import com.example.ui.VipCrown3DIcon
import com.example.ui.viewmodel.PrivateChatViewModel
import java.util.Locale

// 🎨 টেলিগ্রাম অফিসিয়াল ডার্ক কালার প্যালেট
private val TelegramBg = Color(0xFF0E1621)
private val TelegramHeaderBg = Color(0xFF17212B)
private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramOnlineGreen = Color(0xFF00E676)
private val TelegramMuted = Color(0xFF7E8D9F)
private val TelegramDivider = Color(0xFF1A222D)
private val TelegramUnreadBadge = Color(0xFF2AABEE)

@Composable
fun PrivateInboxScreen(
    viewModel: PrivateChatViewModel,
    myUserId: String,
    onBackClick: () -> Unit,
    onOpenChat: (peerId: String, peerName: String, peerAvatar: String?, isOnline: Boolean, isVip: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inboxState by viewModel.inboxUiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(myUserId) {
        if (myUserId.isNotBlank()) {
            viewModel.refreshInbox(myUserId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TelegramBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. টেলিগ্রাম স্টাইল এজ-টু-এজ টপ বার ও সার্চ বক্স
            // =========================================================================
            Surface(
                color = TelegramHeaderBg,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onBackClick, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (!isSearchActive) {
                                Text(
                                    text = "Messages",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                // লাইভ সার্চ ইনপুট
                                BasicTextField(
                                    value = inboxState.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                    cursorBrush = SolidColor(TelegramBlue),
                                    decorationBox = { innerTextField ->
                                        if (inboxState.searchQuery.isEmpty()) {
                                            Text("Search chat or user...", color = TelegramMuted, fontSize = 14.sp)
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSearchActive) {
                                IconButton(
                                    onClick = {
                                        isSearchActive = false
                                        viewModel.onSearchQueryChanged("")
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                IconButton(
                                    onClick = { isSearchActive = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            IconButton(
                                onClick = { viewModel.refreshInbox(myUserId) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TelegramBlue, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 📜 ২. পুল-টু-রিফ্রেশ ও ইনবক্স তালিকা
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = inboxState.isRefreshing,
                onRefresh = { viewModel.refreshInbox(myUserId) },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (inboxState.isLoading && inboxState.rawInbox.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TelegramBlue, strokeWidth = 2.5.dp)
                    }
                } else if (inboxState.filteredInbox.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16202C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = TelegramMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = if (inboxState.searchQuery.isNotEmpty()) "No chats matching '${inboxState.searchQuery}'" else "Your inbox is empty",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Start a private conversation with any drama viewer or friends to see them here.",
                                color = TelegramMuted,
                                fontSize = 12.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            items = inboxState.filteredInbox,
                            key = { it.chatRoomId }
                        ) { chatItem ->
                            TelegramInboxItemRow(
                                item = chatItem,
                                onClick = {
                                    onOpenChat(
                                        chatItem.peerId,
                                        chatItem.peerName,
                                        chatItem.peerAvatar,
                                        chatItem.isOnline,
                                        chatItem.isVip
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 📱 ৩. টেলিগ্রাম স্টাইল প্রতিটি চ্যাট আইটেমের রো (Inbox Row Component)
// =============================================================================

@Composable
private fun TelegramInboxItemRow(
    item: LocalInboxEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val avatarModel = remember(item.peerAvatar, item.peerName) {
        item.peerAvatar?.takeIf { it.isNotBlank() }
            ?: "https://ui-avatars.com/api/?name=${item.peerName}&background=1E293B&color=FACC15&bold=true"
    }

    Surface(
        color = TelegramBg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ১. অবতার ও সবুজ অনলাইন ডট
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222E3A)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.peerName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // 🟢 লাইভ অনলাইন হলে সবুজ ডট
                    if (item.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(TelegramOnlineGreen)
                                .border(2.dp, TelegramBg, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                // ২. নাম, মেটাডাটা ও লাস্ট মেসেজ
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item.peerName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (item.isVip) {
                                VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                            }
                        }

                        // সময়
                        Text(
                            text = item.timeFormatted.ifBlank { "Recent" },
                            color = TelegramMuted,
                            fontSize = 11.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // লাস্ট মেসেজ প্রিভিউ
                        Text(
                            text = item.lastMessage.ifBlank { "No messages yet" },
                            color = if (item.unreadCount > 0) Color.White else TelegramMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // ৩. আনরিড ব্যাজ বা পিন আইকন
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = TelegramMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            if (item.unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TelegramUnreadBadge)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // পাতলা ডিভাইডার
            HorizontalDivider(
                color = TelegramDivider,
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 76.dp)
            )
        }
    }
}
