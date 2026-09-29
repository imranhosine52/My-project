@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)

package com.example.ui.screens.reels

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.DirectConversationItem
import com.example.data.model.ReelVideoQuality
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto
import com.example.data.repository.ChatRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import com.example.util.ReelsCachePreloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val ActionGreen = Color(0xFF00E676)
private val TikTokRed = Color(0xFFFE2C55)
private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)
private val GoldBadge = Color(0xFFF59E0B)

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

    val hasApprovedCreatorPage = uploadState.creatorPage?.isApproved == true

    var showThreeDotSettingsSheet by remember { mutableStateOf(false) }
    var showQualityPickerSheet by remember { mutableStateOf(false) }
    var showSpeedPickerSheet by remember { mutableStateOf(false) }

    var showCommentsSheet by remember { mutableStateOf(false) }
    var showShareBottomSheet by remember { mutableStateOf(false) }
    var activeReelForAction by remember { mutableStateOf<UserReelDto?>(null) }

    var conversationList by remember { mutableStateOf<List<DirectConversationItem>>(emptyList()) }
    var selectedPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var isAppInForeground by remember { mutableStateOf(true) }

    // 🎯 ইউজার লোকালভাবে যে পেজগুলো Dismiss (✕) করবে তার তালিকা
    val dismissedPageIds = remember { mutableStateListOf<Int>() }

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
        viewModel.loadSuggestedPages()
        val convRes = chatRepository.getInboxConversations()
        conversationList = convRes.getOrDefault(emptyList())
    }

    val reelsList = feedState.reels
    val suggestedPages = remember(feedState.suggestedPages, dismissedPageIds.toList()) {
        feedState.suggestedPages.filter { !dismissedPageIds.contains(it.pageId) }
    }

    val tabTitles = listOf("Follow", "Trend", "Popular")
    val mainTabPagerState = rememberPagerState(initialPage = 2, pageCount = { 3 })
    val verticalReelsPagerState = rememberPagerState(initialPage = 0, pageCount = { reelsList.size })

    val trendReels = remember(reelsList) {
        reelsList.sortedByDescending { (it.viewsCount * 2 + it.likesCount * 3) }
    }

    val followingFeedReels = remember(reelsList) {
        reelsList.filter { it.isFollowing }
    }

    // প্রি-লোডার ট্র্যাকিং
    LaunchedEffect(verticalReelsPagerState.currentPage, reelsList, mainTabPagerState.currentPage) {
        if (mainTabPagerState.currentPage == 2 && reelsList.isNotEmpty()) {
            val currentReel = reelsList.getOrNull(verticalReelsPagerState.currentPage)
            if (currentReel != null) {
                viewModel.trackReelView(currentReel.id)
            }
            ReelsCachePreloadManager.onUserScrolledToPosition(
                context = context,
                currentIndex = verticalReelsPagerState.currentPage,
                allReels = reelsList
            )
        }
    }

    // 📏 ক্যালকুলেটেড টপ প্যাডিং (কার্ড ওভারল্যাপ সম্পূর্ণ বন্ধ করার জন্য)
    val safeTopContentPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // =========================================================================
        // ↔️ ৩টি ট্যাবের অনুভূমিক পেজার (HorizontalPager)
        // =========================================================================
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.loadFeed("for_you")
                    viewModel.loadSuggestedPages()
                    delay(500)
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            HorizontalPager(
                state = mainTabPagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                when (pageIndex) {
                    // =============================================================
                    // 👥 ০. FOLLOW TAB (১ নম্বর ছবির হুবহু উল্লম্ব সাজেস্টেড লিস্ট)
                    // =============================================================
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = safeTopContentPadding, bottom = 86.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // ১ নম্বর ছবির মতো হেডার
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Suggested Accounts",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Close",
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable {
                                            coroutineScope.launch {
                                                mainTabPagerState.animateScrollToPage(2)
                                            }
                                        }
                                    )
                                }
                            }

                            // সাজেস্টেড পেজগুলোর ১ম ছবির মতো লিস্ট
                            if (suggestedPages.isNotEmpty()) {
                                itemsIndexed(suggestedPages, key = { _, page -> "follow_row_${page.pageId}_${page.userId}" }) { _, page ->
                                    FollowUserItemRow(
                                        page = page,
                                        onProfileClick = {
                                            val targetId = if (page.pageId > 0) page.pageId else page.userId
                                            onOpenPageProfile(targetId)
                                        },
                                        onFollowClick = {
                                            if (!isLoggedIn) onRequireLogin()
                                            else viewModel.toggleFollowSuggestedPage(page.pageId, page.userId)
                                        },
                                        onDismissClick = {
                                            dismissedPageIds.add(page.pageId)
                                        }
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No more suggested creators",
                                            color = TextMuted,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }
                            }

                            // যদি ফলো করা ভিডিও থাকে তবে নিচে দেখাবে
                            if (followingFeedReels.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = Color(0xFF1E2432), thickness = 0.8.dp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "From creators you follow",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }

                                itemsIndexed(followingFeedReels, key = { idx, reel -> "foll_feed_${reel.id}_$idx" }) { _, reel ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(380.dp)
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkCardBg)
                                            .clickable {
                                                val clickedIdx = reelsList.indexOfFirst { it.id == reel.id }.coerceAtLeast(0)
                                                coroutineScope.launch {
                                                    mainTabPagerState.animateScrollToPage(2)
                                                    verticalReelsPagerState.scrollToPage(clickedIdx)
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl,
                                            contentDescription = reel.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                    )
                                                )
                                        )
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF222838))) {
                                                AsyncImage(
                                                    model = reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=00E676&color=000",
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Column {
                                                Text(reel.pageName, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                                Text(reel.title ?: reel.displayHandle, color = Color(0xFFCBD5E1), fontSize = 11.5.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 🎬 ১. TREND TAB (২-কলাম ভিডিও গ্রিড)
                    // =============================================================
                    1 -> {
                        if (trendReels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No trending reels available", color = Color.White, fontSize = 14.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = safeTopContentPadding, bottom = 86.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(trendReels, key = { idx, reel -> "trend_card_${reel.id}_$idx" }) { _, reel ->
                                    Trend2ColumnVideoCard(
                                        reel = reel,
                                        onClick = {
                                            val clickedIdx = reelsList.indexOfFirst { it.id == reel.id }.coerceAtLeast(0)
                                            coroutineScope.launch {
                                                mainTabPagerState.animateScrollToPage(2)
                                                verticalReelsPagerState.scrollToPage(clickedIdx)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 📱 ২. POPULAR TAB (ফুলস্ক্রিন ভিডিও রিলস প্লেয়ার)
                    // =============================================================
                    2 -> {
                        if (reelsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No popular reels right now", color = Color.White, fontSize = 14.sp)
                            }
                        } else {
                            VerticalPager(
                                state = verticalReelsPagerState,
                                modifier = Modifier.fillMaxSize(),
                                flingBehavior = PagerDefaults.flingBehavior(state = verticalReelsPagerState)
                            ) { pageIndex ->
                                val reel = reelsList[pageIndex]
                                val isCurrentPagePlaying = (verticalReelsPagerState.currentPage == pageIndex) &&
                                        isAppInForeground &&
                                        (mainTabPagerState.currentPage == 2)

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(reel.id) {
                                            detectHorizontalDragGestures { _, dragAmount ->
                                                if (dragAmount < -50f) {
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
                                        isLoggedIn = isLoggedIn,
                                        isCreatorPageUser = hasApprovedCreatorPage,
                                        onRequireLogin = onRequireLogin,
                                        onDoubleTapLike = {
                                            if (!isLoggedIn) onRequireLogin() else viewModel.toggleLike(reel)
                                        },
                                        onToggleLike = {
                                            if (!isLoggedIn) onRequireLogin() else viewModel.toggleLike(reel)
                                        },
                                        onFollowClick = {
                                            if (!isLoggedIn) onRequireLogin() else viewModel.toggleFollowCreator(reel.pageId, reel.userId)
                                        },
                                        onCommentClick = {
                                            if (!isLoggedIn) onRequireLogin()
                                            else {
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
                }
            }
        }

        // =========================================================================
        // 🔝 ফিক্সড টপ বার (২ নম্বর ছবির দাগ অনুযায়ী একদম ওপরে নিখুঁত অবস্থান)
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.80f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ১. বামে (+) ক্রিয়েট বাটন
                if (hasApprovedCreatorPage) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onOpenCreateReel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(1.4.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Reel",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }

                // ২. মাঝখানে ৩টি ট্যাব: Follow, Trend, Popular
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    tabTitles.forEachIndexed { index, tabName ->
                        val isSelected = (mainTabPagerState.currentPage == index)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch {
                                        mainTabPagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = tabName,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                                fontSize = if (isSelected) 16.sp else 14.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.5.dp))
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 22.dp else 0.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
                            )
                        }
                    }
                }

                // ৩. ডানে সার্চ ও থ্রি-ডট মেনু
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onNavigateToSearch("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    if (mainTabPagerState.currentPage == 2) {
                        IconButton(
                            onClick = { showThreeDotSettingsSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        // =========================================================================
        // 📊 লাইভ আপলোড প্রোগ্রেস ও বটম শীটসমূহ
        // =========================================================================
        if (uploadState.isUploading) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ActionGreen),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 48.dp, end = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { uploadState.uploadProgress / 100f },
                        color = ActionGreen,
                        trackColor = Color(0xFF222B3D),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (uploadState.uploadProgress >= 100) "Encoding..." else "${uploadState.uploadProgress}%",
                        color = ActionGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // শেয়ার বটম শীট
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

        // কমেন্টস বটম শীট
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

        // সেটিংস বটম শীট
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
                        modifier = Modifier.fillMaxWidth().clickable {
                            showThreeDotSettingsSheet = false
                            showQualityPickerSheet = true
                        }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
                        modifier = Modifier.fillMaxWidth().clickable {
                            showThreeDotSettingsSheet = false
                            showSpeedPickerSheet = true
                        }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
                onSelectQuality = { newQuality -> viewModel.setVideoQuality(newQuality) },
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Select Playback Speed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

                    speedOptions.forEach { spd ->
                        val isSelected = (selectedPlaybackSpeed == spd)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF132A38) else Color(0xFF19202E),
                            border = BorderStroke(if (isSelected) 1.dp else 0.6.dp, if (isSelected) Color(0xFFFFB300) else BorderStrokeColor),
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedPlaybackSpeed = spd
                                showSpeedPickerSheet = false
                            }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x", color = if (isSelected) Color(0xFFFFB300) else Color.White, fontSize = 14.sp)
                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

// =============================================================================
// 🔲 ১ নম্বর ছবির হুবহু Follow User Row (অ্যাভাটার + গোল্ডেন 'V' + ফলো + '✕')
// =============================================================================
@Composable
private fun FollowUserItemRow(
    page: SuggestedPageDto,
    onProfileClick: () -> Unit,
    onFollowClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onProfileClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ১. অ্যাভাটার ও গোল্ডেন 'V' ভেরিফায়েড ব্যাজ
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(page.avatar ?: "https://ui-avatars.com/api/?name=${page.pageName}&background=222838&color=fff")
                    .crossfade(true)
                    .build(),
                contentDescription = page.pageName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            // নিচের গোল্ডেন 'V' ব্যাজ
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(GoldBadge)
                    .border(1.2.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ২. ইউজারের নাম ও "You May Like"
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = page.pageName,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "You May Like",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // ৩. ১ম ছবির হুবহু লাল ক্যাপসুল Follow বাটন
        Button(
            onClick = onFollowClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (page.isFollowing) Color(0xFF262C38) else TikTokRed
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text(
                text = if (page.isFollowing) "Following" else "Follow",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ৪. ডানপাশের '✕' রিমুভ আইকন
        IconButton(
            onClick = onDismissClick,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// =============================================================================
// 🔲 ২-কলাম ট্রেন্ড ভিডিও কার্ড
// =============================================================================
@Composable
private fun Trend2ColumnVideoCard(
    reel: UserReelDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = reel.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222838))
                    ) {
                        AsyncImage(
                            model = reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=222838&color=fff",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        text = reel.pageName.ifBlank { reel.displayHandle },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Likes",
                        tint = if (reel.isLiked) Color(0xFFFF2A4B) else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = formatTrendCount(reel.likesCount),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatTrendCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fm", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}
