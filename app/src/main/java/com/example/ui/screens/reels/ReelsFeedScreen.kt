@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)

package com.example.ui.screens.reels

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
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
import com.example.data.model.ReelVideoQuality
import com.example.ui.screens.shorts.ShortsCommentsSheet
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)
private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)

@Composable
fun ReelsFeedScreen(
    viewModel: ReelsViewModel,
    onOpenCreateReel: () -> Unit,
    onOpenPageProfile: (pageId: Int) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToVip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()

    var showThreeDotSettingsSheet by remember { mutableStateOf(false) }
    var showQualityPickerSheet by remember { mutableStateOf(false) }
    var showSpeedPickerSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    var selectedPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(42.dp)
                )
            }
        } else if (reelsList.isEmpty()) {
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
                        text = if (feedState.activeTab == "following") "No reels from creators you follow." 
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
            // 🎬 ১. মূল উল্লম্ব রিলস পেজার (YouTube Shorts / TikTok স্টাইল)
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
                        playbackSpeed = selectedPlaybackSpeed,
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
                        onCommentClick = {
                            showCommentsSheet = true
                        },
                        onSaveClick = {
                            Toast.makeText(context, "Saved to your list", Toast.LENGTH_SHORT).show()
                        },
                        onShareClick = {
                            viewModel.shareReel(context, reel)
                        },
                        onOpenPageProfile = {
                            onOpenPageProfile(reel.pageId)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // =========================================================================
            // 🔝 ২. ওপরের হেডার বার: [ Post | Following | For You ] ও ডানপাশে [ 🔍 | ⋮ ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ৩টি ট্যাব: Post | Following | For You
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Post",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenCreateReel() }
                    )

                    Text(text = "|", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)

                    Text(
                        text = "Following",
                        color = if (feedState.activeTab == "following") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontWeight = if (feedState.activeTab == "following") FontWeight.Black else FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.loadFeed(tab = "following") }
                    )

                    Text(text = "|", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)

                    Text(
                        text = "For You",
                        color = if (feedState.activeTab == "for_you") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontWeight = if (feedState.activeTab == "for_you") FontWeight.Black else FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.loadFeed(tab = "for_you") }
                    )
                }

                // ডানপাশের অপশনস: 🔍 Search এবং ⋮ Three-Dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { showThreeDotSettingsSheet = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // ⋮ ৩. থ্রি-ডট সেটিংস বটম শীট (Quality & Speed)
        // =========================================================================
        if (showThreeDotSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showThreeDotSettingsSheet = false },
                containerColor = DarkCardBg,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF333C4D))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Playback Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showThreeDotSettingsSheet = false }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

                    // কোয়ালিটি অপশন
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF19202E),
                        border = BorderStroke(0.8.dp, BorderStrokeColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showThreeDotSettingsSheet = false
                                showQualityPickerSheet = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Outlined.HighQuality, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Video Quality", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(feedState.selectedQuality.label, color = Color(0xFF00E5FF), fontSize = 11.5.sp)
                                }
                            }
                            Text("Change >", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    // প্লেব্যাক স্পিড অপশন
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF19202E),
                        border = BorderStroke(0.8.dp, BorderStrokeColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showThreeDotSettingsSheet = false
                                showSpeedPickerSheet = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Outlined.Speed, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Playback Speed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (selectedPlaybackSpeed == 1.0f) "1.0x (Normal)" else "${selectedPlaybackSpeed}x", color = Color(0xFFFFB300), fontSize = 11.5.sp)
                                }
                            }
                            Text("Change >", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // =========================================================================
        // 🎛️ ৪. কোয়ালিটি সিলেকশন বটম শীট
        // =========================================================================
        if (showQualityPickerSheet) {
            ReelsQualitySelectionSheet(
                selectedQuality = feedState.selectedQuality,
                onSelectQuality = { newQuality ->
                    viewModel.setVideoQuality(newQuality)
                    Toast.makeText(context, "Quality set to ${newQuality.label}", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showQualityPickerSheet = false }
            )
        }

        // =========================================================================
        // ⏱️ ৫. স্পিড সিলেকশন বটম শীট
        // =========================================================================
        if (showSpeedPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSpeedPickerSheet = false },
                containerColor = DarkCardBg,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF333C4D)))
                    }

                    Text("Select Playback Speed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

                    speedOptions.forEach { spd ->
                        val isSelected = (selectedPlaybackSpeed == spd)
                        val label = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x"

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF132A38) else Color(0xFF19202E),
                            border = BorderStroke(if (isSelected) 1.dp else 0.6.dp, if (isSelected) Color(0xFFFFB300) else BorderStrokeColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPlaybackSpeed = spd
                                    showSpeedPickerSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, color = if (isSelected) Color(0xFFFFB300) else Color.White, fontSize = 14.sp)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        // =========================================================================
        // 💬 ৬. রিলস কমেন্ট বটম শীট
        // =========================================================================
        if (showCommentsSheet) {
            val currentReel = reelsList.getOrNull(pagerState.currentPage)
            ShortsCommentsSheet(
                comments = emptyList(),
                totalCommentsCount = currentReel?.commentsCount ?: 0,
                isLoading = false,
                currentUserName = "User",
                onDismiss = { showCommentsSheet = false },
                onAddComment = { _, _ -> },
                onLikeComment = {},
                onShareComment = {}
            )
        }
    }
}
