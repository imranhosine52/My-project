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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.LocalInboxEntity
import com.example.data.model.SearchedUserDto
import com.example.ui.VipCrown3DIcon
import com.example.ui.viewmodel.PrivateChatViewModel

// 🎨 টেলিগ্রাম অফিসিয়াল ডার্ক কালার প্যালেট
private val TelegramBg = Color(0xFF0E1621)
private val TelegramHeaderBg = Color(0xFF17212B)
private val TelegramCardBg = Color(0xFF17212B)
private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramOnlineGreen = Color(0xFF00E676)
private val TelegramMuted = Color(0xFF7E8D9F)
private val TelegramDivider = Color(0xFF1A222D)
private val TelegramUnreadBadge = Color(0xFF2AABEE)
private val TelegramFieldBg = Color(0xFF212D3B)

@Composable
fun PrivateInboxScreen(
    viewModel: PrivateChatViewModel,
    myUserId: String,
    onBackClick: () -> Unit,
    onOpenChat: (peerId: String, peerName: String, peerAvatar: String?, isOnline: Boolean, isVip: Boolean) -> Unit,
    onOpenCommunityChat: () -> Unit = {}, // 👈 কমিউনিটি গ্রুপ ওপেন করার কলব্যাক
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
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
            // 🔝 ১. টেলিগ্রাম স্টাইল এজ-টু-এজ টপ বার
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
                                BasicTextField(
                                    value = inboxState.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                    cursorBrush = SolidColor(TelegramBlue),
                                    decorationBox = { innerTextField ->
                                        if (inboxState.searchQuery.isEmpty()) {
                                            Text("Search chat...", color = TelegramMuted, fontSize = 14.sp)
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
                            } else if (inboxState.selectedTab == 0) {
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // =========================================================================
                    // 🧭 ২. স্লিক পিল ট্যাব বার: [ 💬 Chats ] [ 🔍 Find User by ID ]
                    // =========================================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TabPillItem(
                            label = "Chats",
                            icon = Icons.Outlined.ChatBubbleOutline,
                            badgeCount = inboxState.rawInbox.sumOf { it.unreadCount },
                            isSelected = inboxState.selectedTab == 0,
                            onClick = { viewModel.selectInboxTab(0) },
                            modifier = Modifier.weight(1f)
                        )

                        TabPillItem(
                            label = "Find by 8-Digit ID",
                            icon = Icons.Default.Tag,
                            isSelected = inboxState.selectedTab == 1,
                            onClick = { viewModel.selectInboxTab(1) },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }

            // =========================================================================
            // 📑 ৩. নির্বাচিত ট্যাবের কনটেন্ট
            // =========================================================================
            if (inboxState.selectedTab == 0) {
                // ---------------------------------------------------------------------
                // TAB 0: CHATS LIST (সবার উপরে পিন করা কমিউনিটি গ্রুপ)
                // ---------------------------------------------------------------------
                PullToRefreshBox(
                    isRefreshing = inboxState.isRefreshing,
                    onRefresh = { viewModel.refreshInbox(myUserId) },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        // 🌟 সবার উপরে স্থায়ীভাবে পিন করা কমিউনিটি গ্রুপ ব্যানার
                        item {
                            PinnedCommunityGroupBanner(
                                onClick = onOpenCommunityChat
                            )
                        }

                        if (inboxState.isLoading && inboxState.rawInbox.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = TelegramBlue, strokeWidth = 2.5.dp)
                                }
                            }
                        } else if (inboxState.filteredInbox.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp, start = 32.dp, end = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF16202C)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChatBubbleOutline,
                                                contentDescription = null,
                                                tint = TelegramMuted,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        Text(
                                            text = if (inboxState.searchQuery.isNotEmpty()) "No chats matching '${inboxState.searchQuery}'" else "No private messages yet",
                                            color = Color.White,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text = "Chat with fans in Community above, or tap 'Find by 8-Digit ID' to search a friend and message them directly.",
                                            color = TelegramMuted,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        } else {
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
            } else {
                // ---------------------------------------------------------------------
                // TAB 1: FIND USER BY 8-DIGIT ID
                // ---------------------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ৮-ডিজিট আইডি সার্চ ইনপুট কার্ড
                    Surface(
                        color = TelegramCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF222E3A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Search User by 8-Digit ID",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Every PlayDramaFlix user has a unique 8-digit account number (e.g. 85000100). Enter their ID to start private chat.",
                                color = TelegramMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(TelegramFieldBg)
                                    .border(1.dp, if (inboxState.searchAccountIdInput.length == 8) TelegramBlue else Color(0xFF2E3B4B), RoundedCornerShape(22.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Tag, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))

                                BasicTextField(
                                    value = inboxState.searchAccountIdInput,
                                    onValueChange = { viewModel.onSearchAccountIdInputChanged(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    cursorBrush = SolidColor(TelegramBlue),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        focusManager.clearFocus()
                                        viewModel.searchUserByAccountId(myUserId)
                                    }),
                                    decorationBox = { inner ->
                                        if (inboxState.searchAccountIdInput.isEmpty()) {
                                            Text("Enter 8-digit ID...", color = TelegramMuted, fontSize = 14.sp)
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                if (inboxState.searchAccountIdInput.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TelegramMuted,
                                        modifier = Modifier.size(16.dp).clickable { viewModel.clearSearchedUser() }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.searchUserByAccountId(myUserId)
                                },
                                enabled = inboxState.searchAccountIdInput.length >= 3 && !inboxState.isSearchingUser,
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                if (inboxState.isSearchingUser) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Searching...", color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Find User", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // সার্চ ফলাফল বা এরর ডিসপ্লে
                    if (inboxState.searchUserError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2E1618),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                Text(
                                    text = inboxState.searchUserError ?: "",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 👤 কাঙ্ক্ষিত ইউজার পাওয়া গেলে তার টেলিগ্রাম স্টাইল কার্ড
                    inboxState.searchedUser?.let { user ->
                        SearchedUserCard(
                            user = user,
                            onMessageClick = {
                                onOpenChat(
                                    user.peerId,
                                    user.peerName,
                                    user.displayAvatar,
                                    user.isOnline,
                                    user.isVip
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 👑 ১. সবার উপরে পিন করা কমিউনিটি গ্রুপ কার্ড (Pinned Community Group Banner)
// =============================================================================
@Composable
private fun PinnedCommunityGroupBanner(
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF131D28),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ব্লু গ্রুপ আইকন
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2AABEE), Color(0xFF1D78A8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DramaFlix Community Group",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // PINNED ব্যাজ
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B3245),
                            border = BorderStroke(0.6.dp, Color(0xFF2AABEE).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.PushPin, contentDescription = null, tint = Color(0xFF2AABEE), modifier = Modifier.size(10.dp))
                                Text("PINNED", color = Color(0xFF2AABEE), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(TelegramOnlineGreen)
                        )
                        Text(
                            text = "Public Group • Chat live with drama fans & share moments",
                            color = Color(0xFF8CA0B8),
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF213244),
                thickness = 1.dp
            )
        }
    }
}

// =============================================================================
// 🔍 ২. সার্চে পাওয়া ইউজারের কার্ড (Searched User Card)
// =============================================================================
@Composable
private fun SearchedUserCard(
    user: SearchedUserDto,
    onMessageClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = TelegramCardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, TelegramBlue.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // অবতার ও অনলাইন ডট
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222E3A)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(user.displayAvatar)
                                .crossfade(true)
                                .build(),
                            contentDescription = user.peerName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (user.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(TelegramOnlineGreen)
                                .border(2.dp, TelegramCardBg, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                // নাম, একাউন্ট আইডি ও স্ট্যাটাস
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.peerName,
                            color = Color.White,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.isVip) {
                            VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                        }
                    }

                    // ৮-ডিজিট আইডি ব্যাজ
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E2838),
                        border = BorderStroke(0.6.dp, Color(0xFF2E3E52))
                    ) {
                        Text(
                            text = "ID: ${user.accountId}",
                            color = TelegramBlue,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = if (user.isOnline) "online" else "last seen recently",
                        color = if (user.isOnline) TelegramOnlineGreen else TelegramMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            // বায়ো
            Text(
                text = user.bio ?: "Drama lover & PlayDramaFlix community member ✨",
                color = Color(0xFFCBD5E1),
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            // সরাসরি মেসেজ করার বাটন
            Button(
                onClick = onMessageClick,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Text("Send Message", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================================
// 📱 ৩. ইনবক্স চ্যাট আইটেমের রো (Inbox Row Item)
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
                // অবতার ও সবুজ অনলাইন ডট
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

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

                // নাম, লাস্ট মেসেজ ও মেটা
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
                        Text(
                            text = item.lastMessage.ifBlank { "No messages yet" },
                            color = if (item.unreadCount > 0) Color.White else TelegramMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

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

            HorizontalDivider(
                color = TelegramDivider,
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 76.dp)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// স্লিক পিল ট্যাব আইটেম কম্পোনেন্ট
// -----------------------------------------------------------------------------
@Composable
private fun TabPillItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) TelegramBlue else Color(0xFF1E2834),
        modifier = modifier
            .height(34.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TelegramMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else TelegramMuted,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else TelegramBlue)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$badgeCount",
                        color = if (isSelected) TelegramBlue else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
