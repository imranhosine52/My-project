@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)

package com.example.ui.screens.reels

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DirectConversationItem
import com.example.data.model.UserReelDto
import com.example.data.repository.ChatRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.components.ReelsBottomNavigationBar
import com.example.ui.screens.reels.components.ReelsPlaybackSettingsSheet
import com.example.ui.screens.reels.components.ReelsSpeedSelectionSheet
import com.example.ui.screens.reels.components.ReelsTopNavigationBar
import com.example.ui.screens.reels.tabs.FollowTabContent
import com.example.ui.screens.reels.tabs.PopularTabContent
import com.example.ui.screens.reels.tabs.TrendTabContent
import com.example.ui.viewmodel.ReelsViewModel
import com.example.util.ReelsCachePreloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)

@Composable
fun ReelsFeedScreen(
    viewModel: ReelsViewModel,
    isLoggedIn: Boolean = true,
    currentUserName: String = "User",
    currentUserAvatar: String? = null,
    onBackClick: () -> Unit,
    onNavigateToHome: () -> Unit = onBackClick,
    onNavigateToInbox: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
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

    var isCommentsOpen by remember { mutableStateOf(false) }
    var isSidebarOpen by remember { mutableStateOf(false) }

    var showPlaybackSettingsSheet by remember { mutableStateOf(false) }
    var showQualityPickerSheet by remember { mutableStateOf(false) }
    var showSpeedPickerSheet by remember { mutableStateOf(false) }
    var showShareBottomSheet by remember { mutableStateOf(false) }
    var activeReelForShare by remember { mutableStateOf<UserReelDto?>(null) }

    var conversationList by remember { mutableStateOf<List<DirectConversationItem>>(emptyList()) }
    var selectedPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var isAppInForeground by remember { mutableStateOf(true) }

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

    val safeTopPadding = 70.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
                userScrollEnabled = !isCommentsOpen && !isSidebarOpen,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        FollowTabContent(
                            suggestedPages = suggestedPages,
                            allReels = reelsList,
                            safeTopPadding = safeTopPadding,
                            onProfileClick = onOpenPageProfile,
                            onFollowToggle = { pageId, userId ->
                                if (!isLoggedIn) onRequireLogin()
                                else viewModel.toggleFollowSuggestedPage(pageId, userId)
                            },
                            onDismissPage = { pageId ->
                                dismissedPageIds.add(pageId)
                            },
                            onCloseHeaderClick = {
                                coroutineScope.launch {
                                    mainTabPagerState.animateScrollToPage(2)
                                }
                            }
                        )
                    }

                    1 -> {
                        TrendTabContent(
                            trendReels = trendReels,
                            safeTopPadding = safeTopPadding,
                            onReelClick = { reel ->
                                val clickedIdx = reelsList.indexOfFirst { it.id == reel.id }.coerceAtLeast(0)
                                coroutineScope.launch {
                                    mainTabPagerState.animateScrollToPage(2)
                                    verticalReelsPagerState.scrollToPage(clickedIdx)
                                }
                            }
                        )
                    }

                    2 -> {
                        PopularTabContent(
                            pagerState = verticalReelsPagerState,
                            reelsList = reelsList,
                            selectedQuality = feedState.selectedQuality,
                            playbackSpeed = selectedPlaybackSpeed,
                            isAppInForeground = isAppInForeground,
                            isCurrentTabActive = (mainTabPagerState.currentPage == 2),
                            isCommentsOpen = isCommentsOpen,
                            onCommentsVisibilityChange = { isCommentsOpen = it },
                            isSidebarOpen = isSidebarOpen,
                            onSidebarVisibilityChange = { isSidebarOpen = it },
                            repository = repository,
                            isLoggedIn = isLoggedIn,
                            currentUserName = currentUserName,
                            currentUserAvatar = currentUserAvatar,
                            hasApprovedCreatorPage = hasApprovedCreatorPage,
                            onRequireLogin = onRequireLogin,
                            onToggleLike = { reel ->
                                if (!isLoggedIn) onRequireLogin() else viewModel.toggleLike(reel)
                            },
                            onFollowToggle = { pageId, userId ->
                                if (!isLoggedIn) onRequireLogin() else viewModel.toggleFollowCreator(pageId, userId)
                            },
                            onShareClick = { reel ->
                                activeReelForShare = reel
                                showShareBottomSheet = true
                            },
                            onHashtagClick = { hashtag ->
                                onNavigateToSearch(hashtag)
                            },
                            onOpenPageProfile = onOpenPageProfile
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 🔝 ওপরে টপ বার (সাইডবার বা কমেন্ট ওপেন থাকলে হাইড)
        // =========================================================================
        ReelsTopNavigationBar(
            currentTabIndex = mainTabPagerState.currentPage,
            pagerOffsetFraction = mainTabPagerState.currentPageOffsetFraction,
            tabTitles = tabTitles,
            isVisible = !isCommentsOpen && !isSidebarOpen,
            hasApprovedCreatorPage = hasApprovedCreatorPage,
            onBackClick = onBackClick,
            onOpenCreateReel = onOpenCreateReel,
            onTabSelected = { index ->
                coroutineScope.launch {
                    mainTabPagerState.animateScrollToPage(index)
                }
            },
            onSearchClick = { onNavigateToSearch("") },
            onOptionsClick = { showPlaybackSettingsSheet = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // =========================================================================
        // 🎯 নিচে রিলস পেজের নিজস্ব ৫-আইটেম স্লিম ন্যাভিগেশন বার (০-গ্যাপ)
        // =========================================================================
        if (!isCommentsOpen && !isSidebarOpen) {
            ReelsBottomNavigationBar(
                onHomeClick = onNavigateToHome,
                onReelsClick = {
                    coroutineScope.launch {
                        mainTabPagerState.animateScrollToPage(2)
                    }
                },
                onUploadClick = onOpenCreateReel,
                onInboxClick = onNavigateToInbox,
                onProfileClick = onNavigateToProfile,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // আপলোড প্রোগ্রেস
        if (uploadState.isUploading && !isCommentsOpen && !isSidebarOpen) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ActionGreen),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 12.dp)
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

        // বটম শীটসমূহ
        if (showShareBottomSheet && activeReelForShare != null) {
            val currentReel = activeReelForShare!!
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

        if (showPlaybackSettingsSheet) {
            ReelsPlaybackSettingsSheet(
                selectedQuality = feedState.selectedQuality,
                selectedSpeed = selectedPlaybackSpeed,
                onOpenQualityPicker = {
                    showPlaybackSettingsSheet = false
                    showQualityPickerSheet = true
                },
                onOpenSpeedPicker = {
                    showPlaybackSettingsSheet = false
                    showSpeedPickerSheet = true
                },
                onDismiss = { showPlaybackSettingsSheet = false }
            )
        }

        if (showQualityPickerSheet) {
            ReelsQualitySelectionSheet(
                selectedQuality = feedState.selectedQuality,
                onSelectQuality = { newQuality -> viewModel.setVideoQuality(newQuality) },
                onDismiss = { showQualityPickerSheet = false }
            )
        }

        if (showSpeedPickerSheet) {
            ReelsSpeedSelectionSheet(
                selectedSpeed = selectedPlaybackSpeed,
                onSelectSpeed = { newSpeed -> selectedPlaybackSpeed = newSpeed },
                onDismiss = { showSpeedPickerSheet = false }
            )
        }
    }
}
