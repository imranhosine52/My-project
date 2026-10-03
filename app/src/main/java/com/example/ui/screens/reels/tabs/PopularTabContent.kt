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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import kotlinx.coroutines.launch
import java.util.Locale

private val CyanBlue = Color(0xFF00E5FF)
private val ActionGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8692A6)
private val HeartRed = Color(0xFFFF2A4B)

// 🎯 ২ নম্বর ছবির মতো সাইডবারের অতি-চিকন প্রস্থ
val UltraSlimSidebarWidth = 42.dp

/**
 * 📱 Popular Tab Engine:
 * - সাইডবার খুললে ভিডিও ফ্রেম সংকুচিত হয়ে চার কোণা ক্রপ হবে।
 * - ভিডিও ফ্রেমের নিচে ডেডিকেটেড কালো বারে ফিক্সড থাকবে ক্যাপশন, ৩ নম্বর ছবির মতো আউটলাইন ফলো বাটন এবং লাইক/কমেন্ট।
 * - স্ক্রোল ডাউন করলে শুধুমাত্র মাঝের ভিডিও ফ্রেম স্ক্রোল হবে, নিচের বার নিজ জায়গায় ফিক্সড থাকবে।
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeCommentReel by remember { mutableStateOf<UserReelDto?>(null) }

    // বর্তমানে চলমান রিল
    val currentReel = remember(pagerState.currentPage, reelsList) {
        reelsList.getOrNull(pagerState.currentPage)
    }

    val creatorReels = remember(currentReel, reelsList) {
        if (currentReel == null) emptyList()
        else {
            val targetCreatorId = if (currentReel.pageId > 0) currentReel.pageId else currentReel.userId
            val list = reelsList.filter {
                (it.pageId > 0 && it.pageId == targetCreatorId) || (it.userId > 0 && it.userId == targetCreatorId)
            }
            if (list.isNotEmpty()) list else listOf(currentReel)
        }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // =========================================================================
                    // 🌟 মোড ১: সাধারণ ফুলস্ক্রিন রিলস (সাইডবার বন্ধ থাকা অবস্থায়)
                    // =========================================================================
                    if (!isSidebarOpen) {
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
                                isSidebarOpenState = false,
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
                                    if (targetIndex != -1) {
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
                    // =========================================================================
                    // 🌟 মোড ২: ২ নম্বর ছবির হুবহু ডকড প্লেলিস্ট ও সাইডবার মোড
                    // =========================================================================
                    else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // ক) বাম পাশ: [সংকুচিত ক্রপ করা ভিডিও পেজার] + [নিচে ফিক্সড কালো ব্যাকগ্রাউন্ড এরিয়া]
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color.Black)
                            ) {
                                // 🎯 ২ নম্বর ছবির সবুজ বক্স: সংকুচিত ও চার কোণা ক্রপ করা ভিডিও ফ্রেম
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(top = 4.dp, start = 6.dp, end = 4.dp, bottom = 4.dp)
                                        .clip(RoundedCornerShape(12.dp)) // 🎯 চার কোনা ক্রপ করা
                                        .background(Color.Black)
                                ) {
                                    // শুধু ভিডিও ফ্রেম স্ক্রোল ডাউন হবে
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
                                            isSidebarOpenState = true,
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
                                                if (targetIndex != -1) {
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

                                // =========================================================================
                                // 🎯 ২ নম্বর ছবির কালো ব্যাকগ্রাউন্ড এরিয়া (স্ক্রোল হবে না, ফিক্সড থাকবে)
                                // =========================================================================
                                currentReel?.let { activeReel ->
                                    DockedBottomControlBar(
                                        reel = activeReel,
                                        isLoggedIn = isLoggedIn,
                                        onRequireLogin = onRequireLogin,
                                        onToggleLike = { onToggleLike(activeReel) },
                                        onCommentClick = {
                                            activeCommentReel = activeReel
                                            onCommentsVisibilityChange(true)
                                        },
                                        onSaveClick = {
                                            coroutineScope.launch { repository.toggleSaveReel(activeReel.id) }
                                        },
                                        onShareClick = { onShareClick(activeReel) },
                                        onFollowClick = { onFollowToggle(activeReel.pageId, activeReel.userId) },
                                        onOpenPageProfile = {
                                            val targetPageId = if (activeReel.pageId > 0) activeReel.pageId else activeReel.userId
                                            onOpenPageProfile(targetPageId)
                                        },
                                        onHashtagClick = onHashtagClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .background(Color.Black) // 🎯 সম্পূর্ণ কালো ব্যাকগ্রাউন্ড
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // খ) ডান পাশ: ২ নম্বর ছবির ডকড অতি-চিকন সাইডবার (ভিডিওর সম্পূর্ণ বাইরে)
                            if (currentReel != null) {
                                ReelsPlaylistSidebar(
                                    isOpen = true,
                                    currentReel = currentReel,
                                    creatorReels = creatorReels,
                                    isPlaying = isAppInForeground && isCurrentTabActive,
                                    onTogglePlayPause = {},
                                    onSelectReel = { selectedReel ->
                                        val targetIndex = reelsList.indexOfFirst { it.id == selectedReel.id }
                                        if (targetIndex != -1) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(targetIndex) }
                                        }
                                    },
                                    onCloseSidebar = { onSidebarVisibilityChange(false) },
                                    modifier = Modifier
                                        .width(UltraSlimSidebarWidth)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            },
            commentsContent = {
                val currentReelForComment = activeCommentReel ?: currentReel
                if (currentReelForComment != null) {
                    InstagramCommentsSheet(
                        reelId = currentReelForComment.id,
                        targetCreatorName = currentReelForComment.pageName,
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

/**
 * 🔲 ২ নম্বর ছবির হুবহু ফিক্সড কালো ব্যাকগ্রাউন্ড কন্ট্রোল বার
 * (ভিডিও স্ক্রোল হলেও এটি নিজ জায়গায় স্থির থাকবে, শুধু টেক্সট ও লাইক সংখ্যা স্মুথলি আপডেট হবে)
 */
@Composable
private fun DockedBottomControlBar(
    reel: UserReelDto,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onToggleLike: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onFollowClick: () -> Unit,
    onOpenPageProfile: () -> Unit,
    onHashtagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val annotatedCaption = remember(reel.title, reel.description, reel.hashtags) {
        buildAnnotatedString {
            val fullText = buildString {
                if (!reel.title.isNullOrBlank()) append(reel.title)
                if (!reel.description.isNullOrBlank() && reel.description != reel.title) {
                    if (isNotEmpty()) append(" ")
                    append(reel.description)
                }
                if (!reel.hashtags.isNullOrBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(reel.hashtags.replace(",", " "))
                }
            }

            val words = fullText.split(" ")
            words.forEach { word ->
                if (word.startsWith("#") && word.length > 1) {
                    pushStringAnnotation(tag = "HASHTAG", annotation = word)
                    withStyle(style = SpanStyle(color = CyanBlue, fontWeight = FontWeight.Bold)) {
                        append("$word ")
                    }
                    pop()
                } else {
                    append("$word ")
                }
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ১. ক্যাপশন ও হ্যাশট্যাগ (ভিডিও পরিবর্তন হলে স্মুথলি ফেড ইন হবে)
        AnimatedContent(
            targetState = annotatedCaption,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "caption_anim"
        ) { caption ->
            if (caption.text.isNotBlank()) {
                ClickableText(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        caption.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                            .firstOrNull()?.let { annotation -> onHashtagClick(annotation.item) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ২. নিচের লাইন: [বামে প্রোফাইল + ৩ নম্বর ছবির ফলো বাটন] ----- [ডানে ফিক্সড আইকনগুলো]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 👤 বাম পাশ: অ্যাভাটার + নাম + ৩ নম্বর ছবির আউটলাইন ফলো বাটন
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onOpenPageProfile() }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=222838&color=fff")
                            .crossfade(true)
                            .build(),
                        contentDescription = reel.pageName,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = reel.pageName.ifBlank { reel.displayHandle },
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenPageProfile() }
                )

                // =========================================================================
                // 🎯 ৩ নম্বর ছবির হুবহু আউটলাইন ফলো বাটন (কোনো ব্যাকগ্রাউন্ড ছাড়া)
                // =========================================================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent, // 🎯 ব্যাকগ্রাউন্ড ছাড়া
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (reel.isFollowing) Color.White.copy(alpha = 0.45f) else Color.White // 🎯 সাদা বর্ডার
                    ),
                    modifier = Modifier.clickable {
                        if (!isLoggedIn) onRequireLogin() else onFollowClick()
                    }
                ) {
                    Text(
                        text = if (reel.isFollowing) "Following" else "Follow",
                        color = Color.White, // 🎯 সাদা টেক্সট
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 🔘 ডান পাশ: ২ নম্বর ছবির মতো হার্ট, কমেন্ট, স্টার/রিবন, পেপার প্লেন
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // লাইক
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (!isLoggedIn) onRequireLogin() else onToggleLike() }
                ) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (reel.isLiked) HeartRed else Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (reel.likesCount > 0) formatCompact(reel.likesCount) else "0",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                // কমেন্ট
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onCommentClick() }
                ) {
                    Icon(
                        imageVector = DouyinCommentIcon,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (reel.commentsCount > 0) formatCompact(reel.commentsCount.toLong()) else "0",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                // সেভ / বুকমার্ক
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { if (!isLoggedIn) onRequireLogin() else onSaveClick() }
                ) {
                    Icon(
                        imageVector = if (reel.isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (reel.isSaved) ActionGreen else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (reel.isSaved) "1" else "0",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                // শেয়ার
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onShareClick() }
                ) {
                    Icon(
                        imageVector = DouyinSharePlaneIcon,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (reel.sharesCount > 0) formatCompact(reel.sharesCount.toLong()) else "0",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // নিচে চিকন প্রোগ্রেস লাইন
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF222838))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .background(Brush.horizontalGradient(listOf(CyanBlue, ActionGreen)))
            )
        }
    }
}

private val DouyinCommentIcon: ImageVector by lazy {
    ImageVector.Builder("DouyinComment", 24.dp, 24.dp, 24f, 24f).path(
        stroke = SolidColor(Color.White), strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.5f, 13.5f)
        arcTo(8.5f, 8.5f, 0f, false, true, 12f, 20.5f)
        arcTo(8.5f, 8.5f, 0f, false, true, 7.5f, 19.3f)
        lineTo(3.5f, 20.5f)
        lineTo(4.7f, 16.5f)
        arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 13.5f)
        close()
    }.build()
}

private val DouyinSharePlaneIcon: ImageVector by lazy {
    ImageVector.Builder("DouyinSharePlane", 24.dp, 24.dp, 24f, 24f).path(
        stroke = SolidColor(Color.White), strokeLineWidth = 2.0f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(22f, 2f)
        lineTo(11f, 13f)
        moveTo(22f, 2f)
        lineTo(15f, 22f)
        lineTo(11f, 13f)
        lineTo(2f, 9f)
        close()
    }.build()
}

private fun formatCompact(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
