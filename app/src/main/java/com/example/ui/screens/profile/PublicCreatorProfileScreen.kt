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
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.AppDatabase
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.CreatorPlaylistDto
import com.example.data.model.PublicCreatorProfileDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.VipCrown3DIcon
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

/**
 * 🎯 Int পেজ আইডি সমর্থনকারী ওভারলোড (MainActivity এর সাথে ১০০% কম্প্যাটিবিলিটির জন্য)
 */
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
    PublicCreatorProfileScreen(
        pageId = pageId.toLong(),
        reelsViewModel = reelsViewModel,
        isLoggedIn = isLoggedIn,
        onRequireLogin = onRequireLogin,
        onBackClick = onBackClick,
        onReelClick = onReelClick,
        onOpenDirectMessage = onOpenDirectMessage,
        modifier = modifier
    )
}

/**
 * 🎯 ৮ ডিজিটের Long পেজ আইডি সমর্থনকারী মূল কম্পোজেবল স্ক্রিন
 */
@Composable
fun PublicCreatorProfileScreen(
    pageId: Long,
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

    var profileData by remember { mutableStateOf<PublicCreatorProfileDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isFollowingState by remember { mutableStateOf(false) }
    var followersCountState by remember { mutableLongStateOf(0L) }

    // Tabs: 0 -> Reels, 1 -> Series
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showTopActionMenu by remember { mutableStateOf(false) }
    var showJustWatchedSheet by remember { mutableStateOf(false) }

    // Playlist Episode Drawer
    var activePlaylistForDrawer by remember { mutableStateOf<CreatorPlaylistDto?>(null) }
    var playlistEpisodes by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isEpisodesLoading by remember { mutableStateOf(false) }

    // Room DB থেকে আসল ওয়াচ হিস্ট্রি লোড
    val watchHistoryList by remember {
        AppDatabase.getInstance(context).watchHistoryDao().getContinueWatching()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // 🌐 সার্ভার থেকে ৮ ডিজিটের পেজ ডাটা ফেচ
    fun loadPublicProfile() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val result = repository.getPublicCreatorProfile(pageId)
            isLoading = false
            if (result.isSuccess) {
                val data = result.getOrNull()
                profileData = data
                isFollowingState = data?.isFollowing ?: false
                followersCountState = data?.metrics?.followersCount ?: 0L
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Unable to load profile."
            }
        }
    }

    LaunchedEffect(pageId) {
        loadPublicProfile()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.5.dp)
            }
        } else if (profileData == null) {
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
                    Text(errorMessage ?: "Profile not found", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { loadPublicProfile() },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val profile = profileData!!

            val isOwnProfile = remember(currentLoggedInUserId, profile) {
                currentLoggedInUserId > 0 && (currentLoggedInUserId == profile.userId || currentLoggedInUserId.toLong() == profile.pageId)
            }

            val publicShareUrl = remember(profile.handle) {
                "https://playdramaflix.com/page/${profile.handle.removePrefix("@")}"
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // =========================================================================
                // 1. TOP HEADER: 16:6 WIDESCREEN COVER & ACTIONS
                // =========================================================================
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 6f)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E2838), Color(0xFF0F1522))
                                )
                            )
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

                        // Gradient Shadow Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(0.65f), Color.Transparent, Color.Black.copy(0.85f))
                                    )
                                )
                        )

                        // Top Buttons: [ Back ] ----------------- [ Share ] [ 3-Dot ]
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Check out ${profile.pageName} (${profile.displayHandle}) on DramaFlix:\n$publicShareUrl")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.4f))
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(19.dp))
                                }

                                Box {
                                    IconButton(
                                        onClick = { showTopActionMenu = true },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.4f))
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.size(20.dp))
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
                                                    putExtra(Intent.EXTRA_TEXT, "Check out ${profile.pageName} (${profile.displayHandle}) on DramaFlix:\n$publicShareUrl")
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
                        // Avatar + Real Stats Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-36).dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Circular Profile Avatar with border
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2838))
                                    .border(3.dp, PureBlack, CircleShape),
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

                            // 📊 রিয়েল-টাইম ৩টি স্ট্যাটাস কলাম (Metrics)
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileMetricColumn(count = profile.metrics?.formattedFollowers ?: "0", label = "Followers")
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderColor))
                                ProfileMetricColumn(count = profile.metrics?.formattedFollowing ?: "0", label = "Following")
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderColor))
                                ProfileMetricColumn(count = profile.metrics?.formattedLikes ?: "0", label = "Likes")
                            }
                        }

                        // Name, 8-Digit ID, Handle & Category Pill
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-24).dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = profile.pageName,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Creator",
                                    tint = ActionGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 🎯 ৮ ডিজিট আইডি ব্যাজ ও ক্যাটাগরি পিল
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                // 8-Digit ID Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E2638),
                                    border = BorderStroke(0.6.dp, BorderColor)
                                ) {
                                    Text(
                                        text = profile.displayPageId,
                                        color = CyanAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Category Pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF17202A)
                                ) {
                                    Text(
                                        text = "🎭 ${profile.category ?: "Entertainment"}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // @username + Copy Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Username", profile.displayHandle))
                                    Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = profile.displayHandle,
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            if (!profile.bio.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = profile.bio,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 🔘 Action Buttons Row: [ Follow / Following ] [ Message ]
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
                                    // Follow Button
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
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowingState) Color(0xFF222838) else TikTokRed
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(40.dp)
                                    ) {
                                        Text(
                                            text = if (isFollowingState) "Following" else "+ Follow",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Message Button
                                    Button(
                                        onClick = {
                                            if (!isLoggedIn) onRequireLogin()
                                            else onOpenDirectMessage(profile.userId.toString(), profile.pageName)
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
                                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Text("Message", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        // =========================================================================
                        // 3. DUAL TABS: 🎬 REELS | 📺 SERIES & PLAYLISTS
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
                                .offset(y = (-10).dp)
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = {
                                    Text(
                                        text = "🎬 Reels (${profile.reels.size})",
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
                                        text = "📺 Series (${profile.playlists.size})",
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
                // 4. TAB CONTENTS (3-COLUMN REELS GRID OR SERIES PLAYLISTS)
                // =========================================================================
                when (selectedTabIndex) {
                    // TAB 1: 3-COLUMN REELS GRID
                    0 -> {
                        if (profile.reels.isEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                EmptyStateView(icon = Icons.Outlined.PlayCircleOutline, message = "No reels published yet")
                            }
                        } else {
                            items(profile.reels, key = { it.id }) { reel ->
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

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(0.85f))
                                                )
                                            )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 6.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("▷", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(reel.formattedViews, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: MINI-DRAMA SERIES PLAYLISTS CARDS
                    1 -> {
                        if (profile.playlists.isEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                EmptyStateView(icon = Icons.Outlined.VideoLibrary, message = "No series playlists created yet")
                            }
                        } else {
                            items(profile.playlists, key = { it.effectiveId }) { playlist ->
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
                                                playlistEpisodes = repository.getPlaylistReels(playlist.effectiveId).getOrDefault(emptyList())
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
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF1E2838)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Movie, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(32.dp))
                                            }
                                        }

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

                // স্পেসার যাতে বটম পিল কনটেন্ট না ঢাকে
                item(span = { GridItemSpan(3) }) {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }

        // =========================================================================
        // 5. "JUST WATCHED" FLOATING PILL
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
        // 6. "JUST WATCHED" BOTTOM SHEET (Room Database Real Data)
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
private fun ProfileMetricColumn(count: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = count, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextMuted, fontSize = 11.5.sp)
    }
}

@Composable
private fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Text(text = message, color = TextMuted, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
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
