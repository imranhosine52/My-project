@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
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

    // 🔄 ১. স্ক্রিনে ফিরে আসার সাথে সাথে ব্যাকএন্ড থেকে নতুন পোস্ট লোড হবে
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadHomeContent()
        viewModel.refreshVipStatusAndProfile()
    }

    // 🏷️ ২. শর্ট ও পরিচ্ছন্ন ক্যাটাগরি তালিকা
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
                    val currentCategory = categories.getOrElse(page) { "Home" }

                    if (currentCategory == "Home") {
                        // 🏠 মূল হোম পেজ ফিড
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = statusBarTop + 94.dp, bottom = 72.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Spotlight Hero
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

                            // VIP Banner
                            item {
                                VipPromoBanner(
                                    onVipClick = onNavigateToVip,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }

                            // 1/ Recently Added
                            if (homeState.recentlyAdded.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Recently Added",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(1) }
                                        }
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(homeState.recentlyAdded) { drama ->
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) }
                                            )
                                        }
                                    }
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
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(sortedPopularByViews.take(10)) { drama ->
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) }
                                            )
                                        }
                                    }
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
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(homeState.shortsContent) { drama ->
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) }
                                            )
                                        }
                                    }
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
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(homeState.dramaSeriesContent) { drama ->
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 5/ Bangla Dub
                            if (homeState.banglaDubbed.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Bangla Dub",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(7) }
                                        }
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(homeState.banglaDubbed) { drama ->
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 6/ Hindi Dub
                            if (homeState.hindiDubbed.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Hindi Dub",
                                        onSeeAllClick = {
                                            coroutineScope.launch { categoryPagerState.animateScrollToPage(8) }
                                        }
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(homeState.hindiDubbed) { drama ->
                                            DramaPosterCardHorizontal(
                                                drama = drama,
                                                onClick = { onNavigateToPlayer(drama.slug) }
                                            )
                                        }
                                    }
                                }
                            }

                            // 7/ All Titles Grid
                            item {
                                SectionHeader(
                                    title = "All Titles",
                                    onSeeAllClick = { onNavigateToSearch() }
                                )
                            }

                            val allGridRows = homeState.popularDramas.chunked(3)
                            items(allGridRows) { rowDramas ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowDramas.forEach { drama ->
                                        Box(modifier = Modifier.weight(1f)) {
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
                        // 📂 ক্যাটাগরি পেজ (Tabs)
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
                            contentPadding = PaddingValues(
                                top = statusBarTop + 94.dp,
                                bottom = 72.dp,
                                start = 12.dp,
                                end = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(gridRows) { rowDramas ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowDramas.forEach { drama ->
                                        Box(modifier = Modifier.weight(1f)) {
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

            // ফিক্সড টপ ন্যাভিগেশন বার
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
// ✨ শাইনিং বর্ডার অ্যানিমেশন সহ পোস্টার কার্ড (1dp Animated Shining Line)
// =========================================================================
@Composable
fun DramaPosterCardHorizontal(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 🌟 কার্ডের চারপাশে আলোর রেখা ঘোরার জন্য ইনফিনিট ট্রানজিশন
    val infiniteTransition = rememberInfiniteTransition(label = "borderShine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    // ✨ চিকন শাইনিং গ্রেডিয়েন্ট ব্রাশ
    val shineBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x33FFFFFF),            // হালকা বেসিক বর্ডার
            Color(0xFF00E5FF).copy(alpha = 0.8f),  // গ্লোয়িং সায়ান শাইন
            Color(0xFFFFD700).copy(alpha = 0.85f), // গোল্ডেন শাইন
            Color(0x33FFFFFF)             // হালকা বেসিক বর্ডার
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 250f, 350f)
    )

    Column(
        modifier = modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,              // 👈 চিকন ১ ডিপি লাইন
                    brush = shineBorderBrush,  // 👈 শাইনিং অ্যানিমেশন
                    shape = RoundedCornerShape(10.dp)
                )
                .background(Color(0xFF1E2430))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl ?: drama.bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drama.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // নিচের ডার্ক শ্যাডো
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000), Color(0xF0000000))
                        )
                    )
            )

            // 🏷️ ডাব ব্যাজ (Bangla / Hindi)
            val isBangla = drama.isBanglaDub || drama.dubBadge.contains("Bangla", ignoreCase = true)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, topEnd = 10.dp))
                    .background(if (isBangla) Color(0xFFFFB300) else Color(0xFF00B0FF))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isBangla) "Bangla" else "Hindi",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // 📺 এপিসোড সংখ্যা
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Episodes" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ড্রামার শিরোনাম (১ লাইনে)
        Text(
            text = drama.title,
            color = Color(0xFFE2E8F0),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

// =========================================================================
// 👑 VIP প্রোমো ব্যানার
// =========================================================================
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
