@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.StartAppBanner
import com.example.data.model.ContentItemDto
import com.example.ui.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: DramaFlixViewModel,
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

    val categoryPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { categories.size }
    )

    val sortedPopularByViews = remember(homeState.popularDramas) {
        homeState.popularDramas.sortedByDescending { it.numericViews }
    }

    // ✨ কার্ডের চারপাশের ১ ডিপি শাইনিং অ্যানিমেশন ব্রাশ
    val infiniteTransition = rememberInfiniteTransition(label = "home_card_shine")
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "home_shine_offset"
    )

    val shiningBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E293B),
            Color(0xFF00E5FF).copy(alpha = 0.7f),
            Color(0xFF1E293B)
        ),
        start = Offset(shineOffset, shineOffset),
        end = Offset(shineOffset + 180f, shineOffset + 180f)
    )

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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search drama in any language (বাংলা, English, हिंदी)...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice recognition is not available on this device", Toast.LENGTH_SHORT).show()
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
                CircularProgressIndicator(color = TealAccent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
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
                    val currentCategory = categories.getOrElse(page) { "Home" }

                    if (currentCategory == "Home") {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = statusBarTop + 94.dp, bottom = 72.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (homeState.spotlightDramas.isNotEmpty()) {
                                item {
                                    HotSpotlightHeroCard(
                                        spotlightDramas = homeState.spotlightDramas,
                                        onWatchClick = { drama -> onNavigateToPlayer(drama.slug) },
                                        onDetailsClick = { drama -> onNavigateToPlayer(drama.slug) },
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }

                            item {
                                VipPromoBanner(
                                    onVipClick = onNavigateToVip,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }

                            // 1/ Recently Added (🎯 সম্পূর্ণ নতুন ড্রামাগুলো আগে আসবে)
                            if (homeState.recentlyAdded.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Recently Added",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(1) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = homeState.recentlyAdded,
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 2/ Popular Series
                            if (sortedPopularByViews.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Popular Series",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(2) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = sortedPopularByViews.take(10),
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 3/ Shorts Drama
                            if (homeState.shortsContent.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Shorts Drama",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(3) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = homeState.shortsContent,
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 4/ Drama Series
                            if (homeState.dramaSeriesContent.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Drama Series",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(4) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = homeState.dramaSeriesContent,
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 5/ Anime Series
                            if (homeState.animeContent.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Anime Series",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(5) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = homeState.animeContent,
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 6/ Movies
                            if (homeState.movieContent.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Movies",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(6) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = homeState.movieContent,
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 7/ Bangla Dub
                            if (homeState.banglaDubbed.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Bangla Dubbed Dramas",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(7) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = homeState.banglaDubbed,
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 8/ Hindi Dub
                            if (homeState.hindiDubbed.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Hindi Dubbed Series",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(8) }
                                        }
                                    )
                                    HorizontalDramaRow(
                                        dramas = homeState.hindiDubbed,
                                        onDramaClick = { onNavigateToPlayer(it.slug) }
                                    )
                                }
                            }

                            // 9/ All Series Grid (✨ ১ ডিপি শাইনিং বর্ডার সহ)
                            item {
                                SectionHeader(
                                    title = "All Series & Titles",
                                    onSeeAllClick = { onNavigateToSearch() }
                                )
                            }

                            val allGridRows = homeState.popularDramas.chunked(3)
                            items(allGridRows) { rowDramas ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowDramas.forEach { drama ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = 1.dp,
                                                    brush = shiningBorderBrush,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    repeat(3 - rowDramas.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            item {
                                StartAppBanner(
                                    isVip = authState.isVip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        // 📂 FILTERED CATEGORY TABS (✨ ১ ডিপি শাইনিং বর্ডার সহ)
                        val catItems = remember(currentCategory, homeState) {
                            when (currentCategory) {
                                "Recently Added" -> homeState.recentlyAdded
                                "Popular Series" -> sortedPopularByViews
                                "Shorts Drama" -> homeState.shortsContent
                                "Drama Series" -> homeState.dramaSeriesContent
                                "Anime Series" -> homeState.animeContent
                                "Movies" -> homeState.movieContent
                                "Bangla Dub" -> homeState.banglaDubbed
                                "Hindi Dub" -> homeState.hindiDubbed
                                else -> homeState.popularDramas
                            }
                        }

                        val gridRows = catItems.chunked(3)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = statusBarTop + 94.dp, bottom = 72.dp, start = 12.dp, end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(gridRows) { rowDramas ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowDramas.forEach { drama ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = 1.dp,
                                                    brush = shiningBorderBrush,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    repeat(3 - rowDramas.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

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

@Composable
fun VipPromoBanner(
    onVipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2E2405), Color(0xFF1E1700), Color(0xFF131000))
                )
            )
            .clickable { onVipClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VipCrownVectorIcon(modifier = Modifier.size(34.dp, 26.dp))
                Column {
                    Text(
                        text = "Upgrade to VIP All-Access",
                        color = GoldVip,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zero Ads • 1080p Full HD • All Episodes Unlocked",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(GoldVip)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Get VIP",
                    color = GoldButtonText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
