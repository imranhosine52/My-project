package com.example.ui

import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.platform.testTag
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
fun VipCrownBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 28.dp)
            .clickable { onClick() }
            .testTag("top_vip_icon_button"),
        contentAlignment = Alignment.Center
    ) {
        VipCrownVectorIcon(
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VipCrownVectorIcon(
    modifier: Modifier = Modifier,
    showGlow: Boolean = true
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val leftJewelX = w * 0.18f
            val leftJewelY = h * 0.42f
            val leftJewelRadius = w * 0.11f

            val centerJewelX = w * 0.50f
            val centerJewelY = h * 0.22f
            val centerJewelRadius = w * 0.13f

            val rightJewelX = w * 0.82f
            val rightJewelY = h * 0.42f
            val rightJewelRadius = w * 0.11f

            val crownPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.22f, h * 0.86f)
                quadraticTo(w * 0.50f, h * 0.90f, w * 0.78f, h * 0.86f)
                quadraticTo(w * 0.84f, h * 0.65f, rightJewelX, rightJewelY)
                quadraticTo(w * 0.68f, h * 0.52f, centerJewelX, centerJewelY)
                quadraticTo(w * 0.32f, h * 0.52f, leftJewelX, leftJewelY)
                quadraticTo(w * 0.16f, h * 0.65f, w * 0.22f, h * 0.86f)
                close()
            }

            drawPath(
                path = crownPath,
                color = Color(0xFFFFD54F),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            drawPath(
                path = crownPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFCA28),
                        Color(0xFFFFB300),
                        Color(0xFFFFA000)
                    ),
                    startY = h * 0.20f,
                    endY = h * 0.90f
                )
            )

            val shinePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.30f, h * 0.52f)
                quadraticTo(w * 0.50f, h * 0.42f, w * 0.70f, h * 0.52f)
            }
            drawPath(
                path = shinePath,
                color = Color.White.copy(alpha = 0.45f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            fun drawJewel(cx: Float, cy: Float, radius: Float) {
                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = radius + 1.2.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF5252),
                            Color(0xFFE53935)
                        ),
                        center = androidx.compose.ui.geometry.Offset(cx - radius * 0.2f, cy - radius * 0.2f),
                        radius = radius * 0.85f
                    ),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = radius * 0.22f,
                    center = androidx.compose.ui.geometry.Offset(cx - radius * 0.25f, cy - radius * 0.25f)
                )
            }

            drawJewel(leftJewelX, leftJewelY, leftJewelRadius)
            drawJewel(rightJewelX, rightJewelY, rightJewelRadius)
            drawJewel(centerJewelX, centerJewelY, centerJewelRadius)
        }

        Text(
            text = "VIP",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            style = androidx.compose.ui.text.TextStyle(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0x66000000),
                    offset = androidx.compose.ui.geometry.Offset(1f, 1.5f),
                    blurRadius = 2f
                )
            ),
            letterSpacing = 0.4.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 2.dp)
        )
    }
}

@Composable
fun TopNavigationBar(
    categories: List<String>,
    selectedCategory: String,
    notificationCount: Int = 3,
    onCategorySelected: (String) -> Unit,
    onNotificationClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onVipClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFA0D0F14),
                        Color(0xD90D0F14),
                        Color(0x000D0F14)
                    )
                )
            )
            .padding(top = 0.dp, bottom = 2.dp)
    ) {
        // TOP ROW: PDFlix logo + Search Bar + VIP Crown + Notification Bell
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Brand Title: PDFlix
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onCategorySelected("Home") }
                    .testTag("app_brand_logo")
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = "PD",
                    color = Color(0xFF00D2FF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Flix",
                    color = Color(0xFFFF9900),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Rounded Search Pill Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x2EFFFFFF))
                    .border(0.8.dp, Color(0x1FFFFFFF), RoundedCornerShape(50))
                    .clickable { onSearchClick() }
                    .padding(horizontal = 12.dp)
                    .testTag("top_search_bar_pill"),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Search show...",
                        color = Color(0xFFADB2BE),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFFCCD0DB),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(7.dp))

            // VIP Crown Button
            VipCrownBadge(
                onClick = onVipClick
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Notification Bell
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x2EFFFFFF))
                    .border(0.8.dp, Color(0x1FFFFFFF), CircleShape)
                    .clickable { onNotificationClick() }
                    .testTag("top_notification_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )

                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2A4B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (notificationCount > 9) "9+" else notificationCount.toString(),
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // SECOND ROW: Horizontal Category Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabList = if (categories.contains("Home") || categories.contains("All")) {
                categories.map { if (it == "All") "Home" else it }
            } else {
                listOf("Home", "Shorts", "Drama", "Anime", "Movie", "Variety", "Kids", "Doc")
            }

            tabList.forEach { tab ->
                val isSelected = (tab == "Home" && (selectedCategory == "Home" || selectedCategory == "All")) ||
                        tab.equals(selectedCategory, ignoreCase = true)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onCategorySelected(tab) }
                        .padding(vertical = 2.dp)
                        .testTag("nav_tab_$tab")
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color(0x99FFFFFF),
                        fontSize = if (isSelected) 17.sp else 14.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = if (isSelected) 0.sp else 0.2.sp
                    )

                    Spacer(modifier = Modifier.height(2.5.dp))

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
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
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(pagerState.pageCount) {
        if (totalPages > 1) {
            while (isActive) {
                delay(3600L)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % totalPages
                    try {
                        pagerState.animateScrollToPage(
                            page = nextPage,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "banner_poster_floating")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5.5f,
        targetValue = 5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "poster_float_y"
    )

    val floatTilt by infiniteTransition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "poster_float_tilt"
    )

    val floatScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "poster_float_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "poster_glow_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1B2338),
                        Color(0xFF121522),
                        Color(0xFF0D0F17)
                    )
                )
            )
            .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
            .padding(14.dp)
            .testTag("hot_spotlight_hero")
    ) {
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val drama = spotlightDramas[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                val parallaxScale = 1f - (absOffset * 0.12f)
                val parallaxAlpha = 1f - (absOffset * 0.35f)

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
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GoldVip)
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "HOT SPOTLIGHT",
                                    color = GoldButtonText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.3.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = drama.releaseYear.ifBlank { "2026" },
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF282415))
                                    .border(0.8.dp, GoldVip.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = GoldVip,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (drama.rating > 0) drama.rating.toString() else "8.5",
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
                            lineHeight = 21.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf(drama.dubBadge) + drama.categories.take(2)).filter { it.isNotBlank() }.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceVariantDark.copy(alpha = 0.8f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onWatchClick(drama) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldVip,
                                    contentColor = GoldButtonText
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("spotlight_watch_now")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = GoldButtonText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Watch Now",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = { onDetailsClick(drama) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceVariantDark,
                                    contentColor = TextPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(BorderDark, BorderDark))
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("spotlight_details")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Details",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .height(156.dp)
                            .graphicsLayer {
                                translationY = floatY * (1f - absOffset)
                                rotationZ = floatTilt * (1f - absOffset)
                                scaleX = floatScale * parallaxScale
                                scaleY = floatScale * parallaxScale
                                alpha = parallaxAlpha
                                shadowElevation = 16f
                                shape = RoundedCornerShape(16.dp)
                                clip = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            GoldVip.copy(alpha = 0.45f * glowAlpha),
                                            TealAccent.copy(alpha = 0.25f * glowAlpha),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.4.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            GoldVip.copy(alpha = 0.9f),
                                            TealAccent.copy(alpha = 0.7f),
                                            GoldVip.copy(alpha = 0.4f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onWatchClick(drama) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(drama.posterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = drama.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            .height(4.dp)
                            .width(if (isSelected) 18.dp else 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) Color(0xFF388BFF) else SurfaceVariantDark)
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                    .background(SectionRed)
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
                color = SectionRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SectionRed,
                modifier = Modifier.size(16.dp)
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
                onClick = { onDramaClick(drama) }
            )
        }
    }
}

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
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = cleanText,
            color = Color(0xFF111111),
            fontSize = 6.8.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 8.sp,
            letterSpacing = 0.sp
        )
    }
}

@Composable
fun DramaPosterCardHorizontal(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val episodesLabel = when {
        drama.type.equals("movie", ignoreCase = true) -> "Movie"
        drama.totalEpisodes > 0 -> "${drama.totalEpisodes} Episodes"
        else -> "1 Episode"
    }

    Column(
        modifier = modifier
            .width(124.dp)
            .clickable { onClick() }
            .testTag("drama_item_${drama.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardBackgroundDark)
                .border(
                    width = 0.85.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFEAA61A).copy(alpha = 0.55f),
                            Color(0xFF8899AA).copy(alpha = 0.35f),
                            Color(0xFF1E2433).copy(alpha = 0.6f),
                            Color(0xFFEAA61A).copy(alpha = 0.5f)
                        )
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl)
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
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            LanguageDubBadge(
                dubText = drama.dubBadge.ifBlank { drama.language },
                cornerRadius = 10.dp,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Text(
                text = episodesLabel,
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = drama.title,
            color = Color(0xFFDCE0E8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 1.dp)
        )
    }
}

@Composable
fun PlayDramaFlixBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vip_animation")
    val bounceOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vip_bounce"
    )

    val wobbleRotation by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vip_wobble"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vip_scale"
    )

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
                .height(58.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in BottomNavTab.entries) {
                val isSelected = tab == selectedTab

                if (tab == BottomNavTab.VIP) {
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .height(58.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) }
                            .testTag("bottom_nav_vip"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-4).dp + bounceOffsetY.dp)
                                .graphicsLayer {
                                    rotationZ = wobbleRotation
                                    scaleX = if (isSelected) pulseScale * 1.05f else pulseScale
                                    scaleY = if (isSelected) pulseScale * 1.05f else pulseScale
                                }
                                .size(44.dp, 33.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            VipCrownVectorIcon(
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) }
                            .testTag("bottom_nav_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val iconTint = if (isSelected) TealAccent else TextMuted

                            when (tab) {
                                BottomNavTab.HOME -> {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Home else Icons.Outlined.Home,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                BottomNavTab.SEARCH -> {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                BottomNavTab.WATCHLIST -> {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                BottomNavTab.PROFILE -> {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Person else Icons.Outlined.Person,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                else -> {}
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .size(width = if (isSelected) 14.dp else 0.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) TealAccent else Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerWaveform(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(bar1)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(bar2)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(bar3)
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}
