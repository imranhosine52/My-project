@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)

package com.example.ui.screens.reels

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DirectConversationItem
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ChatRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import com.example.util.ReelsCachePreloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)
private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)

@Composable
fun ReelsFeedScreen(
    viewModel: ReelsViewModel,
    isLoggedIn: Boolean = true,
    currentUserName: String = "User",
    currentUserAvatar: String? = null,
    onOpenCreateReel: () -> Unit,
    onOpenPageProfile: (pageId: Int) -> Unit,
    onNavigateToSearch: (initialQuery: String) -> Unit,
    onNavigateToVip: () -> Unit = {},
    onRequireLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }
    val chatRepository = remember { ChatRepository(context) }

    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()

    // 🎯 শুধুমাত্র অ্যাপ্রুভড ক্রিয়েটর পেজ থাকলে ভিডিও আপলোড আইকন দৃশ্যমান হবে
    val hasApprovedCreatorPage = uploadState.creatorPage?.isApproved == true

    var showThreeDotSettingsSheet by remember { mutableStateOf(false) }
    var showQualityPickerSheet by remember { mutableStateOf(false) }
    var showSpeedPickerSheet by remember { mutableStateOf(false) }

    // বটম শীট কন্ট্রোল
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showShareBottomSheet by remember { mutableStateOf(false) }
    var activeReelForAction by remember { mutableStateOf<UserReelDto?>(null) }

    // ফ্রেন্ডস কনভারসেশন লিস্ট (শেয়ার শীটের জন্য)
    var conversationList by remember { mutableStateOf<List<DirectConversationItem>>(emptyList()) }

    var selectedPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    var isAppInForeground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isAppInForeground = false
                Lifecycle.Event.ON_RESUME -> isAppInForeground = true
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.checkMyCreatorPage()
        val convRes = chatRepository.getInboxConversations()
        conversationList = convRes.getOrDefault(emptyList())
    }

    val reelsList = feedState.reels
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { reelsList.size })

    // ১০-ভিডিও রোলিং প্রিলোডার ও ভিউ ট্র্যাকিং
    LaunchedEffect(pagerState.currentPage, reelsList) {
        if (reelsList.isNotEmpty()) {
            val currentReel = reelsList.getOrNull(pagerState.currentPage)
            if (currentReel != null) {
                viewModel.trackReelView(currentReel.id)
            }
            ReelsCachePreloadManager.onUserScrolledToPosition(
                context = context,
                currentIndex = pagerState.currentPage,
                allReels = reelsList
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // 🎯 ডানে-বামে সোয়াইপ জেসচার: Following এবং For You ট্যাবের মধ্যে রূপান্তর
            .pointerInput(feedState.activeTab) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 55f && feedState.activeTab == "for_you") {
                        // বাম থেকে ডানে সোয়াইপ -> Following ট্যাব
                        viewModel.loadFeed(tab = "following")
                    } else if (dragAmount < -55f && feedState.activeTab == "following") {
                        // ডান থেকে বামে সোয়াইপ -> For You ট্যাব
                        viewModel.loadFeed(tab = "for_you")
                    }
                }
            }
    ) {
        if (feedState.isLoading && reelsList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(40.dp))
            }
        } else if (reelsList.isEmpty()) {
            // =========================================================================
            // 🛑 Following ও For You এম্পটি স্টেট
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = if (feedState.activeTab == "following") Icons.Default.People else Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(56.dp)
                    )

                    Text(
                        text = if (feedState.activeTab == "following")
                            "You haven't followed any creators yet or they haven't uploaded new reels."
                        else
                            "No reels available right now.",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (feedState.activeTab == "following") {
                        Button(
                            onClick = { viewModel.loadFeed("for_you") },
                            colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Explore For You ➔", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else if (hasApprovedCreatorPage) {
                        Button(
                            onClick = onOpenCreateReel,
                            colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Create First Reel +", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadFeed(feedState.activeTab)
                        delay(500)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                ) { pageIndex ->
                    val reel = reelsList[pageIndex]
                    val isCurrentPagePlaying = (pagerState.currentPage == pageIndex) && isAppInForeground

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 🎯 জেসচার সোয়াইপ: For You ট্যাবে থাকাকালীন ভিডিওতে বামে সোয়াইপ করলে ক্রিয়েটর প্রোফাইল খুলবে
                            .pointerInput(reel.id, feedState.activeTab) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount < -50f && feedState.activeTab == "for_you") {
                                        val targetPageId = if (reel.pageId > 0) reel.pageId else reel.userId
                                        onOpenPageProfile(targetPageId)
                                    }
                                }
                            }
                    ) {
                        SingleReelPlayerItem(
                            reel = reel,
                            selectedQuality = feedState.selectedQuality,
                            playbackSpeed = selectedPlaybackSpeed,
                            isActiveVideoPlaying = isCurrentPagePlaying,
                            repository = repository,
                            onDoubleTapLike = {
                                if (!isLoggedIn) onRequireLogin() else viewModel.toggleLike(reel)
                            },
                            onToggleLike = {
                                if (!isLoggedIn) onRequireLogin() else viewModel.toggleLike(reel)
                            },
                            onFollowClick = {
                                if (!isLoggedIn) onRequireLogin() else viewModel.toggleFollowCreator(reel.pageId)
                            },
                            onCommentClick = {
                                if (!isLoggedIn) {
                                    onRequireLogin()
                                } else {
                                    activeReelForAction = reel
                                    showCommentsSheet = true
                                }
                            },
                            onShareClick = {
                                activeReelForAction = reel
                                showShareBottomSheet = true
                            },
                            onHashtagClick = { hashtag -> onNavigateToSearch(hashtag) },
                            onOpenPageProfile = {
                                val targetPageId = if (reel.pageId > 0) reel.pageId else reel.userId
                                onOpenPageProfile(targetPageId)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 🔝 ভাসমান টপ বার: [ 📹 Upload (Creator Only) ] --- [ Following | For You ] --- [ 🔍 | ⋮ ]
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 🎯 বাঁয়ে: "Post" টেক্সটের পরিবর্তে শুধুমাত্র অনুমোদিত ক্রিয়েটরদের জন্য ভিডিও আপলোড আইকন
            if (hasApprovedCreatorPage) {
                IconButton(
                    onClick = onOpenCreateReel,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = "Upload Reel",
                        tint = ActionGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }

            // 🎯 সেন্টারে: Following | For You ট্যাব সুইচ
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Following",
                    color = if (feedState.activeTab == "following") Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = if (feedState.activeTab == "following") FontWeight.Black else FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.loadFeed(tab = "following") }
                )

                Text(text = "|", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)

                Text(
                    text = "For You",
                    color = if (feedState.activeTab == "for_you") Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = if (feedState.activeTab == "for_you") FontWeight.Black else FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.loadFeed(tab = "for_you") }
                )
            }

            // 🎯 ডানে: সার্চ (🔍) এবং থ্রি-ডট সেটিংস (⋮)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { onNavigateToSearch("") },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = { showThreeDotSettingsSheet = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // =========================================================================
        // 📊 লাইভ সার্কুলার আপলোড ও এনকোডিং প্রোগ্রেস রিং (উপরের কোণায়)
        // =========================================================================
        if (uploadState.isUploading) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ActionGreen),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 52.dp, end = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { uploadState.uploadProgress / 100f },
                        color = ActionGreen,
                        trackColor = Color(0xFF222B3D),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (uploadState.uploadProgress >= 100) "Encoding..." else "${uploadState.uploadProgress}%",
                        color = ActionGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // =========================================================================
        // 📤 ২ নম্বর স্ক্রিনশটের হুবহু TikTok "Send to" শেয়ার বটম শীট
        // =========================================================================
        if (showShareBottomSheet && activeReelForAction != null) {
            val currentReel = activeReelForAction!!
            ReelsShareBottomSheet(
                reel = currentReel,
                conversationsList = conversationList,
                isLoggedIn = isLoggedIn,
                isCreatorPageUser = hasApprovedCreatorPage,
                onDismiss = { showShareBottomSheet = false },
                onRepostClick = {
                    coroutineScope.launch {
                        val res = repository.toggleRepost(currentReel.id)
                        if (res.isSuccess) {
                            Toast.makeText(context, "🎉 Reposted to your creator profile!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Repost failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSendToFriendInChat = { friendId, friendName ->
                    coroutineScope.launch {
                        chatRepository.sendDirectTextMessage(
                            conversationId = "direct_${repository.getCurrentUserId()}_$friendId",
                            recipientId = friendId,
                            text = currentReel.shareUrl,
                            senderName = currentUserName,
                            senderAvatar = currentUserAvatar
                        )
                    }
                },
                onRequireLogin = onRequireLogin
            )
        }

        // =========================================================================
        // 💬 রিয়েল-টাইম কমেন্ট বটম শীট (ডুপ্লিকেশন মুক্ত)
        // =========================================================================
        if (showCommentsSheet && activeReelForAction != null) {
            ReelsCommentsSheet(
                reelId = activeReelForAction!!.id,
                repository = repository,
                isLoggedIn = isLoggedIn,
                currentUserName = currentUserName,
                currentUserAvatar = currentUserAvatar,
                onRequireLogin = onRequireLogin,
                onDismiss = { showCommentsSheet = false }
            )
        }

        // কোয়ালিটি ও স্পিড সেটিংস বটম শীট
        if (showThreeDotSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showThreeDotSettingsSheet = false },
                containerColor = DarkCardBg,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF333C4D)))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Playback Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showThreeDotSettingsSheet = false }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF19202E),
                        border = BorderStroke(0.8.dp, BorderStrokeColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showThreeDotSettingsSheet = false
                                showQualityPickerSheet = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.HighQuality, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Video Quality", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(feedState.selectedQuality.label, color = Color(0xFF00E5FF), fontSize = 11.5.sp)
                                }
                            }
                            Text("Change >", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF19202E),
                        border = BorderStroke(0.8.dp, BorderStrokeColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showThreeDotSettingsSheet = false
                                showSpeedPickerSheet = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.Speed, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Playback Speed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (selectedPlaybackSpeed == 1.0f) "1.0x (Normal)" else "${selectedPlaybackSpeed}x", color = Color(0xFFFFB300), fontSize = 11.5.sp)
                                }
                            }
                            Text("Change >", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        if (showQualityPickerSheet) {
            ReelsQualitySelectionSheet(
                selectedQuality = feedState.selectedQuality,
                onSelectQuality = { newQuality ->
                    viewModel.setVideoQuality(newQuality)
                    Toast.makeText(context, "Quality set to ${newQuality.label}", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showQualityPickerSheet = false }
            )
        }

        if (showSpeedPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSpeedPickerSheet = false },
                containerColor = DarkCardBg,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF333C4D)))
                    }

                    Text("Select Playback Speed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

                    speedOptions.forEach { spd ->
                        val isSelected = (selectedPlaybackSpeed == spd)
                        val label = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x"

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF132A38) else Color(0xFF19202E),
                            border = BorderStroke(if (isSelected) 1.dp else 0.6.dp, if (isSelected) Color(0xFFFFB300) else BorderStrokeColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPlaybackSpeed = spd
                                    showSpeedPickerSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, color = if (isSelected) Color(0xFFFFB300) else Color.White, fontSize = 14.sp)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
