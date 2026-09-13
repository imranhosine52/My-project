@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)

package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.data.model.ContentItemDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.BottomNavTab
import com.example.util.DownloadStateTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// =========================================================================
// 🏷️ ১. ভাষা ও ডাবিং ব্যাজ (Bangla = গোল্ডেন, Hindi = ব্লু)
// =========================================================================
@Composable
fun LanguageDubBadge(
    dubText: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 7.dp
) {
    val isHindi = dubText.contains("Hindi", ignoreCase = true)
    val cleanText = when {
        dubText.contains("Bangla", ignoreCase = true) || dubText.contains("Bengali", ignoreCase = true) -> "Bangla"
        isHindi -> "Hindi"
        dubText.isNotBlank() -> dubText.replace(" Dubbed", "").replace(" Dub", "").trim()
        else -> "Bangla"
    }

    val badgeBg = if (isHindi) Color(0xFF1E88E5) else Color(0xFFFFB300)
    val badgeTextCol = if (isHindi) Color.White else Color.Black

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = cornerRadius,
                    bottomStart = 5.dp,
                    bottomEnd = 0.dp
                )
            )
            .background(badgeBg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = cleanText,
            color = badgeTextCol,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 11.sp,
            letterSpacing = 0.sp
        )
    }
}

// =========================================================================
// 👑 ২. VIP ক্রাউন ব্যাজ ও ভেক্টর আইকন
// =========================================================================
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

// =========================================================================
// 👑 ৩. ৩D গোল্ডেন VIP ক্রাউন আইকন (৩টি লাল মুক্তো ও মাঝে সাদা VIP লেখা)
// =========================================================================
@Composable
fun VipCrown3DIcon(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(width = 28.dp, height = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val crownPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.15f, h * 0.40f)
                lineTo(w * 0.18f, h * 0.85f)
                quadraticTo(w * 0.50f, h * 0.95f, w * 0.82f, h * 0.85f)
                lineTo(w * 0.85f, h * 0.40f)
                lineTo(w * 0.68f, h * 0.55f)
                lineTo(w * 0.50f, h * 0.22f)
                lineTo(w * 0.32f, h * 0.55f)
                close()
            }

            drawPath(
                path = crownPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFEA00),
                        Color(0xFFFFB300),
                        Color(0xFFFF8F00)
                    )
                )
            )

            drawPath(
                path = crownPath,
                color = Color(0xFFFFF59D),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
            )

            val rubyBorder = 0.8.dp.toPx()
            val rubyColor = Color(0xFFFF1744)
            val rubyStroke = Color(0xFFFFD54F)

            drawCircle(color = rubyStroke, radius = 2.8.dp.toPx(), center = Offset(w * 0.15f, h * 0.38f))
            drawCircle(color = rubyColor, radius = 2.8.dp.toPx() - rubyBorder, center = Offset(w * 0.15f, h * 0.38f))

            drawCircle(color = rubyStroke, radius = 3.5.dp.toPx(), center = Offset(w * 0.50f, h * 0.20f))
            drawCircle(color = rubyColor, radius = 3.5.dp.toPx() - rubyBorder, center = Offset(w * 0.50f, h * 0.20f))

            drawCircle(color = rubyStroke, radius = 2.8.dp.toPx(), center = Offset(w * 0.85f, h * 0.38f))
            drawCircle(color = rubyColor, radius = 2.8.dp.toPx() - rubyBorder, center = Offset(w * 0.85f, h * 0.38f))
        }

        Text(
            text = "VIP",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 2.dp)
        )
    }
}

// =========================================================================
// 🔝 ৪. ফিক্সড টপ ন্যাভিগেশন বার (অ্যানিমেটেড কি-ওয়ার্ড সার্চ বক্স সহ)
// =========================================================================
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
    // 🔍 সার্চ বারে ভেসে ওঠার জন্য অ্যানিমেটেড কি-ওয়ার্ড লিস্ট
    val searchKeywords = remember {
        listOf(
            "Search show...",
            "Search Bangla Dub...",
            "Search Extraordinary You...",
            "Search Hindi Dubbed...",
            "Search Korean Drama...",
            "Search Mr. Bad...",
            "Search Anime Series..."
        )
    }
    var currentKeywordIndex by remember { mutableIntStateOf(0) }

    // প্রতি ২.৬ সেকেন্ডে সুন্দরভাবে স্লাইড ও ফেড অ্যানিমেশনে পরবর্তী কি-ওয়ার্ড আসবে
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600L)
            currentKeywordIndex = (currentKeywordIndex + 1) % searchKeywords.size
        }
    }

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

            // 🔍 অ্যানিমেটেড সার্চ বক্স
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x2EFFFFFF))
                    .border(0.7.dp, Color(0x38FFFFFF), RoundedCornerShape(50))
                    .clickable { onSearchClick() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnimatedContent(
                    targetState = searchKeywords[currentKeywordIndex],
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn(tween(300)))
                            .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(300)))
                    },
                    label = "searchKeywordAnimation",
                    modifier = Modifier.weight(1f)
                ) { keyword ->
                    Text(
                        text = keyword,
                        color = Color(0xFFB0B6C4),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = Color(0xFF00E676),
                        modifier = Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .clickable { onVoiceSearchClick() }
                    )
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFFCCD0DB),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            VipCrownBadge(onClick = onVipClick)

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
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
                    modifier = Modifier.size(17.dp)
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

        // ক্যাটাগরি রো
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

// =========================================================================
// 🌟 ৫. হট স্পটলাইট হিরো কার্ড (Auto-Scrolling Banner Card)
// =========================================================================
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
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1B2338), Color(0xFF121522), Color(0xFF0D0F17))
                )
            )
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
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
                                shape = RoundedCornerShape(5.dp),
                                color = GoldVip
                            ) {
                                Text(
                                    text = "HOT SPOTLIGHT",
                                    color = GoldButtonText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = SurfaceVariantDark
                            ) {
                                Text(
                                    text = drama.releaseYear.ifBlank { "2026" },
                                    color = TextSecondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF282415),
                                border = BorderStroke(0.8.dp, GoldVip.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(11.dp))
                                    Text(
                                        text = if (drama.rating > 0) drama.rating.toString() else "8.5",
                                        color = GoldVip,
                                        fontSize = 9.5.sp,
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
                                    shape = RoundedCornerShape(5.dp),
                                    color = SurfaceVariantDark.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = tag,
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
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
                            .width(112.dp)
                            .height(156.dp)
                            .graphicsLayer { translationY = floatY }
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.4.dp,
                                brush = Brush.verticalGradient(
                                    listOf(GoldVip.copy(alpha = 0.9f), Color(0xFF00E5FF).copy(alpha = 0.6f), GoldVip.copy(alpha = 0.4f))
                                ),
                                shape = RoundedCornerShape(8.dp)
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

// =========================================================================
// 📌 ৬. সেকশন হেডার
// =========================================================================
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

// =========================================================================
// 🎬 ৭. হরিজন্টাল ড্রামা রো ও পোস্টার কার্ড
// =========================================================================
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
                modifier = Modifier.width(136.dp)
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

    Column(
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(7.dp))
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

            LanguageDubBadge(
                dubText = drama.dubBadge.ifBlank { drama.language },
                cornerRadius = 7.dp,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Text(
                text = "${drama.totalEpisodes} Episodes",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

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

// =========================================================================
// 🧭 ৮. ডার্ক ফ্রস্টেড গ্লাস বটম নেভিগেশন বার
// =========================================================================
@Composable
fun PlayDramaFlixBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTasksMap by DownloadStateTracker.activeDownloads.collectAsState()
    val activeDownloadCount = activeTasksMap.values.count { !it.isCompleted }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF0181C28),
                        Color(0xF7141722),
                        Color(0xFD10121B)
                    )
                )
            )
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x28FFFFFF),
                        Color(0x06FFFFFF)
                    )
                ),
                shape = RectangleShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in BottomNavTab.entries) {
                val isSelected = (tab == selectedTab)
                val iconTint = if (isSelected) Color(0xFF00D166) else Color(0xFF8E95A5)
                val textColor = if (isSelected) Color.White else Color(0xFF8E95A5)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (tab) {
                                BottomNavTab.HOME -> {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                BottomNavTab.SHORT_TV -> {
                                    Icon(
                                        imageVector = Icons.Default.SmartDisplay,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                BottomNavTab.PREMIUM -> {
                                    VipCrown3DIcon(
                                        modifier = Modifier.size(width = 26.dp, height = 20.dp)
                                    )
                                }
                                BottomNavTab.DOWNLOADS -> {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .border(
                                                width = 1.5.dp,
                                                color = iconTint,
                                                shape = RoundedCornerShape(6.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = tab.label,
                                            tint = iconTint,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    if (activeDownloadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 8.dp, y = (-6).dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (activeDownloadCount > 9) "9+" else activeDownloadCount.toString(),
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                BottomNavTab.ME -> {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = tab.label,
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
