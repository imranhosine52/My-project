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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private val LimeYellowAccent = Color(0xFFE5FE00)
private val LimeYellowButtonText = Color(0xFF0F1400)

@Composable
fun MoviesCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = statusBarTop + 94.dp, bottom = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No movies found",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val heroSliderItems = remember(items) { items.take(10) }
        val gridChunks = remember(items) { items.chunked(3) }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0C0F15)),
            contentPadding = PaddingValues(
                top = statusBarTop + 94.dp,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // 🌟 ১. মুভি পেজের হিরো স্পটলাইট স্লাইডার
            // =========================================================================
            if (heroSliderItems.isNotEmpty()) {
                item {
                    MovieHeroSpotlightCard(
                        spotlightDramas = heroSliderItems,
                        onWatchClick = { movie -> onNavigateToPlayer(movie.slug) },
                        onDetailsClick = { movie -> onNavigateToPlayer(movie.slug) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // =========================================================================
            // 🏷️ ২. সেকশন হেডার
            // =========================================================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
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
                                .background(Color(0xFFFF1744)) // Cinema Red Accent
                        )
                        Text(
                            text = "Movies & Films",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${items.size} Movies",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // =========================================================================
            // 🔲 ৩. ৩-কলাম মুভি গ্রিড (MOVIE ব্যাজ ছাড়া)
            // =========================================================================
            items(gridChunks.size) { rowIndex ->
                val rowDramas = gridChunks[rowIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowDramas.forEach { movie ->
                        Box(modifier = Modifier.weight(1f)) {
                            MovieDramaCard(
                                movie = movie,
                                onClick = { onNavigateToPlayer(movie.slug) }
                            )
                        }
                    }
                    repeat(3 - rowDramas.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🎬 মুভি পেজের হিরো স্পটলাইট স্লাইডার কার্ড
// =========================================================================
@Composable
fun MovieHeroSpotlightCard(
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

    // অটো-স্লাইড লুপ (প্রতি ৪.২ সেকেন্ডে)
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

    val infiniteTransition = rememberInfiniteTransition(label = "movie_banner_float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -4.5f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "movie_float_y"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F131D))
            .border(1.dp, Color(0xFF232B3D), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val movie = spotlightDramas[page]

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
                            // ১. টপ ব্যাজ রো: [ MOVIE ]  [ 2026 ]  [ ★ 8.5 ]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = LimeYellowAccent
                                ) {
                                    Text(
                                        text = "MOVIE",
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
                                        text = movie.releaseYear.ifBlank { "2026" },
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
                                            text = if (movie.rating > 0) String.format(Locale.US, "%.1f", movie.rating) else "8.5",
                                            color = Color(0xFFFFB300),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // ২. মুভির নাম
                            Text(
                                text = movie.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // ৩. ক্যাটাগরি ট্যাগস
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                (listOf("All", movie.dubBadge) + movie.categories.take(2)).filter { it.isNotBlank() }.distinct().forEach { tag ->
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

                            // ৪. অ্যাকশন বাটনসমূহ: [ ▶ Watch Now ]  [ ℹ Details ]
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onWatchClick(movie) },
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
                                    onClick = { onDetailsClick(movie) },
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
                                        listOf(Color(0xFFFF1744), Color(0xFFFFD700), Color(0xFFFF1744))
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onWatchClick(movie) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(movie.posterUrl ?: movie.bannerUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = movie.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // ◀ বামের অ্যারো বাটন
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

                    // ▶ ডানের অ্যারো বাটন
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

            Spacer(modifier = Modifier.height(12.dp))

            // =========================================================================
            // 🟡 ডট ইন্ডিকেটর (● ▬ ● ● ●)
            // =========================================================================
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
// 🎬 ৩-কলাম মুভি কার্ড (MOVIE ব্যাজ পুরোপুরি রিমুভ করা হয়েছে)
// =========================================================================
@Composable
fun MovieDramaCard(
    movie: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "movieCardShine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val shineBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x33FFFFFF),
            Color(0xFFFF1744).copy(alpha = 0.75f),
            Color(0xFFFFD700).copy(alpha = 0.85f),
            Color(0x33FFFFFF)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 250f, 350f)
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
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    brush = shineBorderBrush,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(Color(0xFF1E2430))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.posterUrl ?: movie.bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
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

            // 🏷️ ডাবিং ব্যাজ (Bangla / Hindi)
            val isBangla = movie.isBanglaDub || movie.dubBadge.contains("Bangla", ignoreCase = true)
            val badgeColor = if (isBangla) Color(0xFFFFB300) else Color(0xFF00B0FF)

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, topEnd = 10.dp))
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

            // কোয়ালিটি ব্যাজ
            Text(
                text = "Full HD",
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
            text = movie.title,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
