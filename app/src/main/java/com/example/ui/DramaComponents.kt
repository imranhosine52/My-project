package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.BottomNavTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun LanguageDubBadge(
    dubText: String,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 10.dp
) {
    val cleanText = when {
        dubText.contains("Bangla", ignoreCase = true) || dubText.contains("Bengali", ignoreCase = true) -> "Bangla"
        dubText.contains("Hindi", ignoreCase = true) -> "Hindi"
        dubText.isNotBlank() -> dubText.replace(" Dubbed", "").replace(" Dub", "").trim()
        else -> "Bangla"
    }

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = cornerRadius,
                    bottomStart = 4.dp,
                    bottomEnd = 0.dp
                )
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFC107),
                        Color(0xFFEAA61A)
                    )
                )
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = cleanText,
            color = Color(0xFF111111),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 11.sp,
            letterSpacing = 0.sp
        )
    }
}

@Composable
fun VipCrownBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 28.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        VipCrownVectorIcon(
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VipCrownVectorIcon(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val crownPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.22f, h * 0.86f)
                quadraticTo(w * 0.50f, h * 0.90f, w * 0.78f, h * 0.86f)
                quadraticTo(w * 0.84f, h * 0.65f, w * 0.82f, h * 0.42f)
                quadraticTo(w * 0.68f, h * 0.52f, w * 0.50f, h * 0.22f)
                quadraticTo(w * 0.32f, h * 0.52f, w * 0.18f, h * 0.42f)
                quadraticTo(w * 0.16f, h * 0.65f, w * 0.22f, h * 0.86f)
                close()
            }

            drawPath(
                path = crownPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFCA28), Color(0xFFFFB300), Color(0xFFFFA000)),
                    startY = h * 0.20f,
                    endY = h * 0.90f
                )
            )
        }

        Text(
            text = "VIP",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 2.dp)
        )
    }
}

@Composable
fun TopNavigationBar(
    categories: List<String>,
    selectedCategoryIndex: Int,
    notificationCount: Int = 3,
    onCategorySelected: (Int) -> Unit,
    onNotificationClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onVoiceSearchClick: () -> Unit = {},
    onVipClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFA0D0F14), Color(0xD90D0F14), Color(0x000D0F14))
                )
            )
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onCategorySelected(0) }
                    .padding(end = 6.dp)
            ) {
                Text("PD", color = Color(0xFF00D2FF), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Flix", color = Color(0xFFFF9900), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x2EFFFFFF))
                    .border(0.8.dp, Color(0x2EFFFFFF), RoundedCornerShape(50))
                    .clickable { onSearchClick() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Search show...",
                    color = Color(0xFFADB2BE),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = Color(0xFF00E676),
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { onVoiceSearchClick() }
                    )
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFFCCD0DB),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            VipCrownBadge(onClick = onVipClick)

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x2EFFFFFF))
                    .border(0.8.dp, Color(0x1FFFFFFF), CircleShape)
                    .clickable { onNotificationClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )

                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2A4B))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { index, tab ->
                val isSelected = (index == selectedCategoryIndex)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onCategorySelected(index) }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color(0x99FFFFFF),
                        fontSize = if (isSelected) 16.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.5.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HotSpotlightHeroCard(
    spotlightDramas: List<ContentItemDto>,
    onWatchClick: (ContentItemDto) -> Unit,
    onDetailsClick: (ContentItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (spotlightDramas.isEmpty()) return
    val totalPages = spotlightDramas.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalPages })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(pagerState.pageCount) {
        if (totalPages > 1) {
            while (isActive) {
                delay(3800L)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % totalPages
                    try {
                        pagerState.animateScrollToPage(
                            page = nextPage,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "banner_float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -4.5f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "poster_float_y"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1B2338), Color(0xFF121522), Color(0xFF0D0F17))
                )
            )
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val drama = spotlightDramas[page]

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GoldVip
                            ) {
                                Text(
                                    text = "HOT SPOTLIGHT",
                                    color = GoldButtonText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceVariantDark
                            ) {
                                Text(
                                    text = drama.releaseYear.ifBlank { "2023" },
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF282415),
                                border = BorderStroke(0.8.dp, GoldVip.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(11.dp))
                                    Text(
                                        text = if (drama.rating > 0) drama.rating.toString() else "6.9",
                                        color = GoldVip,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = drama.title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            (listOf("All", drama.dubBadge) + drama.categories.take(2)).filter { it.isNotBlank() }.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SurfaceVariantDark.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = tag,
                                        color = TextSecondary,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onWatchClick(drama) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldVip, contentColor = GoldButtonText),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GoldButtonText, modifier = Modifier.size(16.dp))
                                    Text("Watch Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { onDetailsClick(drama) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = TextPrimary),
                                border = BorderStroke(1.dp, BorderDark),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    Text("Details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(154.dp)
                            .graphicsLayer { translationY = floatY }
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 1.4.dp,
                                brush = Brush.verticalGradient(
                                    listOf(GoldVip.copy(alpha = 0.9f), Color(0xFF00E5FF).copy(alpha = 0.6f), GoldVip.copy(alpha = 0.4f))
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onWatchClick(drama) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(drama.posterUrl ?: drama.bannerUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = drama.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                spotlightDramas.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .height(3.5.dp)
                            .width(if (isSelected) 16.dp else 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) Color(0xFF388BFF) else SurfaceVariantDark)
                            .clickable {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFF2A4B))
            )
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.clickable { onSeeAllClick() }
        ) {
            Text(
                text = "See All",
                color = Color(0xFFFF2A4B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = Color(0xFFFF2A4B),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun HorizontalDramaRow(
    dramas: List<ContentItemDto>,
    onDramaClick: (ContentItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(dramas) { drama ->
            DramaPosterCardHorizontal(
                drama = drama,
                onClick = { onDramaClick(drama) },
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

@Composable
fun DramaPosterCardHorizontal(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHindi = drama.isHindiDub || drama.dubBadge.contains("Hindi", ignoreCase = true)
    val dubBadgeColor = if (isHindi) Color(0xFFFFB300) else Color(0xFFFFA000)

    Column(
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl ?: drama.bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drama.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            Surface(
                shape = RoundedCornerShape(topEnd = 10.dp, bottomStart = 6.dp),
                color = dubBadgeColor,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = if (isHindi) "Hindi" else "Bangla",
                    color = Color.Black,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(
                text = "${drama.totalEpisodes} Episodes",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = drama.title,
            color = Color(0xFFDCE0E8),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlayDramaFlixBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0A0C12),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 0.8.dp, color = BorderDark.copy(alpha = 0.5f))
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in BottomNavTab.entries) {
                val isSelected = tab == selectedTab

                if (tab == BottomNavTab.VIP) {
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .height(56.dp)
                            .clickable { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        VipCrownVectorIcon(modifier = Modifier.size(40.dp, 30.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val iconTint = if (isSelected) Color(0xFF00D166) else TextMuted

                            when (tab) {
                                BottomNavTab.HOME -> Icon(Icons.Default.Home, contentDescription = tab.label, tint = iconTint, modifier = Modifier.size(24.dp))
                                BottomNavTab.SEARCH -> Icon(Icons.Default.Search, contentDescription = tab.label, tint = iconTint, modifier = Modifier.size(24.dp))
                                BottomNavTab.WATCHLIST -> Icon(Icons.Default.Bookmark, contentDescription = tab.label, tint = iconTint, modifier = Modifier.size(24.dp))
                                BottomNavTab.PROFILE -> Icon(Icons.Default.Person, contentDescription = tab.label, tint = iconTint, modifier = Modifier.size(24.dp))
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
