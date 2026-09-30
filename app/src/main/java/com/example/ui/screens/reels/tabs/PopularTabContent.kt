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
 * - ভার্টিক্যাল রিলস পেজার
 * - ডান প্রান্ত থেকে টানলে সব ভিডিওর প্লেলিস্ট সাইডবার
 * - কমেন্ট ওপেন হলে স্মুথলি ভিডিও উপরে সংকুচিত হওয়া ও নিচে কমেন্ট বক্স
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
        // =========================================================================
        // 🎬 ১ নম্বর ছবির মতো ভিডিও ছোট হওয়া এবং কমেন্ট বক্স কন্টেইনার
        // =========================================================================
        ShrinkableVideoContainer(
            isCommentsOpen = isCommentsOpen,
            onCloseComments = {
                onCommentsVisibilityChange(false)
            },
            videoContent = { _ ->
                // ফুলস্ক্রিন ভার্টিক্যাল রিলস পেজার (কমেন্ট ওপেন থাকলে পেজিং লক)
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = !isCommentsOpen,
                    modifier = Modifier.fillMaxSize(),
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                ) { pageIndex ->
                    val reel = reelsList[pageIndex]
                    val isCurrentPagePlaying = (pagerState.currentPage == pageIndex) &&
                            isAppInForeground &&
                            isCurrentTabActive

                    SingleReelPlayerItem(
                        reel = reel,
                        allReels = reelsList, // 🎯 সাইডবারে ক্রিয়েটরের সব ভিডিও প্রদর্শনের জন্য
                        selectedQuality = selectedQuality,
                        playbackSpeed = playbackSpeed,
                        isActiveVideoPlaying = isCurrentPagePlaying,
                        repository = repository,
                        isCommentsOpen = isCommentsOpen,
                        isLoggedIn = isLoggedIn,
                        isCreatorPageUser = hasApprovedCreatorPage,
                        onRequireLogin = onRequireLogin,
                        onDoubleTapLike = {
                            onToggleLike(reel)
                        },
                        onToggleLike = {
                            onToggleLike(reel)
                        },
                        onFollowClick = {
                            onFollowToggle(reel.pageId, reel.userId)
                        },
                        onCommentClick = {
                            activeCommentReel = reel
                            onCommentsVisibilityChange(true)
                        },
                        onShareClick = {
                            onShareClick(reel)
                        },
                        onHashtagClick = onHashtagClick,
                        onOpenPageProfile = {
                            val targetPageId = if (reel.pageId > 0) reel.pageId else reel.userId
                            onOpenPageProfile(targetPageId)
                        },
                        onSelectReel = { selectedReel ->
                            // 🎯 সাইডবার থেকে কোনো ভিডিও নির্বাচন করলে পেজার মসৃণভাবে সেই ভিডিওতে চলে যাবে
                            val targetIndex = reelsList.indexOfFirst { it.id == selectedReel.id }
                            if (targetIndex != -1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetIndex)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            commentsContent = {
                // ১ নম্বর ছবির হুবহু কমেন্ট বক্স
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
