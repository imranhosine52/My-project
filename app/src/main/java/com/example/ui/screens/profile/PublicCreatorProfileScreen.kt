@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.PublicCreatorProfileDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val DarkCardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)
private val TikTokRed = Color(0xFFFE2C55)
private val ActionGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun PublicCreatorProfileScreen(
    pageId: Int,
    fromReelId: Int? = null,
    reelsViewModel: ReelsViewModel,
    isLoggedIn: Boolean = true,
    onRequireLogin: () -> Unit = {},
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    PublicCreatorProfileScreen(
        pageId = pageId.toLong(),
        fromReelId = fromReelId,
        reelsViewModel = reelsViewModel,
        isLoggedIn = isLoggedIn,
        onRequireLogin = onRequireLogin,
        onBackClick = onBackClick,
        onReelClick = onReelClick,
        onSearchClick = onSearchClick,
        modifier = modifier
    )
}

@Composable
fun PublicCreatorProfileScreen(
    pageId: Long,
    fromReelId: Int? = null,
    reelsViewModel: ReelsViewModel,
    isLoggedIn: Boolean = true,
    onRequireLogin: () -> Unit = {},
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }
    val authRepository = remember { AuthRepository(context) }

    val currentLoggedInUserId = remember(isLoggedIn) {
        if (!isLoggedIn) 0 else (authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0)
    }

    var profileData by remember { mutableStateOf<PublicCreatorProfileDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    var isFollowingState by remember { mutableStateOf(false) }
    var followersCountState by remember { mutableLongStateOf(0L) }

    val followButtonScale = remember { Animatable(1f) }
    val animatedFollowBtnColor by animateColorAsState(
        targetValue = if (isFollowingState) Color(0xFF222838) else TikTokRed,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "follow_btn_color"
    )

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val gridState = rememberLazyGridState()

    var showTopActionMenu by remember { mutableStateOf(false) }
    var highlightedJustWatchedId by remember { mutableStateOf(fromReelId) }

    fun loadProfileData(force: Boolean = false) {
        if (!force && profileData == null) isLoading = true
        coroutineScope.launch {
            val result = repository.getPublicCreatorProfile(pageId)
            isLoading = false
            isRefreshing = false
            if (result.isSuccess) {
                val data = result.getOrNull()
                profileData = data

                // 🎯 লগআউট থাকলে অথবা আইডি না থাকলে নিশ্চিতভাবে false হবে
                val effectiveIsFollowed = if (!isLoggedIn || currentLoggedInUserId <= 0) {
                    false
                } else {
                    data?.isFollowing ?: false
                }

                isFollowingState = effectiveIsFollowed
                followersCountState = data?.followersCount ?: 0L
            }
        }
    }

    LaunchedEffect(pageId, isLoggedIn, currentLoggedInUserId) {
        loadProfileData()
    }

    // 🎯 ভিডিও দেখে ব্যাক করে প্রোফাইলে এলেই লাইভ ভিউজ ও তথ্য সাথে সাথে আপডেট হবে
    DisposableEffect(lifecycleOwner, pageId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadProfileData(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        onBackClick()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                loadProfileData(force = true)
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && profileData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.5.dp)
                }
            } else if (profileData != null) {
                val profile = profileData!!
                val isOwnProfile = currentLoggedInUserId > 0 && (currentLoggedInUserId == profile.userId || currentLoggedInUserId.toLong() == profile.pageId)
                val publicShareUrl = "https://playdramaflix.com/page/${profile.handle.removePrefix("@")}"

                val followersText = if (followersCountState > 0L) followersCountState.toString() else profile.formattedFollowers
                val followingText = profile.formattedFollowing
                val likesText = profile.formattedLikes

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // =========================================================================
                    // 🔝 ১. কভার ব্যানার ও টপ বার
                    // =========================================================================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .background(Color(0xFF1E2430))
                    ) {
                        if (!profile.cover.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(profile.cover)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(65.dp)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.50f), Color.Transparent)))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = onSearchClick,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.4f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showTopActionMenu = true },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.4f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showTopActionMenu,
                                        onDismissRequest = { showTopActionMenu = false },
                                        modifier = Modifier.background(DarkCardBg).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Share Profile", color = Color.White, fontSize = 13.5.sp) },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = ActionGreen) },
                                            onClick = {
                                                showTopActionMenu = false
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "Check out ${profile.pageName} on DramaFlix:\n$publicShareUrl")
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy Profile Link", color = Color.White, fontSize = 13.5.sp) },
                                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = CyanAccent) },
                                            onClick = {
                                                showTopActionMenu = false
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Profile Link", publicShareUrl))
                                                Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // 👤 ২. প্রোফাইল হেডার তথ্য ও মেট্রিক্স
                    // =========================================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(y = (-28).dp)
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2838))
                                    .border(2.5.dp, PureBlack, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(profile.avatar ?: "https://ui-avatars.com/api/?name=${profile.pageName}&background=00E676&color=000")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = profile.pageName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Row(
                                modifier = Modifier.weight(1f).padding(start = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileMetricItem(count = followersText, label = "Followers")
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(BorderColor))
                                ProfileMetricItem(count = followingText, label = "Following")
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(BorderColor))
                                ProfileMetricItem(count = likesText, label = "Likes")
                            }
                        }

                        // নাম, হ্যান্ডেল ও অ্যাকশন বাটন
                        Column(
                            modifier = Modifier.fillMaxWidth().offset(y = (-18).dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = profile.pageName,
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = ActionGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Username", profile.displayHandle))
                                        Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(profile.displayHandle, color = TextMuted, fontSize = 12.5.sp)
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = TextMuted, modifier = Modifier.size(13.dp))
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF17202A),
                                    border = BorderStroke(0.6.dp, BorderColor)
                                ) {
                                    Text(
                                        text = "🎭 ${profile.category ?: "Entertainment"}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (!profile.bio.isNullOrBlank()) {
                                Text(
                                    text = profile.bio,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 3,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isOwnProfile) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E2638),
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier.fillMaxWidth().height(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("Your Channel Profile", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (!isLoggedIn) {
                                                onRequireLogin()
                                            } else {
                                                coroutineScope.launch {
                                                    followButtonScale.animateTo(0.90f, tween(70))
                                                    followButtonScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                }

                                                val newState = !isFollowingState
                                                isFollowingState = newState
                                                followersCountState += if (newState) 1 else -1

                                                coroutineScope.launch {
                                                    val res = repository.toggleFollowPage(profile.pageId, profile.userId)
                                                    if (res.isFailure) {
                                                        isFollowingState = !newState
                                                        followersCountState += if (newState) -1 else 1
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = animatedFollowBtnColor),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.weight(1f).height(38.dp).scale(followButtonScale.value)
                                    ) {
                                        AnimatedContent(
                                            targetState = isFollowingState,
                                            transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) },
                                            label = "follow_text_anim"
                                        ) { following ->
                                            Text(
                                                text = if (following) "Following ✓" else "+ Follow",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E2638),
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier.size(width = 44.dp, height = 38.dp).clickable {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Check out ${profile.pageName} on PlayDramaFlix:\n$publicShareUrl")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // =========================================================================
                        // 📑 ৩. TabRow (Reels ও Series)
                        // =========================================================================
                        TabRow(
                            selectedTabIndex = pagerState.currentPage,
                            containerColor = PureBlack,
                            contentColor = Color.White,
                            divider = { HorizontalDivider(color = BorderColor, thickness = 0.8.dp) },
                            indicator = { tabPositions ->
                                if (pagerState.currentPage < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                        color = Color.White,
                                        height = 2.dp
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Tab(
                                selected = pagerState.currentPage == 0,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                text = {
                                    Text(
                                        text = "Reels (${profile.reels.size})",
                                        fontSize = 13.5.sp,
                                        fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (pagerState.currentPage == 0) Color.White else TextMuted
                                    )
                                }
                            )
                            Tab(
                                selected = pagerState.currentPage == 1,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                text = {
                                    Text(
                                        text = "Series (${profile.playlists.size})",
                                        fontSize = 13.5.sp,
                                        fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (pagerState.currentPage == 1) Color.White else TextMuted
                                    )
                                }
                            )
                        }
                    }

                    // =========================================================================
                    // 🎬 ৪. ৩-কলাম গ্রিড ও ভিডিও ভিউজ ডিসপ্লে
                    // =========================================================================
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 350.dp, max = 2500.dp)
                    ) { pageIndex ->
                        when (pageIndex) {
                            // TAB 0: REELS
                            0 -> {
                                if (profile.reels.isEmpty()) {
                                    EmptyProfileView("No reels published yet")
                                } else {
                                    LazyVerticalGrid(
                                        state = gridState,
                                        columns = GridCells.Fixed(3),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        contentPadding = PaddingValues(top = 4.dp, bottom = 60.dp, start = 2.dp, end = 2.dp),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 2200.dp)
                                    ) {
                                        items(profile.reels, key = { it.id }) { reel ->
                                            val isJustWatched = (highlightedJustWatchedId != null && highlightedJustWatchedId == reel.id)

                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(0.72f)
                                                    .background(DarkCardBg)
                                                    .clickable {
                                                        val singleReel = reel.toUserReelDto(profile.pageName, profile.displayHandle, profile.avatar)
                                                        onReelClick(singleReel)
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl,
                                                    contentDescription = reel.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )

                                                Box(
                                                    modifier = Modifier.fillMaxSize().background(
                                                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))
                                                    )
                                                )

                                                Row(
                                                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 4.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text("▷", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text(reel.formattedViews, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }

                                                if (isJustWatched) {
                                                    Surface(
                                                        shape = RoundedCornerShape(3.dp),
                                                        color = Color(0xFF00E5FF),
                                                        modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
                                                    ) {
                                                        Text("Watched", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // TAB 1: SERIES
                            1 -> {
                                if (profile.playlists.isEmpty()) {
                                    EmptyProfileView("No series playlists created yet")
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(top = 6.dp, bottom = 60.dp, start = 6.dp, end = 6.dp),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 2200.dp)
                                    ) {
                                        items(profile.playlists, key = { it.id }) { playlist ->
                                            val formattedEpsText = remember(playlist.totalEpisodes) {
                                                String.format(Locale.US, "Eps %02d", playlist.totalEpisodes)
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        coroutineScope.launch {
                                                            val res = repository.getPlaylistReels(playlist.id)
                                                            val episodes = res.getOrDefault(emptyList())

                                                            if (episodes.isNotEmpty()) {
                                                                onReelClick(episodes.first())
                                                            } else {
                                                                val fallbackReel = UserReelDto(
                                                                    id = playlist.id,
                                                                    pageId = profile.pageId.toInt(),
                                                                    userId = profile.userId,
                                                                    pageName = profile.pageName,
                                                                    handle = profile.displayHandle,
                                                                    pageAvatar = profile.avatar,
                                                                    title = playlist.title,
                                                                    playlistId = playlist.id,
                                                                    playlistTitle = playlist.title,
                                                                    rawEpisodeNum = 1,
                                                                    videoUrl = playlist.coverUrl ?: profile.cover ?: ""
                                                                )
                                                                onReelClick(fallbackReel)
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(0.72f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(DarkCardBg)
                                                        .border(0.8.dp, BorderColor, RoundedCornerShape(8.dp))
                                                ) {
                                                    AsyncImage(
                                                        model = playlist.coverUrl?.takeIf { it.isNotBlank() } ?: profile.cover,
                                                        contentDescription = playlist.title,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )

                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color.Black.copy(alpha = 0.70f),
                                                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = formattedEpsText,
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = playlist.title,
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 4.dp, start = 1.dp, end = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetricItem(count: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = count, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyProfileView(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = TextMuted, fontSize = 13.sp)
    }
}
