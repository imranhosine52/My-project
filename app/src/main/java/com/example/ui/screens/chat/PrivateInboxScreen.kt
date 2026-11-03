@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

// 🎨 স্ক্রিনশটের হুবহু WhatsApp Dark Mode কালার প্যালেট
private val WaDarkBg = Color(0xFF0B141B)             // মূল হোয়াটসঅ্যাপ ডার্ক ব্যাকগ্রাউন্ড
private val WaHeaderBg = Color(0xFF111B21)           // টপ হেডার ব্যাকগ্রাউন্ড
private val WaSearchPillBg = Color(0xFF202C33)       // সার্চ পিল ব্যাকগ্রাউন্ড
private val WaGreen = Color(0xFF25D366)              // সিগনেচার হোয়াটসঅ্যাপ নিয়ন গ্রিন
private val WaTextPrimary = Color(0xFFE9EDEF)        // প্রধান টেক্সট হোয়াইট
private val WaTextMuted = Color(0xFF8696A0)          // মিউটেড সাবটাইটেল কালার
private val WaDivider = Color(0xFF202C33)            // স্লিম ডিভাইডার
private val WaBlueTick = Color(0xFF53BDEB)           // ব্লু টিক মার্ক কালার
private val WaFilterChipSelected = Color(0xFF005C4B) // অ্যাক্টিভ ফিল্টার চিপ
private val WaFilterChipUnselected = Color(0xFF202C33)

@Composable
fun PrivateInboxScreen(
    viewModel: PrivateChatViewModel,
    myUserId: String,
    onBackClick: () -> Unit,
    onOpenChat: (peerId: String, peerName: String, peerAvatar: String?, isOnline: Boolean, isVip: Boolean) -> Unit,
    onOpenCommunityChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val inboxState by viewModel.inboxUiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    var activeFilter by remember { mutableStateOf("All") }
    var showMenuDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(myUserId) {
        if (myUserId.isNotBlank()) {
            viewModel.refreshInbox(myUserId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WaDarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. WhatsApp অফিসিয়াল হেডার বার
            // =========================================================================
            Surface(
                color = WaHeaderBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = WaTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Text(
                                text = "WhatsApp",
                                color = WaTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.refreshInbox(myUserId) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = WaTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Box {
                                IconButton(onClick = { showMenuDropdown = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = WaTextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenuDropdown,
                                    onDismissRequest = { showMenuDropdown = false },
                                    modifier = Modifier
                                        .background(WaHeaderBg)
                                        .border(1.dp, WaDivider, RoundedCornerShape(10.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open Community Group", color = WaTextPrimary, fontSize = 14.sp) },
                                        leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, tint = WaGreen, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            onOpenCommunityChat()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Search by 8-Digit ID", color = WaTextPrimary, fontSize = 14.sp) },
                                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, tint = WaGreen, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showMenuDropdown = false
                                            activeFilter = "Find by ID"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // =========================================================================
                    // 🔍 ২. ফ্রস্টেড ডার্ক Search Bar Pill
                    // =========================================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(WaSearchPillBg)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = WaTextMuted,
                            modifier = Modifier.size(18.dp)
                        )

                        BasicTextField(
                            value = inboxState.searchQuery,
                            onValueChange = { query ->
                                viewModel.onSearchQueryChanged(query)
                                if (query.length == 8 && query.all { it.isDigit() }) {
                                    viewModel.onSearchAccountIdInputChanged(query)
                                    viewModel.searchUserByAccountId(myUserId)
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = WaTextPrimary, fontSize = 15.sp),
                            cursorBrush = SolidColor(WaGreen),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            decorationBox = { innerTextField ->
                                if (inboxState.searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search or enter 8-digit ID...",
                                        color = WaTextMuted,
                                        fontSize = 14.5.sp
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        if (inboxState.searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = WaTextMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.onSearchQueryChanged("")
                                        viewModel.clearSearchedUser()
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // =========================================================================
                    // 📑 ৩. WhatsApp ফিল্টার চিপস ([All], [Unread], [🔍 Find by ID])
                    // =========================================================================
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            WhatsAppFilterChip(
                                label = "All",
                                isSelected = activeFilter == "All",
                                onClick = {
                                    activeFilter = "All"
                                    viewModel.selectInboxTab(0)
                                }
                            )
                        }
                        item {
                            val unreadTotal = inboxState.rawInbox.sumOf { it.unreadCount }
                            WhatsAppFilterChip(
                                label = if (unreadTotal > 0) "Unread ($unreadTotal)" else "Unread",
                                isSelected = activeFilter == "Unread",
                                onClick = {
                                    activeFilter = "Unread"
                                    viewModel.selectInboxTab(0)
                                }
                            )
                        }
                        item {
                            WhatsAppFilterChip(
                                label = "🔍 Find by 8-Digit ID",
                                isSelected = activeFilter == "Find by ID",
                                onClick = {
                                    activeFilter = "Find by ID"
                                    viewModel.selectInboxTab(1)
                                }
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 📜 ৪. মূল কন্টেন্ট (চ্যাটস বা ৮-ডিজিট আইডি সার্চ)
            // =========================================================================
            if (activeFilter == "Find by ID" || inboxState.selectedTab == 1) {
                // ---------------------------------------------------------------------
                // ৮-ডিজিট আইডি সার্চ মোড
                // ---------------------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = WaHeaderBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, WaDivider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Find User by 8-Digit ID",
                                color = WaTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enter any user's 8-digit unique contact number to start private conversation directly.",
                                color = WaTextMuted,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(WaSearchPillBg)
                                    .border(1.dp, if (inboxState.searchAccountIdInput.length == 8) WaGreen else WaDivider, RoundedCornerShape(22.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Tag, contentDescription = null, tint = WaGreen, modifier = Modifier.size(18.dp))

                                BasicTextField(
                                    value = inboxState.searchAccountIdInput,
                                    onValueChange = { viewModel.onSearchAccountIdInputChanged(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = WaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    cursorBrush = SolidColor(WaGreen),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        focusManager.clearFocus()
                                        viewModel.searchUserByAccountId(myUserId)
                                    }),
                                    decorationBox = { inner ->
                                        if (inboxState.searchAccountIdInput.isEmpty()) {
                                            Text("Enter 8-digit ID...", color = WaTextMuted, fontSize = 14.sp)
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                if (inboxState.searchAccountIdInput.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = WaTextMuted,
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
                                colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                if (inboxState.isSearchingUser) {
                                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Searching...", color = Color.Black, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Find & Message", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    if (inboxState.searchUserError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF281418),
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

                    inboxState.searchedUser?.let { user ->
                        WhatsAppSearchedUserCard(
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
            } else {
                // ---------------------------------------------------------------------
                // মূল চ্যাট তালিকা (WhatsApp Dark Mode)
                // ---------------------------------------------------------------------
                val displayList = remember(inboxState.filteredInbox, activeFilter) {
                    if (activeFilter == "Unread") {
                        inboxState.filteredInbox.filter { it.unreadCount > 0 }
                    } else {
                        inboxState.filteredInbox
                    }
                }

                PullToRefreshBox(
                    isRefreshing = inboxState.isRefreshing,
                    onRefresh = { viewModel.refreshInbox(myUserId) },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // =============================================================
                        // 📌 ১. সবার উপরে পিন করা কমিউনিটি গ্রুপ
                        // =============================================================
                        if (activeFilter == "All" && inboxState.searchQuery.isEmpty()) {
                            item {
                                WhatsAppCommunityGroupRow(
                                    onClick = onOpenCommunityChat
                                )
                            }
                        }

                        // ২. সার্চ রেজাল্টে যদি ৮-ডিজিট আইডি দিয়ে ইউজার পাওয়া যায়
                        if (inboxState.searchedUser != null) {
                            item {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    WhatsAppSearchedUserCard(
                                        user = inboxState.searchedUser!!,
                                        onMessageClick = {
                                            val u = inboxState.searchedUser!!
                                            onOpenChat(u.peerId, u.peerName, u.displayAvatar, u.isOnline, u.isVip)
                                        }
                                    )
                                }
                            }
                        }

                        // ৩. খালি স্টেট
                        if (inboxState.isLoading && inboxState.rawInbox.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = WaGreen, strokeWidth = 2.5.dp)
                                }
                            }
                        } else if (displayList.isEmpty() && inboxState.searchedUser == null) {
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
                                                .background(WaHeaderBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChatBubble,
                                                contentDescription = null,
                                                tint = WaTextMuted,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        Text(
                                            text = if (inboxState.searchQuery.isNotEmpty()) "No chats found matching '${inboxState.searchQuery}'" else "No conversations yet",
                                            color = WaTextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text = "Chat in DramaFlix Community Group above or search a user's 8-digit ID to start a direct message.",
                                            color = WaTextMuted,
                                            fontSize = 12.5.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // ৪. চ্যাট রোগুলো
                            items(
                                items = displayList,
                                key = { it.chatRoomId }
                            ) { chatItem ->
                                WhatsAppStyleChatRow(
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
}

// =============================================================================
// 👑 ১. পিন করা কমিউনিটি গ্রুপ ব্যানার
// =============================================================================
@Composable
private fun WhatsAppCommunityGroupRow(
    onClick: () -> Unit
) {
    Surface(
        color = WaDarkBg,
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
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // গ্রিন গ্রুপ আইকন
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF005C4B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = WaGreen,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // টাইটেল ও মেটাডাটা
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DramaFlix Community",
                            color = WaTextPrimary,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "Pinned",
                            color = WaTextMuted,
                            fontSize = 11.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Public group • Chat live with drama fans & share moments",
                            color = WaTextMuted,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // 📌 ফিক্সড পিন আইকন (Icons.Default.PushPin)
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = WaTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = WaDivider,
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 80.dp)
            )
        }
    }
}

// =============================================================================
// 📱 ২. চ্যাট রো
// =============================================================================
@Composable
private fun WhatsAppStyleChatRow(
    item: LocalInboxEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val avatarModel = remember(item.peerAvatar, item.peerName) {
        item.peerAvatar?.takeIf { it.isNotBlank() }
            ?: "https://ui-avatars.com/api/?name=${item.peerName}&background=202C33&color=25D366&bold=true"
    }

    Surface(
        color = WaDarkBg,
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
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ১. অবতার ও অনলাইন ডট
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(WaSearchPillBg),
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

                    if (item.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(WaGreen)
                                .border(2.dp, WaDarkBg, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                // ২. নাম ও শেষ মেসেজ রো
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                color = WaTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (item.isVip) {
                                VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                            }
                        }

                        Text(
                            text = item.timeFormatted.ifBlank { "Recent" },
                            color = if (item.unreadCount > 0) WaGreen else WaTextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "✓✓",
                                color = if (item.unreadCount == 0) WaBlueTick else WaTextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = item.lastMessage.ifBlank { "Tap to send message" },
                                color = WaTextMuted,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // ৩. আনরিড ব্যাজ ও পিন
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = WaTextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // 🟢 WhatsApp নিয়ন গ্রিন আনরিড ব্যাজ
                            if (item.unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(WaGreen)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = WaDivider,
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 80.dp)
            )
        }
    }
}

// =============================================================================
// 🔍 ৩. সার্চ করা ইউজারের কার্ড
// =============================================================================
@Composable
private fun WhatsAppSearchedUserCard(
    user: SearchedUserDto,
    onMessageClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = WaHeaderBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WaGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(WaSearchPillBg),
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
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(WaGreen)
                                .border(2.dp, WaHeaderBg, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.peerName,
                            color = WaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.isVip) {
                            VipCrown3DIcon(modifier = Modifier.size(16.dp, 12.dp))
                        }
                    }

                    Text(
                        text = "Account ID: ${user.accountId}",
                        color = WaGreen,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (user.isOnline) "online" else "offline",
                        color = if (user.isOnline) WaGreen else WaTextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            Button(
                onClick = onMessageClick,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text("Chat on WhatsApp Mode", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================================
// 🏷️ ৪. WhatsApp ফিল্টার চিপ কম্পোনেন্ট
// =============================================================================
@Composable
private fun WhatsAppFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) WaFilterChipSelected else WaFilterChipUnselected,
        border = BorderStroke(0.6.dp, if (isSelected) WaGreen.copy(alpha = 0.5f) else Color.Transparent),
        modifier = Modifier
            .height(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) WaGreen else WaTextMuted,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
