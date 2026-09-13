package com.example.ui.screens.categories

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.example.ui.SectionHeader
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeUiState

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
        contentPadding = PaddingValues(top = statusBarTop + 94.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ১. Spotlight Hero Carousel
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

        // ২. VIP Promo Banner
        item {
            HomeVipPromoBanner(
                onVipClick = onNavigateToVip,
                modifier = Modifier.padding(horizontal = 12.dp)
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
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    onSelectCategoryTab = { onSelectCategoryTab(3) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// =========================================================================
// 🖼️ বড় সাইজের হরিজন্টাল কার্ড (চিকন গোল্ডেন শিমার অ্যানিমেশন সহ)
// =========================================================================
@Composable
fun HomePosterCardHorizontal(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 🌟 খাঁটি গোল্ডেন শিমার অ্যানিমেশন
    val infiniteTransition = rememberInfiniteTransition(label = "goldenCardShine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    // ✨ ০.৭dp চিকন প্রিমিয়াম গোল্ডেন গ্রেডিয়েন্ট ব্রাশ
    val goldenShineBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x22FFD700),
            Color(0xFFFFD700).copy(alpha = 0.92f), // Glowing Pure Gold
            Color(0xFFFFB300).copy(alpha = 0.85f), // Rich Amber
            Color(0x22FFD700)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 240f, 340f)
    )

    Column(
        modifier = modifier
            .width(145.dp) // 👈 বড় ও সিনেমাটিক প্রস্থ
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(205.dp) // 👈 বড় ও ক্লিয়ার উচ্চতা
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 0.7.dp, // 👈 অতি চিকন ও নিখুঁত বর্ডার লাইন
                    brush = goldenShineBorderBrush,
                    shape = RoundedCornerShape(12.dp)
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
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x99000000), Color(0xF5000000))
                        )
                    )
            )

            val isBangla = drama.isBanglaDub || drama.dubBadge.contains("Bangla", ignoreCase = true)
            val badgeColor = if (isBangla) Color(0xFFFFB300) else Color(0xFF00B0FF)

            // ভাষা ব্যাজ
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, topEnd = 12.dp))
                    .background(badgeColor)
                    .padding(horizontal = 7.5.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = if (isBangla) "Bangla" else "Hindi",
                    color = Color.Black,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black
                )
            }

            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Episodes" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = drama.title,
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

// =========================================================================
// 🖼️ হোম পেজের নিচের গ্রিড কার্ড (চিকন গোল্ডেন শিমার সহ)
// =========================================================================
@Composable
fun HomeGridDramaCard(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "homeGridCardShine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val goldenShineBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x22FFD700),
            Color(0xFFFFD700).copy(alpha = 0.92f),
            Color(0xFFFFB300).copy(alpha = 0.85f),
            Color(0x22FFD700)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 240f, 340f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(11.dp))
                .border(
                    width = 0.7.dp, // 👈 অতি চিকন ও প্রিমিয়াম বর্ডার লাইন
                    brush = goldenShineBorderBrush,
                    shape = RoundedCornerShape(11.dp)
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

            val isBangla = drama.isBanglaDub || drama.dubBadge.contains("Bangla", ignoreCase = true)
            val badgeColor = if (isBangla) Color(0xFFFFB300) else Color(0xFF00B0FF)

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, topEnd = 11.dp))
                    .background(badgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isBangla) "Bangla" else "Hindi",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }

            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Episodes" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = drama.title,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

// =========================================================================
// 👑 VIP ব্যানার
// =========================================================================
@Composable
fun HomeVipPromoBanner(
    onVipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2E2405), Color(0xFF1E1700), Color(0xFF131000))
                )
            )
            .border(1.dp, Color(0xFF5E4804), RoundedCornerShape(14.dp))
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeGolden3DVipCrownIcon()

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
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Get VIP",
                    color = GoldButtonText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun HomeGolden3DVipCrownIcon(
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
