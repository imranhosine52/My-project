@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.StartAppBanner
import com.example.data.model.ContentItemDto
import com.example.ui.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
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

    val categories = remember(homeState.categories) {
        if (homeState.categories.isNotEmpty()) {
            homeState.categories.map { if (it.equals("All", ignoreCase = true)) "Home" else it }.distinct()
        } else {
            listOf("Home", "Shorts Drama", "Drama Series", "Bangla Dub", "Hindi Dub", "Anime Series", "Movies")
        }
    }

    // 📲 স্মুথ সোয়াইপ স্লাইডিং পেজার স্টেট (ডানে-বামে টান দিলে ক্যাটাগরি পাল্টাবে)
    val categoryPagerState = rememberPagerState(pageCount = { categories.size })

    // 🎙️ মাল্টি-ল্যাঙ্গুয়েজ ভয়েস সার্চ
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
        if (homeState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TealAccent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            }
        } else {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            // 📲 Horizontal Pager: পেজে ধরে টান দিলে এক ক্যাটাগরি থেকে অন্যটিতে স্মুথভাবে যাবে
            HorizontalPager(
                state = categoryPagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val currentCategory = categories[page]

                if (currentCategory == "Home" || currentCategory == "All") {
                    // ----------------- 🏠 MAIN RICH HOMEPAGE -----------------
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = statusBarTop + 94.dp, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Hot Spotlight Hero Slider
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

                        // VIP Promo Banner
                        item {
                            VipPromoBanner(
                                onVipClick = onNavigateToVip,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        // Recently Added Row
                        if (homeState.recentlyAdded.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Recently Added",
                                    onSeeAllClick = {
                                        val idx = categories.indexOfFirst { it.contains("Popular", ignoreCase = true) || it.contains("Drama", ignoreCase = true) }
                                        if (idx != -1) coroutineScope.launch { categoryPagerState.animateScrollToPage(idx) }
                                    }
                                )
                                HorizontalDramaRow(
                                    dramas = homeState.recentlyAdded,
                                    onDramaClick = { onNavigateToPlayer(it.slug) }
                                )
                            }
                        }

                        // Popular Series Row
                        if (homeState.popularDramas.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Popular Series",
                                    onSeeAllClick = {
                                        val idx = categories.indexOfFirst { it.contains("Drama Series", ignoreCase = true) }
                                        if (idx != -1) coroutineScope.launch { categoryPagerState.animateScrollToPage(idx) }
                                    }
                                )
                                HorizontalDramaRow(
                                    dramas = homeState.popularDramas.take(10),
                                    onDramaClick = { onNavigateToPlayer(it.slug) }
                                )
                            }
                        }

                        // Shorts Drama Row
                        if (homeState.shortsContent.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Shorts Drama",
                                    onSeeAllClick = {
                                        val idx = categories.indexOfFirst { it.contains("Shorts", ignoreCase = true) }
                                        if (idx != -1) coroutineScope.launch { categoryPagerState.animateScrollToPage(idx) }
                                    }
                                )
                                HorizontalDramaRow(
                                    dramas = homeState.shortsContent,
                                    onDramaClick = { onNavigateToPlayer(it.slug) }
                                )
                            }
                        }

                        // Drama Series Row
                        if (homeState.dramaSeriesContent.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Drama Series",
                                    onSeeAllClick = {
                                        val idx = categories.indexOfFirst { it.contains("Drama Series", ignoreCase = true) }
                                        if (idx != -1) coroutineScope.launch { categoryPagerState.animateScrollToPage(idx) }
                                    }
                                )
                                HorizontalDramaRow(
                                    dramas = homeState.dramaSeriesContent,
                                    onDramaClick = { onNavigateToPlayer(it.slug) }
                                )
                            }
                        }

                        // Bangla Dubbed Row
                        if (homeState.banglaDubbed.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Bangla Dubbed Dramas",
                                    onSeeAllClick = {
                                        val idx = categories.indexOfFirst { it.contains("Bangla", ignoreCase = true) }
                                        if (idx != -1) coroutineScope.launch { categoryPagerState.animateScrollToPage(idx) }
                                    }
                                )
                                HorizontalDramaRow(
                                    dramas = homeState.banglaDubbed,
                                    onDramaClick = { onNavigateToPlayer(it.slug) }
                                )
                            }
                        }

                        // Hindi Dubbed Row
                        if (homeState.hindiDubbed.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Hindi Dubbed Series",
                                    onSeeAllClick = {
                                        val idx = categories.indexOfFirst { it.contains("Hindi", ignoreCase = true) }
                                        if (idx != -1) coroutineScope.launch { categoryPagerState.animateScrollToPage(idx) }
                                    }
                                )
                                HorizontalDramaRow(
                                    dramas = homeState.hindiDubbed,
                                    onDramaClick = { onNavigateToPlayer(it.slug) }
                                )
                            }
                        }

                        // Start.io Banner Ad
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
                    // ----------------- 📂 FILTERED CATEGORY PAGE (3-COLUMN GRID) -----------------
                    val catItems = remember(currentCategory, homeState) {
                        when {
                            currentCategory.contains("Shorts", ignoreCase = true) -> homeState.shortsContent
                            currentCategory.contains("Drama Series", ignoreCase = true) -> homeState.dramaSeriesContent
                            currentCategory.contains("Bangla", ignoreCase = true) -> homeState.banglaDubbed
                            currentCategory.contains("Hindi", ignoreCase = true) -> homeState.hindiDubbed
                            currentCategory.contains("Anime", ignoreCase = true) -> homeState.animeContent
                            currentCategory.contains("Movie", ignoreCase = true) -> homeState.movieContent
                            else -> homeState.popularDramas.filter { drama ->
                                drama.categories.any { it.contains(currentCategory, ignoreCase = true) }
                            }.ifEmpty { homeState.popularDramas }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(top = statusBarTop + 94.dp, bottom = 72.dp, start = 12.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(catItems, key = { it.id }) { drama ->
                            DramaPosterCardHorizontal(
                                drama = drama,
                                onClick = { onNavigateToPlayer(drama.slug) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Fixed Top Header & Navigation Bar (Synced with Swiping Pager)
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
            .clip(RoundedCornerShape(16.dp))
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
