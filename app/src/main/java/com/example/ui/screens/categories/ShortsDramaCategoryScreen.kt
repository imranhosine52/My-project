@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.categories

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.AppDatabase
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.util.R2DownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val CHUNK_SIZE_BATCH = 25

@Composable
fun ShortsDramaCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // সক্রিয় পেজ স্টেট (null = Main Feed, "Latest" | "Hottest" = Listing Page, "All" = 4-Column Filter Page)
    var activeListingViewType by remember { mutableStateOf<String?>(null) }
    var targetDramaForBatchDownload by remember { mutableStateOf<ContentItemDto?>(null) }

    // 🔖 My List পর্যবেক্ষণ (ডাটাবেজে সেভ থাকলে তবেই শো করবে)
    val watchlistDao = remember { AppDatabase.getInstance(context).watchlistDao() }
    val watchlistEntities by watchlistDao.getAllWatchlist().collectAsState(initial = emptyList())
    val mySavedShorts = remember(watchlistEntities, items) {
        val savedIds = watchlistEntities.map { it.id }.toSet()
        items.filter { it.slug in savedIds || it.id in savedIds }
    }

    // 🔀 প্রতি রিফ্রেশে কার্ডের অবস্থান পরিবর্তনের স্মার্ট অ্যালগরিদম
    var refreshSeed by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dynamicGridItems = remember(items, refreshSeed) {
        if (items.size <= 3) items
        else items.shuffled(java.util.Random(refreshSeed))
    }

    // হার্ডওয়্যার ব্যাক বাটন হ্যান্ডলার
    BackHandler(enabled = targetDramaForBatchDownload != null || activeListingViewType != null) {
        when {
            targetDramaForBatchDownload != null -> targetDramaForBatchDownload = null
            activeListingViewType != null -> activeListingViewType = null
        }
    }

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
            val gridChunks = remember(dynamicGridItems) { dynamicGridItems.chunked(3) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = statusBarTop + 94.dp,
                    bottom = 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =============================================================
                // ১. 🎬 ইনফিনিট লুপ ও অটো-স্লাইডিং ৯:১৬ ক্যারোজেল (১ম ছবি)
                // =============================================================
                if (topSliderItems.isNotEmpty()) {
                    item {
                        InfiniteShortTvTopVideoCarousel(
                            dramas = topSliderItems,
                            onDramaClick = { drama -> onNavigateToPlayer(drama.slug) }
                        )
                    }
                }

                // =============================================================
                // ২. 🔘 ৩টি ফিল্টার পিল: [ Latest ]  [ Hottest ]  [ All ]
                // =============================================================
                item {
                    ShortTvFilterPillsRow(
                        onSelectFilter = { filterName ->
                            activeListingViewType = filterName
                        }
                    )
                }

                // =============================================================
                // ৩. 🔖 ডাইনামিক "My List" রো (ড্রামা সেভ থাকলে তবেই শো করবে)
                // =============================================================
                if (mySavedShorts.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(17.dp))
                                    Text("My List", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("${mySavedShorts.size} Saved", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(mySavedShorts, key = { "saved_${it.slug}" }) { drama ->
                                    ShortTvMyListCard(
                                        drama = drama,
                                        onClick = { onNavigateToPlayer(drama.slug) }
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // ৪. 🏷️ সেকশন হেডার ও রোটেশনাল ৩-কলাম ড্রামা গ্রিড
                // =============================================================
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

                items(gridChunks.size) { rowIndex ->
                    val rowDramas = gridChunks[rowIndex]
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
        // 🚀 ৩ নম্বর ছবির হুবহু ৪-কলামের "All" ফিল্টার পেজ (Bangla, Hindi, English)
        // =========================================================================
        if (activeListingViewType == "All") {
            Dialog(
                onDismissRequest = { activeListingViewType = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
            ) {
                ShortsFilterAllScreen(
                    items = items,
                    onBackClick = { activeListingViewType = null },
                    onItemClick = { drama ->
                        activeListingViewType = null
                        onNavigateToPlayer(drama.slug)
                    }
                )
            }
        }

        // =========================================================================
        // 🚀 ১ নম্বর ছবির হুবহু লিস্টিং পেজ (Latest / Hottest)
        // =========================================================================
        if (activeListingViewType == "Latest" || activeListingViewType == "Hottest") {
            val displayList = remember(activeListingViewType, items) {
                if (activeListingViewType == "Latest") items.take(15)
                else items.sortedByDescending { it.numericViews }
            }

            Dialog(
                onDismissRequest = { activeListingViewType = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
            ) {
                ShortsListingTopPicksView(
                    title = if (activeListingViewType == "Latest") "Latest Releases" else "Hottest Short Dramas",
                    items = displayList,
                    onBackClick = { activeListingViewType = null },
                    onItemClick = { drama ->
                        activeListingViewType = null
                        onNavigateToPlayer(drama.slug)
                    },
                    onDownloadClick = { drama ->
                        targetDramaForBatchDownload = drama
                    }
                )
            }
        }

        // =========================================================================
        // 📥 ২ নম্বর ছবির হুবহু মাল্টি-এপিসোড ব্যাচ ডাউনলোড পপ-আপ
        // =========================================================================
        targetDramaForBatchDownload?.let { drama ->
            val totalEps = if (drama.totalEpisodes > 0) drama.totalEpisodes else 38
            val dramaEpisodes = remember(drama) {
                (1..totalEps).map { num ->
                    EpisodeDto(
                        rawEpisodeId = "ep_${drama.slug}_$num",
                        episodeNumber = num,
                        downloadUrl = "https://cdn.playdramaflix.com/streams/${drama.slug}/ep_$num/download.mp4"
                    )
                }
            }

            ShortsEpisodeBatchDownloadModal(
                dramaTitle = drama.title,
                dramaSlug = drama.slug,
                episodes = dramaEpisodes,
                onDismiss = { targetDramaForBatchDownload = null },
                onStartBatchDownload = { selectedEps ->
                    targetDramaForBatchDownload = null
                    selectedEps.forEach { ep ->
                        R2DownloadManager.startDownload(
                            context = context,
                            downloadUrl = ep.resolveDownloadUrl(drama.slug),
                            title = drama.title,
                            episodeNumber = ep.episodeNumber
                        )
                    }
                    Toast.makeText(context, "Downloading ${selectedEps.size} episodes in background...", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// =========================================================================
// 🎬 ১. ইনফিনিট লুপ ও অটো-স্লাইডিং ৯:১৬ ক্যারোজেল
// =========================================================================
@Composable
fun InfiniteShortTvTopVideoCarousel(
    dramas: List<ContentItemDto>,
    onDramaClick: (ContentItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val actualCount = dramas.size
    if (actualCount == 0) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // ৯:১৬ রেশিও মাপ ক্যালকুলেশন
    val cardWidth = (screenWidth * 0.64f).coerceIn(230.dp, 260.dp)
    val cardHeight = cardWidth * (16f / 9f)
    val sidePadding = ((screenWidth - cardWidth) / 2)

    val virtualCount = if (actualCount > 1) 10_000 else 1
    val initialPage = remember(actualCount) {
        if (actualCount > 1) (virtualCount / 2) - ((virtualCount / 2) % actualCount) else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { virtualCount }
    )

    val context = LocalContext.current

    // ⏱️ ৪.৫ সেকেন্ড পর পর মসৃণ অটো-স্লাইডিং লুপ
    LaunchedEffect(pagerState.currentPage, actualCount) {
        if (actualCount > 1) {
            while (isActive) {
                delay(4500L)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage + 1,
                        animationSpec = tween(700, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

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
            val drama = dramas[page % actualCount]
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

                // ২. ⚡ ১ম পর্বের MP4 লাইভ ভিডিও অটো-প্লে
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

                // 🟢 ২ নম্বর ছবির মতো নিয়ন গ্রিন প্লে বাটন (নিচে ডান পাশে)
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
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Text("Latest", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }

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
                    Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                }
                Text("Hottest", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E2430),
            border = BorderStroke(0.8.dp, Color(0xFF2C3545)),
            modifier = Modifier
                .weight(0.8f)
                .height(44.dp)
                .clickable { onSelectFilter("All") }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("All", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================
// 🔖 My List হরাইজন্টাল কার্ড
// =========================================================================
@Composable
fun ShortTvMyListCard(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .width(105.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )
            Text(
                text = "${drama.totalEpisodes} Eps",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = drama.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// 🎨 ৩ নম্বর ছবির হুবহু ৪-কলাম ফিল্টার পেজ (Bangla, Hindi, English ট্যাব)
// =========================================================================
@Composable
fun ShortsFilterAllScreen(
    items: List<ContentItemDto>,
    onBackClick: () -> Unit,
    onItemClick: (ContentItemDto) -> Unit
) {
    val context = LocalContext.current
    val filterTabs = listOf("All", "Bangla", "Hindi", "English")
    var selectedTab by remember { mutableStateOf("All") }

    val filteredList = remember(selectedTab, items) {
        when (selectedTab) {
            "Bangla" -> items.filter { it.isBanglaDub || it.dubBadge.contains("Bangla", true) }
            "Hindi" -> items.filter { it.isHindiDub || it.dubBadge.contains("Hindi", true) }
            "English" -> items.filter { it.dubBadge.contains("Eng", true) || it.language.contains("Eng", true) }
            else -> items
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12151D))
            .statusBarsPadding()
    ) {
        // ৩ নম্বর ছবির টপ হেডার
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text("Filter", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // ৩টি নীল দাগের জায়গায়: ভাষা ফিল্টার ট্যাব (All, Bangla, Hindi, English)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterTabs.forEach { tabName ->
                val isSelected = selectedTab == tabName
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF00E676) else Color(0xFF1E2432),
                    border = BorderStroke(0.8.dp, if (isSelected) Color(0xFF00E676) else Color(0xFF2C3546)),
                    modifier = Modifier.clickable { selectedTab = tabName }
                ) {
                    Text(
                        text = tabName,
                        color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ৩ নম্বর ছবির হুবহু ৪-কলাম ড্রামা গ্রিড
        LazyVerticalGrid(
            columns = GridCells.Fixed(4), // 👈 ৪ কলাম
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredList, key = { "filter_${it.slug}" }) { drama ->
                ShortsFourColumnGridCard(
                    drama = drama,
                    onClick = { onItemClick(drama) }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// ৩ নম্বর ছবির ৪-কলাম সিঙ্গেল কার্ড
// -------------------------------------------------------------
@Composable
fun ShortsFourColumnGridCard(
    drama: ContentItemDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isBangla = drama.isBanglaDub || drama.dubBadge.contains("Bangla", true)
    val isHindi = drama.isHindiDub || drama.dubBadge.contains("Hindi", true)
    val langLabel = if (isBangla) "Bangla" else if (isHindi) "Hindi" else "English"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(6.dp))
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

            // উপরে-ডানে ভাষা ব্যাজ (যেমন: English / Bangla)
            Surface(
                shape = RoundedCornerShape(bottomStart = 4.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = langLabel,
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            // নিচে-ডানে গোল্ডেন স্টার রেটিং (যেমন: 7.9)
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = if (drama.rating > 0) String.format("%.1f", drama.rating) else "7.8",
                    color = Color(0xFFFFB300),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = drama.title,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${drama.type.replaceFirstChar { it.uppercase() }} • ${drama.country}",
            color = Color(0xFF7E8698),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// 📥 ২ নম্বর ছবির হুবহু মাল্টি-এপিসোড ব্যাচ ডাউনলোড শিট
// =========================================================================
@Composable
fun ShortsEpisodeBatchDownloadModal(
    dramaTitle: String,
    dramaSlug: String,
    episodes: List<EpisodeDto>,
    onDismiss: () -> Unit,
    onStartBatchDownload: (List<EpisodeDto>) -> Unit
) {
    val episodeChunks = remember(episodes) { episodes.chunked(CHUNK_SIZE_BATCH) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }
    val selectedEpisodes = remember { mutableStateListOf<EpisodeDto>() }

    val isAllSelected = remember(selectedEpisodes.size, episodes.size) {
        selectedEpisodes.size == episodes.size && episodes.isNotEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181C26),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ২ নম্বর ছবির হেডার: Drama Title ও Close 'X'
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dramaTitle,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = Color(0xFF262E3E), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 8.dp))

            Text("Download", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            // রেঞ্জ ট্যাব (1-25, 26-38)
            if (episodeChunks.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(episodeChunks) { index, chunk ->
                        val start = index * CHUNK_SIZE_BATCH + 1
                        val end = start + chunk.size - 1
                        val isSelected = (index == selectedChunkIndex)

                        Text(
                            text = "$start-$end",
                            color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clickable { selectedChunkIndex = index }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

            // ২ নম্বর ছবির হুবহু ৫-কলাম গ্রিড
            LazyVerticalGrid(
                columns = GridCells.Fixed(5), // 👈 ৫ কলাম
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            ) {
                items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                    val isSelected = selectedEpisodes.contains(ep)

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF0F3B32) else Color(0xFF262B38))
                            .border(
                                width = if (isSelected) 1.2.dp else 0.dp,
                                color = if (isSelected) Color(0xFF00E676) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                if (isSelected) selectedEpisodes.remove(ep) else selectedEpisodes.add(ep)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ep.episodeNumber.toString(),
                            color = if (isSelected) Color(0xFF00E676) else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // সিলেক্ট সার্কেল আইকন
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(11.dp)
                                .clip(CircleShape)
                                .border(1.dp, if (isSelected) Color(0xFF00E676) else Color(0xFF6B7280), CircleShape)
                                .background(if (isSelected) Color(0xFF00E676) else Color.Transparent)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF262E3E), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 6.dp))

            // ২ নম্বর ছবির নিচের বার: [ ◯ Select All ] ও [ Download (Green Button) ]
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        if (isAllSelected) selectedEpisodes.clear()
                        else {
                            selectedEpisodes.clear()
                            selectedEpisodes.addAll(episodes)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, if (isAllSelected) Color(0xFF00E676) else Color(0xFF8E95A5), CircleShape)
                            .background(if (isAllSelected) Color(0xFF00E676) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAllSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                        }
                    }
                    Text("Select All", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val targets = if (selectedEpisodes.isNotEmpty()) selectedEpisodes.toList() else episodes.take(1)
                        onStartBatchDownload(targets)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth(0.72f).height(42.dp)
                ) {
                    Text(
                        text = if (selectedEpisodes.isNotEmpty()) "Download (${selectedEpisodes.size})" else "Download",
                        color = Color.Black,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =========================================================================
// 📱 ১ নম্বর ছবির হুবহু লিস্টিং পেজ (Top Picks / Listing View)
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
        // ১ নম্বর ছবির টপ ব্যানার ও ব্যাক বাটন (<)
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

            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }

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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(13.dp))
                    Text(
                        text = if (drama.rating > 0) String.format("%.1f", drama.rating) else "7.8",
                        color = Color(0xFFFFB300),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = drama.description?.takeIf { it.isNotBlank() } ?: drama.synopsis,
                color = Color(0xFF8E95A5),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onDownload,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("Download", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// 🎥 ১ম পর্বের লাইভ অটো-প্লে ExoPlayer ইঞ্জিন
// =========================================================================
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
                        if (playbackState == Player.STATE_READY) isVideoReady = true
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
        onDispose { exoPlayer.release() }
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
        modifier = modifier.graphicsLayer { alpha = if (isVideoReady) 1f else 0f }
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
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Eps" else "Short TV"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 5.dp)
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
