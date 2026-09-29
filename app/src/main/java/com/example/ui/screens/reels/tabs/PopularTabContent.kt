@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.comments.InstagramCommentsSheet
import com.example.ui.screens.reels.player.ShrinkableVideoContainer
import com.example.ui.screens.reels.player.SingleReelPlayerItem

/**
 * 📱 Popular Tab Orchestrator:
 * (ভার্টিক্যাল রিলস পেজার + ১ নম্বর ছবির মতো স্মুথ ভিডিও ছোট হওয়ার ইঞ্জিন + কমেন্ট বক্স ইন্টিগ্রেশন)
 */
@Composable
fun PopularTabContent(
    pagerState: PagerState,
    reelsList: List<UserReelDto>,
    selectedQuality: ReelVideoQuality,
    playbackSpeed: Float,
    isAppInForeground: Boolean,
    isCurrentTabActive: Boolean,
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
    // কমেন্ট বক্স ওপেন/ক্লোজ স্টেট
    var isCommentsOpen by remember { mutableStateOf(false) }
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
        // 🎬 ১ নম্বর ছবির মতো ভিডিও ছোট হওয়া এবং কমেন্ট বক্সের সমন্বিত কন্টেইনার
        // =========================================================================
        ShrinkableVideoContainer(
            isCommentsOpen = isCommentsOpen,
            onCloseComments = {
                isCommentsOpen = false
            },
            videoContent = { _ ->
                // ফুলস্ক্রিন ভার্টিক্যাল রিলস পেজার (কমেন্ট ওপেন থাকলে পেজিং লক থাকবে)
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(reel.id) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    // বামে সোয়াইপ করলে ক্রিয়েটর প্রোফাইল খুলবে
                                    if (dragAmount < -50f && !isCommentsOpen) {
                                        val targetPageId = if (reel.pageId > 0) reel.pageId else reel.userId
                                        onOpenPageProfile(targetPageId)
                                    }
                                }
                            }
                    ) {
                        SingleReelPlayerItem(
                            reel = reel,
                            selectedQuality = selectedQuality,
                            playbackSpeed = playbackSpeed,
                            isActiveVideoPlaying = isCurrentPagePlaying,
                            repository = repository,
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
                                // 🎯 কমেন্ট বাটনে চাপ দিলে ১ নম্বর ছবির মতো ভিডিও ছোট হয়ে নিচে কমেন্ট আসবে
                                activeCommentReel = reel
                                isCommentsOpen = true
                            },
                            onShareClick = {
                                onShareClick(reel)
                            },
                            onHashtagClick = onHashtagClick,
                            onOpenPageProfile = {
                                val targetPageId = if (reel.pageId > 0) reel.pageId else reel.userId
                                onOpenPageProfile(targetPageId)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            commentsContent = {
                // ১ নম্বর ছবির হুবহু কমেন্ট বক্স ইন্টারফেস
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
