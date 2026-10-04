@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.screens.categories

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.ui.LanguageDubBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Random

// 🎨 হোমপেজের হুবহু ব্যাকগ্রাউন্ড ও কালার প্যালেট
private val HomeBackgroundDark = Color(0xFF090A0F)
private val CardBorderColor = Color(0xFF1E2638)
private val GoldRating = Color(0xFFFFB300)
private val LimeYellowAccent = Color(0xFFE5FE00)
private val LimeYellowButtonText = Color(0xFF0F1400)

@Composable
fun RecentlyAddedCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 🎯 রিফ্রেশ সিড (কার্ডগুলোর অবস্থান প্রতিবার ডায়নামিক রোটেট হওয়ার জন্য)
    var refreshSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    val heroSliderItems = remember(items) { items.take(10) }

    // ডায়নামিক রোটেশন সহ ফিল্টারবিহীন নতুন ড্রামা তালিকা
    val dynamicNewList = remember(items, refreshSeed) {
        if (items.size > 2) items.shuffled(Random(refreshSeed)) else items
    }

    // ৪টি করে কার্ড প্রতি সারিতে
    val gridChunks = remember(dynamicNewList) {
        dynamicNewList.chunked(4)
    }

    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(HomeBackgroundDark)
                .padding(top = statusBarTop + 94.dp, bottom = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recently added dramas found",
                color = Color(0xFF94A3B8),
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(HomeBackgroundDark),
            contentPadding = PaddingValues(
                top = statusBarTop + 84.dp,
                bottom = 70.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 🌟 ১. টপ হিরো স্পটলাইট স্লাইডার কার্ড
            // =========================================================================
            if (heroSliderItems.isNotEmpty()) {
                item {
                    RecentlyAddedHeroSpotlightCard(
                        spotlightDramas = heroSliderItems,
                        onWatchClick = { drama -> onNavigateToPlayer(drama.slug) },
                        onDetailsClick = { drama -> onNavigateToPlayer(drama.slug) },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }

            // =========================================================================
            // 🏷️ ২. সেকশন হেডার (কোনো ফিল্টার অপশন ছাড়া)
            // =========================================================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color(0xFFFF2A4B))
                        )
                        Text(
                            text = "Recently Added Releases",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${dynamicNewList.size} Titles",
                        color = Color(0xFF8E95A5),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // =========================================================================
            // 🔲 ৩. ৪-কলাম কম্প্যাক্ট ড্রামা গ্রিড
            // =========================================================================
            items(gridChunks.size) { rowIndex ->
                val rowDramas = gridChunks[rowIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowDramas.forEach { drama ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDesktopNewCard(
                                drama = drama,
                                onClick = { onNavigateToPlayer(drama.slug) }
                            )
                        }
                    }
                    repeat(4 - rowDramas.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🎬 হিরো স্পটলাইট স্লাইডার কার্ড (Display Name সহ)
// =========================================================================
@Composable
fun RecentlyAddedHeroSpotlightCard(
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
                delay(4200L)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % totalPages
                    try {
                        pagerState.animateScrollToPage(
                            page = nextPage,
                            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
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
            .background(Color(0xFF0F131D))
            .border(1.dp, Color(0xFF232B3D), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
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
                        // 👈 বামপাশের ইনফরমেশন সেকশন
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            // ১. টপ ব্যাজ রো: [ NEW ]  [ 2026 ]  [ ★ 9.0 ]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = LimeYellowAccent
                                ) {
                                    Text(
                                        text = "NEW",
                                        color = LimeYellowButtonText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF222B3D)
                                ) {
                                    Text(
                                        text = drama.releaseYear.ifBlank { "2026" },
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2D2305),
                                    border = BorderStroke(0.8.dp, Color(0xFFFFB300).copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(11.dp))
                                        Text(
                                            text = if (drama.rating > 0) String.format(Locale.US, "%.1f", drama.rating) else "9.0",
                                            color = Color(0xFFFFB300),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 🎯 ড্রামার ছোট ও পরিচ্ছন্ন নাম
                            Text(
                                text = drama.displayName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // ৩. ক্যাটাগরি ও দেশ ট্যাগস
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                (listOf("All", drama.dubBadge, drama.country) + drama.categories.take(2)).filter { it.isNotBlank() }.distinct().forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color(0xFF1E2638)
                                    ) {
                                        Text(
                                            text = tag,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // ৪. অ্যাকশন বাটনসমূহ
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onWatchClick(drama) },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = LimeYellowAccent, contentColor = LimeYellowButtonText),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = LimeYellowButtonText, modifier = Modifier.size(16.dp))
                                        Text("Watch Now", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    }
                                }

                                Button(
                                    onClick = { onDetailsClick(drama) },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B3D), contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                        Text("Details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // 👉 ডানপাশের ৩D ফ্লোটিং পোস্টার
                        Box(
                            modifier = Modifier
                                .width(116.dp)
                                .height(160.dp)
                                .graphicsLayer { translationY = floatY }
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = 1.4.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(LimeYellowAccent.copy(alpha = 0.9f), Color(0xFF00E5FF).copy(alpha = 0.6f), LimeYellowAccent.copy(alpha = 0.3f))
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onWatchClick(drama) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(drama.posterUrl ?: drama.bannerUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = drama.displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // ◀ বামের অ্যারো
                if (totalPages > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-8).dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable {
                                val prev = if (pagerState.currentPage > 0) pagerState.currentPage - 1 else totalPages - 1
                                coroutineScope.launch { pagerState.animateScrollToPage(prev) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // ▶ ডানের অ্যারো
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable {
                                val next = (pagerState.currentPage + 1) % totalPages
                                coroutineScope.launch { pagerState.animateScrollToPage(next) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 🟡 ডট ইন্ডিকেটর
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                spotlightDramas.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.5.dp)
                            .height(4.dp)
                            .width(if (isSelected) 18.dp else 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) LimeYellowAccent else Color(0xFF334155))
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
// 🖼️ ৪-কলাম কম্প্যাক্ট কার্ড (হালকা রেটিং ও ডাইনামিক ডাবিং ব্যাজ সহ)
// =========================================================================
@Composable
fun CompactDesktopNewCard(
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
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(6.dp))
                .border(0.6.dp, CardBorderColor, RoundedCornerShape(6.dp))
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

            // নিচের হালকা ডার্ক শ্যাডো
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.70f)
                            )
                        )
                    )
            )

            // ডাবিং ব্যাজ (উপরে ডানে)
            LanguageDubBadge(
                dubText = drama.dubBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // এপিসোড সংখ্যা
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Ep" else "New"
            Text(
                text = epCount,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            )

            // রেটিং (নিচে ডানে - হালকা ইফেক্ট)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
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

        Spacer(modifier = Modifier.height(2.5.dp))

        // ছোট ও পরিচ্ছন্ন নাম (Display Name)
        Text(
            text = drama.displayName,
            color = Color(0xFFE2E8F0),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
