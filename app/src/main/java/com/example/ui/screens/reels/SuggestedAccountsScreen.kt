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
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.GroupMemberInfo
import com.example.data.model.SocialActivityDto
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import com.example.util.FirebaseChatManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

enum class SocialHubTab(val label: String, val icon: ImageVector) {
    DISCOVER("Discover", Icons.Default.PersonSearch),
    REQUESTS("Requests", Icons.Default.GroupAdd),
    ACTIVITY("Activity", Icons.Default.Notifications),
    FRIENDS("Friends", Icons.Default.People)
}

// 🎯 ফ্রেন্ড বাটনের ৩টি লাইভ স্টেট
enum class FriendStatus {
    NOT_FRIEND,     // [ + Add Friend ]
    REQUEST_SENT,   // [ Cancel Request ✕ ]
    FRIENDS         // [ Message 💬 ] (Friends ট্যাবে স্থানান্তরিত হবে)
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
    val lastActiveTime: Long = 0L,
    val friendStatus: FriendStatus = FriendStatus.NOT_FRIEND,
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
    val authRepository = remember { AuthRepository(context) }

    val currentLoggedInUserId = remember { repository.getCurrentUserId() }
    val myUserProfile = remember { authRepository.getSavedUserProfile() }

    val pagerState = rememberPagerState(initialPage = 0) { 4 }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var isLoadingData by remember { mutableStateOf(true) }

    // পারসিস্টেড রিকোয়েস্ট ও ফ্রেন্ডস আইডি সেট
    var sentRequestUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmedFriendUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var discoverAccountsList by remember { mutableStateOf<List<SmartSuggestedUser>>(emptyList()) }
    var friendRequestsList by remember { mutableStateOf<List<FriendRequestDto>>(emptyList()) }
    var friendRequestsTotalCount by remember { mutableIntStateOf(0) }
    var activitiesList by remember { mutableStateOf<List<SocialActivityDto>>(emptyList()) }
    var confirmedFriendsList by remember { mutableStateOf<List<ConfirmedFriendDto>>(emptyList()) }

    // =========================================================================
    // 🌐 লাইভ ফ্রেন্ড রিকোয়েস্ট ও সোশ্যাল ডাটাবেজ সিঙ্ক ইঞ্জিন
    // =========================================================================
    fun loadAllSocialHubData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val myIdStr = currentLoggedInUserId.toString()
                val db = FirebaseFirestore.getInstance()

                // ১. ক্লাউড থেকে আমি যাদের রিকোয়েস্ট পাঠিয়েছি তাদের আইডি সেট লোড
                val sentReqSnapshot = runCatching {
                    db.collection("user_friend_requests")
                        .whereEqualTo("senderId", myIdStr)
                        .get()
                        .await()
                }.getOrNull()
                val sentIds = sentReqSnapshot?.documents?.mapNotNull { it.getString("receiverId") }?.toSet() ?: emptySet()
                sentRequestUserIds = sentIds

                // ২. ক্লাউড থেকে আমার ফ্রেন্ডলিস্ট লোড
                val friendsSnapshot1 = runCatching {
                    db.collection("user_friendships")
                        .whereEqualTo("user1Id", myIdStr)
                        .get()
                        .await()
                }.getOrNull()

                val friendsSnapshot2 = runCatching {
                    db.collection("user_friendships")
                        .whereEqualTo("user2Id", myIdStr)
                        .get()
                        .await()
                }.getOrNull()

                val friendIds = mutableSetOf<String>()
                friendsSnapshot1?.documents?.forEach { doc -> doc.getString("user2Id")?.let { friendIds.add(it) } }
                friendsSnapshot2?.documents?.forEach { doc -> doc.getString("user1Id")?.let { friendIds.add(it) } }
                confirmedFriendUserIds = friendIds

                // ৩. অপর পাশের ইউজারদের থেকে আসা পেন্ডিং রিকোয়েস্ট লোড
                val incomingReqSnapshot = runCatching {
                    db.collection("user_friend_requests")
                        .whereEqualTo("receiverId", myIdStr)
                        .get()
                        .await()
                }.getOrNull()

                val realIncomingRequests = incomingReqSnapshot?.documents?.mapNotNull { doc ->
                    val reqId = doc.id.hashCode().let { if (it < 0) -it else it }
                    val sId = doc.getString("senderId")?.toIntOrNull() ?: return@mapNotNull null
                    val sName = doc.getString("senderName") ?: "User"
                    val sAvatar = doc.getString("senderAvatar")
                    val time = doc.getLong("timestamp") ?: now
                    val diffMinutes = (now - time) / 60000L
                    val timeAgo = when {
                        diffMinutes < 60 -> "${diffMinutes}m"
                        diffMinutes < 1440 -> "${diffMinutes / 60}h"
                        else -> "${diffMinutes / 1440}d"
                    }
                    FriendRequestDto(
                        rawRequestId = reqId,
                        rawUserId = sId,
                        rawName = sName,
                        avatar = sAvatar,
                        mutualInfo = "Sent you a friend request",
                        customTimeAgo = timeAgo,
                        isOnline = false
                    )
                } ?: emptyList()

                // ৪. রেজিস্টার্ড সকল মেম্বার
                val firestoreMembers: List<GroupMemberInfo> = runCatching {
                    val snapshot = db.collection("community_group_members").get().await()
                    snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("userId") ?: doc.id
                        val name = doc.getString("userName") ?: "Member"
                        val avatar = doc.getString("userAvatar")
                        val email = doc.getString("userEmail")
                        val isOwner = doc.getBoolean("isOwner") ?: false
                        val isVip = doc.getBoolean("isVip") ?: false
                        val lastActive = doc.getLong("lastActive") ?: 0L
                        val joinedAt = doc.getLong("joinedAt") ?: 0L
                        GroupMemberInfo(uid, name, avatar, email, isOwner, isVip, joinedAt, lastActive)
                    }
                }.getOrDefault(emptyList())

                val feedResult = repository.getReelsFeed(tab = "for_you", page = 1)
                val allReels: List<UserReelDto> = feedResult.getOrDefault(emptyList())
                val reelsByCreator = allReels.filter { it.userId > 0 }.groupBy { it.userId }

                val discoverMap = mutableMapOf<Int, SmartSuggestedUser>()
                val confirmedFriendsListLocal = mutableListOf<ConfirmedFriendDto>()

                firestoreMembers.forEach { member ->
                    val uidInt = member.userId.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val uidStr = uidInt.toString()

                    if (uidInt > 0 && uidInt != currentLoggedInUserId) {
                        val isOnline = member.lastActive > 0 && (now - member.lastActive) < 180_000L
                        val isAlreadyFriend = confirmedFriendUserIds.contains(uidStr)
                        val isReqSent = sentRequestUserIds.contains(uidStr)

                        val statusText = when {
                            isOnline -> "Active now 🟢"
                            member.lastActive > 0 -> {
                                val diffMinutes = (now - member.lastActive) / 60000L
                                if (diffMinutes < 60) "Active ${diffMinutes}m ago" else "Active recently"
                            }
                            else -> "Community member"
                        }

                        val friendStatus = when {
                            isAlreadyFriend -> FriendStatus.FRIENDS
                            isReqSent -> FriendStatus.REQUEST_SENT
                            else -> FriendStatus.NOT_FRIEND
                        }

                        // যদি ইতোমধ্যে ফ্রেন্ড হয়, তবে ফ্রেন্ডস ট্যাবে পাঠানো
                        if (isAlreadyFriend) {
                            confirmedFriendsListLocal.add(
                                ConfirmedFriendDto(
                                    rawUserId = uidInt,
                                    rawName = member.userName,
                                    avatar = member.userAvatar,
                                    isOnline = isOnline,
                                    handle = "@${member.userName.lowercase().replace(" ", "_")}"
                                )
                            )
                        } else {
                            val score = (if (isOnline) 1_000_000_000L else 0L) + member.lastActive
                            discoverMap[uidInt] = SmartSuggestedUser(
                                userId = uidInt,
                                pageId = uidInt,
                                name = member.userName,
                                handle = "@${member.userName.lowercase().replace(" ", "_")}",
                                avatar = member.userAvatar,
                                isOnline = isOnline,
                                statusText = statusText,
                                activityScore = score,
                                lastActiveTime = member.lastActive,
                                friendStatus = friendStatus,
                                recentReels = reelsByCreator[uidInt] ?: emptyList()
                            )
                        }
                    }
                }

                // 🎯 সর্টিং: অনলাইন ইউজার সবার প্রথমে, এরপর সক্রিয় ইউজার
                val sortedDiscover = discoverMap.values.sortedWith(
                    compareByDescending<SmartSuggestedUser> { it.isOnline }
                        .thenByDescending { it.activityScore }
                        .thenByDescending { it.lastActiveTime }
                )

                val actRes = repository.getSocialActivities()
                val realActivities = actRes.getOrNull()?.effectiveActivities ?: emptyList()

                withContext(Dispatchers.Main) {
                    discoverAccountsList = sortedDiscover
                    friendRequestsList = realIncomingRequests
                    friendRequestsTotalCount = realIncomingRequests.size
                    activitiesList = realActivities
                    confirmedFriendsList = confirmedFriendsListLocal
                    isLoadingData = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllSocialHubData()
    }

    // =========================================================================
    // 🤝 ফ্রেন্ড রিকোয়েস্ট সেন্ড, ক্যানসেল, একসেপ্ট ও ডিলিট মেথডস
    // =========================================================================

    // ১. রিকোয়েস্ট পাঠানো (+ Add Friend -> Cancel Request)
    fun sendRequest(targetUser: SmartSuggestedUser) {
        val targetId = targetUser.userId.toString()
        val myId = currentLoggedInUserId.toString()
        if (currentLoggedInUserId <= 0) {
            Toast.makeText(context, "Please log in to add friends", Toast.LENGTH_SHORT).show()
            return
        }

        // লোকাল ইনস্ট্যান্ট আপডেট
        sentRequestUserIds = sentRequestUserIds + targetId
        discoverAccountsList = discoverAccountsList.map {
            if (it.userId == targetUser.userId) it.copy(friendStatus = FriendStatus.REQUEST_SENT) else it
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val reqDocId = "${myId}_$targetId"
                val requestData = mapOf(
                    "senderId" to myId,
                    "senderName" to (myUserProfile?.displayName ?: "User"),
                    "senderAvatar" to (myUserProfile?.avatar ?: ""),
                    "receiverId" to targetId,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis()
                )
                FirebaseFirestore.getInstance().collection("user_friend_requests").document(reqDocId).set(requestData).await()
                repository.toggleFriend(targetUser.userId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Friend request sent!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SocialHub", "Send error: ${e.message}")
            }
        }
    }

    // ২. রিকোয়েস্ট বাতিল (Cancel Request -> + Add Friend)
    fun cancelRequest(targetUser: SmartSuggestedUser) {
        val targetId = targetUser.userId.toString()
        val myId = currentLoggedInUserId.toString()

        // লোকাল ইনস্ট্যান্ট আপডেট
        sentRequestUserIds = sentRequestUserIds - targetId
        discoverAccountsList = discoverAccountsList.map {
            if (it.userId == targetUser.userId) it.copy(friendStatus = FriendStatus.NOT_FRIEND) else it
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val reqDocId = "${myId}_$targetId"
                FirebaseFirestore.getInstance().collection("user_friend_requests").document(reqDocId).delete().await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SocialHub", "Cancel error: ${e.message}")
            }
        }
    }

    // ৩. অপর পাশের ইউজারের রিকোয়েস্ট একসেপ্ট (Confirm -> Move to Friends Tab)
    fun confirmRequest(request: FriendRequestDto) {
        val myId = currentLoggedInUserId.toString()
        val senderId = request.userId.toString()

        // অপটিমিস্টিকালি রিকোয়েস্ট তালিকা থেকে সরানো
        friendRequestsList = friendRequestsList.filter { it.requestId != request.requestId }
        friendRequestsTotalCount = (friendRequestsTotalCount - 1).coerceAtLeast(0)

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                val reqDocId = "${senderId}_$myId"
                db.collection("user_friend_requests").document(reqDocId).delete().await()

                // ফ্রেন্ডশিপ তৈরি
                val ids = listOf(myId, senderId).sorted()
                val friendshipDocId = "friend_${ids[0]}_${ids[1]}"
                val friendshipData = mapOf(
                    "user1Id" to ids[0],
                    "user2Id" to ids[1],
                    "status" to "accepted",
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("user_friendships").document(friendshipDocId).set(friendshipData).await()
                repository.handleFriendRequest(request.requestId, "confirm")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "🎉 You are now friends with ${request.name}!", Toast.LENGTH_SHORT).show()
                    loadAllSocialHubData()
                }
            } catch (e: Exception) {
                Log.e("SocialHub", "Confirm error: ${e.message}")
            }
        }
    }

    // ৪. রিকোয়েস্ট ডিলিট
    fun deleteRequest(request: FriendRequestDto) {
        val myId = currentLoggedInUserId.toString()
        val senderId = request.userId.toString()

        friendRequestsList = friendRequestsList.filter { it.requestId != request.requestId }
        friendRequestsTotalCount = (friendRequestsTotalCount - 1).coerceAtLeast(0)

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val reqDocId = "${senderId}_$myId"
                FirebaseFirestore.getInstance().collection("user_friend_requests").document(reqDocId).delete().await()
                repository.handleFriendRequest(request.requestId, "delete")
            } catch (_: Exception) {}
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
            // 📑 ২. ৪টি ট্যাবের বার (লাইভ ব্যাজ কাউন্ট সহ)
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

                                // রিকোয়েস্ট আসলে লাল ব্যাজ
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
            // 🔄 ৩. পেজার কন্টেন্ট (৪টি ট্যাবের রিয়েল-টাইম ডাটা)
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
                            // 🌟 TAB 0: DISCOVER (+ Add Friend -> Cancel Request)
                            // =========================================================
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
                                        items(discoverAccountsList, key = { "disc_${it.userId}" }) { account ->
                                            DiscoverUserRowCard(
                                                account = account,
                                                onProfileClick = { onOpenProfile(account.userId) },
                                                onSendRequest = { sendRequest(account) },
                                                onCancelRequest = { cancelRequest(account) }
                                            )
                                        }
                                    }
                                }
                            }

                            // =========================================================
                            // 👥 TAB 1: REQUESTS (অপর পাশের ইউজারের Confirm/Delete)
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
                                                onConfirm = { confirmRequest(request) },
                                                onDelete = { deleteRequest(request) }
                                            )
                                        }
                                    }
                                }
                            }

                            // =========================================================
                            // 🔔 TAB 2: ACTIVITY (নোটিফিকেশন ফিড)
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
                                        items(activitiesList, key = { "act_${it.actorId}_${it.createdAt}_${it.rawId}" }) { activity ->
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

                            // =========================================================
                            // 🤝 TAB 3: FRIENDS LIST (একসেপ্ট হওয়া কনফার্মড ফ্রেন্ডস)
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
// 🔲 ১. DISCOVER ROW (+ Add Friend / Cancel Request বাটন সহ)
// =============================================================================
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

        // 🎯 লাইভ বাটন ট্রান্সফরমেশন: [+ Add Friend] অথবা [Cancel Request ✕]
        AnimatedContent(targetState = account.friendStatus, label = "discover_action_btn") { status ->
            when (status) {
                FriendStatus.REQUEST_SENT -> {
                    // রিকোয়েস্ট পাঠানো থাকলে Cancel Request বাটন
                    Button(
                        onClick = onCancelRequest,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CancelGray),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(15.dp))
                            Text("Cancel Request", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> {
                    // রিকোয়েস্ট পাঠানো না থাকলে + Add Friend বাটন
                    Button(
                        onClick = onSendRequest,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
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

// =============================================================================
// 🔲 ২. FRIEND REQUEST ROW (Confirm / Delete)
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

            Text(request.mutualInfo ?: "Friend request", color = TextMuted, fontSize = 12.sp)

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
// 🔲 ৩. SOCIAL ACTIVITY ROW
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
// 🔲 ৪. CONFIRMED FRIEND ROW (Friends ট্যাবে [Message 💬] বাটন)
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
