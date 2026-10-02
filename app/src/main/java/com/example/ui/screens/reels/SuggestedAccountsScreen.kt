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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
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
import com.example.data.model.ConfirmedFriendDto
import com.example.data.model.FriendRequestDto
import com.example.data.model.GroupMemberInfo
import com.example.data.model.SocialActivityDto
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

enum class SocialHubTab(val label: String, val icon: ImageVector) {
    DISCOVER("Discover", Icons.Default.PersonSearch),
    REQUESTS("Requests", Icons.Default.GroupAdd),
    ACTIVITY("Activity", Icons.Default.Notifications),
    FRIENDS("Friends", Icons.Default.People)
}

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

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { SocialHubTab.values().size })

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var isLoadingData by remember { mutableStateOf(true) }

    // 🎯 ১০০% আসল সার্ভার ডাটা স্টেট (ডামি ডাটা ০%)
    var discoverAccountsList by remember { mutableStateOf<List<SmartSuggestedUser>>(emptyList()) }
    var friendRequestsList by remember { mutableStateOf<List<FriendRequestDto>>(emptyList()) }
    var friendRequestsTotalCount by remember { mutableIntStateOf(0) }
    var activitiesList by remember { mutableStateOf<List<SocialActivityDto>>(emptyList()) }
    var confirmedFriendsList by remember { mutableStateOf<List<ConfirmedFriendDto>>(emptyList()) }

    // =========================================================================
    // 🌐 লাইভ সার্ভার এপিআই ডাটা সিন্থেসিস ইঞ্জিন (NO DUMMY DATA)
    // =========================================================================
    fun loadAllSocialHubData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()

                // ১. Discover Tab: সার্ভারের পেজ ও একটিভ মেম্বারস
                val serverPagesRes = repository.getSuggestedPages()
                val serverPages: List<SuggestedPageDto> = serverPagesRes.getOrDefault(emptyList())

                val communityMembers: List<GroupMemberInfo> = runCatching {
                    FirebaseChatManager.getLiveGroupMembersFlow().firstOrNull() ?: emptyList()
                }.getOrDefault(emptyList())

                val feedResult = repository.getReelsFeed(tab = "for_you", page = 1)
                val allReels: List<UserReelDto> = feedResult.getOrDefault(emptyList())
                val reelsByCreator = allReels.filter { it.userId > 0 }.groupBy { it.userId }

                val discoverMap = mutableMapOf<Int, SmartSuggestedUser>()

                serverPages.forEach { page ->
                    if (page.userId > 0 && page.userId != currentLoggedInUserId) {
                        val member = communityMembers.find { it.userId == page.userId.toString() }
                        val isOnline = member?.let { (now - it.lastActive) < 180_000L } ?: false
                        val statusDesc = if (isOnline) "Active now 🟢" else "${page.formattedFollowers} fans"
                        val score = (if (isOnline) 100_000_000L else 0L) + (page.followersCount * 10L)

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

                // ২. Requests Tab: সার্ভার থেকে পেন্ডিং ফ্রেন্ড রিকোয়েস্ট (আসল ডাইনামিক ব্যাজ কাউন্ট)
                val requestsRes = repository.getFriendRequests()
                val reqData = requestsRes.getOrNull()
                val realRequests = reqData?.effectiveRequests ?: emptyList()
                val realRequestsCount = reqData?.totalCount ?: realRequests.size

                // ৩. Activity Tab: সার্ভার থেকে আসল সোশ্যাল অ্যাক্টিভিটি নোটিফিকেশন ফিড
                val actRes = repository.getSocialActivities()
                val actData = actRes.getOrNull()
                val realActivities = actData?.effectiveActivities ?: emptyList()

                // ৪. Friends Tab: সার্ভার থেকে আসল কনফার্মড ফ্রেন্ডলিস্ট
                val friendsRes = repository.getConfirmedFriends()
                val realFriends = friendsRes.getOrDefault(emptyList())

                withContext(Dispatchers.Main) {
                    discoverAccountsList = discoverMap.values.sortedWith(
                        compareByDescending<SmartSuggestedUser> { it.isOnline }.thenByDescending { it.activityScore }
                    )
                    friendRequestsList = realRequests
                    friendRequestsTotalCount = realRequestsCount
                    activitiesList = realActivities
                    confirmedFriendsList = realFriends
                    isLoadingData = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllSocialHubData()
    }

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
            // 📑 ২. ৪টি ট্যাবের স্ক্রোলযোগ্য বার (ডায়নামিক সার্ভার ব্যাজ কাউন্টার সহ)
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

                                // 🎯 সার্ভার থেকে আসা রিয়েল ব্যাজ সংখ্যা (০ হলে দেখাবে না)
                                if (tab == SocialHubTab.REQUESTS && friendRequestsTotalCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = RequestBadgeRed
                                    ) {
                                        Text(
                                            text = "$friendRequestsTotalCount",
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
            // 🔄 ৩. পেজার কন্টেন্ট (৪টি ট্যাবের রিয়েল-টাইম সার্ভার ডাটা ভিউ)
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
                if (isLoadingData && discoverAccountsList.isEmpty() && activitiesList.isEmpty()) {
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
                            // 🌟 TAB 0: DISCOVER (স্মার্ট অনলাইন র্যাংকিং)
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
                            // 👥 TAB 1: REQUESTS (আসল ফ্রেন্ড রিকোয়েস্ট - Confirm/Delete)
                            // =========================================================
                            SocialHubTab.REQUESTS -> {
                                if (friendRequestsList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.GroupAdd,
                                        title = "No pending friend requests",
                                        subtitle = "When people send you a friend request, they will appear here."
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
                                                Text("$friendRequestsTotalCount", color = RequestBadgeRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                            }
                                        }

                                        items(friendRequestsList, key = { "req_${it.requestId}" }) { request ->
                                            FriendRequestRowCard(
                                                request = request,
                                                onProfileClick = { onOpenProfile(request.userId) },
                                                onConfirm = {
                                                    val reqId = request.requestId
                                                    // অপটিমিস্টিক আপডেট
                                                    friendRequestsList = friendRequestsList.filter { it.requestId != reqId }
                                                    friendRequestsTotalCount = (friendRequestsTotalCount - 1).coerceAtLeast(0)

                                                    coroutineScope.launch {
                                                        val res = repository.handleFriendRequest(reqId, "confirm")
                                                        if (res.isSuccess) {
                                                            Toast.makeText(context, "Added ${request.name} as friend!", Toast.LENGTH_SHORT).show()
                                                            val freshFriends = repository.getConfirmedFriends().getOrDefault(emptyList())
                                                            confirmedFriendsList = freshFriends
                                                        }
                                                    }
                                                },
                                                onDelete = {
                                                    val reqId = request.requestId
                                                    friendRequestsList = friendRequestsList.filter { it.requestId != reqId }
                                                    friendRequestsTotalCount = (friendRequestsTotalCount - 1).coerceAtLeast(0)

                                                    coroutineScope.launch {
                                                        repository.handleFriendRequest(reqId, "delete")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // =========================================================
                            // 🔔 TAB 2: ACTIVITY (আসল সোশ্যাল নোটিফিকেশন ফিড)
                            // =========================================================
                            SocialHubTab.ACTIVITY -> {
                                if (activitiesList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.NotificationsNone,
                                        title = "No notifications yet",
                                        subtitle = "When people like, comment on your videos, or follow you, updates will show up here."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(activitiesList, key = { "act_${it.actorId}_${it.createdAt}_${it.reelId}" }) { activity ->
                                            SocialActivityRowItem(
                                                item = activity,
                                                onActorAvatarClick = { onOpenProfile(activity.actorId) },
                                                onItemClick = {
                                                    if (activity.reelId != null && activity.reelId > 0) {
                                                        onReelClick(
                                                            UserReelDto(
                                                                id = activity.reelId,
                                                                title = activity.text,
                                                                thumbUrl = activity.thumbUrl,
                                                                videoUrl = ""
                                                            )
                                                        )
                                                    } else {
                                                        onOpenProfile(activity.actorId)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // =========================================================
                            // 🤝 TAB 3: FRIENDS LIST (আসল কনফার্মড ফ্রেন্ডস)
                            // =========================================================
                            SocialHubTab.FRIENDS -> {
                                if (confirmedFriendsList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.PeopleOutline,
                                        title = "You have not added any friends yet",
                                        subtitle = "Discover users in the Discover tab to connect and chat."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(confirmedFriendsList, key = { "frnd_${it.userId}" }) { friend ->
                                            ConfirmedFriendRowItem(
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
// 🔲 ১. DISCOVER ROW (স্মার্ট বাটন সহ)
// =============================================================================
@Composable
private fun DiscoverUserRowCard(
    account: SmartSuggestedUser,
    onProfileClick: () -> Unit,
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
// 🔲 ২. FRIEND REQUEST ROW (সার্ভার চালিত Confirm/Delete)
// =============================================================================
@Composable
private fun FriendRequestRowCard(
    request: FriendRequestDto,
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

            if (!request.mutualInfo.isNullOrBlank()) {
                Text(request.mutualInfo, color = TextMuted, fontSize = 12.sp)
            }

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
// 🔲 ৩. SOCIAL ACTIVITY ROW (আসল সার্ভার নোটিফিকেশন + থাম্বনেল)
// =============================================================================
@Composable
private fun SocialActivityRowItem(
    item: SocialActivityDto,
    onActorAvatarClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    text = "${item.actorName} ${item.text}",
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

        if (!item.thumbUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1B202D))
            ) {
                AsyncImage(
                    model = item.thumbUrl,
                    contentDescription = "Reel Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// =============================================================================
// 🔲 ৪. CONFIRMED FRIEND ROW (সার্ভার থেকে আসল ফ্রেন্ডলিস্ট)
// =============================================================================
@Composable
private fun ConfirmedFriendRowItem(
    friend: ConfirmedFriendDto,
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
                Text(friend.displayHandle, color = TextMuted, fontSize = 11.5.sp)
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
