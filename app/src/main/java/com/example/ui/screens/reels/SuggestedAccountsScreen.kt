@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PureBlack = Color(0xFF000000)
private val TikTokRed = Color(0xFFFE2C55)
private val DarkButtonBg = Color(0xFF262C38)
private val TextMuted = Color(0xFF8692A6)
private val CyanBorder = Color(0xFF00E5FF)

/**
 * 🎯 অর্গানিক সাজেস্টেড অ্যাকাউন্ট মডেল
 */
data class OrganicSuggestedAccount(
    val userId: Int,
    val pageId: Int,
    val name: String,
    val handle: String,
    val avatar: String?,
    val mutualText: String = "People you may know",
    val isFollowing: Boolean = false,
    val recentReels: List<UserReelDto> = emptyList()
)

@Composable
fun SuggestedAccountsScreen(
    reelsViewModel: ReelsViewModel,
    onBackClick: () -> Unit,
    onOpenProfile: (userId: Int) -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    var accountsList by remember { mutableStateOf<List<OrganicSuggestedAccount>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val currentLoggedInUserId = remember { repository.getCurrentUserId() }

    // =========================================================================
    // 🌐 সার্ভার থেকে ১০০% অর্গানিক সাজেস্টেড ক্রিয়েটর লোড করা
    // =========================================================================
    fun loadOrganicSuggestedAccounts() {
        coroutineScope.launch {
            val feedResult = repository.getReelsFeed(tab = "for_you", page = 1)
            val allReels = feedResult.getOrDefault(emptyList())

            if (allReels.isNotEmpty()) {
                // সার্ভারের রিলসগুলোকে ক্রিয়েটর আইডি অনুযায়ী গ্রুপ করা (নিজের আইডি বাদে)
                val groupedByCreator = allReels
                    .filter { it.userId > 0 && it.userId != currentLoggedInUserId }
                    .groupBy { it.userId }

                val organicList = groupedByCreator.map { (creatorId, reelsOfCreator) ->
                    val firstReel = reelsOfCreator.first()
                    val creatorPageId = if (firstReel.pageId > 0) firstReel.pageId else creatorId

                    OrganicSuggestedAccount(
                        userId = creatorId,
                        pageId = creatorPageId,
                        name = firstReel.pageName.ifBlank { "Drama Creator" },
                        handle = firstReel.displayHandle,
                        avatar = firstReel.pageAvatar,
                        mutualText = if (firstReel.isFollowing) "Follows you" else "People you may know",
                        isFollowing = firstReel.isFollowing,
                        recentReels = reelsOfCreator.take(4) // সর্বশেষ ৪টি রিলস প্রিভিউ
                    )
                }
                accountsList = organicList
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadOrganicSuggestedAccounts()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. স্ক্রিনশটের হুবহু টপ বার: [ ← Back ] ----- Find friends ----- [ ⛶ QR ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Find friends",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        Toast.makeText(context, "QR Scanner ready", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Scan QR",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // =========================================================================
            // 🏷️ ২. হেডার: "Suggested accounts ⓘ"
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Suggested accounts",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ⓘ",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }

            // =========================================================================
            // 📋 ৩. সাজেস্টেড অ্যাকাউন্টস তালিকা (পুল-টু-রিফ্রেশ সহ)
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        loadOrganicSuggestedAccounts()
                        delay(500)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (isLoading && accountsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TikTokRed, strokeWidth = 2.5.dp)
                    }
                } else if (accountsList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Text("No suggestions available", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("New creators and friends will appear here.", color = TextMuted, fontSize = 12.5.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        items(accountsList, key = { it.userId }) { account ->
                            SuggestedAccountCardItem(
                                account = account,
                                onProfileClick = { onOpenProfile(account.userId) },
                                onReelClick = onReelClick,
                                onFollowToggle = {
                                    val newFollowState = !account.isFollowing
                                    // ১. লোকাল স্টেট তাত্ক্ষণিক আপডেট
                                    accountsList = accountsList.map {
                                        if (it.userId == account.userId) it.copy(isFollowing = newFollowState)
                                        else it
                                    }
                                    // ২. রিয়েল সার্ভার এপিআই সিঙ্ক
                                    coroutineScope.launch {
                                        val res = repository.toggleFollowPage(account.pageId, account.userId)
                                        if (res.isFailure) {
                                            // ফেইল হলে রোলব্যাক
                                            accountsList = accountsList.map {
                                                if (it.userId == account.userId) it.copy(isFollowing = !newFollowState)
                                                else it
                                            }
                                        }
                                    }
                                },
                                onRemoveClick = {
                                    // তালিকা থেকে রিমুভ
                                    accountsList = accountsList.filter { it.userId != account.userId }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🔲 স্ক্রিনশটের হুবহু একক সাজেস্টেড অ্যাকাউন্ট কার্ড
// =============================================================================
@Composable
private fun SuggestedAccountCardItem(
    account: OrganicSuggestedAccount,
    onProfileClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onFollowToggle: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =============================================================
        // ১. ক্রিয়েটর প্রোফাইল ইনফো রো (অবতার + নাম + হ্যান্ডেল + ... মেনু)
        // =============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProfileClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // স্ক্রিনশটের মতো নিয়ন সায়ান সার্কেল বর্ডার অবতার
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, CyanBorder, CircleShape)
                        .padding(2.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(account.avatar ?: "https://ui-avatars.com/api/?name=${account.name}&background=1E2638&color=fff")
                            .crossfade(true)
                            .build(),
                        contentDescription = account.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = account.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = account.handle,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = account.mutualText,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            IconButton(onClick = onRemoveClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // =============================================================
        // 🎬 ২. ৪টি রিলসের থাম্বনেল প্রিভিউ স্ট্রিপ (New ব্যাজ সহ)
        // =============================================================
        if (account.recentReels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                account.recentReels.take(4).forEach { reel ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF141722))
                            .clickable { onReelClick(reel) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = reel.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // স্ক্রিনশটের হুবহু "New" ব্যাজ (বাম কোণায়)
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(3.dp)
                        ) {
                            Text(
                                text = "New",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // যদি ৪টির কম রিলস থাকে তবে ফাঁকা জায়গা পূরণ
                repeat(4 - account.recentReels.take(4).size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // =============================================================
        // 🔘 ৩. বাটন রো: [ Remove ] (Dark Gray)  [ Follow ] (TikTok Red)
        // =============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remove বাটন
            Button(
                onClick = onRemoveClick,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkButtonBg),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Text(
                    text = "Remove",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Follow / Following বাটন (রিয়েল-টাইম টগল)
            Button(
                onClick = onFollowToggle,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (account.isFollowing) DarkButtonBg else TikTokRed
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Text(
                    text = if (account.isFollowing) "Following" else "Follow",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
