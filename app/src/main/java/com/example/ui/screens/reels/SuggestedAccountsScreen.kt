@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GroupMemberInfo
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import com.example.util.FirebaseChatManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PureBlack = Color(0xFF000000)
private val ButtonBlue = Color(0xFF007AFF)
private val ActionGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8E95A5)
private val CyanBorder = Color(0xFF00E5FF)
private val OnlineGreen = Color(0xFF00E676)

/**
 * 🌟 স্মার্ট অনলাইন ও অ্যাক্টিভিটি স্কোরযুক্ত ইউজার মডেল
 */
data class SmartSuggestedUser(
    val userId: Int,
    val pageId: Int,
    val name: String,
    val handle: String,
    val avatar: String?,
    val isOnline: Boolean = false,
    val statusText: String = "People you may know",
    val activityScore: Long = 0L,
    val isFriend: Boolean = false,
    val recentReels: List<UserReelDto> = emptyList()
)

@Composable
fun SuggestedAccountsScreen(
    reelsViewModel: ReelsViewModel,
    onBackClick: () -> Unit,
    onOpenProfile: (userId: Int) -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenDirectMessage: ((userId: String, userName: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    var accountsList by remember { mutableStateOf<List<SmartSuggestedUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val currentLoggedInUserId = remember { repository.getCurrentUserId() }

    // =========================================================================
    // 🧠 স্মার্ট অ্যালগরিদম: অনলাইন ইউজার সবার প্রথমে + অ্যাক্টিভিটি র্যাংকিং
    // =========================================================================
    fun loadSmartAccounts() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()

                // ১. সার্ভার থেকে সাজেস্টেড পেজ ও অ্যাকাউন্ট আনা
                val serverPagesRes = repository.getSuggestedPages()
                val serverPages: List<SuggestedPageDto> = serverPagesRes.getOrDefault(emptyList())

                // ২. 🎯 ফিক্সড: লাইভ ফ্লো থেকে রিয়েল-টাইম মেম্বারদের উপস্থিতি আনা (কোনো আনরিসলভড এরর ছাড়া)
                val communityMembers: List<GroupMemberInfo> = runCatching {
                    FirebaseChatManager.getLiveGroupMembersFlow().firstOrNull() ?: emptyList()
                }.getOrDefault(emptyList())

                // ৩. ফিড থেকে রিলস আনা (প্রিভিউ থাম্বনেলের জন্য)
                val feedResult = repository.getReelsFeed(tab = "for_you", page = 1)
                val allReels: List<UserReelDto> = feedResult.getOrDefault(emptyList())
                val reelsByCreator = allReels.filter { it.userId > 0 }.groupBy { it.userId }

                val combinedMap = mutableMapOf<Int, SmartSuggestedUser>()

                // ক) সার্ভারের সাজেস্টেড অ্যাকাউন্ট প্রসেস
                serverPages.forEach { page ->
                    if (page.userId > 0 && page.userId != currentLoggedInUserId) {
                        val matchingMember = communityMembers.find {
                            it.userId == page.userId.toString() || it.userName.equals(page.pageName, ignoreCase = true)
                        }
                        val isOnline = matchingMember?.let { (now - it.lastActive) < 180_000L } ?: false
                        val statusDesc = when {
                            isOnline -> "Active now 🟢"
                            page.isFollowing -> "Connected"
                            page.followersCount > 1000 -> "${page.formattedFollowers} fans"
                            else -> "Recommended for you"
                        }

                        val score = (if (isOnline) 100_000_000L else 0L) +
                                (page.followersCount * 10L) +
                                (page.totalReels * 50L) +
                                ((matchingMember?.lastActive ?: 0L) / 1000L)

                        combinedMap[page.userId] = SmartSuggestedUser(
                            userId = page.userId,
                            pageId = page.pageId,
                            name = page.pageName,
                            handle = page.displayHandle,
                            avatar = page.avatar,
                            isOnline = isOnline,
                            statusText = statusDesc,
                            activityScore = score,
                            isFriend = page.isFollowing,
                            recentReels = reelsByCreator[page.userId] ?: emptyList()
                        )
                    }
                }

                // খ) কমিউনিটি মেম্বারদের প্রসেস (ডাটাবেজের সব অ্যাকাউন্ট একত্রীকরণ)
                communityMembers.forEach { member ->
                    val uidInt = member.userId.filter { it.isDigit() }.toIntOrNull() ?: 0
                    if (uidInt > 0 && uidInt != currentLoggedInUserId && !combinedMap.containsKey(uidInt)) {
                        val isOnline = (now - member.lastActive) < 180_000L
                        val statusDesc = when {
                            isOnline -> "Active now 🟢"
                            member.lastActive > 0 -> "Active recently"
                            else -> "Community member"
                        }

                        val score = (if (isOnline) 100_000_000L else 0L) + (member.lastActive / 1000L)

                        combinedMap[uidInt] = SmartSuggestedUser(
                            userId = uidInt,
                            pageId = uidInt,
                            name = member.userName,
                            handle = "@${member.userName.lowercase().replace(" ", "_")}",
                            avatar = member.userAvatar,
                            isOnline = isOnline,
                            statusText = statusDesc,
                            activityScore = score,
                            isFriend = false,
                            recentReels = reelsByCreator[uidInt] ?: emptyList()
                        )
                    }
                }

                // গ) রিলস ফিডের ক্রিয়েটরদের প্রসেস
                reelsByCreator.forEach { (creatorId, reelsList) ->
                    if (creatorId != currentLoggedInUserId && !combinedMap.containsKey(creatorId)) {
                        val firstReel = reelsList.first()
                        val pId = if (firstReel.pageId > 0) firstReel.pageId else creatorId

                        combinedMap[creatorId] = SmartSuggestedUser(
                            userId = creatorId,
                            pageId = pId,
                            name = firstReel.pageName.ifBlank { "Drama Creator" },
                            handle = firstReel.displayHandle,
                            avatar = firstReel.pageAvatar,
                            isOnline = false,
                            statusText = "Content Creator",
                            activityScore = (reelsList.size * 500L),
                            isFriend = firstReel.isFollowing,
                            recentReels = reelsList.take(4)
                        )
                    }
                }

                // 🎯 চূড়ান্ত সর্টিং: অনলাইন ইউজার সবার শীর্ষে, এরপর সর্বোচ্চ সক্রিয় অ্যাকাউন্ট
                val sortedList = combinedMap.values.sortedWith(
                    compareByDescending<SmartSuggestedUser> { it.isOnline }
                        .thenByDescending { it.activityScore }
                )

                withContext(Dispatchers.Main) {
                    accountsList = sortedList
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadSmartAccounts()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. টপ বার: [ ← Back ] ----- Find friends ----- [ ⛶ QR ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Find friends",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        Toast.makeText(context, "QR Scanner ready", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Scan QR",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // =========================================================================
            // 🏷️ ২. হেডার: "Suggested accounts ⓘ" + লাইভ অনলাইন কাউন্ট
            // =========================================================================
            val onlineCount = accountsList.count { it.isOnline }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Suggested accounts",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ⓘ",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }

                if (onlineCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OnlineGreen.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, OnlineGreen)
                    ) {
                        Text(
                            text = "$onlineCount Online 🟢",
                            color = OnlineGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 📋 ৩. অনলাইন-ফার্স্ট সাজেস্টেড অ্যাকাউন্টস তালিকা
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    loadSmartAccounts()
                },
                state = pullRefreshState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (isLoading && accountsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.5.dp)
                    }
                } else if (accountsList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Text("No accounts found", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Pull down to refresh and discover users.", color = TextMuted, fontSize = 12.5.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(accountsList, key = { it.userId }) { account ->
                            SmartSuggestedUserCard(
                                account = account,
                                onProfileClick = { onOpenProfile(account.userId) },
                                onReelClick = onReelClick,
                                onOpenMessage = {
                                    if (onOpenDirectMessage != null) {
                                        onOpenDirectMessage(account.userId.toString(), account.name)
                                    } else {
                                        onOpenProfile(account.userId)
                                    }
                                },
                                onToggleFriend = {
                                    val newFriendState = !account.isFriend
                                    // লোকাল স্টেট তৎক্ষণাৎ আপডেট (বাটন ট্রান্সফর্ম হবে)
                                    accountsList = accountsList.map {
                                        if (it.userId == account.userId) it.copy(isFriend = newFriendState)
                                        else it
                                    }
                                    coroutineScope.launch {
                                        val res = repository.toggleFriend(account.userId)
                                        if (res.isFailure) {
                                            accountsList = accountsList.map {
                                                if (it.userId == account.userId) it.copy(isFriend = !newFriendState)
                                                else it
                                            }
                                        }
                                    }
                                },
                                onRemoveClick = {
                                    accountsList = accountsList.filter { it.userId != account.userId }
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
// 🔲 একক স্মার্ট ইউজার কার্ড (🎯 ফ্রেন্ড করার পর বাটনটি মেসেজ বাটনে রূপান্তরিত হবে)
// =============================================================================
@Composable
private fun SmartSuggestedUserCard(
    account: SmartSuggestedUser,
    onProfileClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenMessage: () -> Unit,
    onToggleFriend: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ১. ক্রিয়েটর প্রোফাইল ইনফো রো (অবতার + নাম + অনলাইন ইন্ডিকেটর)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProfileClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // সার্কুলার অবতার + লাইভ অনলাইন ডট
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(2.dp, if (account.isOnline) OnlineGreen else CyanBorder, CircleShape)
                            .padding(2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(account.avatar ?: "https://ui-avatars.com/api/?name=${account.name}&background=1E2638&color=fff")
                                .crossfade(true)
                                .build(),
                            contentDescription = account.name,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (account.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(OnlineGreen)
                                .border(2.dp, PureBlack, CircleShape)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = account.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = account.handle,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = account.statusText,
                        color = if (account.isOnline) OnlineGreen else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (account.isOnline) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            IconButton(onClick = onRemoveClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ২. ৪টি রিলসের থাম্বনেল প্রিভিউ স্ট্রিপ (যদি থাকে)
        if (account.recentReels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                account.recentReels.take(4).forEach { reel ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF141722))
                            .clickable { onReelClick(reel) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = reel.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Black.copy(alpha = 0.60f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(3.dp)
                        ) {
                            Text(
                                text = "▷ ${reel.viewsCount}",
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                repeat(4 - account.recentReels.take(4).size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // =========================================================================
        // 🎯 ৩. স্মার্ট বাটন: ফ্রেন্ড না থাকলে [+ Add Friend], ফ্রেন্ড হলে [Message 💬]
        // =========================================================================
        AnimatedContent(
            targetState = account.isFriend,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
            },
            label = "friend_to_message_button"
        ) { isFriend ->
            if (isFriend) {
                // 💬 ফ্রেন্ড হওয়ার পর মেসেজ বাটন (সরাসরি চ্যাট ওপেন হবে)
                Button(
                    onClick = onOpenMessage,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Message",
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Message",
                            color = Color.Black,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // ➕ ফ্রেন্ড না থাকলে ফ্রেন্ড রিকোয়েস্ট বাটন
                Button(
                    onClick = onToggleFriend,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add Friend",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "+ Add Friend",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
