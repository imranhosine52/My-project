@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.TopNavigationBar
import com.example.ui.screens.categories.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: DramaFlixViewModel,
    initialCategory: String = "Home",
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToVip: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNotification: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 🔄 স্ক্রিনে ফিরে আসার সাথে সাথে ডাটা লোড
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadHomeContent()
        viewModel.refreshVipStatusAndProfile()
    }

    val categories = remember {
        listOf(
            "Home",
            "Recently Added",
            "Popular Series",
            "Shorts Drama",
            "Drama Series",
            "Anime Series",
            "Movies",
            "Bangla Dub",
            "Hindi Dub",
            "All"
        )
    }

    val initialPage = remember(initialCategory) {
        val idx = categories.indexOf(initialCategory)
        if (idx != -1) idx else 0
    }

    val categoryPagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { categories.size }
    )

    // 🎯 ক্যাটাগরি সুইচ লিসেনার
    LaunchedEffect(initialCategory) {
        val targetIdx = categories.indexOf(initialCategory)
        if (targetIdx != -1 && categoryPagerState.currentPage != targetIdx) {
            categoryPagerState.animateScrollToPage(targetIdx)
        }
    }

    // 🎙️ ভয়েস সার্চ লাউঞ্চার
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.onSearchQueryChanged(spokenText)
                onNavigateToSearch()
            }
        }
    }

    fun startVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search drama...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (homeState.isLoading && !isRefreshing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = TealAccent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadHomeContent()
                        viewModel.refreshVipStatusAndProfile()
                        delay(600)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                HorizontalPager(
                    state = categoryPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (categories.getOrElse(page) { "Home" }) {
                        "Home" -> MainHomeFeedTab(
                            homeState = homeState,
                            isVip = authState.isVip,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer,
                            onNavigateToVip = onNavigateToVip,
                            onNavigateToSearch = onNavigateToSearch,
                            onSelectCategoryTab = { index ->
                                coroutineScope.launch { categoryPagerState.animateScrollToPage(index) }
                            }
                        )

                        "Recently Added" -> RecentlyAddedCategoryScreen(
                            items = homeState.recentlyAdded,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        "Popular Series" -> PopularSeriesCategoryScreen(
                            items = homeState.popularDramas,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        "Shorts Drama" -> ShortsDramaCategoryScreen(
                            items = homeState.shortsContent,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        "Drama Series" -> DramaSeriesCategoryScreen(
                            items = homeState.dramaSeriesContent,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        "Anime Series" -> AnimeSeriesCategoryScreen(
                            items = homeState.animeContent,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        "Movies" -> MoviesCategoryScreen(
                            items = homeState.movieContent,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        "Bangla Dub" -> BanglaDubCategoryScreen(
                            items = homeState.banglaDubbed,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        "Hindi Dub" -> HindiDubCategoryScreen(
                            items = homeState.hindiDubbed,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )

                        else -> AllTitlesCategoryScreen(
                            items = homeState.popularDramas,
                            statusBarTop = statusBarTop,
                            onNavigateToPlayer = onNavigateToPlayer
                        )
                    }
                }
            }

            // 🔝 ফিক্সড টপ ন্যাভিগেশন বার
            TopNavigationBar(
                categories = categories,
                selectedCategoryIndex = categoryPagerState.currentPage,
                onCategorySelected = { index ->
                    coroutineScope.launch { categoryPagerState.animateScrollToPage(index) }
                },
                onSearchClick = onNavigateToSearch,
                onVoiceSearchClick = { startVoiceSearch() },
                onVipClick = onNavigateToVip,
                onNotificationClick = onNavigateToNotification,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

// =========================================================================
// 👑 ৩D গোল্ডেন VIP ক্রাউন আইকন (ProfileScreen এবং অন্যান্য স্ক্রিনের জন্য)
// =========================================================================
@Composable
fun Golden3DVipCrownIcon(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp, 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp, topStart = 6.dp, topEnd = 6.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFEA00),
                            Color(0xFFFF9100),
                            Color(0xFFFF6D00)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = Color(0xFFFFF176),
                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp, topStart = 6.dp, topEnd = 6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VIP",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.5.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744))
                    .border(1.dp, Color(0xFFFFD54F), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744))
                    .border(1.dp, Color(0xFFFFD54F), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744))
                    .border(1.dp, Color(0xFFFFD54F), CircleShape)
            )
        }
    }
}
