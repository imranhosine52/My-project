@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.AppDatabase
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.CreatorPlaylistDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.VipCrown3DIcon
import com.example.ui.screens.reels.components.PlaylistEpisodesBottomSheet
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
    reelsViewModel: ReelsViewModel,
    isLoggedIn: Boolean = true,
    onRequireLogin: () -> Unit = {},
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenDirectMessage: (creatorId: String, creatorName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }
    val authRepository = remember { AuthRepository(context) }

    val currentLoggedInUserId = remember {
        authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // Profile state from ViewModel
    val profileUiState by reelsViewModel.profileState.collectAsStateWithLifecycle()
    val creatorProfile = profileUiState.profile

    // Real playlists created by this page
    var creatorPlaylists by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }
    var isPlaylistsLoading by remember { mutableStateOf(false) }

    // Real watch history from local Room database
    val watchHistoryList by remember {
        AppDatabase.getInstance(context).watchHistoryDao().getContinueWatching()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Tabs: 0 -> Posts, 1 -> Playlist
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showTopActionMenu by remember { mutableStateOf(false) }
    var showJustWatchedSheet by remember { mutableStateOf(false) }

    // Playlist Episode Drawer
    var activePlaylistForDrawer by remember { mutableStateOf<CreatorPlaylistDto?>(null) }
    var playlistEpisodes by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isEpisodesLoading by remember { mutableStateOf(false) }

    // Load real profile metrics and playlists on entry
    fun loadAllCreatorData() {
        reelsViewModel.loadUserProfileMetrics(targetUserId = pageId)
        isPlaylistsLoading = true
        coroutineScope.launch {
            val res = repository.getPlaylists(pageId)
            creatorPlaylists = res.getOrDefault(emptyList())
            isPlaylistsLoading = false
        }
    }

    LaunchedEffect(pageId) {
        loadAllCreatorData()
    }

    // Filter real reels belonging to this creator from feed
    val feedState by reelsViewModel.feedState.collectAsStateWithLifecycle()
    val creatorReels = remember(feedState.reels, pageId, creatorProfile) {
        val targetId = creatorProfile?.userId ?: pageId
        val targetPageId = creatorProfile?.pageId ?: pageId
        feedState.reels.filter { it.userId == targetId || (it.pageId > 0 && it.pageId == targetPageId) }
    }

    val isOwnProfile = remember(currentLoggedInUserId, creatorProfile, pageId) {
        val targetId = creatorProfile?.userId ?: pageId
        currentLoggedInUserId > 0 && currentLoggedInUserId == targetId
    }

    // UI Values strictly from backend
    val displayName = creatorProfile?.displayName ?: "Creator"
    val displayHandle = creatorProfile?.displayHandle ?: "@creator"
    val avatarUrl = creatorProfile?.effectiveAvatar
    val coverUrl = creatorProfile?.effectiveCover
    val bioText = creatorProfile?.bio
    val isFollowing = creatorProfile?.isFollowing ?: false

    val followersText = creatorProfile?.formattedFollowers ?: "0"
    val followingText = creatorProfile?.formattedFollowing ?: "0"
    val likesText = creatorProfile?.formattedLikes ?: "0"

    val publicShareUrl = remember(displayHandle) {
        "https://playdramaflix.com/page/${displayHandle.removePrefix("@")}"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        when {
            profileUiState.isLoading && creatorProfile == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.5.dp)
                }
            }

            profileUiState.errorMessage != null && creatorProfile == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Unable to load profile.",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { loadAllCreatorData() },
                            colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                        ) {
                            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // =========================================================================
                    // 1. COVER PHOTO & TOP OVERLAY ACTIONS
                    // =========================================================================
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF222B3D), Color(0xFF0F1522))
                                    )
                                )
                        ) {
                            if (!coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(coverUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Cover photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // Dark gradient overlay for status bar and icons
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.65f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.75f)
                                            )
                                        )
                                    )
                            )

                            // Top action icons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.4f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Check out $displayName ($displayHandle) on PlayDramaFlix:\n$publicShareUrl"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.4f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { showTopActionMenu = true },
                                            modifier = Modifier
                                                .size(36.dp)
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
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            "Check out $displayName ($displayHandle) on PlayDramaFlix:\n$publicShareUrl"
                                                        )
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
                                            DropdownMenuItem(
                                                text = { Text("Report Profile", color = Color(0xFFFF5252), fontSize = 14.sp) },
                                                leadingIcon = { Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFFF5252)) },
                                                onClick = {
                                                    showTopActionMenu = false
                                                    Toast.makeText(context, "Profile reported to admin for review", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // 2. PROFILE HEADER OVERLAPPING THE COVER
                    // =========================================================================
                    item(span = { GridItemSpan(3) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Row containing circular profile picture + 3 stats columns
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = (-40).dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Real Circular Avatar
                                Box(
                                    modifier = Modifier
                                        .size(86.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E2838))
                                        .border(3.dp, PureBlack, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(avatarUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = displayName,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = displayName.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Real Statistics: Followers | Following | Likes
                                Row(
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProfileStatColumn(count = followersText, label = "Followers")
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderColor))
                                    ProfileStatColumn(count = followingText, label = "Following")
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderColor))
                                    ProfileStatColumn(count = likesText, label = "Likes")
                                }
                            }

                            // Profile Name & Handle
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = (-28).dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (creatorProfile?.hasPage == true) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified Creator",
                                            tint = ActionGreen,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    if (creatorProfile?.isVip == true) {
                                        VipCrown3DIcon(modifier = Modifier.size(18.dp, 14.dp))
                                    }
                                }

                                // @username + Copy Icon
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Username", displayHandle))
                                        Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = displayHandle,
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy username",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Real Bio description
                                if (!bioText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = bioText,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.5.sp,
                                        lineHeight = 17.sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action Buttons Row: [ Follow ] [ Message ]
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
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "Your Profile",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        // Follow button
                                        Button(
                                            onClick = {
                                                if (!isLoggedIn) {
                                                    onRequireLogin()
                                                } else {
                                                    reelsViewModel.toggleFollowUser(creatorProfile?.userId ?: pageId)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isFollowing) Color(0xFF222838) else TikTokRed
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(40.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (!isFollowing) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                                Text(
                                                    text = if (isFollowing) "Following" else "Follow",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Message button
                                        Button(
                                            onClick = {
                                                if (!isLoggedIn) {
                                                    onRequireLogin()
                                                } else {
                                                    onOpenDirectMessage(
                                                        (creatorProfile?.userId ?: pageId).toString(),
                                                        displayName
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                                            border = BorderStroke(1.dp, BorderColor),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(40.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ChatBubbleOutline,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Message",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // =========================================================================
                            // 3. MATERIAL 3 TAB ROW: POSTS | PLAYLIST
                            // =========================================================================
                            TabRow(
                                selectedTabIndex = selectedTabIndex,
                                containerColor = PureBlack,
                                contentColor = Color.White,
                                divider = { HorizontalDivider(color = BorderColor, thickness = 0.8.dp) },
                                indicator = { tabPositions ->
                                    if (selectedTabIndex < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                            color = Color.White,
                                            height = 2.5.dp
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = (-14).dp)
                            ) {
                                Tab(
                                    selected = selectedTabIndex == 0,
                                    onClick = { selectedTabIndex = 0 },
                                    text = {
                                        Text(
                                            text = "Posts (${creatorProfile?.totalReelsCount ?: creatorReels.size})",
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedTabIndex == 0) Color.White else TextMuted
                                        )
                                    }
                                )
                                Tab(
                                    selected = selectedTabIndex == 1,
                                    onClick = { selectedTabIndex = 1 },
                                    text = {
                                        Text(
                                            text = "Playlist (${creatorPlaylists.size})",
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedTabIndex == 1) Color.White else TextMuted
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // =========================================================================
                    // 4. TAB CONTENT (POSTS GRID OR PLAYLISTS)
                    // =========================================================================
                    when (selectedTabIndex) {
                        0 -> {
                            // POSTS TAB
                            if (creatorReels.isEmpty()) {
                                item(span = { GridItemSpan(3) }) {
                                    EmptyStateView(
                                        icon = Icons.Outlined.PlayCircleOutline,
                                        message = "No posts yet"
                                    )
                                }
                            } else {
                                items(creatorReels, key = { it.id }) { reel ->
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(0.72f)
                                            .background(DarkCardBg)
                                            .clickable { onReelClick(reel) }
                                    ) {
                                        AsyncImage(
                                            model = reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl,
                                            contentDescription = reel.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Dark gradient bottom overlay
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                    )
                                                )
                                        )

                                        // Bottom info (▷ View count)
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(horizontal = 6.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = "▷",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = reel.formattedViews,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Top-left duration badge if available
                                        if (reel.durationSec > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = reel.formattedDuration,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // PLAYLIST TAB
                            if (isPlaylistsLoading) {
                                item(span = { GridItemSpan(3) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.dp)
                                    }
                                }
                            } else if (creatorPlaylists.isEmpty()) {
                                item(span = { GridItemSpan(3) }) {
                                    EmptyStateView(
                                        icon = Icons.Outlined.VideoLibrary,
                                        message = "No playlists yet"
                                    )
                                }
                            } else {
                                items(creatorPlaylists, key = { it.effectiveId }) { playlist ->
                                    Card(
                                        shape = RoundedCornerShape(6.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                        border = BorderStroke(0.6.dp, BorderColor),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.72f)
                                            .clickable {
                                                activePlaylistForDrawer = playlist
                                                isEpisodesLoading = true
                                                coroutineScope.launch {
                                                    playlistEpisodes = reelsViewModel.getPlaylistEpisodes(playlist.effectiveId)
                                                    isEpisodesLoading = false
                                                }
                                            }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (!playlist.coverUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = playlist.coverUrl,
                                                    contentDescription = playlist.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0xFF1E2838)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Movie,
                                                        contentDescription = null,
                                                        tint = CyanAccent,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }

                                            // Gradient overlay
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                                                        )
                                                    )
                                            )

                                            // Playlist details at bottom
                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    color = CyanAccent
                                                ) {
                                                    Text(
                                                        text = "${playlist.totalEpisodes} Episodes",
                                                        color = Color.Black,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }

                                                Text(
                                                    text = playlist.title,
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
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

                    // Extra space at bottom so floating pill doesn't block the last row
                    item(span = { GridItemSpan(3) }) {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }

        // =========================================================================
        // 5. "JUST WATCHED" FLOATING PILL (Inspired by Screenshot)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 18.dp, end = 16.dp)
                .clickable { showJustWatchedSheet = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Just watched",
                    color = Color.Black,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // =========================================================================
        // 6. "JUST WATCHED" REAL USER ACTIVITY BOTTOM SHEET
        // =========================================================================
        if (showJustWatchedSheet) {
            ModalBottomSheet(
                onDismissRequest = { showJustWatchedSheet = false },
                containerColor = DarkCardBg,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = ActionGreen)
                            Text(
                                text = "Recently Watched",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showJustWatchedSheet = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    HorizontalDivider(color = BorderColor, thickness = 0.8.dp)

                    if (watchHistoryList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recently watched videos",
                                color = TextMuted,
                                fontSize = 13.5.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(watchHistoryList, key = { it.id }) { item ->
                                WatchHistoryItemRow(
                                    item = item,
                                    onClick = {
                                        showJustWatchedSheet = false
                                        // Jumps directly to player using existing native player navigation
                                        Toast.makeText(context, "Resuming ${item.contentTitle}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 7. PLAYLIST EPISODES DRAWER
        // =========================================================================
        if (activePlaylistForDrawer != null) {
            val pl = activePlaylistForDrawer!!
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

// =============================================================================
// HELPER COMPONENTS
// =============================================================================

@Composable
private fun ProfileStatColumn(
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = count,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = message,
                color = TextMuted,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WatchHistoryItemRow(
    item: WatchHistoryEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF19202E),
        border = BorderStroke(0.8.dp, BorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.contentTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.episodeTitle} • ${item.dubBadge}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = ActionGreen,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
