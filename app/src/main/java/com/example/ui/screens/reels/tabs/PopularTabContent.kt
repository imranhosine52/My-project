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
 * 📱 Popular Tab:
 * (ভার্টিক্যাল পেজার + কমেন্ট ওপেন হলে ভিডিওর সব ওভারলে হাইড হওয়া এবং নিচে কমেন্ট ওপেন হওয়া)
 */
@Composable
fun PopularTabContent(
    pagerState: PagerState,
    reelsList: List<UserReelDto>,
    selectedQuality: ReelVideoQuality,
    playbackSpeed: Float,
    isAppInForeground: Boolean,
    isCurrentTabActive: Boolean,
    isCommentsOpen: Boolean, // 🎯 কমেন্ট ওপেন স্টেট
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
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = !isCommentsOpen, // কমেন্ট ওপেন থাকলে পেজিং লক
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
                            isCommentsOpen = isCommentsOpen, // 🎯 কমেন্ট ওপেন থাকলে সব ওভারলে হাইড থাকবে
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
