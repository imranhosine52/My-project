@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.AppDatabase
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.CreatorPlaylistDto
import com.example.data.model.PublicCreatorProfileDto
import com.example.data.model.PublicPlaylistSummaryDto
import com.example.data.model.PublicReelSummaryDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.screens.reels.components.PlaylistEpisodesBottomSheet
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.launch

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
    onOpenDirectMessage: (creatorId: String, creatorName: String) -> Unit,
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
        onOpenDirectMessage = onOpenDirectMessage,
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
    onOpenDirectMessage: (creatorId: String, creatorName: String) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }
    val authRepository = remember { AuthRepository(context) }

    val currentLoggedInUserId = remember {
        authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    var profileData by remember { mutableStateOf<PublicCreatorProfileDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    var isFollowingState by remember { mutableStateOf(false) }
    var followersCountState by remember { mutableLongStateOf(0L) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val gridState = rememberLazyGridState()

    var showTopActionMenu by remember { mutableStateOf(false) }
    var highlightedJustWatchedId by remember { mutableStateOf(fromReelId) }

    var previewingReel by remember { mutableStateOf<PublicReelSummaryDto?>(null) }

    var activePlaylistForDrawer by remember { mutableStateOf<PublicPlaylistSummaryDto?>(null) }
    var playlistEpisodes by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isEpisodesLoading by remember { mutableStateOf(false) }

    fun loadProfileData(force: Boolean = false) {
        if (!force) isLoading = true
        coroutineScope.launch {
            val result = repository.getPublicCreatorProfile(pageId)
            isLoading = false
            isRefreshing = false
            if (result.isSuccess) {
                val data = result.getOrNull()
                profileData = data
                isFollowingState = data?.isFollowing ?: false
                followersCountState = data?.followersCount ?: 0L
            }
        }
    }

    LaunchedEffect(pageId) {
        loadProfileData()
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
                    // 1. TOP BANNER & ACTIONS (৩ নম্বর ছবির মতো)
                    // =========================================================================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
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
                                .height(60.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(0.40f), Color.Transparent)
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { onBackClick() }
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onSearchClick() }
                                )

                                Box {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { showTopActionMenu = true }
                                    )

                                    DropdownMenu(
                                        expanded = showTopActionMenu,
                                        onDismissRequest = { showTopActionMenu = false },
                                        modifier = Modifier
                                            .background(DarkCardBg)
                                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Share Profile", color = Color.White, fontSize = 14.sp) },
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
                                            text = { Text("Copy Profile Link", color = Color.White, fontSize = 14.sp) },
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
                    // 2. PROFILE HEADER INFO
                    // =========================================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-32).dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
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
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .weight(1f),
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

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-20).dp),
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

                            Spacer(modifier = Modifier.height(10.dp))

                            // ৪ নম্বর ছবির মতো ফলো ও মেসেজ বাটন
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
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowingState) Color(0xFF222838) else TikTokRed
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Text(
                                            text = if (isFollowingState) "Following" else "+ Follow",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1E2638),
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 38.dp)
                                            .clickable {
                                                if (!isLoggedIn) onRequireLogin()
                                                else onOpenDirectMessage(profile.userId.toString(), profile.pageName)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Message",
                                                tint = Color.White,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // TabRow
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
                            modifier = Modifier.fillMaxWidth().offset(y = (-10).dp)
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

                    // Horizontal Pager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 400.dp, max = 1800.dp)
                    ) { pageIndex ->
                        when (pageIndex) {
                            0 -> {
                                if (profile.reels.isEmpty()) {
                                    EmptyProfileView("No reels published yet")
                                } else {
                                    LazyVerticalGrid(
                                        state = gridState,
                                        columns = GridCells.Fixed(3),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 1600.dp)
                                            .padding(bottom = 60.dp)
                                    ) {
                                        items(profile.reels, key = { it.id }) { reel ->
                                            val isJustWatched = (highlightedJustWatchedId != null && highlightedJustWatchedId == reel.id)

                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(0.72f)
                                                    .background(DarkCardBg)
                                                    .pointerInput(reel.id) {
                                                        detectTapGestures(
                                                            onTap = {
                                                                val singleReel = reel.toUserReelDto(profile.pageName, profile.displayHandle, profile.avatar)
                                                                onReelClick(singleReel)
                                                            },
                                                            onLongPress = {
                                                                previewingReel = reel
                                                            }
                                                        )
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
                                                                listOf(Color.Transparent, Color.Black.copy(0.80f))
                                                            )
                                                        )
                                                )

                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Text("▷", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                    Text(reel.formattedViews, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                                }

                                                if (isJustWatched) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF00E5FF),
                                                        modifier = Modifier
                                                            .align(Alignment.TopStart)
                                                            .padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "Just watched",
                                                            color = Color.Black,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                if (profile.playlists.isEmpty()) {
                                    EmptyProfileView("No series playlists created yet")
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 1600.dp)
                                            .padding(bottom = 60.dp)
                                    ) {
                                        items(profile.playlists, key = { it.id }) { playlist ->
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                                border = BorderStroke(0.6.dp, BorderColor),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(0.72f)
                                                    .clickable {
                                                        activePlaylistForDrawer = playlist
                                                        isEpisodesLoading = true
                                                        coroutineScope.launch {
                                                            playlistEpisodes = repository.getPlaylistReels(playlist.id).getOrDefault(emptyList())
                                                            isEpisodesLoading = false
                                                        }
                                                    }
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    AsyncImage(
                                                        model = playlist.coverUrl?.takeIf { it.isNotBlank() } ?: profile.cover,
                                                        contentDescription = playlist.title,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(
                                                                Brush.verticalGradient(
                                                                    listOf(Color.Transparent, Color.Black.copy(0.92f))
                                                                )
                                                            )
                                                    )

                                                    Column(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .padding(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = CyanAccent
                                                        ) {
                                                            Text(
                                                                text = "${playlist.totalEpisodes} Episodes",
                                                                color = Color.Black,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                            )
                                                        }

                                                        Text(
                                                            text = playlist.title,
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
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

        // Just Watched Floating Button
        if (profileData != null && fromReelId != null) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp, end = 16.dp)
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                            val idx = profileData!!.reels.indexOfFirst { it.id == fromReelId }
                            if (idx != -1) {
                                highlightedJustWatchedId = fromReelId
                                gridState.animateScrollToItem(idx)
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Just watched", color = Color.Black, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Video Long Press Preview Dialog
        if (previewingReel != null && profileData != null) {
            VideoLongPressPreviewDialog(
                reel = previewingReel!!,
                pageName = profileData!!.pageName,
                pageAvatar = profileData!!.avatar,
                onDismiss = { previewingReel = null }
            )
        }

        // Playlist Episodes Drawer
        if (activePlaylistForDrawer != null) {
            val pl = activePlaylistForDrawer!!
            val playlistDto = CreatorPlaylistDto(
                id = pl.id,
                title = pl.title,
                description = pl.description,
                coverUrl = pl.coverUrl,
                rawTotalEpisodes = pl.totalEpisodes
            )
            PlaylistEpisodesBottomSheet(
                seriesTitle = pl.title,
                currentReelId = 0,
                episodes = playlistEpisodes,
                isLoading = isEpisodesLoading,
                onEpisodeClick = { targetReel ->
                    activePlaylistForDrawer = null
                    onReelClick(targetReel)
                },
                onDismiss = { activePlaylistForDrawer = null }
            )
        }
    }
}

@Composable
private fun VideoLongPressPreviewDialog(
    reel: PublicReelSummaryDto,
    pageName: String,
    pageAvatar: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(reel.videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, Color(0xFF263346), RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2838))
                    ) {
                        AsyncImage(
                            model = pageAvatar,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pageName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Verified, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(13.dp))
                        }
                        Text("Original audio", color = TextMuted, fontSize = 10.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181C28))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "Liked!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Like", color = Color.White, fontSize = 13.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Comment", color = Color.White, fontSize = 13.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Watch this reel: ${reel.videoUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Reel"))
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Share", color = Color.White, fontSize = 13.sp)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = TextMuted, fontSize = 13.sp)
    }
}
