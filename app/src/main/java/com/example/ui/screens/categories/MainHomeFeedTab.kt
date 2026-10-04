package com.example.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.StartAppBanner
import com.example.data.model.ContentItemDto
import com.example.ui.HotSpotlightHeroCard
import com.example.ui.LanguageDubBadge
import com.example.ui.SectionHeader
import com.example.ui.VipCrown3DIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeUiState
import java.util.Locale

private val GoldRating = Color(0xFFFFB300)

@Composable
fun MainHomeFeedTab(
    homeState: HomeUiState,
    isVip: Boolean,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToVip: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onSelectCategoryTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedPopularByViews = remember(homeState.popularDramas) {
        homeState.popularDramas.sortedByDescending { it.numericViews }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = statusBarTop + 84.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ১. Spotlight Hero Carousel
        if (homeState.spotlightDramas.isNotEmpty()) {
            item {
                HotSpotlightHeroCard(
                    spotlightDramas = homeState.spotlightDramas,
                    onWatchClick = { drama -> onNavigateToPlayer(drama.slug) },
                    onDetailsClick = { drama -> onNavigateToPlayer(drama.slug) },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }

        // ২. VIP Promo Banner
        item {
            HomeVipPromoBanner(
                onVipClick = onNavigateToVip,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }

        // ৩. Recently Added
        if (homeState.recentlyAdded.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Added",
                    onSeeAllClick = { onSelectCategoryTab(1) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(homeState.recentlyAdded) { drama ->
                        HomePosterCardHorizontal(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }

        // ৪. Popular Series
        if (sortedPopularByViews.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Popular Series",
                    onSeeAllClick = { onSelectCategoryTab(2) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedPopularByViews.take(10)) { drama ->
                        HomePosterCardHorizontal(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }

        // ৫. Shorts Drama
        if (homeState.shortsContent.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Shorts Drama",
                    onSeeAllClick = { onSelectCategoryTab(3) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(homeState.shortsContent) { drama ->
                        HomePosterCardHorizontal(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }

        // ৬. Drama Series
        if (homeState.dramaSeriesContent.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Drama Series",
                    onSeeAllClick = { onSelectCategoryTab(4) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(homeState.dramaSeriesContent) { drama ->
                        HomePosterCardHorizontal(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }

        // ৭. Bangla Dub
        if (homeState.banglaDubbed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Bangla Dub",
                    onSeeAllClick = { onSelectCategoryTab(7) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(homeState.banglaDubbed) { drama ->
                        HomePosterCardHorizontal(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }

        // ৮. Hindi Dub
        if (homeState.hindiDubbed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Hindi Dub",
                    onSeeAllClick = { onSelectCategoryTab(8) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(homeState.hindiDubbed) { drama ->
                        HomePosterCardHorizontal(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }

        // ৯. All Titles Grid (৩ কলাম)
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
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowDramas.forEach { drama ->
                    Box(modifier = Modifier.weight(1f)) {
                        HomeGridDramaCard(
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

        // ১০. Start.io Ad Banner
        item {
            StartAppBanner(
                isVip = isVip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}

// =========================================================================
// 🖼️ কমপ্যাক্ট হরিজন্টাল কার্ড (হালকা রেটিং ও Display Name সহ)
// =========================================================================
@Composable
fun HomePosterCardHorizontal(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .width(118.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(162.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(0.6.dp, Color(0xFF1E2638), RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl ?: drama.bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drama.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // নিচের ডার্ক শ্যাডো
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x99000000), Color(0xF0000000))
                        )
                    )
            )

            // ডাইনামিক ডাবিং ব্যাজ
            LanguageDubBadge(
                dubText = drama.dubBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // এপিসোড সংখ্যা (নিচে বাঁয়ে)
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Ep" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 4.dp)
            )

            // 🎯 হালকা রেটিং (নিচে ডানে - কোনো সলিড বক্স ছাড়া)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldRating,
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = if (drama.rating > 0) String.format(Locale.US, "%.1f", drama.rating) else "8.5",
                    color = GoldRating,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // ছোট ও পরিচ্ছন্ন নাম
        Text(
            text = drama.displayName,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// 🖼️ হোম পেজের ৩-কলাম গ্রিড কার্ড (হালকা রেটিং ও Display Name সহ)
// =========================================================================
@Composable
fun HomeGridDramaCard(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .clip(RoundedCornerShape(8.dp))
                .border(0.6.dp, Color(0xFF1E2638), RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl ?: drama.bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drama.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // ডাইনামিক ডাবিং ব্যাজ
            LanguageDubBadge(
                dubText = drama.dubBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // এপিসোড সংখ্যা (নিচে বাঁয়ে)
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Ep" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            )

            // 🎯 হালকা রেটিং (নিচে ডানে)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldRating,
                    modifier = Modifier.size(8.5.dp)
                )
                Text(
                    text = if (drama.rating > 0) String.format(Locale.US, "%.1f", drama.rating) else "8.5",
                    color = GoldRating,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // ছোট ও পরিচ্ছন্ন নাম
        Text(
            text = drama.displayName,
            color = Color(0xFFE2E8F0),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// 👑 স্লিম VIP ব্যানার
// =========================================================================
@Composable
fun HomeVipPromoBanner(
    onVipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2E2405), Color(0xFF1E1700), Color(0xFF131000))
                )
            )
            .border(0.8.dp, Color(0xFF5E4804), RoundedCornerShape(10.dp))
            .clickable { onVipClick() }
            .padding(10.dp)
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
                VipCrown3DIcon(modifier = Modifier.size(24.dp, 20.dp))

                Column {
                    Text(
                        text = "Upgrade to VIP All-Access",
                        color = GoldVip,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zero Ads • 1080p Ultra HD • All Episodes Unlocked",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(GoldVip)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Get VIP",
                    color = GoldButtonText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
