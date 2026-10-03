@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.reels.tabs

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

private val CyanBlue = Color(0xFF00E5FF)
private val ActionGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8692A6)
private val HeartRed = Color(0xFFFF2A4B)
val UltraSlimSidebarWidth = 42.dp

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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val sidebarWidthPx = with(density) { UltraSlimSidebarWidth.toPx() }

    var activeCommentReel by remember { mutableStateOf<UserReelDto?>(null) }

    // 🎯 লোকাল সেভ ট্র্যাকার (যাতে সাইডবার মোডে সেভ বাটন ১০০% কাজ করে)
    val localSavedMap = remember { mutableStateMapOf<Int, Boolean>() }

    // 🎯 হাতের আঙুলের সাথে সাইডবার আসার স্মুথ ড্র্যাগ ট্র্যাকার (0f = বন্ধ, 1f = সম্পূর্ণ খোলা)
    val sidebarProgress = remember { Animatable(if (isSidebarOpen) 1f else 0f) }

    LaunchedEffect(isSidebarOpen) {
        val target = if (isSidebarOpen) 1f else 0f
        if (sidebarProgress.targetValue != target) {
            sidebarProgress.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val currentProgress = sidebarProgress.value
    val isSidebarActive = currentProgress > 0.05f

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

    // ড্র্যাগ করে সাইডবার খোলার জেসচার হ্যান্ডলার
    val horizontalDragState = rememberDraggableState { deltaPx ->
        if (!isCommentsOpen) {
            coroutineScope.launch {
                val deltaProgress = -deltaPx / sidebarWidthPx
                val newProgress = (sidebarProgress.value + deltaProgress).coerceIn(0f, 1f)
                sidebarProgress.snapTo(newProgress)
            }
        }
    }

    if (reelsList.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
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
                        // 🎯 হাতের আঙুলের সাথে সাইডবার স্মুথলি ড্র্যাগ করার জেসচার
                        .draggable(
                            state = horizontalDragState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity ->
                                coroutineScope.launch {
                                    val shouldOpen = when {
                                        velocity < -400f -> true
                                        velocity > 400f -> false
                                        else -> sidebarProgress.value > 0.40f
                                    }
                                    val target = if (shouldOpen) 1f else 0f
                                    sidebarProgress.animateTo(
                                        targetValue = target,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    onSidebarVisibilityChange(shouldOpen)
                                }
                            }
                        )
                ) {
                    // =========================================================================
                    // 🚀 একক ইউনিফাইড পেজার (ভিডিও কখনোই রিস্টার্ট বা রি-লোড হবে না!)
                    // =========================================================================
                    val animatedEndPadding = (UltraSlimSidebarWidth * currentProgress)
                    val animatedCornerRadius = (12.dp * currentProgress)
                    val animatedBottomSpace = (80.dp * currentProgress)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = animatedEndPadding)
                    ) {
                        // 📺 সংকুচিত ভিডিও এরিয়া
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(
                                    top = (4.dp * currentProgress),
                                    start = (6.dp * currentProgress),
                                    end = (4.dp * currentProgress),
                                    bottom = (4.dp * currentProgress)
                                )
                                .clip(RoundedCornerShape(animatedCornerRadius))
                                .background(Color.Black)
                        ) {
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
                                    isSidebarOpenState = isSidebarActive,
                                    onSidebarStateChange = { open ->
                                        onSidebarVisibilityChange(open)
                                        coroutineScope.launch {
                                            sidebarProgress.animateTo(if (open) 1f else 0f)
                                        }
                                    },
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
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(targetIndex)
                                            }
                                        }
                                    },
                                    // 🎯 অটোমেটিক পরবর্তী ভিডিও চলা (কোনো বাফারিং ছাড়া)
                                    onVideoCompleteAutoPlayNext = {
                                        if (pagerState.currentPage < reelsList.size - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    page = pagerState.currentPage + 1,
                                                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // =========================================================================
                        // 🎯 ২ নম্বর ছবির ফিক্সড কালো ব্যাকগ্রাউন্ড এরিয়া (সাইডবার সক্রিয় হলে মসৃণভাবে দৃশ্যমান হবে)
                        // =========================================================================
                        if (currentProgress > 0.05f && currentReel != null) {
                            val activeSaved = localSavedMap[currentReel.id] ?: currentReel.isSaved

                            DockedBottomControlBar(
                                reel = currentReel,
                                isSaved = activeSaved,
                                isLoggedIn = isLoggedIn,
                                onRequireLogin = onRequireLogin,
                                onToggleLike = { onToggleLike(currentReel) },
                                onCommentClick = {
                                    activeCommentReel = currentReel
                                    onCommentsVisibilityChange(true)
                                },
                                // 🎯 সেভ বাটন ১০০% ফিক্স করা হলো
                                onSaveClick = {
                                    if (!isLoggedIn) {
                                        onRequireLogin()
                                    } else {
                                        val newState = !activeSaved
                                        localSavedMap[currentReel.id] = newState
                                        Toast.makeText(
                                            context,
                                            if (newState) "Saved to your list" else "Removed from saved",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        coroutineScope.launch {
                                            val res = repository.toggleSaveReel(currentReel.id)
                                            if (res.isFailure) {
                                                localSavedMap[currentReel.id] = activeSaved
                                            }
                                        }
                                    }
                                },
                                onShareClick = { onShareClick(currentReel) },
                                onFollowClick = { onFollowToggle(currentReel.pageId, currentReel.userId) },
                                onOpenPageProfile = {
                                    val targetPageId = if (currentReel.pageId > 0) currentReel.pageId else currentReel.userId
                                    onOpenPageProfile(targetPageId)
                                },
                                onHashtagClick = onHashtagClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .background(Color.Black)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // =========================================================================
                    // 🎯 ডানপাশের আল্ট্রা-চিকন সাইডবার (হাতের আঙুলের সাথে স্মুথলি আসবে)
                    // =========================================================================
                    if (currentReel != null) {
                        val sidebarOffset = ((1f - currentProgress) * sidebarWidthPx).roundToInt()

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(UltraSlimSidebarWidth)
                                .fillMaxHeight()
                                .offset { IntOffset(sidebarOffset, 0) }
                        ) {
                            ReelsPlaylistSidebar(
                                isOpen = currentProgress > 0.05f,
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
                                onCloseSidebar = {
                                    coroutineScope.launch {
                                        sidebarProgress.animateTo(0f)
                                        onSidebarVisibilityChange(false)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
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
 * 🔲 ফিক্সড কালো ব্যাকগ্রাউন্ড কন্ট্রোল বার
 */
@Composable
private fun DockedBottomControlBar(
    reel: UserReelDto,
    isSaved: Boolean,
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
        AnimatedContent(
            targetState = annotatedCaption,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "docked_caption_anim"
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (reel.isFollowing) Color.White.copy(alpha = 0.45f) else Color.White
                    ),
                    modifier = Modifier.clickable {
                        if (!isLoggedIn) onRequireLogin() else onFollowClick()
                    }
                ) {
                    Text(
                        text = if (reel.isFollowing) "Following" else "Follow",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // লাইক বাটন
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

                // কমেন্ট বাটন
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

                // 🎯 সেভ / বুকমার্ক বাটন (১০০% কার্যকর)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onSaveClick() }
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (isSaved) ActionGreen else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (isSaved) "1" else "0",
                        color = if (isSaved) ActionGreen else Color.White,
                        fontSize = 10.sp
                    )
                }

                // শেয়ার বাটন
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
