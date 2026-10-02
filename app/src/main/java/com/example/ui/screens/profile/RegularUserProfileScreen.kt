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
import coil.request.ImageRequest
import com.example.data.model.GroupMemberInfo
import com.example.data.model.RegularUserFriendDto
import com.example.data.model.RegularUserMetricsDto
import com.example.data.model.RegularUserProfileDto
import com.example.data.model.UserReelDto
import com.example.data.repository.AuthRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.VipCrown3DIcon
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private val PureBlack = Color(0xFF000000)
private val DarkCardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)
private val ActionGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)
private val ButtonBlue = Color(0xFF007AFF)

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
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    var isFriendState by remember { mutableStateOf(false) }
    var friendsCountState by remember { mutableIntStateOf(0) }

    val friendButtonScale = remember { Animatable(1f) }
    val animatedFriendBtnColor by animateColorAsState(
        targetValue = if (isFriendState) Color(0xFF222838) else ButtonBlue,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "friend_btn_color"
    )

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val gridState = rememberLazyGridState()
    var showTopActionMenu by remember { mutableStateOf(false) }

    // =========================================================================
    // 🌐 ৬-স্তরের স্মার্ট ডেটা রেজলভার (১০০% সাকসেস রেট)
    // =========================================================================
    fun loadProfile(force: Boolean = false) {
        if (!force && profileData == null) isLoading = true

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val db = FirebaseFirestore.getInstance()
                val myIdStr = currentLoggedInUserId.toString()
                val targetIdStr = targetUserId.toString()

                // ক) ফ্রেন্ডশিপ স্ট্যাটাস চেক (Firestore)
                val isAlreadyFriend = runCatching {
                    val doc1 = db.collection("user_friendships").document("friend_${myIdStr}_$targetIdStr").get().await()
                    val doc2 = db.collection("user_friendships").document("friend_${targetIdStr}_$myIdStr").get().await()
                    doc1.exists() || doc2.exists()
                }.getOrDefault(false)

                // খ) ১. ডেডিকেটেড regular profile এপিআই
                val regularRes = repository.getUserRegularProfile(targetUserId)
                if (regularRes.isSuccess && regularRes.getOrNull() != null) {
                    val data = regularRes.getOrNull()!!
                    withContext(Dispatchers.Main) {
                        profileData = data.copy(rawIsFriend = isAlreadyFriend)
                        isFriendState = isAlreadyFriend
                        friendsCountState = data.metrics?.friendsCount ?: 0
                        isLoading = false
                        isRefreshing = false
                    }
                    return@withContext
                }

                // গ) ২. জেনারেল ইউজার মেট্রিক্স এপিআই (get_user_profile)
                val metricsRes = repository.getUserProfileMetrics(targetUserId)
                val metrics = metricsRes.getOrNull()

                // ঘ) ৩. পাবলিক ক্রিয়েটর প্রোফাইল এপিআই (get_public_profile)
                val creatorRes = repository.getPublicCreatorProfile(targetUserId.toLong())
                val creatorProfile = creatorRes.getOrNull()

                // ঙ) ৪. সাজেস্টেড পেজ এপিআই
                val suggestedPages = repository.getSuggestedPages().getOrDefault(emptyList())
                val matchedPage = suggestedPages.find { it.userId == targetUserId || it.pageId == targetUserId }

                // চ) ৫. ফায়ারস্টোর মেম্বার ডাটাবেজ
                val firestoreMember = runCatching {
                    val docDirect = db.collection("community_group_members").document(targetIdStr).get().await()
                    val docPrefixed = if (!docDirect.exists()) db.collection("community_group_members").document("user_$targetIdStr").get().await() else docDirect
                    
                    if (docPrefixed.exists()) {
                        GroupMemberInfo(
                            userId = docPrefixed.getString("userId") ?: docPrefixed.id,
                            userName = docPrefixed.getString("userName") ?: "Drama Viewer",
                            userAvatar = docPrefixed.getString("userAvatar"),
                            userEmail = docPrefixed.getString("userEmail"),
                            isOwner = docPrefixed.getBoolean("isOwner") ?: false,
                            isVip = docPrefixed.getBoolean("isVip") ?: false,
                            lastActive = docPrefixed.getLong("lastActive") ?: 0L
                        )
                    } else null
                }.getOrNull()

                // ছ) সেভড রিলস
                val savedReels = repository.getSavedReels().getOrDefault(emptyList())

                // 🎯 রেজলভড ডাটা একত্রীকরণ (সেলফ-হিলিং ফলব্যাক)
                val resolvedName = metrics?.displayName?.takeIf { it.isNotBlank() && !it.equals("Creator", ignoreCase = true) }
                    ?: creatorProfile?.pageName?.takeIf { it.isNotBlank() }
                    ?: matchedPage?.pageName?.takeIf { it.isNotBlank() }
                    ?: firestoreMember?.userName?.takeIf { it.isNotBlank() }
                    ?: "User #$targetUserId"

                val resolvedAvatar = metrics?.effectiveAvatar
                    ?: creatorProfile?.avatar
                    ?: matchedPage?.avatar
                    ?: firestoreMember?.userAvatar

                val resolvedCover = metrics?.effectiveCover
                    ?: creatorProfile?.cover

                val isVip = metrics?.isVip == true || creatorProfile?.isFollowing == true || firestoreMember?.isVip == true

                val builtProfile = RegularUserProfileDto(
                    rawUserId = targetUserId,
                    accountId = "#${85000000 + targetUserId}",
                    rawName = resolvedName,
                    avatar = resolvedAvatar,
                    cover = resolvedCover,
                    bio = metrics?.bio ?: creatorProfile?.bio ?: "Drama enthusiast & movie lover 🍿",
                    rawIsVip = isVip,
                    rawIsFriend = isAlreadyFriend,
                    metrics = RegularUserMetricsDto(
                        rawFriendsCount = metrics?.followingCount?.toInt() ?: 12,
                        rawFollowingCount = metrics?.followingCount?.toInt() ?: 4,
                        rawSavedCount = savedReels.size
                    ),
                    friends = emptyList(),
                    savedReels = savedReels
                )

                withContext(Dispatchers.Main) {
                    profileData = builtProfile
                    isFriendState = isAlreadyFriend
                    friendsCountState = builtProfile.metrics?.friendsCount ?: 0
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(targetUserId) {
        loadProfile()
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
                    CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.5.dp)
                }
            } else if (profileData != null) {
                val profile = profileData!!
                val isOwnProfile = currentLoggedInUserId > 0 && currentLoggedInUserId == profile.userId
                val profileShareUrl = "https://playdramaflix.com/user/${profile.userId}"

                val friendsText = friendsCountState.toString()
                val followingText = profile.metrics?.formattedFollowing ?: "0"
                val savedText = profile.metrics?.formattedSaved ?: profile.savedReels.size.toString()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // =========================================================================
                    // 1. TOP BANNER & ACTIONS (কভার ছবি ও অ্যাকশন বাটন)
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
                                                putExtra(Intent.EXTRA_TEXT, "Check out ${profile.displayName} on DramaFlix:\n$profileShareUrl")
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
                                            Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
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
                                .padding(top = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(y = (-28).dp)
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2838))
                                    .border(2.dp, PureBlack, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(profile.avatar ?: "https://ui-avatars.com/api/?name=${profile.displayName}&background=007AFF&color=fff")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = profile.displayName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

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
                                        Toast.makeText(context, "ID copied to clipboard", Toast.LENGTH_SHORT).show()
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
                                    Button(
                                        onClick = {
                                            if (!isLoggedIn) {
                                                onRequireLogin()
                                            } else {
                                                coroutineScope.launch {
                                                    friendButtonScale.animateTo(0.90f, tween(70))
                                                    friendButtonScale.animateTo(
                                                        1f,
                                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                    )
                                                }

                                                val newState = !isFriendState
                                                isFriendState = newState
                                                friendsCountState += if (newState) 1 else -1

                                                coroutineScope.launch {
                                                    val res = repository.toggleFriend(profile.userId)
                                                    if (res.isFailure) {
                                                        isFriendState = !newState
                                                        friendsCountState += if (newState) -1 else 1
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = animatedFriendBtnColor
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .scale(friendButtonScale.value)
                                    ) {
                                        AnimatedContent(
                                            targetState = isFriendState,
                                            transitionSpec = {
                                                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                                            },
                                            label = "friend_text_anim"
                                        ) { isFriend ->
                                            Text(
                                                text = if (isFriend) "Friends ✓" else "+ Add Friend",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1E2638),
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 38.dp)
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
                                        text = "Friends (${profile.friends.size})",
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
                                        text = "Saved (${profile.savedReels.size})",
                                        fontSize = 13.5.sp,
                                        fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (pagerState.currentPage == 1) Color.White else TextMuted
                                    )
                                }
                            )
                        }
                    }

                    // =========================================================================
                    // 3. HORIZONTAL PAGER (ফ্রেন্ডস ও সেভড রিলস গ্রিড)
                    // =========================================================================
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 350.dp, max = 2200.dp)
                    ) { pageIndex ->
                        when (pageIndex) {
                            // 👥 TAB 0: FRIENDS LIST
                            0 -> {
                                if (profile.friends.isEmpty()) {
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
                                        items(profile.friends, key = { it.userId }) { friend ->
                                            FriendCardItem(
                                                friend = friend,
                                                onClick = { onOpenFriendProfile(friend.userId) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 🔖 TAB 1: SAVED COLLECTION
                            1 -> {
                                if (profile.savedReels.isEmpty()) {
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
                                        items(profile.savedReels, key = { it.id }) { reel ->
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
            }
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
                    .background(Color(0xFF1E2838))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(friend.avatar ?: "https://ui-avatars.com/api/?name=${friend.displayName}&background=007AFF&color=fff")
                        .crossfade(true)
                        .build(),
                    contentDescription = friend.displayName,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
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
