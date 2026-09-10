@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.categories

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import kotlin.math.absoluteValue

@Composable
fun ShortsDramaCategoryScreen(
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
                text = "No Short TV dramas found",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        // টপ স্লাইডারের জন্য সর্বশেষ ৬টি ড্রামা
        val topSliderItems = remember(items) { items.take(6) }
        val gridItems = remember(items) { items.chunked(3) }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + 94.dp,
                bottom = 72.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ১. 🎬 টপ স্লাইডার (অটোপ্লে ভিডিও প্রিভিউ সহ)
            if (topSliderItems.isNotEmpty()) {
                item {
                    ShortTvTopVideoCarousel(
                        dramas = topSliderItems,
                        onDramaClick = { drama -> onNavigateToPlayer(drama.slug) }
                    )
                }
            }

            // ২. 🏷️ সেকশন হেডার
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Short TV", // 👈 নাম আপডেট করা হয়েছে
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${items.size} Dramas",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ৩. 🖼️ নিচের ৩-কলাম গ্রিড (আগের ছবির রেফারেন্স অনুযায়ী ১০৮-১১০dp কার্ড)
            items(gridItems.size) { rowIndex ->
                val rowDramas = gridItems[rowIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp) // ছবির মতো ১০dp ফাঁকা
                ) {
                    rowDramas.forEach { drama ->
                        Box(modifier = Modifier.weight(1f)) {
                            ShortTvGridDramaCard(
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
        }
    }
}

// =========================================================================
// 🎬 ১. টপ হিরো স্লাইডার (ভিডিও প্লেয়ার + বুকমার্ক + সাউন্ড বাটন)
// =========================================================================
@Composable
fun ShortTvTopVideoCarousel(
    dramas: List<ContentItemDto>,
    onDramaClick: (ContentItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { dramas.size }
    )

    var isMuted by remember { mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 38.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp) // 👈 ছবির মতো বড় ভার্টিক্যাল অনুপাত
        ) { page ->
            val drama = dramas[page]
            val isCurrentPage = pagerState.currentPage == page

            // স্লাইড করার সময় পাশের কার্ডগুলো একটু ছোট হওয়ার 3D এফেক্ট
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val cardScale = lerp(0.90f, 1f, 1f - pageOffset.coerceIn(0f, 1f))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF141820))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                    .clickable { onDramaClick(drama) }
            ) {
                // ভিডিও অথবা পোস্টার লোড
                val videoUrl = drama.trailerUrl ?: drama.videoUrl
                if (!videoUrl.isNullOrBlank() && isCurrentPage) {
                    ShortTvInlineVideoPlayer(
                        videoUrl = videoUrl,
                        isMuted = isMuted,
                        posterUrl = drama.posterUrl ?: drama.bannerUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = drama.posterUrl ?: drama.bannerUrl,
                        contentDescription = drama.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ডার্ক গ্রেডিয়েন্ট ওভারলে
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                // 🔖 বুকমার্ক বাটন (উপরে ডান পাশে - ছবির মতো)
                var isBookmarked by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { isBookmarked = !isBookmarked },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .background(Color(0x55000000), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) Color(0xFFFFD700) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 🔊 সাউন্ড মিউট/আনমিউট বাটন (নিচে ডান পাশে)
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Sound Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 🏷️ ড্রামার টাইটেল ও ব্যাজ (নিচে বাম পাশে)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 14.dp, end = 50.dp)
                ) {
                    // ডাবিং ব্যাজ
                    DubbingLanguageBadge(drama = drama)

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = drama.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 🔘 ডট ইন্ডিকেটর (নিচে কয় নম্বর স্লাইড চলছে তা দেখার জন্য)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(dramas.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color(0xFF00E5FF) else Color(0x66FFFFFF)
                        )
                )
            }
        }
    }
}

// =========================================================================
// 🎥 ভিডিও প্লেয়ার কম্পোনেন্ট (ExoPlayer অটোপ্লে)
// =========================================================================
@OptIn(UnstableApi::class)
@Composable
fun ShortTvInlineVideoPlayer(
    videoUrl: String,
    isMuted: Boolean,
    posterUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ALL
            volume = if (isMuted) 0f else 1f
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

// =========================================================================
// 🖼️ ২. নিচের ৩-কলাম গ্রিড কার্ড (আগের রেফারেন্স সাইজ ১১০dp × ১৫৮dp)
// =========================================================================
@Composable
fun ShortTvGridDramaCard(
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
                .aspectRatio(0.70f) // 👈 ১১০dp × ১৫৮dp এর হুবহু অনুপাত
                .clip(RoundedCornerShape(8.dp)) // 👈 ৮dp কর্নার রেডিয়াস
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

            // নিচে হালকা শ্যাডো
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

            // 🏷️ ডাবিং ব্যাজ (উপরে ডান পাশে)
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DubbingLanguageBadge(drama = drama)
            }

            // এপিসোড সংখ্যা (নিচে বামে)
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Eps" else "Short TV"
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

        Spacer(modifier = Modifier.height(6.dp))

        // ড্রামার নাম (১ লাইনে সীমাবদ্ধ)
        Text(
            text = drama.title,
            color = Color(0xFFEDEDED),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// 🏷️ ডাবিং ব্যাজ লজিক (বাংলা, Hindi, English)
// =========================================================================
@Composable
fun DubbingLanguageBadge(drama: ContentItemDto) {
    val isBangla = drama.isBanglaDub || drama.dubBadge.contains("Bangla", ignoreCase = true) || drama.dubBadge.contains("বাংলা", ignoreCase = true)
    val isHindi = drama.dubBadge.contains("Hindi", ignoreCase = true)
    val isEnglish = drama.dubBadge.contains("English", ignoreCase = true) || drama.dubBadge.contains("Eng", ignoreCase = true)

    val (badgeText, badgeBgColor, badgeTextColor) = when {
        isBangla -> Triple("বাংলা", Color(0xFFFFB300), Color.Black) // 👈 বাংলায় "বাংলা"
        isHindi -> Triple("Hindi", Color(0xFF00B0FF), Color.Black)   // 👈 ইংরেজিতে "Hindi"
        isEnglish -> Triple("English", Color(0xFF10B981), Color.White)// 👈 ইংরেজিতে "English"
        drama.dubBadge.isNotBlank() -> Triple(drama.dubBadge, Color(0xFF6366F1), Color.White)
        else -> Triple("", Color.Transparent, Color.Transparent)
    }

    if (badgeText.isNotBlank()) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(bottomStart = 6.dp, topEnd = 8.dp))
                .background(badgeBgColor)
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = badgeText,
                color = badgeTextColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
