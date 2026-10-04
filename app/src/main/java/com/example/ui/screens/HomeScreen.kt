@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.TopNavigationBar
import com.example.ui.screens.categories.*
import com.example.ui.screens.profile.components.VersionScannerDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.AppAnalyticsTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(
    viewModel: DramaFlixViewModel,
    initialCategory: String = "Home",
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToVip: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 🚀 লাইভ অ্যাপ আপডেট স্ক্যানার পপ-আপ কন্ট্রোল
    var showVersionScannerDialog by remember { mutableStateOf(false) }
    val installedVersion = remember { viewModel.getInstalledAppVersion() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadHomeContent(forceRefresh = true)
        viewModel.refreshVipStatusAndProfile()
    }

    val categories = remember {
        listOf(
            "Home",        // 0
            "New",         // 1
            "Popular",     // 2
            "Short TV",    // 3
            "Series",      // 4
            "Anime",       // 5
            "Movies",      // 6
            "Bangla Dub",  // 7
            "Hindi Dub",   // 8
            "All Series"   // 9
        )
    }

    fun resolveCategoryIndex(target: String): Int {
        val directIndex = categories.indexOf(target)
        if (directIndex != -1) return directIndex

        return when {
            target.equals("Shorts Drama", ignoreCase = true) || 
            target.equals("Short TV", ignoreCase = true) || 
            target.equals("Shorts", ignoreCase = true) -> categories.indexOf("Short TV")

            target.equals("Recently Added", ignoreCase = true) || 
            target.equals("New", ignoreCase = true) -> categories.indexOf("New")

            target.equals("Popular Series", ignoreCase = true) || 
            target.equals("Popular", ignoreCase = true) -> categories.indexOf("Popular")

            target.equals("Drama Series", ignoreCase = true) || 
            target.equals("Series", ignoreCase = true) -> categories.indexOf("Series")

            target.equals("Anime Series", ignoreCase = true) || 
            target.equals("Anime", ignoreCase = true) -> categories.indexOf("Anime")

            target.equals("All", ignoreCase = true) || 
            target.equals("All Series", ignoreCase = true) -> categories.indexOf("All Series")

            else -> 0
        }
    }

    val initialPage = remember(initialCategory) {
        resolveCategoryIndex(initialCategory)
    }

    val categoryPagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { categories.size }
    )

    // 📊 অ্যানালিটিক্স স্ক্রিন ট্র্যাকার
    LaunchedEffect(categoryPagerState.currentPage) {
        val activeCategory = categories.getOrElse(categoryPagerState.currentPage) { "Home" }
        val screenLabel = if (activeCategory == "Home") "Home Screen" else "Category: $activeCategory"
        val numericUserId = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull()
        AppAnalyticsTracker.trackScreen(context, screenLabel, numericUserId)
    }

    // 🎯 স্মুথ ট্যাব অ্যানিমেশন
    LaunchedEffect(initialCategory) {
        val targetIdx = resolveCategoryIndex(initialCategory)
        if (categoryPagerState.currentPage != targetIdx) {
            categoryPagerState.animateScrollToPage(
                page = targetIdx,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
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
        if (homeState.isLoading && !isRefreshing && homeState.popularDramas.isEmpty()) {
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
                        viewModel.loadHomeContent(forceRefresh = true)
                        viewModel.refreshVipStatusAndProfile()
                        delay(500)
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
                    // 🌟 মাখনের মতো মসৃণ পেজ ট্রানজিশন ইফেক্ট (Alpha & Scale Animation)
                    val pageOffset = ((categoryPagerState.currentPage - page) + categoryPagerState.currentPageOffsetFraction).absoluteValue
                    val pageAlpha = lerp(0.55f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))
                    val pageScale = lerp(0.96f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))

                    // 🎯 কনটেন্ট মার্জিন একদম উপরে তুলে দেওয়া হয়েছে
                    val compactTopMargin = (statusBarTop - 24.dp).coerceAtLeast(0.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = pageAlpha
                                scaleX = pageScale
                                scaleY = pageScale
                            }
                    ) {
                        when (categories.getOrElse(page) { "Home" }) {
                            "Home" -> MainHomeFeedTab(
                                homeState = homeState,
                                isVip = authState.isVip,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer,
                                onNavigateToVip = onNavigateToVip,
                                onNavigateToSearch = onNavigateToSearch,
                                onSelectCategoryTab = { index ->
                                    coroutineScope.launch {
                                        categoryPagerState.animateScrollToPage(
                                            page = index,
                                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            )

                            "New" -> RecentlyAddedCategoryScreen(
                                items = homeState.recentlyAdded,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            "Popular" -> PopularSeriesCategoryScreen(
                                items = homeState.popularDramas,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            "Short TV" -> ShortsDramaCategoryScreen(
                                items = homeState.shortsContent,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            "Series" -> DramaSeriesCategoryScreen(
                                items = homeState.dramaSeriesContent,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            "Anime" -> AnimeSeriesCategoryScreen(
                                items = homeState.animeContent,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            "Movies" -> MoviesCategoryScreen(
                                items = homeState.movieContent,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            "Bangla Dub" -> BanglaDubCategoryScreen(
                                items = homeState.banglaDubbed,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            "Hindi Dub" -> HindiDubCategoryScreen(
                                items = homeState.hindiDubbed,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )

                            else -> AllTitlesCategoryScreen(
                                items = homeState.popularDramas,
                                statusBarTop = compactTopMargin,
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                        }
                    }
                }
            }

            // 🔝 কমপ্যাক্ট আল্ট্রা-স্লিম টপ বার
            TopNavigationBar(
                categories = categories,
                selectedCategoryIndex = categoryPagerState.currentPage,
                onCategorySelected = { index ->
                    coroutineScope.launch {
                        categoryPagerState.animateScrollToPage(
                            page = index,
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                        )
                    }
                },
                onSearchClick = onNavigateToSearch,
                onVoiceSearchClick = { startVoiceSearch() },
                onVipClick = onNavigateToVip,
                onUpdateCheckClick = { showVersionScannerDialog = true },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // =========================================================================
        // 🛰️ লাইভ অ্যাপ আপডেট ও ভার্সন স্ক্যানার ডায়ালগ
        // =========================================================================
        if (showVersionScannerDialog) {
            VersionScannerDialog(
                viewModel = viewModel,
                installedVersion = installedVersion,
                onDismiss = { showVersionScannerDialog = false }
            )
        }
    }
}
