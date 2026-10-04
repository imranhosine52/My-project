@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)

package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
// 🏷️ ১. ১০০% ডাইনামিক ডাবিং ব্যাজ (সার্ভার থেকে আসা যেকোনো ভাষার নাম দেখাবে)
// =========================================================================
@Composable
fun LanguageDubBadge(
    dubText: String,
    modifier: Modifier = Modifier
) {
    val cleanText = remember(dubText) {
        val trimmed = dubText.trim()
        when {
            trimmed.contains("Bangla", ignoreCase = true) || trimmed.contains("Bengali", ignoreCase = true) -> "Bangla"
            trimmed.contains("Hindi", ignoreCase = true) -> "Hindi"
            trimmed.contains("English", ignoreCase = true) || trimmed.contains("Eng", ignoreCase = true) -> "English"
            trimmed.contains("Tamil", ignoreCase = true) -> "Tamil"
            trimmed.contains("Telugu", ignoreCase = true) -> "Telugu"
            trimmed.contains("Korean", ignoreCase = true) -> "Korean"
            trimmed.contains("Japanese", ignoreCase = true) -> "Japanese"
            trimmed.contains("Chinese", ignoreCase = true) -> "Chinese"
            trimmed.contains("Dual", ignoreCase = true) -> "Dual"
            trimmed.isNotBlank() -> trimmed.replace(" Dubbed", "", ignoreCase = true)
                .replace(" Dub", "", ignoreCase = true).trim()
            else -> "HD"
        }
    }

    // ভাষার ধরন অনুযায়ী ব্যাকগ্রাউন্ড টিন্ট
    val (badgeBg, badgeBorder, badgeTextColor) = remember(cleanText) {
        when (cleanText.lowercase()) {
            "bangla" -> Triple(Color(0xFF2A2000), Color(0xFFFFB300), Color(0xFFFFB300))
            "hindi"  -> Triple(Color(0xFF001F3F), Color(0xFF00B0FF), Color(0xFF00E5FF))
            "english"-> Triple(Color(0xFF00291B), Color(0xFF00D166), Color(0xFF00E676))
            "tamil", "telugu" -> Triple(Color(0xFF20102E), Color(0xFFA855F7), Color(0xFFD8B4FE))
            "korean", "japanese", "chinese" -> Triple(Color(0xFF1E2430), Color(0xFF60A5FA), Color(0xFF93C5FD))
            else     -> Triple(Color(0x99000000), Color(0x44FFFFFF), Color.White)
        }
    }

    Box(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(badgeBg)
            .border(0.6.dp, badgeBorder.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = cleanText,
            color = badgeTextColor,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
            letterSpacing = 0.2.sp
        )
    }
}

// =========================================================================
// 👑 ছবির হুবহু ৩-পয়েন্ট গোল্ডেন ক্রাউন ও রুবি জেমস্টোন ভেক্টর আইকন
// =========================================================================
@Composable
fun VipCrownIllustratedIcon(
    modifier: Modifier = Modifier.size(26.dp, 22.dp)
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val crownPath = Path().apply {
                moveTo(w * 0.18f, h * 0.88f)
                lineTo(w * 0.82f, h * 0.88f)
                quadraticTo(w * 0.90f, h * 0.88f, w * 0.88f, h * 0.78f)
                lineTo(w * 0.84f, h * 0.44f)
                quadraticTo(w * 0.68f, h * 0.54f, w * 0.50f, h * 0.28f)
                quadraticTo(w * 0.32f, h * 0.54f, w * 0.16f, h * 0.44f)
                lineTo(w * 0.12f, h * 0.78f)
                quadraticTo(w * 0.10f, h * 0.88f, w * 0.18f, h * 0.88f)
                close()
            }

            drawPath(
                path = crownPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFDF00), Color(0xFFFFB300), Color(0xFFFF8F00))
                )
            )

            drawPath(
                path = crownPath,
                color = Color(0xFFFFEA00),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val rubyRadius = w * 0.085f
            val rubyGoldBorder = 1.2.dp.toPx()
            val rubyColor = Color(0xFFFF2A55)
            val rimColor = Color(0xFFFFEA00)

            drawCircle(color = rimColor, radius = rubyRadius + rubyGoldBorder, center = Offset(w * 0.16f, h * 0.44f))
            drawCircle(color = rubyColor, radius = rubyRadius, center = Offset(w * 0.16f, h * 0.44f))

            drawCircle(color = rimColor, radius = rubyRadius * 1.15f + rubyGoldBorder, center = Offset(w * 0.50f, h * 0.26f))
            drawCircle(color = rubyColor, radius = rubyRadius * 1.15f, center = Offset(w * 0.50f, h * 0.26f))

            drawCircle(color = rimColor, radius = rubyRadius + rubyGoldBorder, center = Offset(w * 0.84f, h * 0.44f))
            drawCircle(color = rubyColor, radius = rubyRadius, center = Offset(w * 0.84f, h * 0.44f))
        }

        Text(
            text = "VIP",
            color = Color.White,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.Center).offset(y = 2.dp)
        )
    }
}

@Composable
fun VipCrownVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF6D38B)
) {
    VipCrownIllustratedIcon(modifier = modifier)
}

@Composable
fun VipCrown3DIcon(modifier: Modifier = Modifier) {
    VipCrownIllustratedIcon(modifier = modifier)
}

// =========================================================================
// 🎬 কাস্টম ভেক্টর আইকনসমূহ
// =========================================================================
@Composable
fun ShortTvCustomStackedIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.7.dp.toPx()

        val leftPath = Path().apply {
            moveTo(w * 0.22f, h * 0.22f)
            lineTo(w * 0.10f, h * 0.22f)
            lineTo(w * 0.10f, h * 0.78f)
            lineTo(w * 0.22f, h * 0.78f)
        }
        drawPath(leftPath, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val rightPath = Path().apply {
            moveTo(w * 0.78f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.78f)
            lineTo(w * 0.78f, h * 0.78f)
        }
        drawPath(rightPath, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.10f),
            size = Size(w * 0.56f, h * 0.80f),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        val playPath = Path().apply {
            moveTo(w * 0.44f, h * 0.38f)
            lineTo(w * 0.60f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.62f)
            close()
        }
        drawPath(playPath, color = tint, style = Fill)
    }
}

@Composable
fun DownloadsCustomTrayIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()

        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.14f),
            end = Offset(w * 0.50f, h * 0.58f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val arrowHead = Path().apply {
            moveTo(w * 0.35f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.59f)
            lineTo(w * 0.65f, h * 0.44f)
        }
        drawPath(arrowHead, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val trayPath = Path().apply {
            moveTo(w * 0.22f, h * 0.68f)
            lineTo(w * 0.22f, h * 0.84f)
            lineTo(w * 0.78f, h * 0.84f)
            lineTo(w * 0.78f, h * 0.68f)
        }
        drawPath(trayPath, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun MeCustomUserIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()

        drawCircle(
            color = tint,
            radius = w * 0.17f,
            center = Offset(w * 0.50f, h * 0.28f),
            style = Stroke(width = strokeWidth)
        )

        val bodyPath = Path().apply {
            moveTo(w * 0.16f, h * 0.85f)
            cubicTo(
                w * 0.22f, h * 0.58f,
                w * 0.78f, h * 0.58f,
                w * 0.84f, h * 0.85f
            )
        }
        drawPath(bodyPath, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
fun VipCrownBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(width = 30.dp, height = 24.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        VipCrownIllustratedIcon(modifier = Modifier.fillMaxSize())
    }
}

// =========================================================================
// 🔝 আল্ট্রা-স্লিম টপ ন্যাভিগেশন বার
// =========================================================================
@Composable
fun TopNavigationBar(
    categories: List<String>,
    selectedCategoryIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onSearchClick: () -> Unit = {},
    onVoiceSearchClick: () -> Unit = {},
    onVipClick: () -> Unit = {},
    onUpdateCheckClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val searchKeywords = remember {
        listOf(
            "Search show...",
            "Search Bangla Dub...",
            "Search Extraordinary You...",
            "Search Hindi Dubbed...",
            "Search Korean Drama..."
        )
    }
    var currentKeywordIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2800L)
            currentKeywordIndex = (currentKeywordIndex + 1) % searchKeywords.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF2080C14),
                        Color(0xD9080C14),
                        Color(0x00080C14)
                    )
                )
            )
            .statusBarsPadding()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onCategorySelected(0) }
                    .padding(end = 6.dp)
            ) {
                Text("PD", color = Color(0xFF00D2FF), fontSize = 15.5.sp, fontWeight = FontWeight.Black)
                Text("Flix", color = Color(0xFFFF9900), fontSize = 15.5.sp, fontWeight = FontWeight.Black)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x28FFFFFF))
                    .border(0.6.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .clickable { onSearchClick() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnimatedContent(
                    targetState = searchKeywords[currentKeywordIndex],
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn(tween(250)))
                            .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(250)))
                    },
                    label = "searchKeywordAnim",
                    modifier = Modifier.weight(1f)
                ) { keyword ->
                    Text(
                        text = keyword,
                        color = Color(0xFFA6AFBF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(14.dp).clip(CircleShape).clickable { onVoiceSearchClick() }
                    )
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFFCCD0DB),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            VipCrownBadge(onClick = onVipClick)
            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x28FFFFFF))
                    .border(0.6.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable { onUpdateCheckClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = "Check for Update",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { index, tab ->
                val isSelected = (index == selectedCategoryIndex)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onCategorySelected(index) }
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color(0x88FFFFFF),
                        fontSize = if (isSelected) 13.5.sp else 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color(0xFF00E5FF))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🌟 হিরো স্পটলাইট কার্ড (Display Name সহ)
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
                        pagerState.animateScrollToPage(page = nextPage, animationSpec = tween(600, easing = FastOutSlowInEasing))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "banner_float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -4.5f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "poster_float_y"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B2338), Color(0xFF121522), Color(0xFF0D0F17))))
            .border(0.8.dp, Color(0xFF222838), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val drama = spotlightDramas[page]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(4.dp), color = GoldVip) {
                                Text("HOT SPOTLIGHT", color = GoldButtonText, fontSize = 8.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = SurfaceVariantDark) {
                                Text(drama.releaseYear.ifBlank { "2026" }, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF282415), border = BorderStroke(0.6.dp, GoldVip.copy(alpha = 0.5f))) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(10.dp))
                                    Text(if (drama.rating > 0) drama.rating.toString() else "8.5", color = GoldVip, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        // 🎯 ছোট ও পরিষ্কার ডিসপ্লে নেম
                        Text(drama.displayName, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            (listOf("All", drama.dubBadge, drama.country) + drama.categories.take(2)).filter { it.isNotBlank() }.distinct().forEach { tag ->
                                Surface(shape = RoundedCornerShape(4.dp), color = SurfaceVariantDark.copy(alpha = 0.8f)) {
                                    Text(tag, color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onWatchClick(drama) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldVip, contentColor = GoldButtonText),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GoldButtonText, modifier = Modifier.size(15.dp))
                                    Text("Watch Now", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { onDetailsClick(drama) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = TextPrimary),
                                border = BorderStroke(0.8.dp, BorderDark),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                    Text("Details", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(108.dp)
                            .height(150.dp)
                            .graphicsLayer { translationY = floatY }
                            .clip(RoundedCornerShape(8.dp))
                            .border(width = 1.dp, color = Color(0xFF2E384D), shape = RoundedCornerShape(8.dp))
                            .clickable { onWatchClick(drama) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(drama.posterUrl ?: drama.bannerUrl).crossfade(true).build(),
                            contentDescription = drama.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                spotlightDramas.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .height(3.dp)
                            .width(if (isSelected) 14.dp else 3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (isSelected) Color(0xFF388BFF) else SurfaceVariantDark)
                            .clickable { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFFFF2A4B)))
            Text(title, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.clickable { onSeeAllClick() }
        ) {
            Text("See All", color = Color(0xFFFF2A4B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFFF2A4B), modifier = Modifier.size(13.dp))
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
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dramas) { drama ->
            DramaPosterCardHorizontal(
                drama = drama,
                onClick = { onDramaClick(drama) },
                modifier = Modifier.width(118.dp)
            )
        }
    }
}

// =========================================================================
// 🖼️ কমপ্যাক্ট কার্ড (ডাইনামিক ডাবিং ব্যাজ ও Display Name সহ)
// =========================================================================
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
                .height(162.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(0.6.dp, Color(0xFF1E2638), RoundedCornerShape(8.dp))
                .background(SurfaceDark)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(drama.posterUrl ?: drama.bannerUrl).crossfade(true).build(),
                contentDescription = drama.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            )

            // 🎯 সার্ভার থেকে আসা যেকোনো ভাষার ডাবিং ব্যাজ
            LanguageDubBadge(
                dubText = drama.dubBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Text(
                text = "${drama.totalEpisodes} Episodes",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // 🎯 পরিচ্ছন্ন ছোট নাম
        Text(
            text = drama.displayName,
            color = Color(0xFFDCE0E8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// 🧭 ফ্রস্টেড গ্লাস ৫-ট্যাব বটম ন্যাভিগেশন বার
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
                    colors = listOf(Color(0x88080C14), Color(0xB3080C14))
                )
            )
            .border(
                width = 0.6.dp,
                color = Color(0x2EFFFFFF),
                shape = RectangleShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(46.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in BottomNavTab.entries) {
                val isSelected = (tab == selectedTab)

                val iconTint = when {
                    tab == BottomNavTab.VIP -> Color(0xFFFFB300)
                    isSelected -> Color(0xFFFFFFFF)
                    else -> Color(0xFF8E95A5)
                }

                val textColor = when {
                    tab == BottomNavTab.VIP -> Color(0xFFFFB300)
                    isSelected -> Color(0xFFFFFFFF)
                    else -> Color(0xFF8E95A5)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (tab) {
                                BottomNavTab.HOME -> {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Home else Icons.Outlined.Home,
                                        contentDescription = "Home",
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                BottomNavTab.SHORT_TV -> {
                                    ShortTvCustomStackedIcon(
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                BottomNavTab.VIP -> {
                                    VipCrownIllustratedIcon(
                                        modifier = Modifier.size(24.dp, 20.dp)
                                    )
                                }
                                BottomNavTab.DOWNLOADS -> {
                                    DownloadsCustomTrayIcon(
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (activeDownloadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 5.dp, y = (-4).dp)
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (activeDownloadCount > 9) "9+" else activeDownloadCount.toString(),
                                                color = Color.Black,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                                BottomNavTab.ME -> {
                                    MeCustomUserIcon(
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when (tab) {
                                BottomNavTab.SHORT_TV -> "Short"
                                else -> tab.label
                            },
                            color = textColor,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
