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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonRemove
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
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.model.RegularUserFriendDto
import com.example.data.model.RegularUserProfileDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.VipCrown3DIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PureBlack = Color(0xFF000000)
private val DarkCardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)
private val ActionGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)
private val ButtonBlue = Color(0xFF007AFF)
private val UnfriendRed = Color(0xFFFF3B30)

@Composable
fun RegularUserProfileScreen(
    targetUserId: Int,
    isLoggedIn: Boolean = true,
    onRequireLogin: () -> Unit = {},
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenDirectMessage: (userId: String, userName: String) -> Unit,
    onOpenFriendProfile: (friendUserId: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }
    val authRepository = remember { AuthRepository(context) }

    val currentLoggedInUserId = remember {
        authRepository.getSavedUserId().filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    var profileData by remember { mutableStateOf<RegularUserProfileDto?>(null) }
    var targetUserSavedReels by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var targetUserFriends by remember { mutableStateOf<List<RegularUserFriendDto>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    var isFriendState by remember { mutableStateOf(false) }
    var friendsCountState by remember { mutableIntStateOf(0) }
    var followingCountState by remember { mutableIntStateOf(0) }
    var showUnfriendDialog by remember { mutableStateOf(false) }

    val friendButtonScale = remember { Animatable(1f) }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val gridState = rememberLazyGridState()
    var showTopActionMenu by remember { mutableStateOf(false) }

    // =========================================================================
    // 🌐 ১০০% সার্ভার অথেনটিক ডেটা লোডার (জিরো ডামি ডাটা)
    // =========================================================================
    fun loadProfile(force: Boolean = false) {
        if (!force && profileData == null) isLoading = true

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                // ১. সরাসরি সার্ভার এপিআই কল (get_user_regular_profile)
                val result = repository.getUserRegularProfile(targetUserId)

                if (result.isSuccess && result.getOrNull() != null) {
                    val serverProfile = result.getOrNull()!!

                    withContext(Dispatchers.Main) {
                        profileData = serverProfile
                        targetUserSavedReels = serverProfile.savedReels
                        targetUserFriends = serverProfile.friends
                        isFriendState = serverProfile.isFriend
                        friendsCountState = serverProfile.metrics?.friendsCount ?: 0
                        followingCountState = serverProfile.metrics?.followingCount ?: 0
                        isLoading = false
                        isRefreshing = false
                    }
                } else {
                    // ২. সার্ভার ব্যাকআপ: auth/profile এপিআই থেকে ইউজারের আসল তথ্য চেক করা
                    val authUserRes = authRepository.getUserProfile(targetUserId.toString())
                    val authUser = authUserRes.getOrNull()?.user

                    if (authUser != null) {
                        val realSavedReels = repository.getSavedReels(targetUserId).getOrDefault(emptyList())
                        val realFriends = repository.getConfirmedFriends(targetUserId).getOrDefault(emptyList()).map {
                            RegularUserFriendDto(it.userId, it.name, it.avatar)
                        }

                        val realProfile = RegularUserProfileDto(
                            rawUserId = authUser.id.toIntOrNull() ?: targetUserId,
                            accountId = authUser.effectiveAccountId,
                            rawName = authUser.displayName,
                            avatar = authUser.effectiveAvatar,
                            cover = null,
                            bio = "Hello, I am using PlayDramaFlix.",
                            rawIsVip = authUser.isVip,
                            rawIsFriend = false,
                            savedReels = realSavedReels,
                            friends = realFriends
                        )

                        withContext(Dispatchers.Main) {
                            profileData = realProfile
                            targetUserSavedReels = realSavedReels
                            targetUserFriends = realFriends
                            friendsCountState = realFriends.size
                            isLoading = false
                            isRefreshing = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(targetUserId) {
        loadProfile()
    }

    // ফ্রেন্ড যোগ / আনফ্রেন্ড এক্সিকিউটার
    fun performFriendToggle() {
        if (!isLoggedIn) {
            onRequireLogin()
            return
        }

        coroutineScope.launch {
            friendButtonScale.animateTo(0.92f, tween(70))
            friendButtonScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))

            val newState = !isFriendState
            isFriendState = newState
            friendsCountState += if (newState) 1 else -1

            val res = repository.toggleFriend(targetUserId)
            if (res.isFailure) {
                isFriendState = !newState
                friendsCountState += if (newState) -1 else 1
                Toast.makeText(context, "Action failed", Toast.LENGTH_SHORT).show()
            } else {
                val msg = if (newState) "Friend added!" else "Unfriended successfully"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
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
                loadProfile(force = true)
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && profileData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.5.dp)
                }
            } else if (profileData != null) {
                val profile = profileData!!
                val isOwnProfile = currentLoggedInUserId > 0 && currentLoggedInUserId == profile.userId
                val profileShareUrl = "https://playdramaflix.com/user/${profile.userId}"

                val friendsText = friendsCountState.toString()
                val followingText = followingCountState.toString()
                val savedText = targetUserSavedReels.size.toString()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // =========================================================================
                    // 1. TOP BANNER & ACTIONS (আসল কভার ছবি)
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
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.DISABLED)
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
                                        listOf(Color.Black.copy(0.50f), Color.Transparent)
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
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Box {
                                IconButton(onClick = { showTopActionMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
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
                                                putExtra(Intent.EXTRA_TEXT, "Check out ${profile.displayName} on PlayDramaFlix:\n$profileShareUrl")
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
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Profile Link", profileShareUrl))
                                            Toast.makeText(context, "Profile link copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    if (isFriendState && !isOwnProfile) {
                                        DropdownMenuItem(
                                            text = { Text("Unfriend", color = UnfriendRed, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Icon(Icons.Outlined.PersonRemove, contentDescription = null, tint = UnfriendRed) },
                                            onClick = {
                                                showTopActionMenu = false
                                                showUnfriendDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // 2. PROFILE HEADER INFO (আসল অবতার, আসল নাম, আসল আইডি)
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
                            // আসল অবতার
                            Box(
                                modifier = Modifier
                                    .offset(y = (-28).dp)
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2838))
                                    .border(2.dp, PureBlack, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!profile.avatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(profile.avatar)
                                            .memoryCachePolicy(CachePolicy.DISABLED)
                                            .diskCachePolicy(CachePolicy.DISABLED)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = profile.displayName,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = profile.displayName.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // মেট্রিক্স কাউন্টার
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RegularMetricItem(count = friendsText, label = "Friends")
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(BorderColor))
                                RegularMetricItem(count = followingText, label = "Following")
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(BorderColor))
                                RegularMetricItem(count = savedText, label = "Saved")
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-18).dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // আসল নাম
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = profile.displayName,
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black
                                )

                                if (profile.isVip) {
                                    VipCrown3DIcon(modifier = Modifier.size(18.dp, 14.dp))
                                }
                            }

                            // আসল অ্যাকাউন্ট আইডি
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Account ID", profile.displayAccountId))
                                        Toast.makeText(context, "ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "ID: ${profile.displayAccountId}",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy ID",
                                        tint = TextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF17202A),
                                    border = BorderStroke(0.6.dp, BorderColor)
                                ) {
                                    Text(
                                        text = if (profile.isVip) "👑 VIP Member" else "Viewer",
                                        color = if (profile.isVip) Color(0xFFFFB300) else Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // আসল বায়ো (ফাঁকা না থাকলে দেখাবে)
                            if (!profile.bio.isNullOrBlank()) {
                                Text(
                                    text = profile.bio!!,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 3,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // অ্যাকশন বাটনসমূহ
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
                                            Text(
                                                text = "Your Profile",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    // ফ্রেন্ড / আনফ্রেন্ড বাটন
                                    Button(
                                        onClick = {
                                            if (isFriendState) {
                                                showUnfriendDialog = true
                                            } else {
                                                performFriendToggle()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFriendState) Color(0xFF261214) else ButtonBlue
                                        ),
                                        border = if (isFriendState) BorderStroke(1.dp, UnfriendRed.copy(alpha = 0.6f)) else null,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .scale(friendButtonScale.value)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFriendState) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                                contentDescription = null,
                                                tint = if (isFriendState) UnfriendRed else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (isFriendState) "Unfriend" else "+ Add Friend",
                                                color = if (isFriendState) UnfriendRed else Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // ডিরেক্ট মেসেজ বাটন
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E2638),
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 38.dp)
                                            .clickable {
                                                if (!isLoggedIn) onRequireLogin()
                                                else onOpenDirectMessage(profile.userId.toString(), profile.displayName)
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
                                        color = CyanAccent,
                                        height = 2.dp
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-8).dp)
                        ) {
                            Tab(
                                selected = pagerState.currentPage == 0,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                text = {
                                    Text(
                                        text = "Friends (${targetUserFriends.size})",
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
                                        text = "Saved (${targetUserSavedReels.size})",
                                        fontSize = 13.5.sp,
                                        fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (pagerState.currentPage == 1) Color.White else TextMuted
                                    )
                                }
                            )
                        }
                    }

                    // =========================================================================
                    // 3. HORIZONTAL PAGER (আসল ফ্রেন্ডস ও আসল সেভড কালেকশন)
                    // =========================================================================
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 350.dp, max = 2200.dp)
                    ) { pageIndex ->
                        when (pageIndex) {
                            // 👥 TAB 0: TARGET USER'S FRIENDS LIST
                            0 -> {
                                if (targetUserFriends.isEmpty()) {
                                    EmptyRegularView("No friends added yet")
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 2000.dp)
                                    ) {
                                        items(targetUserFriends, key = { it.userId }) { friend ->
                                            FriendCardItem(
                                                friend = friend,
                                                onClick = { onOpenFriendProfile(friend.userId) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 🔖 TAB 1: TARGET USER'S OWN SAVED COLLECTION
                            1 -> {
                                if (targetUserSavedReels.isEmpty()) {
                                    EmptyRegularView("No saved reels in collection")
                                } else {
                                    LazyVerticalGrid(
                                        state = gridState,
                                        columns = GridCells.Fixed(3),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 2000.dp)
                                            .padding(bottom = 60.dp)
                                    ) {
                                        items(targetUserSavedReels, key = { it.id }) { reel ->
                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(0.70f)
                                                    .clip(RoundedCornerShape(4.dp))
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
                                                                listOf(Color.Transparent, Color.Black.copy(0.75f))
                                                            )
                                                        )
                                                )

                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(horizontal = 5.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text("▷", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                    Text(reel.formattedViews, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("User not found on server", color = TextMuted, fontSize = 14.sp)
                }
            }
        }

        // আনফ্রেন্ড ডায়ালগ
        if (showUnfriendDialog && profileData != null) {
            AlertDialog(
                onDismissRequest = { showUnfriendDialog = false },
                containerColor = Color(0xFF191D28),
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.PersonRemove,
                        contentDescription = null,
                        tint = UnfriendRed,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Unfriend ${profileData!!.displayName}?",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove ${profileData!!.displayName} from your friends list?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnfriendDialog = false
                            performFriendToggle()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = UnfriendRed)
                    ) {
                        Text("Unfriend", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnfriendDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun FriendCardItem(
    friend: RegularUserFriendDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkCardBg,
        border = BorderStroke(0.8.dp, BorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2838)),
                contentAlignment = Alignment.Center
            ) {
                if (!friend.avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(friend.avatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = friend.displayName,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = friend.displayName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "View Profile",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RegularMetricItem(count: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = count, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyRegularView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = TextMuted, fontSize = 13.sp)
    }
}
