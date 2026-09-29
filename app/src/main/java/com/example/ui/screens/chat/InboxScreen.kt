@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.DirectConversationItem
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PureBlack = Color(0xFF000000)
private val AlertRed = Color(0xFFFE2C55)
private val TextMuted = Color(0xFF8692A6)
private val OnlineGreen = Color(0xFF00E676)

@Composable
fun InboxScreen(
    currentUserId: String,
    currentUserAvatar: String?,
    onOpenPersonalChat: (otherUserId: String, otherUserName: String, otherUserAvatar: String?) -> Unit,
    onOpenSearch: () -> Unit,
    onCreateStoryOrReel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository(context) }

    // 🎯 VPS 2 থেকে রিয়েল ইনবক্স কনভারসেশন স্টেট
    var conversationList by remember { mutableStateOf<List<DirectConversationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val isSocketConnected by chatRepository.isSocketConnected.collectAsStateWithLifecycle()

    fun loadInboxData() {
        coroutineScope.launch {
            val result = chatRepository.getInboxConversations()
            conversationList = result.getOrDefault(emptyList())
            isLoading = false
        }
    }

    // স্ক্রিন ওপেন হতেই লাইভ সকেটে কানেক্ট ও ইনবক্স ফেচ
    LaunchedEffect(Unit) {
        chatRepository.connectLiveSocket()
        loadInboxData()
    }

    // ⚡ WebSocket দিয়ে নতুন মেসেজ আসলে ইনবক্স তৎক্ষণাৎ আপডেট হওয়া
    LaunchedEffect(Unit) {
        chatRepository.incomingLiveMessages.collect { newMsg ->
            loadInboxData()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. টপ বার: [ 👤+ ] ------ Inbox 🟢 ------ [ 🔍 ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onOpenSearch, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Find friends",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // লাইভ WebSocket স্ট্যাটাস অনুযায়ী অনলাইন ডট
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Inbox",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isSocketConnected) OnlineGreen else Color(0xFF6B7280))
                    )
                }

                IconButton(onClick = onOpenSearch, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // =========================================================================
            // 🌟 ২. অ্যাক্টিভ ফ্রেন্ডস ও স্টোরি রো
            // =========================================================================
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ১ম আইটেম: নিজের অবতার ও "Create"
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .width(66.dp)
                            .clickable { onCreateStoryOrReel() }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF262C38)
                        ) {
                            Text(
                                text = "What's good?",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.5.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Box(
                            modifier = Modifier.size(62.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2638)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = currentUserAvatar ?: "https://ui-avatars.com/api/?name=Me&background=00E676&color=000&bold=true",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00B0FF))
                                    .border(1.5.dp, PureBlack, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Text("Create", color = TextMuted, fontSize = 11.sp)
                    }
                }

                // আসল অ্যাক্টিভ কনভারসেশনের ইউজারদের ছোট সার্কেল
                val activeContacts = conversationList.filter { !it.isSystemNotification }.take(6)
                items(activeContacts, key = { "top_${it.conversationId}" }) { contact ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .width(66.dp)
                            .clickable {
                                onOpenPersonalChat(contact.otherUserId, contact.otherUserName, contact.otherUserAvatar)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (contact.isOnline) OnlineGreen else Color(0xFF00B0FF), CircleShape)
                                .padding(2.5.dp)
                        ) {
                            AsyncImage(
                                model = contact.otherUserAvatar ?: "https://ui-avatars.com/api/?name=${contact.otherUserName}&background=1E2638&color=fff",
                                contentDescription = contact.otherUserName,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Text(
                            text = contact.otherUserName.split(" ").firstOrNull() ?: contact.otherUserName,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1A1D27), thickness = 0.6.dp)

            // =========================================================================
            // 💬 ৩. আসল ইনবক্স কনভারসেশন লিস্ট (VPS 2 Live)
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        loadInboxData()
                        delay(500)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (isLoading && conversationList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OnlineGreen, strokeWidth = 2.5.dp)
                    }
                } else if (conversationList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                            Text("No messages yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Start a conversation with a creator or friend from their profile.", color = TextMuted, fontSize = 12.5.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                    ) {
                        items(conversationList, key = { it.conversationId }) { item ->
                            InboxRowItem(
                                item = item,
                                onClick = {
                                    onOpenPersonalChat(
                                        item.otherUserId,
                                        item.otherUserName,
                                        item.otherUserAvatar
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

// -------------------------------------------------------------
// 📋 একক ইনবক্স রো আইটেম
// -------------------------------------------------------------
@Composable
private fun InboxRowItem(
    item: DirectConversationItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // অবতার বা সিস্টেম আইকন
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            when {
                item.isSystemNotification -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF262C38)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF1E2638)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = item.otherUserAvatar ?: "https://ui-avatars.com/api/?name=${item.otherUserName}&background=1E2638&color=fff",
                            contentDescription = item.otherUserName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (item.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(OnlineGreen)
                                .border(2.dp, PureBlack, CircleShape)
                        )
                    }
                }
            }
        }

        // নাম ও সর্বশেষ মেসেজ
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.otherUserName,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.lastMessage.ifBlank { "Sent an attachment" },
                color = if (item.unreadCount > 0 && !item.isSystemNotification) Color.White else TextMuted,
                fontSize = 12.sp,
                fontWeight = if (item.unreadCount > 0 && !item.isSystemNotification) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ডানপাশে সময় ও লাল আনরিড কাউন্ট ব্যাজ
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.lastMessageTime,
                color = TextMuted,
                fontSize = 10.5.sp
            )

            if (item.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AlertRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
