@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens.reels

import android.content.Context
import android.util.Log
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
import com.example.data.model.ConfirmedFriendDto
import com.example.data.model.FriendRequestDto
import com.example.data.model.SocialActivityDto
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PureBlack = Color(0xFF000000)
private val DarkCardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)
private val ButtonBlue = Color(0xFF007AFF)
private val ConfirmBlue = Color(0xFF1877F2)
private val ActionGreen = Color(0xFF00E676)
private val DeleteGray = Color(0xFF2C3240)
private val CancelGray = Color(0xFF262C38)
private val TextMuted = Color(0xFF8E95A5)
private val CyanBorder = Color(0xFF00E5FF)
private val OnlineGreen = Color(0xFF00E676)
private val RequestBadgeRed = Color(0xFFFF2A4B)

enum class SocialHubTab(val label: String) {
    DISCOVER("Discover"),
    REQUESTS("Requests"),
    ACTIVITY("Activity"),
    FRIENDS("Friends")
}

enum class FriendStatus {
    NOT_FRIEND,
    REQUEST_SENT,
    FRIENDS
}

data class SmartSuggestedUser(
    val userId: Int,
    val pageId: Int,
    val name: String,
    val handle: String,
    val avatar: String?,
    val isOnline: Boolean = false,
    val statusText: String = "Active user",
    val activityScore: Long = 0L,
    val friendStatus: FriendStatus = FriendStatus.NOT_FRIEND
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
    val authRepository = remember { AuthRepository(context) }

    val currentLoggedInUserId = remember { repository.getCurrentUserId() }
    val pagerState = rememberPagerState(initialPage = 0) { 4 }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var isLoadingData by remember { mutableStateOf(true) }

    var discoverAccountsList by remember { mutableStateOf<List<SmartSuggestedUser>>(emptyList()) }
    var friendRequestsList by remember { mutableStateOf<List<FriendRequestDto>>(emptyList()) }
    var friendRequestsTotalCount by remember { mutableIntStateOf(0) }
    var activitiesList by remember { mutableStateOf<List<SocialActivityDto>>(emptyList()) }
    var confirmedFriendsList by remember { mutableStateOf<List<ConfirmedFriendDto>>(emptyList()) }

    // =========================================================================
    // 🌐 ১০০% সার্ভার MySQL ডাটাবেজ থেকে আসল ইউজার লোড করার ইঞ্জিন
    // =========================================================================
    fun loadAllSocialHubData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                // ১. সরাসরি MySQL ডাটাবেজের সাজেস্টেড পেজ ও আসল রেজিস্টার্ড ইউজার ফেচ
                val suggestedRes = repository.getSuggestedPages()
                val serverPages: List<SuggestedPageDto> = suggestedRes.getOrDefault(emptyList())

                // ২. সার্ভার থেকে কনফার্মড ফ্রেন্ডস লোড
                val confirmedRes = repository.getConfirmedFriends(currentLoggedInUserId)
                val realConfirmedFriends = confirmedRes.getOrDefault(emptyList())
                val confirmedIds = realConfirmedFriends.map { it.userId }.toSet()

                // ৩. সার্ভার থেকে পেন্ডিং ফ্রেন্ড রিকোয়েস্ট লোড
                val requestsRes = repository.getFriendRequests()
                val realRequests = requestsRes.getOrNull()?.effectiveRequests ?: emptyList()

                // ৪. সার্ভার থেকে লাইভ সোশ্যাল অ্যাক্টিভিটি নোটিফিকেশন লোড
                val activitiesRes = repository.getSocialActivities()
                val realActivities = activitiesRes.getOrNull()?.effectiveActivities ?: emptyList()

                // ৫. MySQL-এর আসল ইউজারদের ডিসকভার তালিকায় কনভার্ট করা (কোনো ভুয়া ফায়ারবেস আইডি ঢুকবে না)
                val cleanDiscoverList = mutableListOf<SmartSuggestedUser>()

                serverPages.forEach { page ->
                    val uid = if (page.userId > 0) page.userId else page.pageId
                    if (uid > 0 && uid != currentLoggedInUserId) {
                        val isFriend = confirmedIds.contains(uid) || page.isFollowing
                        cleanDiscoverList.add(
                            SmartSuggestedUser(
                                userId = uid,
                                pageId = page.pageId,
                                name = page.pageName,
                                handle = page.displayHandle,
                                avatar = page.avatar,
                                isOnline = true,
                                statusText = "${page.category} • ${page.formattedFollowers} fans",
                                activityScore = page.followersCount,
                                friendStatus = if (isFriend) FriendStatus.FRIENDS else FriendStatus.NOT_FRIEND
                            )
                        )
                    }
                }

                // যদি সাজেস্টেড পেজ কম থাকে, ডাটাবেজের আইডি ১ থেকে ২০ এর রিয়েল ইউজারদের দিয়ে ব্যাকআপ দেওয়া
                if (cleanDiscoverList.size < 5) {
                    val fallbackIds = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                    for (fId in fallbackIds) {
                        if (fId != currentLoggedInUserId && cleanDiscoverList.none { it.userId == fId }) {
                            val userRes = repository.getUserRegularProfile(fId)
                            val uData = userRes.getOrNull()
                            if (uData != null && !uData.displayName.equals("DramaFlix User", ignoreCase = true)) {
                                cleanDiscoverList.add(
                                    SmartSuggestedUser(
                                        userId = fId,
                                        pageId = fId,
                                        name = uData.displayName,
                                        handle = "@user_$fId",
                                        avatar = uData.avatar,
                                        isOnline = false,
                                        statusText = "DramaFlix Member",
                                        activityScore = 100L,
                                        friendStatus = if (uData.isFriend) FriendStatus.FRIENDS else FriendStatus.NOT_FRIEND
                                    )
                                )
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    discoverAccountsList = cleanDiscoverList.distinctBy { it.userId }
                    friendRequestsList = realRequests
                    friendRequestsTotalCount = realRequests.size
                    activitiesList = realActivities
                    confirmedFriendsList = realConfirmedFriends
                    isLoadingData = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllSocialHubData()
    }

    // ১. ফ্রেন্ড রিকোয়েস্ট পাঠানো
    fun sendRequest(targetUser: SmartSuggestedUser) {
        if (currentLoggedInUserId <= 0) {
            Toast.makeText(context, "Please log in to add friends", Toast.LENGTH_SHORT).show()
            return
        }

        discoverAccountsList = discoverAccountsList.map {
            if (it.userId == targetUser.userId) it.copy(friendStatus = FriendStatus.REQUEST_SENT) else it
        }

        coroutineScope.launch {
            val res = repository.toggleFriend(targetUser.userId)
            if (res.isSuccess) {
                Toast.makeText(context, "Friend request sent to ${targetUser.name}!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ২. রিকোয়েস্ট ক্যানসেল
    fun cancelRequest(targetUser: SmartSuggestedUser) {
        discoverAccountsList = discoverAccountsList.map {
            if (it.userId == targetUser.userId) it.copy(friendStatus = FriendStatus.NOT_FRIEND) else it
        }

        coroutineScope.launch {
            repository.toggleFriend(targetUser.userId)
            Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // ৩. রিকোয়েস্ট কনফার্ম
    fun confirmRequest(request: FriendRequestDto) {
        friendRequestsList = friendRequestsList.filter { it.userId != request.userId }
        friendRequestsTotalCount = (friendRequestsTotalCount - 1).coerceAtLeast(0)

        coroutineScope.launch {
            val res = repository.handleFriendRequest(request.requestId, "confirm")
            if (res.isSuccess) {
                Toast.makeText(context, "🎉 You are now friends with ${request.name}!", Toast.LENGTH_SHORT).show()
                loadAllSocialHubData()
            }
        }
    }

    // ৪. রিকোয়েস্ট ডিলিট
    fun deleteRequest(request: FriendRequestDto) {
        friendRequestsList = friendRequestsList.filter { it.userId != request.userId }
        friendRequestsTotalCount = (friendRequestsTotalCount - 1).coerceAtLeast(0)

        coroutineScope.launch {
            repository.handleFriendRequest(request.requestId, "delete")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // টপ বার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = PureBlack,
                    contentColor = Color.White,
                    edgePadding = 8.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = CyanBorder,
                                height = 2.dp
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
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
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = tab.label,
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )

                                    if (tab == SocialHubTab.REQUESTS && friendRequestsTotalCount > 0) {
                                        Surface(shape = CircleShape, color = RequestBadgeRed) {
                                            Text(
                                                text = "$friendRequestsTotalCount",
                                                color = Color.White,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.8.dp)

            // ৪টি ট্যাব পেজার
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
                            // 🌟 TAB 0: DISCOVER (১০০% আসল MySQL ইউজার)
                            SocialHubTab.DISCOVER -> {
                                if (discoverAccountsList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.PersonSearch,
                                        title = "No users found",
                                        subtitle = "Pull down to refresh and discover users."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        itemsIndexed(
                                            items = discoverAccountsList,
                                            key = { index, item -> "user_${item.userId}_$index" }
                                        ) { _, account ->
                                            DiscoverUserRowCard(
                                                account = account,
                                                onProfileClick = {
                                                    // 🎯 নিশ্চিতভাবে আসল MySQL user_id পাস করা হচ্ছে
                                                    onOpenProfile(account.userId)
                                                },
                                                onSendRequest = { sendRequest(account) },
                                                onCancelRequest = { cancelRequest(account) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 👥 TAB 1: REQUESTS
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
                                        itemsIndexed(
                                            items = friendRequestsList,
                                            key = { index, item -> "req_${item.requestId}_${item.userId}_$index" }
                                        ) { _, request ->
                                            FriendRequestRowCard(
                                                request = request,
                                                onProfileClick = { onOpenProfile(request.userId) },
                                                onConfirm = { confirmRequest(request) },
                                                onDelete = { deleteRequest(request) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 🔔 TAB 2: ACTIVITY
                            SocialHubTab.ACTIVITY -> {
                                if (activitiesList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.NotificationsNone,
                                        title = "No notifications yet",
                                        subtitle = "When people interact with your profile, updates show up here."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(
                                            items = activitiesList,
                                            key = { index, item -> "act_${item.actorId}_${item.createdAt}_$index" }
                                        ) { _, activity ->
                                            SocialActivityRowItem(
                                                item = activity,
                                                onActorAvatarClick = { onOpenProfile(activity.actorId) },
                                                onItemClick = {
                                                    val targetReelId = activity.reelId
                                                    if (targetReelId != null && targetReelId > 0) {
                                                        onReelClick(
                                                            UserReelDto(
                                                                id = targetReelId,
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

                            // 🤝 TAB 3: FRIENDS LIST
                            SocialHubTab.FRIENDS -> {
                                if (confirmedFriendsList.isEmpty()) {
                                    EmptySocialHubView(
                                        icon = Icons.Default.PeopleOutline,
                                        title = "No friends added yet",
                                        subtitle = "Add friends from the Discover tab to connect."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(
                                            items = confirmedFriendsList,
                                            key = { index, item -> "frnd_${item.userId}_$index" }
                                        ) { _, friend ->
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

@Composable
private fun DiscoverUserRowCard(
    account: SmartSuggestedUser,
    onProfileClick: () -> Unit,
    onSendRequest: () -> Unit,
    onCancelRequest: () -> Unit
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
                        if (!account.avatar.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(account.avatar).crossfade(true).build(),
                                contentDescription = account.name,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFF202A3C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = account.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (account.isOnline) {
                        Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(OnlineGreen).border(2.dp, PureBlack, CircleShape))
                    }
                }

                Column {
                    Text(account.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(account.handle, color = TextMuted, fontSize = 12.sp)
                    Text(account.statusText, color = if (account.isOnline) OnlineGreen else TextMuted, fontSize = 11.sp)
                }
            }
        }

        AnimatedContent(targetState = account.friendStatus, label = "discover_action_btn") { status ->
            when (status) {
                FriendStatus.REQUEST_SENT -> {
                    Button(
                        onClick = onCancelRequest,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CancelGray),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(15.dp))
                            Text("Cancel Request", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                FriendStatus.FRIENDS -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E2838),
                        border = BorderStroke(1.dp, ActionGreen.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().height(36.dp).clickable { onProfileClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Friends ✓", color = ActionGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onSendRequest,
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
}

@Composable
private fun FriendRequestRowCard(
    request: FriendRequestDto,
    onProfileClick: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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

@Composable
private fun SocialActivityRowItem(
    item: SocialActivityDto,
    onActorAvatarClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onItemClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).clickable { onActorAvatarClick() }
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
                modifier = Modifier.size(width = 44.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B202D))
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

@Composable
private fun ConfirmedFriendRowItem(
    friend: ConfirmedFriendDto,
    onProfileClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onProfileClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                Text("Message", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
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
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
