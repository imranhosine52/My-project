@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.model.CreatorPageDto
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.VipCrown3DIcon
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PureBlack = Color(0xFF000000)
private val ActionGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8692A6)
private val CardDarkBg = Color(0xFF131722)

@Composable
fun CreatorStudioScreen(
    page: CreatorPageDto,
    reelsViewModel: ReelsViewModel,
    onSwitchToPersonalProfile: () -> Unit,
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onCreateReelClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    val targetUserId = remember(page.userId, page.id) {
        if (page.userId > 0) page.userId else if (page.id > 0) page.id else repository.getCurrentUserId()
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    var show3DotMenu by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var isUploadingStory by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    var activePageData by remember(page) { mutableStateOf(page) }

    fun refreshAllCreatorData() {
        reelsViewModel.loadUserProfileMetrics(targetUserId)
        reelsViewModel.loadFeed("for_you")
    }

    LaunchedEffect(targetUserId) {
        refreshAllCreatorData()
    }

    val profileUiState by reelsViewModel.profileState.collectAsStateWithLifecycle()
    val liveMetrics = profileUiState.profile

    val feedState by reelsViewModel.feedState.collectAsStateWithLifecycle()
    val pageReels = remember(feedState.reels, activePageData.id, targetUserId) {
        feedState.reels.filter { it.pageId == activePageData.id || it.userId == targetUserId }
    }

    // 🎯 ৫টি টেক্সট ট্যাবের নাম (Love বাদ দিয়ে আপনার নির্দেশিত ক্রম অনুযায়ী)
    val tabTitles = listOf("Reels", "Post", "Private", "Repost", "Favorite")
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabTitles.size })

    // Favorite (Saved) Reels
    var savedReelsList by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isLoadingSavedReels by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 4) { // Favorite tab
            isLoadingSavedReels = true
            val result = repository.getSavedReels()
            savedReelsList = result.getOrDefault(emptyList())
            isLoadingSavedReels = false
        }
    }

    // 🎯 ১. কভার ফটো আপলোড লঞ্চার (FastAPI VPS 2)
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingCover = true
            coroutineScope.launch {
                val result = repository.uploadUserCover(uri, fallbackUserId = targetUserId)
                isUploadingCover = false
                if (result.isSuccess) {
                    val newCoverUrl = result.getOrNull()
                    activePageData = activePageData.copy(cover = newCoverUrl)
                    refreshAllCreatorData()
                    Toast.makeText(context, "✓ Cover photo updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Cover upload failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 🎯 ২. প্রোফাইল ক্যামেরা আইকনে চাপ দিলে ২৪ ঘণ্টার স্টোরি (Story) আপলোড লঞ্চার
    val storyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingStory = true
            coroutineScope.launch {
                val isVideo = context.contentResolver.getType(uri)?.contains("video", true) == true
                val result = repository.uploadReel(
                    pageId = activePageData.id,
                    title = "Story",
                    description = "24h Story",
                    videoUri = uri,
                    onProgressUpdate = {}
                )
                isUploadingStory = false
                if (result.isSuccess) {
                    Toast.makeText(context, "🎉 Story published successfully!", Toast.LENGTH_SHORT).show()
                    refreshAllCreatorData()
                } else {
                    Toast.makeText(context, "Story upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 🔥 সার্ভার থেকে পাওয়া ১০০% আসল মেট্রিক্স
    val realFollowingCount = liveMetrics?.formattedFollowing ?: activePageData.followingCount.toString()
    val realFollowersCount = liveMetrics?.formattedFollowers ?: activePageData.followersCount.toString()
    val realLikesCount = liveMetrics?.formattedLikes ?: activePageData.totalLikes.toString()

    val currentAvatar = liveMetrics?.effectiveAvatar ?: activePageData.avatar
    val currentCover = liveMetrics?.effectiveCover ?: activePageData.cover

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    refreshAllCreatorData()
                    delay(500)
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // =========================================================================
                // 🌄 ১. ফুলস্ক্রিন কভার ব্যানার (Status Bar-এর নিচে দিয়ে যাবে)
                // =========================================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1B2338), Color(0xFF0F1520))
                            )
                        )
                ) {
                    if (!currentCover.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(currentCover).crossfade(true).build(),
                            contentDescription = "Cover Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // ডার্ক গ্রেডিয়েন্ট ওভারলে
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.65f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // 🔝 টপ বার: [ < Back ] ----------------- [ ⋮ 3-Dot Menu ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // বামে ব্যাক বাটন
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // ডানে থ্রি-ডট (⋮) মেনু ও পপ-আপ অপশনসমূহ
                        Box {
                            IconButton(
                                onClick = { show3DotMenu = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = show3DotMenu,
                                onDismissRequest = { show3DotMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF181F2B))
                                    .border(1.dp, Color(0xFF263346), RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Profile", color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ActionGreen) },
                                    onClick = {
                                        show3DotMenu = false
                                        showEditProfileSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Switch to Personal", color = ActionGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = ActionGreen) },
                                    onClick = {
                                        show3DotMenu = false
                                        onSwitchToPersonalProfile()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Page Link", color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00E5FF)) },
                                    onClick = {
                                        show3DotMenu = false
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Check out ${activePageData.pageName} (${activePageData.displayHandle}) on DramaFlix:\n${activePageData.pageShareUrl}"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Page Link"))
                                    }
                                )
                            }
                        }
                    }

                    // কভার ফটো পরিবর্তন করার বাটন (ডান নিচে)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clickable { coverPickerLauncher.launch("image/*") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text("Edit Cover", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isUploadingCover) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // =========================================================================
                // 👤 ২. প্রোফাইল ইনফো, মেট্রিক্স ও স্টোরি ক্যামেরা অবতার
                // =========================================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activePageData.pageName.ifBlank { "Creator Page" },
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Page",
                                    tint = ActionGreen,
                                    modifier = Modifier.size(18.dp)
                                )

                                if (liveMetrics?.isVip == true) {
                                    VipCrown3DIcon(modifier = Modifier.size(18.dp, 14.dp))
                                }
                            }

                            Text(
                                text = activePageData.displayHandle,
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 🔥 সার্ভারের আসল মেট্রিক্স (কোনো ফেক সংখ্যা নেই)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = realFollowingCount, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Text("Following", color = TextMuted, fontSize = 11.5.sp)
                                }
                                Column {
                                    Text(text = realFollowersCount, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Text("Followers", color = TextMuted, fontSize = 11.5.sp)
                                }
                                Column {
                                    Text(text = realLikesCount, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Text("Likes", color = TextMuted, fontSize = 11.5.sp)
                                }
                            }
                        }

                        // 🎯 প্রোফাইল অবতার ও স্টোরি (Story) আপলোড ক্যামেরা আইকন
                        Box(
                            modifier = Modifier.size(76.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF1E2838), CircleShape)
                                    .background(Color(0xFF161C26)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!currentAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(currentAvatar).crossfade(true).build(),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = activePageData.pageName.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isUploadingStory) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }

                            // 📷 ক্যামেরা আইকন (ক্লিক করলে ২৪ ঘণ্টার স্টোরি আপলোড ওপেন হবে)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                                    .border(1.5.dp, PureBlack, CircleShape)
                                    .clickable { storyPickerLauncher.launch("*/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Add 24h Story",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // বায়ো ও পেজ লিঙ্ক
                    val effectiveBioText = remember(activePageData.bio, activePageData.handle) {
                        activePageData.bio?.takeIf { it.isNotBlank() } 
                            ?: "Full Drama Link 👉 https://playdramaflix.com/page/${activePageData.handle.removePrefix("@")}"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Page Link", activePageData.pageShareUrl))
                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = effectiveBioText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // =========================================================================
                // 📑 ৩. আইকন-বিহীন ৫টি টেক্সট ট্যাব: [ Reels | Post | Private | Repost | Favorite ]
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = (pagerState.currentPage == index)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else TextMuted,
                                fontSize = if (isSelected) 15.sp else 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 24.dp else 0.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) ActionGreen else Color.Transparent)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E2432), thickness = 0.8.dp)

                // =========================================================================
                // ↔️ ৪. ডানে-বামে সোয়াইপযোগ্য পেজার (HorizontalPager)
                // =========================================================================
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    when (pageIndex) {
                        // 🎬 TAB 0: REELS (১ম কার্ডটি ফেসবুক স্টাইল + Create Reel ফ্রেম)
                        0 -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                contentPadding = PaddingValues(bottom = 70.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // 🌟 ১ম আইটেম: Facebook-Style Create Reel ফ্রেম
                                item {
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(0.68f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF161D2A))
                                            .border(1.dp, Color(0xFF263346), RoundedCornerShape(6.dp))
                                            .clickable { onCreateReelClick() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(ActionGreen.copy(alpha = 0.15f))
                                                    .border(1.5.dp, ActionGreen, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Create Reel",
                                                    tint = ActionGreen,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Text(
                                                text = "Create Reel",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // বাকি আসল রিলসসমূহ
                                items(pageReels, key = { it.id }) { reel ->
                                    ReelGridThumbnailItem(reel = reel, onReelClick = onReelClick)
                                }
                            }
                        }

                        // 🖼️ TAB 1: POST (ইমেজ পোস্টসমূহ)
                        1 -> {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                    Text("No image posts yet", color = TextMuted, fontSize = 13.5.sp)
                                }
                            }
                        }

                        // 🔒 TAB 2: PRIVATE
                        2 -> {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No private reels", color = TextMuted, fontSize = 13.5.sp)
                            }
                        }

                        // 🔁 TAB 3: REPOST
                        3 -> {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No reposted reels", color = TextMuted, fontSize = 13.5.sp)
                            }
                        }

                        // ⭐ TAB 4: FAVORITE (সেভ করা রিলস)
                        4 -> {
                            if (isLoadingSavedReels) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                                }
                            } else if (savedReelsList.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No saved reels found", color = TextMuted, fontSize = 13.5.sp)
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    contentPadding = PaddingValues(bottom = 70.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(savedReelsList, key = { "fav_${it.id}" }) { reel ->
                                        ReelGridThumbnailItem(reel = reel, onReelClick = onReelClick)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Edit Profile Sheet
        if (showEditProfileSheet) {
            EditPageProfileSheet(
                page = activePageData,
                repository = repository,
                onBackClick = { showEditProfileSheet = false },
                onPageUpdated = { updated ->
                    activePageData = updated
                    refreshAllCreatorData()
                }
            )
        }
    }
}

// =============================================================================
// 🔲 একক রিলস থাম্বনেল আইটেম (আসল ভিউ সংখ্যা সহ)
// =============================================================================
@Composable
private fun ReelGridThumbnailItem(
    reel: UserReelDto,
    onReelClick: (UserReelDto) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(6.dp))
            .background(CardDarkBg)
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
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
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
            Text(
                text = "▷",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = formatViewsCount(reel.viewsCount),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatViewsCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
