package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.StartAppBanner
import com.example.data.model.ContentItemDto
import com.example.ui.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

@Composable
fun HomeScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToVip: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onCategorySelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf("Home") }

    val filteredList = remember(selectedCategory, homeState) {
        when {
            selectedCategory.equals("Shorts", ignoreCase = true) -> homeState.shortsContent.ifEmpty { homeState.popularDramas }
            selectedCategory.equals("Anime", ignoreCase = true) -> homeState.animeContent.ifEmpty { homeState.popularDramas }
            selectedCategory.equals("Drama", ignoreCase = true) -> (homeState.koreanDramas + homeState.chineseDramas).distinctBy { it.id }.ifEmpty { homeState.popularDramas }
            selectedCategory.equals("Movie", ignoreCase = true) -> homeState.popularDramas.filter { it.type.equals("movie", ignoreCase = true) }.ifEmpty { homeState.popularDramas }
            selectedCategory.equals("Bangla", ignoreCase = true) -> homeState.banglaDubbed.ifEmpty { homeState.popularDramas }
            selectedCategory.equals("Hindi", ignoreCase = true) -> homeState.hindiDubbed.ifEmpty { homeState.popularDramas }
            else -> homeState.popularDramas
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
                CircularProgressIndicator(
                    color = TealAccent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_screen_list"),
                contentPadding = PaddingValues(top = 80.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // If "Home" category is selected -> Show full rich homepage with Hot Spotlight, Dubbed rows, VIP Banner etc.
                if (selectedCategory == "Home" || selectedCategory == "All") {
                    // Hot Spotlight Hero Card
                    if (homeState.spotlightDramas.isNotEmpty()) {
                        item {
                            HotSpotlightHeroCard(
                                spotlightDramas = homeState.spotlightDramas,
                                onWatchClick = { drama -> onNavigateToPlayer(drama.slug) },
                                onDetailsClick = { drama -> onNavigateToPlayer(drama.slug) },
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }

                    // VIP All-Access Promo Banner
                    item {
                        VipPromoBanner(
                            onVipClick = onNavigateToVip,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }

                    // Bangla Dubbed Dramas Row
                    if (homeState.banglaDubbed.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Bangla Dubbed Dramas",
                                onSeeAllClick = {
                                    selectedCategory = "Bangla"
                                    onCategorySelected("Bangla")
                                }
                            )
                            HorizontalDramaRow(
                                dramas = homeState.banglaDubbed,
                                onDramaClick = { onNavigateToPlayer(it.slug) }
                            )
                        }
                    }

                    // Trending Dramas Row
                    if (homeState.trendingDramas.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Trending Now 🔥",
                                onSeeAllClick = { onNavigateToSearch() }
                            )
                            HorizontalDramaRow(
                                dramas = homeState.trendingDramas,
                                onDramaClick = { onNavigateToPlayer(it.slug) }
                            )
                        }
                    }

                    // Hindi Dubbed Dramas Row
                    if (homeState.hindiDubbed.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Hindi Dubbed Series",
                                onSeeAllClick = {
                                    selectedCategory = "Hindi"
                                    onCategorySelected("Hindi")
                                }
                            )
                            HorizontalDramaRow(
                                dramas = homeState.hindiDubbed,
                                onDramaClick = { onNavigateToPlayer(it.slug) }
                            )
                        }
                    }

                    // Korean Dramas Row
                    if (homeState.koreanDramas.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Korean K-Dramas",
                                onSeeAllClick = {
                                    selectedCategory = "Drama"
                                    onCategorySelected("Drama")
                                }
                            )
                            HorizontalDramaRow(
                                dramas = homeState.koreanDramas,
                                onDramaClick = { onNavigateToPlayer(it.slug) }
                            )
                        }
                    }

                    // Chinese Dramas Row
                    if (homeState.chineseDramas.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Chinese C-Dramas",
                                onSeeAllClick = {
                                    selectedCategory = "Drama"
                                    onCategorySelected("Drama")
                                }
                            )
                            HorizontalDramaRow(
                                dramas = homeState.chineseDramas,
                                onDramaClick = { onNavigateToPlayer(it.slug) }
                            )
                        }
                    }

                    // Anime Row
                    if (homeState.animeContent.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Anime & Animation",
                                onSeeAllClick = {
                                    selectedCategory = "Anime"
                                    onCategorySelected("Anime")
                                }
                            )
                            HorizontalDramaRow(
                                dramas = homeState.animeContent,
                                onDramaClick = { onNavigateToPlayer(it.slug) }
                            )
                        }
                    }

                    // All Popular Dramas Grid Section
                    item {
                        SectionHeader(
                            title = "Popular Dramas",
                            onSeeAllClick = { onNavigateToSearch() }
                        )
                    }

                    val dramaGridChunks = homeState.popularDramas.chunked(3)
                    items(dramaGridChunks) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { drama ->
                                Box(modifier = Modifier.weight(1f)) {
                                    DramaPosterCardHorizontal(
                                        drama = drama,
                                        onClick = { onNavigateToPlayer(drama.slug) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            // Fill blank weights if row has fewer than 3 items
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // Filtered Tab View (e.g. Shorts, Anime, Drama, Movies)
                    item {
                        SectionHeader(
                            title = "$selectedCategory Collection (${filteredList.size})",
                            onSeeAllClick = {}
                        )
                    }

                    val filteredChunks = filteredList.chunked(3)
                    items(filteredChunks) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { drama ->
                                Box(modifier = Modifier.weight(1f)) {
                                    DramaPosterCardHorizontal(
                                        drama = drama,
                                        onClick = { onNavigateToPlayer(drama.slug) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Adaptive Start.io Banner Ad for Free Users (Suppressed if VIP)
                item {
                    StartAppBanner(
                        isVip = authState.isVip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Fixed Top Header & Navigation Bar
            TopNavigationBar(
                categories = homeState.categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { cat ->
                    selectedCategory = cat
                    onCategorySelected(cat)
                },
                onSearchClick = onNavigateToSearch,
                onVipClick = onNavigateToVip,
                onNotificationClick = {
                    // Open notifications / updates dialog
                    viewModel.checkAppVersion()
                },
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
                    listOf(
                        Color(0xFF2E2405),
                        Color(0xFF1E1700),
                        Color(0xFF131000)
                    )
                )
            )
            .clickable { onVipClick() }
            .padding(14.dp)
            .testTag("vip_promo_banner")
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
