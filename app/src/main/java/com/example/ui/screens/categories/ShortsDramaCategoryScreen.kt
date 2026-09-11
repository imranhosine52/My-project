@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.categories

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.util.R2DownloadManager
import kotlin.math.absoluteValue

@Composable
fun ShortsDramaCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeListingViewType by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0C0F15))) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
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
            val topSliderItems = remember(items) { items.take(10) }
            val gridItems = remember(items) { items.chunked(3) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = statusBarTop + 94.dp,
                    bottom = 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ১. 🎬 খাঁটি ৯:১৬ টিকটক সাইজ স্লাইডার (উভয় পাশের ৫০% অংশ দৃশ্যমান)
                if (topSliderItems.isNotEmpty()) {
                    item {
                        ShortTvTopVideoCarousel(
                            dramas = topSliderItems,
                            onDramaClick = { drama -> onNavigateToPlayer(drama.slug) }
                        )
                    }
                }

                // ২. 🔘 ২ নম্বর ছবির ৩টি ফিল্টার বাটন: [ Latest ]  [ Hottest ]  [ All ]
                item {
                    ShortTvFilterPillsRow(
                        onSelectFilter = { filterName ->
                            activeListingViewType = filterName
                        }
                    )
                }

                // ৩. 🏷️ সেকশন হেডার
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Short TV",
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

                // ৪. 🖼️ নিচের ৩-কলাম ড্রামা গ্রিড
                items(gridItems.size) { rowIndex ->
                    val rowDramas = gridItems[rowIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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

        // =========================================================================
        // 🚀 ১ নম্বর ছবির ফুলস্ক্রিন পেজ (উপরের কোনো সার্চবার/হেডার থাকবে না)
        // =========================================================================
        if (activeListingViewType != null) {
            val displayList = remember(activeListingViewType, items) {
                when (activeListingViewType) {
                    "Latest" -> items.take(15)
                    "Hottest" -> items.sortedByDescending { it.numericViews }
                    else -> items
                }
            }

            Dialog(
                onDismissRequest = { activeListingViewType = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true
                )
            ) {
                ShortsListingTopPicksView(
                    title = when (activeListingViewType) {
                        "Latest" -> "Latest Releases"
                        "Hottest" -> "Hottest Short Dramas"
                        else -> "Top Picks"
                    },
                    items = displayList,
                    onBackClick = { activeListingViewType = null },
                    onItemClick = { drama ->
                        activeListingViewType = null
                        onNavigateToPlayer(drama.slug)
                    },
                    onDownloadClick = { drama ->
                        val downloadUrl = drama.shareUrl ?: "https://cdn.playdramaflix.com/streams/${drama.slug}/ep_1/download.mp4"
                        R2DownloadManager.startDownload(
                            context = context,
                            downloadUrl = downloadUrl,
                            title = drama.title,
                            episodeNumber = 1
                        )
                    }
                )
            }
        }
    }
}

// =========================================================================
// 🎬 ১. নিখুঁত ৯:১৬ টিকটক সাইজ স্লাইডার (উভয় পাশের ৫০% অংশ দৃশ্যমান)
// =========================================================================
@Composable
fun ShortTvTopVideoCarousel(
    dramas: List<ContentItemDto>,
    onDramaClick: (ContentItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // 🎯 খাঁটি ৯:১৬ টিকটক কার্ড মাপ ক্যালকুলেশন (উভয় পাশে ৫০% দৃশ্যমান থাকবে)
    val cardWidth = (screenWidth * 0.64f).coerceIn(230.dp, 260.dp)
    val cardHeight = cardWidth * (16f / 9f)
    val sidePadding = ((screenWidth - cardWidth) / 2)

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { dramas.size }
    )

    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSpacing = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
        ) { page ->
            val drama = dramas[page]
            val isCurrentPage = pagerState.currentPage == page

            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val cardScale = lerp(0.84f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))
            val cardAlpha = lerp(0.55f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF141822))
                    .border(
                        width = if (isCurrentPage) 1.2.dp else 0.8.dp,
                        color = if (isCurrentPage) Color(0x6600E5FF) else Color(0x22FFFFFF),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onDramaClick(drama) }
            ) {
                // ১. বেস পোস্টার ইমেজ
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(drama.posterUrl ?: drama.bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = drama.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // ২. ⚡ ১ম পর্বের MP4 লাইভ ভিডিও অটো-প্লে (মাঝের পেজে)
                val ep1VideoUrl = remember(drama.slug) {
                    drama.trailerUrl.takeIf { !it.isNullOrBlank() && (it.endsWith(".mp4") || it.contains("cdn.")) }
                        ?: "https://cdn.playdramaflix.com/streams/${drama.slug}/ep_1/download.mp4"
                }

                if (isCurrentPage) {
                    ShortTvInlineVideoPlayer(
                        videoUrl = ep1VideoUrl,
                        isMuted = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ৩. সিনেমাটিক শ্যাডো
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.80f)
                                )
                            )
                        )
                )

                // 🟢 ২ নম্বর ছবির মতো নিয়ন গ্রিন প্লে বাটন (নিচে ডান পাশে)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF00E676))
                            )
                        )
                        .clickable { onDramaClick(drama) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// =========================================================================
// 🔘 ২ নম্বর ছবির ৩টি ফিল্টার বাটন: [ Latest ]  [ Hottest ]  [ All ]
// =========================================================================
@Composable
fun ShortTvFilterPillsRow(
    onSelectFilter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ১. Latest Button (Blue Icon)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E2430),
            border = BorderStroke(0.8.dp, Color(0xFF2C3545)),
            modifier = Modifier
                .weight(1.3f)
                .height(44.dp)
                .clickable { onSelectFilter("Latest") }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E88E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text("Latest", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ২. Hottest Button (Green Flame Icon)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E2430),
            border = BorderStroke(0.8.dp, Color(0xFF2C3545)),
            modifier = Modifier
                .weight(1.3f)
                .height(44.dp)
                .clickable { onSelectFilter("Hottest") }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text("Hottest", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ৩. All Button
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E2430),
            border = BorderStroke(0.8.dp, Color(0xFF2C3545)),
            modifier = Modifier
                .weight(0.8f)
                .height(44.dp)
                .clickable { onSelectFilter("All") }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("All", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================
// 📱 ১ নম্বর ছবির হুবহু লিস্টিং ও টপ পিকস পেজ (সম্পূর্ণ হেডার-মুক্ত ফুলস্ক্রিন)
// =========================================================================
@Composable
fun ShortsListingTopPicksView(
    title: String,
    items: List<ContentItemDto>,
    onBackClick: () -> Unit,
    onItemClick: (ContentItemDto) -> Unit,
    onDownloadClick: (ContentItemDto) -> Unit
) {
    val topHeroDrama = items.firstOrNull()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F15))
    ) {
        // 🔝 ১ নম্বর ছবির টপ হিরো ব্যানার ও ব্যাক বাটন (<)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
            if (topHeroDrama != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(topHeroDrama.bannerUrl ?: topHeroDrama.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.85f),
                                Color(0xFF121622)
                            )
                        )
                    )
            )

            // ব্যাক বাটন (<)
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // হেডার টাইটেল: "Top Picks"
            Text(
                text = title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .statusBarsPadding()
            )
        }

        // 📋 ১ নম্বর ছবির কার্ডের তালিকা
        Surface(
            color = Color(0xFF121622),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-20).dp)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.slug }) { drama ->
                    TopPicksItemRow(
                        drama = drama,
                        onClick = { onItemClick(drama) },
                        onDownload = { onDownloadClick(drama) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ১ নম্বর ছবির সিঙ্গেল রো কার্ড
// -------------------------------------------------------------
@Composable
fun TopPicksItemRow(
    drama: ContentItemDto,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ১. পোস্টার থাম্বনেইল
        Box(
            modifier = Modifier
                .width(66.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(8.dp))
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
        }

        // ২. টাইটেল, স্টার রেটিং, বিবরণ ও ডাউনলোড বাটন
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = drama.title,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )

                // ⭐ স্টার রেটিং
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (drama.rating > 0) String.format("%.1f", drama.rating) else "7.8",
                        color = Color(0xFFFFB300),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // বিবরণ টেক্সট (১-২ লাইন)
            Text(
                text = drama.description?.takeIf { it.isNotBlank() } ?: drama.synopsis,
                color = Color(0xFF8E95A5),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // ⬇️ ১ নম্বর ছবির হুবহু ডাউনলোড বাটন
            Button(
                onClick = onDownload,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Download",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =========================================================================
// 🎥 ১ম পর্বের লাইভ অটো-প্লে ExoPlayer ইঞ্জিন
// =========================================================================
@OptIn(UnstableApi::class)
@Composable
fun ShortTvInlineVideoPlayer(
    videoUrl: String,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVideoReady by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            try {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ALL
                volume = if (isMuted) 0f else 1f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            isVideoReady = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isVideoReady = false
                    }
                })
                prepare()
                playWhenReady = true
            } catch (e: Exception) {
                isVideoReady = false
            }
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
        modifier = modifier.graphicsLayer {
            alpha = if (isVideoReady) 1f else 0f
        }
    )
}

// =========================================================================
// 🖼️ নিচের ৩-কলাম ড্রামা গ্রিড কার্ড
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
                .aspectRatio(0.70f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
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
