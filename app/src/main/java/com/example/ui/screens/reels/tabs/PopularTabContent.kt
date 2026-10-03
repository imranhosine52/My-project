@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.reels.tabs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ReelVideoQuality
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.comments.InstagramCommentsSheet
import com.example.ui.screens.reels.player.ReelsPlaylistSidebar
import com.example.ui.screens.reels.player.ShrinkableVideoContainer
import com.example.ui.screens.reels.player.SingleReelPlayerItem
import com.example.ui.screens.reels.player.SlimSidebarWidth
import kotlinx.coroutines.launch
import java.util.Locale

private val CyanBlue = Color(0xFF00E5FF)
private val ActionGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8E95A5)

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeCommentReel by remember { mutableStateOf<UserReelDto?>(null) }

    // বর্তমানে যে রিলটি চলছে
    val currentReel = reelsList.getOrNull(pagerState.currentPage)

    val creatorReels = remember(currentReel, reelsList) {
        if (currentReel != null) {
            val targetId = if (currentReel.pageId > 0) currentReel.pageId else currentReel.userId
            val list = reelsList.filter { (it.pageId > 0 && it.pageId == targetId) || (it.userId > 0 && it.userId == targetId) }
            if (list.isNotEmpty()) list else listOf(currentReel)
        } else emptyList()
    }

    if (reelsList.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No popular reels right now", color = Color.White, fontSize = 14.sp)
        }
    } else {
        ShrinkableVideoContainer(
            isCommentsOpen = isCommentsOpen,
            onCloseComments = { onCommentsVisibilityChange(false) },
            videoContent = { _ ->
                // =========================================================================
                // 🎬 ২ নম্বর ছবির আসল কাঠামো (খাঁটি কালো ব্যাকগ্রাউন্ড ও আলাদা ফ্রেম)
                // =========================================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // =====================================================================
                    // 🔝 ১. মাঝের অংশ: [ভিডিও ফ্রেম রাউন্ডেড কার্ড] + [ডানপাশে সাইডবার]
                    // =====================================================================
                    Row(
                        modifier = Modifier
                            .weight(1f) // 🎯 নিচে বটম বারের জায়গা ছেড়ে দিয়ে বাকি উচ্চতা নেবে
                            .fillMaxWidth()
                    ) {
                        // 📺 সবুজ দাগের মতো ক্রপ হওয়া চারকোনা ভিডিও ফ্রেম
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .padding(
                                    top = 2.dp,
                                    start = if (isSidebarOpen) 6.dp else 0.dp,
                                    end = if (isSidebarOpen) 6.dp else 0.dp,
                                    bottom = 4.dp
                                )
                                .clip(RoundedCornerShape(if (isSidebarOpen) 14.dp else 0.dp)) // 🎯 চার কোনা ক্রপ/রাউন্ড
                                .background(Color.Black)
                        ) {
                            // 🎯 শুধুমাত্র ভিডিও ফ্রেম স্ক্রোল ডাউন হবে
                            VerticalPager(
                                state = pagerState,
                                userScrollEnabled = !isCommentsOpen,
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
                                        val targetIndex = reelsList.indexOfFirst { it.id == selectedReel.id }
                                        if (targetIndex != -1 && targetIndex in 0 until reelsList.size) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(targetIndex) }
                                        }
                                    },
                                    onVideoCompleteAutoPlayNext = {
                                        if (pagerState.currentPage < reelsList.size - 1) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // 🎯 অতি-চিকন সাইডবার (ভিডিও স্ক্রোল করলেও এটি নড়বে না, ফিক্সড থাকবে)
                        AnimatedVisibility(
                            visible = isSidebarOpen && currentReel != null,
                            enter = slideInHorizontally { it } + fadeIn(),
                            exit = slideOutHorizontally { it } + fadeOut()
                        ) {
                            ReelsPlaylistSidebar(
                                isOpen = isSidebarOpen,
                                currentReel = currentReel!!,
                                creatorReels = creatorReels,
                                isPlaying = isAppInForeground,
                                onTogglePlayPause = {},
                                onSelectReel = { selectedItem ->
                                    val targetIdx = reelsList.indexOfFirst { it.id == selectedItem.id }
                                    if (targetIdx != -1) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(targetIdx) }
                                    }
                                },
                                onCloseSidebar = { onSidebarVisibilityChange(false) },
                                modifier = Modifier
                                    .width(SlimSidebarWidth)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    // =====================================================================
                    // 🌟 ২. ২ নম্বর ছবির নিচের অংশ: ভিডিও ফ্রেমের নিচে ফিক্সড কালো বার
                    // =====================================================================
                    // (ভিডিও স্ক্রোল করলে এটি নিচে স্ক্রোল হবে না, স্থির থাকবে; শুধু সংখ্যা আপডেট হবে)
                    if (currentReel != null && !isCommentsOpen) {
                        Surface(
                            color = Color.Black, // 🎯 খাঁটি কালো ব্যাকগ্রাউন্ড
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 48.dp) // বটম ন্যাভ বারের উপরে
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // ১. ক্যাপশন ও টাইটেল (২ নম্বর ছবির মতো)
                                val caption = currentReel.title?.takeIf { it.isNotBlank() }
                                    ?: currentReel.description?.takeIf { it.isNotBlank() }
                                    ?: currentReel.hashtags?.replace(",", " ")
                                    ?: ""

                                if (caption.isNotBlank()) {
                                    Text(
                                        text = caption,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // ২. নিচের রো: বামে [প্রোফাইল + ৩ নম্বর ছবির ফলো বাটন] | ডানে [লাইক, কমেন্ট, সেভ, শেয়ার]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // 👤 বাম পাশ: অ্যাভাটার + নাম + সাদা আউটলাইন ফলো বাটন
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val pId = if (currentReel.pageId > 0) currentReel.pageId else currentReel.userId
                                                    onOpenPageProfile(pId)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(currentReel.pageAvatar ?: "https://ui-avatars.com/api/?name=${currentReel.pageName}&background=222838&color=fff")
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        }

                                        Text(
                                            text = currentReel.pageName.ifBlank { currentReel.displayHandle },
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable {
                                                val pId = if (currentReel.pageId > 0) currentReel.pageId else currentReel.userId
                                                onOpenPageProfile(pId)
                                            }
                                        )

                                        // =============================================================
                                        // 🎯 ৩ নম্বর ছবি: খাঁটি সাদা আউটলাইন ফলো বাটন (নো ব্যাকগ্রাউন্ড)
                                        // =============================================================
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.Transparent, // 🎯 কোনো ব্যাকগ্রাউন্ড থাকবে না
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (currentReel.isFollowing) Color.White.copy(alpha = 0.4f) else Color.White
                                            ),
                                            modifier = Modifier.clickable {
                                                if (!isLoggedIn) onRequireLogin()
                                                else onFollowToggle(currentReel.pageId, currentReel.userId)
                                            }
                                        ) {
                                            Text(
                                                text = if (currentReel.isFollowing) "Following" else "Follow",
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // 🔘 ডান পাশ: ২ নম্বর ছবির মতো লাইক, কমেন্ট, সেভ, শেয়ার আইকন (সংখ্যা নিচে)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // লাইক
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                if (!isLoggedIn) onRequireLogin() else onToggleLike(currentReel)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (currentReel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                                contentDescription = "Like",
                                                tint = if (currentReel.isLiked) ActionGreen else Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = formatCompactNumber(currentReel.likesCount),
                                                color = Color.White,
                                                fontSize = 10.5.sp
                                            )
                                        }

                                        // কমেন্ট
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                activeCommentReel = currentReel
                                                onCommentsVisibilityChange(true)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = "Comment",
                                                tint = Color.White,
                                                modifier = Modifier.size(21.dp)
                                            )
                                            Text(
                                                text = formatCompactNumber(currentReel.commentsCount.toLong()),
                                                color = Color.White,
                                                fontSize = 10.5.sp
                                            )
                                        }

                                        // সেভ / বুকমার্ক
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                if (!isLoggedIn) onRequireLogin()
                                                else coroutineScope.launch { repository.toggleSaveReel(currentReel.id) }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (currentReel.isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                                contentDescription = "Save",
                                                tint = if (currentReel.isSaved) CyanBlue else Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = if (currentReel.isSaved) "1" else "0",
                                                color = Color.White,
                                                fontSize = 10.5.sp
                                            )
                                        }

                                        // শেয়ার
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { onShareClick(currentReel) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = "Share",
                                                tint = Color.White,
                                                modifier = Modifier.size(21.dp)
                                            )
                                            Text(
                                                text = formatCompactNumber(currentReel.sharesCount.toLong()),
                                                color = Color.White,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            commentsContent = {
                val activeReel = activeCommentReel ?: currentReel
                if (activeReel != null) {
                    InstagramCommentsSheet(
                        reelId = activeReel.id,
                        targetCreatorName = activeReel.pageName,
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

private fun formatCompactNumber(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}
