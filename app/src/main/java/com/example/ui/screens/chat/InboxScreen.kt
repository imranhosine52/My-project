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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
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
import coil.request.ImageRequest
import com.example.data.model.DirectConversationItem

private val PureBlack = Color(0xFF000000)
private val AlertRed = Color(0xFFFE2C55)
private val TextMuted = Color(0xFF8692A6)
private val DarkCardBg = Color(0xFF12141B)
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

    // ইনবক্সের নমুনা চ্যাট ও নোটিফিকেশন তালিকা (যা স্ক্রিনশটের হুবহু ডিজাইনে সাজানো)
    val conversationList = remember {
        listOf(
            DirectConversationItem(
                conversationId = "conv_1",
                otherUserId = "user_101",
                otherUserName = "Md Mahidul Islam Raihan",
                otherUserAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=120&q=80",
                lastMessage = "started following you",
                unreadCount = 1,
                activityType = "follow"
            ),
            DirectConversationItem(
                conversationId = "conv_2",
                otherUserId = "user_102",
                otherUserName = "Activity & new followers",
                otherUserAvatar = null,
                lastMessage = "Akhi Akter replied to your comment: আপু একটু সাপোর্ট ...",
                unreadCount = 10,
                activityType = "comment"
            ),
            DirectConversationItem(
                conversationId = "conv_3",
                otherUserId = "page_1",
                otherUserName = "Scene Flix",
                otherUserAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&q=80",
                lastMessage = "Seen",
                lastMessageTime = "Today",
                unreadCount = 0
            ),
            DirectConversationItem(
                conversationId = "conv_4",
                otherUserId = "system_notif",
                otherUserName = "System notifications",
                otherUserAvatar = null,
                lastMessage = "Account updates: Community Guidelines update • Sep 6",
                unreadCount = 1,
                isSystemNotification = true
            ),
            DirectConversationItem(
                conversationId = "conv_5",
                otherUserId = "user_103",
                otherUserName = "জাতীয় কিউট বয় 🧸",
                otherUserAvatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=120&q=80",
                lastMessage = "Mentioned you in a post • Aug 30",
                unreadCount = 1,
                activityType = "mention"
            ),
            DirectConversationItem(
                conversationId = "conv_6",
                otherUserId = "user_104",
                otherUserName = "Proma",
                otherUserAvatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=120&q=80",
                lastMessage = "shared a video • Aug 28",
                unreadCount = 0
            ),
            DirectConversationItem(
                conversationId = "conv_7",
                otherUserId = "user_105",
                otherUserName = "Md Rasel mia",
                otherUserAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=120&q=80",
                lastMessage = "😍 • Aug 20",
                unreadCount = 0
            ),
            DirectConversationItem(
                conversationId = "conv_8",
                otherUserId = "user_106",
                otherUserName = "YouR MiM 🍒",
                otherUserAvatar = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=120&q=80",
                lastMessage = "Sent",
                unreadCount = 0
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. স্ক্রিনশটের হুবহু টপ বার: [ 👤+ ] ------ Inbox 🟢 ------ [ 🔍 ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // বামে ফ্রেন্ডস আইকন
                IconButton(onClick = {}, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Find friends",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // সেন্টারে Inbox + অনলাইন ইন্ডিকেটর
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
                            .background(OnlineGreen)
                    )
                }

                // ডানে সার্চ আইকন
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
            // 🌟 ২. স্ক্রিনশটের হুবহু টপ স্টোরি ও ফ্রেন্ডস ক্যারোজেল
            // =========================================================================
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ১ম আইটেম: নিজের অবতার ও "What's good?" বাবল
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

                // বন্ধুদের তালিকা
                val onlineFriends = listOf(
                    Pair("পরী মনি", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=120&q=80"),
                    Pair("তানহা", "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=120&q=80"),
                    Pair("স্বপ্নের রানী", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=120&q=80")
                )

                items(onlineFriends) { friend ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .width(66.dp)
                            .clickable {
                                onOpenPersonalChat("user_${friend.first.hashCode()}", friend.first, friend.second)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFF00B0FF), CircleShape)
                                .padding(2.5.dp)
                        ) {
                            AsyncImage(
                                model = friend.second,
                                contentDescription = friend.first,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Text(
                            text = friend.first,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // শেষ আইটেম: "+ Widget"
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.width(66.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF262C38)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Text("+ Widget", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1A1D27), thickness = 0.6.dp)

            // =========================================================================
            // 💬 ৩. স্ক্রিনশটের হুবহু অ্যাক্টিভিটি ও পার্সোনাল চ্যাট লিস্ট
            // =========================================================================
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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

// =============================================================================
// 📋 একক ইনবক্স রো কম্পোনেন্ট (স্ক্রিনশটের সব স্টাইল সহ)
// =============================================================================
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
        // ১. অবতার বা আইকন
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            when {
                item.activityType == "comment" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFFFF2A4B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChatBubble, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
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

                    // ফলো অ্যাক্টিভিটিতে ছোট নীল ফ্রেন্ডস ব্যাজ
                    if (item.activityType == "follow") {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00B0FF))
                                .border(1.5.dp, PureBlack, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        }

        // ২. নাম ও সাবটাইটেল
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
                text = item.lastMessage,
                color = if (item.unreadCount > 0 && !item.isSystemNotification) Color.White else TextMuted,
                fontSize = 12.sp,
                fontWeight = if (item.unreadCount > 0 && !item.isSystemNotification) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ৩. ডানপাশে লাল আনরিড কাউন্ট ব্যাজ (যেমন: 1, 10)
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
