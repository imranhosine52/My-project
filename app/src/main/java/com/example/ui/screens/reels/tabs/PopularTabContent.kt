@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.comments.InstagramCommentsSheet
import com.example.ui.screens.reels.player.ShrinkableVideoContainer
import com.example.ui.screens.reels.player.SingleReelPlayerItem
import kotlinx.coroutines.launch

/**
 * 📱 Popular Tab:
 * - ক্র্যাশ-প্রুফ পেজিং
 * - সাইডবার থেকে সিলেক্ট করলে মসৃণ স্ক্রোল
 * - সিঙ্গেল টাচে সাইডবার ক্লোজিং
 */
@Composable
fun PopularTabContent(
    pagerState: PagerState,
    reelsList: List<UserReelDto>,
    selectedQuality: ReelVideoQuality,
    playbackSpeed: Float,
    isAppInForeground: Boolean,
    isCurrentTabActive: Boolean,
    isCommentsOpen: Boolean,
    onCommentsVisibilityChange: (Boolean) -> Unit,
    isSidebarOpen: Boolean,
    onSidebarVisibilityChange: (Boolean) -> Unit,
    repository: ReelsRepository,
    isLoggedIn: Boolean,
    currentUserName: String,
    currentUserAvatar: String?,
    hasApprovedCreatorPage: Boolean,
    onRequireLogin: () -> Unit,
    onToggleLike: (UserReelDto) -> Unit,
    onFollowToggle: (pageId: Int, userId: Int) -> Unit,
    onShareClick: (UserReelDto) -> Unit,
    onHashtagClick: (String) -> Unit,
    onOpenPageProfile: (pageId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var activeCommentReel by remember { mutableStateOf<UserReelDto?>(null) }

    if (reelsList.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No popular reels right now",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    } else {
        ShrinkableVideoContainer(
            isCommentsOpen = isCommentsOpen,
            onCloseComments = {
                onCommentsVisibilityChange(false)
            },
            videoContent = { _ ->
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = !isCommentsOpen && !isSidebarOpen,
                    modifier = Modifier.fillMaxSize(),
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                ) { pageIndex ->
                    val reel = reelsList.getOrNull(pageIndex) ?: return@VerticalPager
                    val isCurrentPagePlaying = (pagerState.currentPage == pageIndex) &&
                            isAppInForeground &&
                            isCurrentTabActive

                    SingleReelPlayerItem(
                        reel = reel,
                        allReels = reelsList,
                        selectedQuality = selectedQuality,
                        playbackSpeed = playbackSpeed,
                        isActiveVideoPlaying = isCurrentPagePlaying,
                        repository = repository,
                        isCommentsOpen = isCommentsOpen,
                        isSidebarOpenState = isSidebarOpen,
                        onSidebarStateChange = onSidebarVisibilityChange,
                        isLoggedIn = isLoggedIn,
                        isCreatorPageUser = hasApprovedCreatorPage,
                        onRequireLogin = onRequireLogin,
                        onDoubleTapLike = { onToggleLike(reel) },
                        onToggleLike = { onToggleLike(reel) },
                        onFollowClick = { onFollowToggle(reel.pageId, reel.userId) },
                        onCommentClick = {
                            activeCommentReel = reel
                            onCommentsVisibilityChange(true)
                        },
                        onShareClick = { onShareClick(reel) },
                        onHashtagClick = onHashtagClick,
                        onOpenPageProfile = {
                            val targetPageId = if (reel.pageId > 0) reel.pageId else reel.userId
                            onOpenPageProfile(targetPageId)
                        },
                        onSelectReel = { selectedReel ->
                            // 🎯 ক্র্যাশ-প্রুফ সেফ ইনডেক্স নেভিগেশন
                            val targetIndex = reelsList.indexOfFirst { it.id == selectedReel.id }
                            if (targetIndex != -1 && targetIndex in 0 until reelsList.size) {
                                coroutineScope.launch {
                                    runCatching {
                                        pagerState.animateScrollToPage(targetIndex)
                                    }
                                }
                            }
                        },
                        onVideoCompleteAutoPlayNext = {
                            if (pagerState.currentPage < reelsList.size - 1) {
                                coroutineScope.launch {
                                    runCatching {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            commentsContent = {
                val currentReel = activeCommentReel ?: reelsList.getOrNull(pagerState.currentPage)
                if (currentReel != null) {
                    InstagramCommentsSheet(
                        reelId = currentReel.id,
                        targetCreatorName = currentReel.pageName,
                        repository = repository,
                        isLoggedIn = isLoggedIn,
                        currentUserName = currentUserName,
                        currentUserAvatar = currentUserAvatar,
                        onRequireLogin = onRequireLogin,
                        onPickImageClick = {},
                        onGifClick = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
