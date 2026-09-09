package com.example.ui.screens.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ads.StartAppBanner
import com.example.data.model.ContentItemDto
import com.example.ui.*
import com.example.ui.screens.CategoryGridDramaCard
import com.example.ui.screens.DramaPosterCardHorizontal
import com.example.ui.screens.VipPromoBanner
import com.example.ui.viewmodel.HomeUiState

/**
 * 🏠 ১. মূল হোম ফিড পেজ
 * (স্পটলাইট হিরো ব্যানার, ভিআইপি প্রমো ব্যানার, ক্যাটাগরি অনুযায়ী অনুভূমিক ড্রামা রো এবং অল টাইটেলস গ্রিড)
 */
@Composable
fun MainHomeFeedTab(
    homeState: HomeUiState,
    isVip: Boolean,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToVip: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onSeeAllCategory: (categoryIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // ভিউ অনুযায়ী সাজানো জনপ্রিয় সিরিজ
    val sortedPopularByViews = homeState.popularDramas.sortedByDescending { it.numericViews }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = statusBarTop + 94.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 🌟 ১. হট স্পটলাইট হিরো ব্যানার (Hero Carousel)
        // =========================================================================
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

        // =========================================================================
        // 👑 ২. ৩D ক্রাউন সহ VIP প্রোমো ব্যানার
        // =========================================================================
        item {
            VipPromoBanner(
                onVipClick = onNavigateToVip,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // =========================================================================
        // 🎬 ৩. Recently Added সেকশন (ইনডেক্স: ১)
        // =========================================================================
        if (homeState.recentlyAdded.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Added",
                    onSeeAllClick = { onSeeAllCategory(1) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // =========================================================================
        // 🎬 ৪. Popular Series সেকশন (ইনডেক্স: ২)
        // =========================================================================
        if (sortedPopularByViews.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Popular Series",
                    onSeeAllClick = { onSeeAllCategory(2) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // =========================================================================
        // 🎬 ৫. Shorts Drama সেকশন (ইনডেক্স: ৩)
        // =========================================================================
        if (homeState.shortsContent.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Shorts Drama",
                    onSeeAllClick = { onSeeAllCategory(3) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // =========================================================================
        // 🎬 ৬. Drama Series সেকশন (ইনডেক্স: ৪)
        // =========================================================================
        if (homeState.dramaSeriesContent.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Drama Series",
                    onSeeAllClick = { onSeeAllCategory(4) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // =========================================================================
        // 🎬 ৭. Bangla Dub সেকশন (ইনডেক্স: ৭)
        // =========================================================================
        if (homeState.banglaDubbed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Bangla Dub",
                    onSeeAllClick = { onSeeAllCategory(7) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // =========================================================================
        // 🎬 ৮. Hindi Dub সেকশন (ইনডেক্স: ৮)
        // =========================================================================
        if (homeState.hindiDubbed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Hindi Dub",
                    onSeeAllClick = { onSeeAllCategory(8) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // =========================================================================
        // 🎬 ৯. All Titles সেকশন (৩-কলাম গ্রিড)
        // =========================================================================
        item {
            SectionHeader(
                title = "All Titles",
                onSeeAllClick = onNavigateToSearch
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
                        CategoryGridDramaCard(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
                repeat(3 - rowDramas.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // =========================================================================
        // 📢 ১০. StartApp Ad Banner
        // =========================================================================
        item {
            StartAppBanner(
                isVip = isVip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
