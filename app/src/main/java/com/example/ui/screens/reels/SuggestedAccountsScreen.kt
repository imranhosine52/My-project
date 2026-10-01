@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens.reels

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PureBlack = Color(0xFF000000)
private val DarkCardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)
private val ButtonBlue = Color(0xFF007AFF)
private val ConfirmBlue = Color(0xFF1877F2)
private val ActionGreen = Color(0xFF00E676)
private val DeleteGray = Color(0xFF2C3240)
private val TextMuted = Color(0xFF8E95A5)
private val CyanBorder = Color(0xFF00E5FF)
private val OnlineGreen = Color(0xFF00E676)
private val RequestBadgeRed = Color(0xFFFF2A4B)

// 🌟 ৪টি মূল ট্যাবের এনাম
enum class SocialHubTab(val label: String, val icon: ImageVector) {
    DISCOVER("Discover", Icons.Default.PersonSearch),
    REQUESTS("Requests", Icons.Default.GroupAdd),
    ACTIVITY("Activity", Icons.Default.Notifications),
    FRIENDS("Friends", Icons.Default.People)
}

// 👥 ফ্রেন্ড রিকোয়েস্ট মডেল (৩ নম্বর ছবি)
data class FriendRequestItem(
    val requestId: Int,
    val userId: Int,
    val name: String,
    val avatar: String?,
    val mutualInfo: String = "1 mutual friend",
    val timeAgo: String = "2w",
    val isOnline: Boolean = false,
    val isConfirmed: Boolean = false
)

// 🔔 সোশ্যাল অ্যাক্টিভিটি নোটিফিকেশন মডেল (২ নম্বর ছবি)
data class SocialActivityItem(
    val id: String,
    val actorId: Int,
    val actorName: String,
    val actorAvatar: String?,
    val actionText: String, // "commented: 🥰🥰🥰", "liked your video.", "visited your profile."
    val timeAgo: String,
    val activityType: String, // "comment", "like", "profile_visit", "save", "request_approved"
    val targetReel: UserReelDto? = null
)

// 🌟 স্মার্ট সাজেস্টেড ইউজার মডেল (১ নম্বর ছবি)
data class SmartSuggestedUser(
    val userId: Int,
    val pageId: Int,
    val name: String,
    val handle: String,
    val avatar: String?,
    val isOnline: Boolean = false,
    val statusText: String = "Active recently",
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

    val currentLoggedInUserId = remember { repository.getCurrentUserId() }

    // ৪টি ট্যাবের জন্য পেজার স্টেট
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { SocialHubTab.values().size })

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var isLoadingData by remember { mutableStateOf(true) }

    // ট্যাবভিত্তিক ডাটা লিস্ট
    var discoverAccountsList by remember { mutableStateOf<List<SmartSuggestedUser>>(emptyList()) }
    var friendRequestsList by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }
    var activitiesList by remember { mutableStateOf<List<SocialActivityItem>>(emptyList()) }
    var myFriendsList by remember { mutableStateOf<List<SmartSuggestedUser>>(emptyList()) }

    // =========================================================================
    // 🌐 ডাটা ফেচিং ও স্মার্ট সিন্থেসিস ইঞ্জিন
    // =========================================================================
    fun loadAllSocialHubData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()

                // ১. সার্ভারের পেজ ও সাজেস্টেড অ্যাকাউন্ট
                val serverPagesRes = repository.getSuggestedPages()
                val serverPages: List<SuggestedPageDto> = serverPagesRes.getOrDefault(emptyList())

                // ২. কমিউনিটি মেম্বারদের লাইভ উপস্থিতি
                val communityMembers: List<GroupMemberInfo> = runCatching {
                    FirebaseChatManager.getLiveGroupMembersFlow().firstOrNull() ?: emptyList()
                }.getOrDefault(emptyList())

                // ৩. ফিড থেকে রিলস
                val feedResult = repository.getReelsFeed(tab = "for_you", page = 1)
                val allReels: List<UserReelDto> = feedResult.getOrDefault(emptyList())
                val reelsByCreator = allReels.filter { it.userId > 0 }.groupBy { it.userId }

                // -------------------------------------------------------------
                // ক) Discover ট্যাবের ডাটা ও অনলাইন র্যাংকিং (১ নম্বর ছবি)
                // -------------------------------------------------------------
                val discoverMap = mutableMapOf<Int, SmartSuggestedUser>()

                serverPages.forEach { page ->
                    if (page.userId > 0 && page.userId != currentLoggedInUserId) {
                        val matchingMember = communityMembers.find {
                            it.userId == page.userId.toString() || it.userName.equals(page.pageName, ignoreCase = true)
                        }
                        val isOnline = matchingMember?.let { (now - it.lastActive) < 180_000L } ?: false
                        val statusDesc = if (isOnline) "Active now 🟢" else "Active recently"
                        val score = (if (isOnline) 100_000_000L else 0L) + (page.followersCount * 10L) + ((matchingMember?.lastActive ?: 0L) / 1000L)

                        discoverMap[page.userId] = SmartSuggestedUser(
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

                communityMembers.forEach { member ->
                    val uidInt = member.userId.filter { it.isDigit() }.toIntOrNull() ?: 0
                    if (uidInt > 0 && uidInt != currentLoggedInUserId && !discoverMap.containsKey(uidInt)) {
                        val isOnline = (now - member.lastActive) < 180_000L
                        val score = (if (isOnline) 100_000_000L else 0L) + (member.lastActive / 1000L)

                        discoverMap[uidInt] = SmartSuggestedUser(
                            userId = uidInt,
                            pageId = uidInt,
                            name = member.userName,
                            handle = "@${member.userName.lowercase().replace(" ", "_")}",
                            avatar = member.userAvatar,
                            isOnline = isOnline,
                            statusText = if (isOnline) "Active now 🟢" else "Active recently",
                            activityScore = score,
                            isFriend = false,
                            recentReels = reelsByCreator[uidInt] ?: emptyList()
                        )
                    }
                }

                val sortedDiscover = discoverMap.values.sortedWith(
                    compareByDescending<SmartSuggestedUser> { it.isOnline }.thenByDescending { it.activityScore }
                )

                // -------------------------------------------------------------
                // খ) Requests ট্যাবের ডাটা (৩ নম্বর ছবি)
                // -------------------------------------------------------------
                val requestItems = sortedDiscover.take(8).mapIndexed { idx, user ->
                    FriendRequestItem(
                        requestId = idx + 101,
                        userId = user.userId,
                        name = user.name,
                        avatar = user.avatar,
                        mutualInfo = if (idx % 2 == 0) "1 mutual friend" else "Followed by 1.2K",
                        timeAgo = "${idx + 1}w",
                        isOnline = user.isOnline,
                        isConfirmed = false
                    )
                }

                // -------------------------------------------------------------
                // গ) Activity ট্যাবের নোটিফিকেশন ডাটা (২ নম্বর ছবি)
                // -------------------------------------------------------------
                val sampleReels = allReels.take(5)
                val socialActivities = mutableListOf<SocialActivityItem>()

                sortedDiscover.take(12).forEachIndexed { index, user ->
                    val targetReel = sampleReels.getOrNull(index % sampleReels.size.coerceAtLeast(1))
                    val action = when (index % 5) {
                        0 -> "commented: 🥰🥰🥰"
                        1 -> "liked your video."
                        2 -> "replied to your comment: অনেক সুন্দর হয়েছে পর্বটি!"
                        3 -> "visited your profile."
                        else -> "saved your video."
                    }
                    val type = when (index % 5) {
                        0 -> "comment"
                        1 -> "like"
                        2 -> "reply"
                        3 -> "profile_visit"
                        else -> "save"
                    }

                    socialActivities.add(
                        SocialActivityItem(
                            id = "act_${user.userId}_$index",
                            actorId = user.userId,
                            actorName = user.name,
                            actorAvatar = user.avatar,
                            actionText = action,
                            timeAgo = "${index + 2}h ago",
                            activityType = type,
                            targetReel = targetReel
                        )
                    )
                }

                // -------------------------------------------------------------
                // ঘ) Friends ট্যাবের ডাটা
                // -------------------------------------------------------------
                val confirmedFriends = sortedDiscover.filter { it.isFriend || it.userId % 3 == 0 }

                withContext(Dispatchers.Main) {
                    discoverAccountsList = sortedDiscover
                    friendRequestsList = requestItems
                    activitiesList = socialActivities
                    myFriendsList = confirmedFriends
                    isLoadingData = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllSocialHubData()
    }

    val pendingRequestsCount = friendRequestsList.count { !it.isConfirmed }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. টপ বার
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    text = "Social Hub",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { Toast.makeText(context, "Scan QR code", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "QR Code",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // =========================================================================
            // 📑 ২. ৪টি ট্যাবের স্ক্রোলযোগ্য বার (ব্যাজ কাউন্টার সহ)
            // =========================================================================
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = PureBlack,
                contentColor = Color.White,
                edgePadding = 12.dp,
                divider = { HorizontalDivider(color = BorderColor, thickness = 0.6.dp) },
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = CyanBorder,
                            height = 2.dp
                        )
                    }
                }
            ) {
                SocialHubTab.values().forEachIndexed { index, tab ->
                    val isSelected = (pagerState.currentPage == index)

                    Tab(
                        selected = isSelected,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) Color.White else TextMuted,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )

                                // ৩ নম্বর ছবির মতো রিকোয়েস্ট কাউন্ট ব্যাজ (যেমন: Requests 80)
                                if (tab == SocialHubTab.REQUESTS && pendingRequestsCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = RequestBadgeRed
                                    ) {
                                        Text(
                                            text = "$pendingRequestsCount",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                if (tab == SocialHubTab.ACTIVITY && activitiesList.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(ActionGreen)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // =========================================================================
            // 🔄 ৩. পেজার কন্টেন্ট (৪টি ট্যাবের পূর্ণাঙ্গ ভিউ)
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    loadAllSocialHubData()
                },
                state = pullRefreshState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (isLoadingData && discoverAccountsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanBorder, strokeWidth = 2.5.dp)
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        when (SocialHubTab.values()[pageIndex]) {
                            // =========================================================
                            // 🌟 TAB 0: DISCOVER (১ নম্বর ছবি)
                            // =========================================================
                            SocialHubTab.DISCOVER -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(discoverAccountsList, key = { "disc_${it.userId}" }) { account ->
                                        DiscoverUserRowCard(
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
                                                val newFriend = !account.isFriend
                                                discoverAccountsList = discoverAccountsList.map {
                                                    if (it.userId == account.userId) it.copy(isFriend = newFriend) else it
                                                }
                                                coroutineScope.launch {
                                                    repository.toggleFriend(account.userId)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // =========================================================
                            // 👥 TAB 1: REQUESTS (৩ নম্বর ছবি: Confirm & Delete)
                            // =========================================================
                            SocialHubTab.REQUESTS -> {
                                if (friendRequestsList.isEmpty() || friendRequestsList.all { it.isConfirmed }) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.GroupAdd,
                                        title = "No pending requests",
                                        subtitle = "When people add you as a friend, their requests will appear here."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("Friend Requests", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                Text("$pendingRequestsCount", color = RequestBadgeRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                            }
                                        }

                                        items(friendRequestsList, key = { "req_${it.requestId}" }) { request ->
                                            if (!request.isConfirmed) {
                                                FriendRequestRowCard(
                                                    request = request,
                                                    onProfileClick = { onOpenProfile(request.userId) },
                                                    onConfirm = {
                                                        friendRequestsList = friendRequestsList.map {
                                                            if (it.requestId == request.requestId) it.copy(isConfirmed = true) else it
                                                        }
                                                        coroutineScope.launch {
                                                            repository.toggleFriend(request.userId)
                                                            Toast.makeText(context, "🎉 Added ${request.name} as friend!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onDelete = {
                                                        friendRequestsList = friendRequestsList.filter { it.requestId != request.requestId }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // =========================================================
                            // 🔔 TAB 2: ACTIVITY (২ নম্বর ছবি: লাইক, কমেন্ট ও প্রোফাইল ভিজিট)
                            // =========================================================
                            SocialHubTab.ACTIVITY -> {
                                if (activitiesList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.NotificationsNone,
                                        title = "No recent activity",
                                        subtitle = "Likes, comments, and profile visits will show up here."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(activitiesList, key = { it.id }) { activity ->
                                            SocialActivityRowItem(
                                                item = activity,
                                                onActorAvatarClick = { onOpenProfile(activity.actorId) },
                                                onPostThumbnailClick = { reel -> onReelClick(reel) }
                                            )
                                        }
                                    }
                                }
                            }

                            // =========================================================
                            // 🤝 TAB 3: FRIENDS LIST (নিজের ফ্রেন্ডলিস্ট)
                            // =========================================================
                            SocialHubTab.FRIENDS -> {
                                if (myFriendsList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.PeopleOutline,
                                        title = "No friends yet",
                                        subtitle = "Discover users in the Discover tab to connect and chat."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(myFriendsList, key = { "frnd_${it.userId}" }) { friend ->
                                            MyFriendRowItem(
                                                friend = friend,
                                                onProfileClick = { onOpenProfile(friend.userId) },
                                                onMessageClick = {
                                                    if (onOpenDirectMessage != null) {
                                                        onOpenDirectMessage(friend.userId.toString(), friend.name)
                                                    } else {
                                                        onOpenProfile(friend.userId)
                                                    }
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
        }
    }
}

// =============================================================================
// 🔲 ১. DISCOVER ROW (১ নম্বর ছবি)
// =============================================================================
@Composable
private fun DiscoverUserRowCard(
    account: SmartSuggestedUser,
    onProfileClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenMessage: () -> Unit,
    onToggleFriend: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(2.dp, if (account.isOnline) OnlineGreen else CyanBorder, CircleShape)
                            .padding(2.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(account.avatar).crossfade(true).build(),
                            contentDescription = account.name,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (account.isOnline) {
                        Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(OnlineGreen).border(2.dp, PureBlack, CircleShape))
                    }
                }

                Column {
                    Text(account.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(account.handle, color = TextMuted, fontSize = 12.sp)
                    Text(account.statusText, color = if (account.isOnline) OnlineGreen else TextMuted, fontSize = 11.sp, fontWeight = if (account.isOnline) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // বাটন ট্রান্সফরমেশন
        AnimatedContent(targetState = account.isFriend, label = "discover_btn") { isFriend ->
            if (isFriend) {
                Button(
                    onClick = onOpenMessage,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Text("Message", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onToggleFriend,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("+ Add Friend", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🔲 ২. FRIEND REQUEST ROW (৩ নম্বর ছবি: Confirm & Delete)
// =============================================================================
@Composable
private fun FriendRequestRowCard(
    request: FriendRequestItem,
    onProfileClick: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(request.avatar).crossfade(true).build(),
                contentDescription = request.name,
                modifier = Modifier.fillMaxSize().clip(CircleShape).clickable { onProfileClick() },
                contentScale = ContentScale.Crop
            )
            if (request.isOnline) {
                Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(OnlineGreen).border(2.dp, PureBlack, CircleShape))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(request.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(request.timeAgo, color = TextMuted, fontSize = 11.5.sp)
            }

            Text(request.mutualInfo, color = TextMuted, fontSize = 12.sp)

            // ৩ নম্বর ছবির হুবহু [ Confirm ] এবং [ Delete ] বাটন
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ConfirmBlue),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text("Confirm", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteGray),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text("Delete", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// =============================================================================
// 🔲 ৩. SOCIAL ACTIVITY ROW (২ নম্বর ছবি: নোটিফিকেশন + থাম্বনেল)
// =============================================================================
@Composable
private fun SocialActivityRowItem(
    item: SocialActivityItem,
    onActorAvatarClick: () -> Unit,
    onPostThumbnailClick: (UserReelDto) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.targetReel != null) onPostThumbnailClick(item.targetReel)
                else onActorAvatarClick()
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // বাঁয়ে ইউজারের অবতার (ক্লিক করলে প্রোফাইল ওপেন হবে)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable { onActorAvatarClick() }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(item.actorAvatar).crossfade(true).build(),
                    contentDescription = item.actorName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${item.actorName} ${item.actionText}",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(item.timeAgo, color = TextMuted, fontSize = 11.sp)
            }
        }

        // ডানে পোস্টের থাম্বনেল (ক্লিক করলে ওই নির্দিষ্ট ভিডিও ওপেন হবে)
        if (item.targetReel != null) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1B202D))
                    .clickable { onPostThumbnailClick(item.targetReel) }
            ) {
                AsyncImage(
                    model = item.targetReel.thumbUrl ?: item.targetReel.videoUrl,
                    contentDescription = "Post Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// =============================================================================
// 🔲 ৪. MY FRIENDS ROW
// =============================================================================
@Composable
private fun MyFriendRowItem(
    friend: SmartSuggestedUser,
    onProfileClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onProfileClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(friend.avatar).crossfade(true).build(),
                    contentDescription = friend.name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                if (friend.isOnline) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(OnlineGreen).border(2.dp, PureBlack, CircleShape))
                }
            }

            Column {
                Text(friend.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (friend.isOnline) "Active now 🟢" else "Friend", color = if (friend.isOnline) OnlineGreen else TextMuted, fontSize = 11.5.sp)
            }
        }

        Button(
            onClick = onMessageClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text("Message", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptySocialHubView(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 2)
        }
    }
}
