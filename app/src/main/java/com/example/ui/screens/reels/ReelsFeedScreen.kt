@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)

package com.example.ui.screens.reels

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)

@Composable
fun ReelsFeedScreen(
    viewModel: ReelsViewModel,
    onOpenCreateReel: () -> Unit,
    onOpenPageProfile: (pageId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()

    var showQualitySheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val reelsList = feedState.reels
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { reelsList.size }
    )

    // 🎯 ভিডিও স্ক্রিনে আসতেই স্বয়ংক্রিয়ভাবে একবার "type=view" ট্র্যাকিং
    LaunchedEffect(pagerState.currentPage, reelsList) {
        if (reelsList.isNotEmpty()) {
            val currentReel = reelsList.getOrNull(pagerState.currentPage)
            if (currentReel != null) {
                viewModel.trackReelView(currentReel.id)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (feedState.isLoading && reelsList.isEmpty()) {
            // লোডিং স্পিনার
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ActionGreen,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(46.dp)
                )
            }
        } else if (reelsList.isEmpty()) {
            // খালি ফিড স্টেট
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
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = if (feedState.activeTab == "following") "No reels from creators you follow yet." 
                               else "No reels available right now.",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onOpenCreateReel,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Create First Reel +", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // =========================================================================
            // 🎬 মূল উল্লম্ব রিলস পেজার (TikTok / Instagram Reels)
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadFeed(feedState.activeTab)
                        delay(500)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                ) { pageIndex ->
                    val reel = reelsList[pageIndex]
                    val isCurrentPagePlaying = (pagerState.currentPage == pageIndex)

                    SingleReelPlayerItem(
                        reel = reel,
                        selectedQuality = feedState.selectedQuality,
                        isActiveVideoPlaying = isCurrentPagePlaying,
                        onDoubleTapLike = {
                            viewModel.toggleLike(reel)
                        },
                        onToggleLike = {
                            viewModel.toggleLike(reel)
                        },
                        onFollowClick = {
                            viewModel.toggleFollowCreator(reel.pageId)
                        },
                        onShareClick = {
                            viewModel.shareReel(context, reel)
                        },
                        onQualityClick = {
                            showQualitySheet = true
                        },
                        onOpenPageProfile = {
                            onOpenPageProfile(reel.pageId)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // =========================================================================
            // 🔝 ওপরে ভাসমান হেডার বার: [ Following | For You ] ও ক্যামেরা আইকন
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ক্যামেরা / রিলস আপলোড বাটন
                IconButton(
                    onClick = onOpenCreateReel,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Create Reel",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // [ Following | For You ] সুইচ
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Following",
                        color = if (feedState.activeTab == "following") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = if (feedState.activeTab == "following") FontWeight.Black else FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.loadFeed(tab = "following") }
                    )

                    Text(
                        text = "|",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 14.sp
                    )

                    Text(
                        text = "For You",
                        color = if (feedState.activeTab == "for_you") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = if (feedState.activeTab == "for_you") FontWeight.Black else FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.loadFeed(tab = "for_you") }
                    )
                }

                // ডানপাশের স্পেসার
                Spacer(modifier = Modifier.size(38.dp))
            }
        }

        // =========================================================================
        // 🎛️ মাল্টি-কোয়ালিটি সিলেকশন বটম শীট (720P / 480P / 360P)
        // =========================================================================
        if (showQualitySheet) {
            ReelsQualitySelectionSheet(
                selectedQuality = feedState.selectedQuality,
                onSelectQuality = { newQuality ->
                    viewModel.setVideoQuality(newQuality)
                },
                onDismiss = { showQualitySheet = false }
            )
        }
    }
}
