@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens.categories

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ShortTvNavHelper
import com.example.data.local.AppDatabase
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import com.example.data.remote.ApiClient
import com.example.ui.theme.GoldVip
import com.example.util.DownloadQuotaManager
import com.example.util.R2DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.absoluteValue

private const val CHUNK_SIZE_BATCH = 25

private val BlueGreenGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF007AFF),
        Color(0xFF00D166)
    )
)

private suspend fun fetchRealFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val cleanUrl = R2DownloadManager.resolveDirectMp4Url(url)
        val connection = (URL(cleanUrl).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
            setRequestProperty("Accept-Encoding", "identity")
        }
        val length = connection?.contentLengthLong ?: 0L
        connection?.disconnect()
        if (length > 0) length else 0L
    } catch (_: Exception) {
        0L
    }
}

private fun formatBytesDisplay(bytes: Long, isCalculating: Boolean = false): String {
    if (bytes <= 0L) {
        return if (isCalculating) "Calculating..." else "0 MB"
    }
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun ShortsDramaCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }
    val isUserVip = remember(authPrefs) {
        authPrefs.getBoolean("is_vip", false) ||
        (authPrefs.getString("user_plan", "free")?.lowercase() in listOf("vip", "premium"))
    }

    // 🎯 শর্ট ড্রামার পাথ ট্র্যাকিং স্টেট (Saveable যাতে প্রসেস ও ব্যাকস্ট্যাকে মান হারিয়ে না যায়)
    var activeListingViewType by rememberSaveable { 
        mutableStateOf(ShortTvNavHelper.activeSubTab) 
    }
    var targetDramaForBatchDownload by remember { mutableStateOf<ContentItemDto?>(null) }

    // সিঙ্ক শর্টটিভি নেভিগেশন স্টেট
    LaunchedEffect(ShortTvNavHelper.activeSubTab) {
        if (activeListingViewType != ShortTvNavHelper.activeSubTab) {
            activeListingViewType = ShortTvNavHelper.activeSubTab
        }
    }

    val watchlistDao = remember { AppDatabase.getInstance(context).watchlistDao() }
    val watchlistEntities by watchlistDao.getAllWatchlist().collectAsState(initial = emptyList())
    val mySavedShorts = remember(watchlistEntities, items) {
        val savedIds = watchlistEntities.map { it.id }.toSet()
        items.filter { it.slug in savedIds || it.id in savedIds }
    }

    val refreshSeed = rememberSaveable { System.currentTimeMillis() }
    val dynamicGridItems = remember(items, refreshSeed) {
        if (items.size <= 3) items
        else items.shuffled(java.util.Random(refreshSeed))
    }

    // 🎯 ব্যাক বাটন লজিক: সাব-ট্যাবে থাকলে সাব-ট্যাব বন্ধ করে মূল পেজে আনবে
    BackHandler(enabled = targetDramaForBatchDownload != null || activeListingViewType != null) {
        when {
            targetDramaForBatchDownload != null -> targetDramaForBatchDownload = null
            activeListingViewType != null -> {
                ShortTvNavHelper.activeSubTab = null
                activeListingViewType = null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0C0F15))) {
        // ১. মূল শর্ট টিভি হোম কন্টেন্ট
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
            val topSliderItems = remember(items, refreshSeed) {
                items.shuffled(java.util.Random(refreshSeed + 7)).take(10)
            }
            val gridChunks = remember(dynamicGridItems) { dynamicGridItems.chunked(3) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = statusBarTop + 94.dp,
                    bottom = 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (topSliderItems.isNotEmpty()) {
                    item {
                        SingleFocusInfiniteTopCarousel(
                            dramas = topSliderItems,
                            onDramaClick = { drama -> onNavigateToPlayer(drama.slug) }
                        )
                    }
                }

                item {
                    ShortTvFilterPillsRow(
                        onSelectFilter = { filterName ->
                            ShortTvNavHelper.activeSubTab = filterName
                            activeListingViewType = filterName
                        }
                    )
                }

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

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            ShortTvNavHelper.activeSubTab = "MyList"
                                            activeListingViewType = "MyList"
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "View All (${mySavedShorts.size})",
                                        color = Color(0xFF00E676),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                }
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
        // 🚀 স্মুথ সাব-ট্যাব ওভারলে (Dialog সরানো হয়েছে যাতে কোনো গ্লিচ বা ফ্লিকার না হয়)
        // =========================================================================
        AnimatedVisibility(
            visible = activeListingViewType != null,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(260)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(220, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            when (activeListingViewType) {
                "All" -> {
                    ShortsFilterAllScreen(
                        items = items,
                        onBackClick = {
                            ShortTvNavHelper.activeSubTab = null
                            activeListingViewType = null
                        },
                        onItemClick = { drama ->
                            onNavigateToPlayer("${drama.slug}###subTab=All")
                        }
                    )
                }
                "Latest", "Hottest", "MyList" -> {
                    val currentType = activeListingViewType
                    val displayList = remember(currentType, items, mySavedShorts) {
                        when (currentType) {
                            "Latest" -> items.take(20)
                            "Hottest" -> items.sortedByDescending { it.numericViews }
                            "MyList" -> mySavedShorts
                            else -> items
                        }
                    }

                    ShortsListingTopPicksView(
                        title = when (currentType) {
                            "Latest" -> "Latest Releases"
                            "Hottest" -> "Hottest Short Dramas"
                            "MyList" -> "My Saved Short Dramas"
                            else -> "Top Picks"
                        },
                        items = displayList,
                        onBackClick = {
                            ShortTvNavHelper.activeSubTab = null
                            activeListingViewType = null
                        },
                        onItemClick = { drama ->
                            onNavigateToPlayer("${drama.slug}###subTab=$currentType")
                        },
                        onDownloadClick = { drama ->
                            targetDramaForBatchDownload = drama
                        }
                    )
                }
            }
        }

        // =========================================================================
        // 📥 ২ নম্বর ছবির ব্যাচ ডাউনলোড পপ-আপ
        // =========================================================================
        targetDramaForBatchDownload?.let { drama ->
            ShortsEpisodeBatchDownloadModal(
                drama = drama,
                isVip = isUserVip,
                onDismiss = { targetDramaForBatchDownload = null }
            )
        }
    }
}

// =========================================================================
// 🎬 ১. একক কার্ড ফোকাসড ইনফিনিট অটো-স্লাইডার
// =========================================================================
@Composable
fun SingleFocusInfiniteTopCarousel(
    dramas: List<ContentItemDto>,
    onDramaClick: (ContentItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val actualCount = dramas.size
    if (actualCount == 0) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val cardWidth = (screenWidth * 0.63f).coerceIn(230.dp, 260.dp)
    val cardHeight = cardWidth * (16f / 9.2f)
    val horizontalSidePadding = ((screenWidth - cardWidth) / 2)

    val virtualCount = if (actualCount > 1) 10_000 else 1
    val initialPage = remember(actualCount) {
        if (actualCount > 1) (virtualCount / 2) - ((virtualCount / 2) % actualCount) else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { virtualCount }
    )

    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "carouselGlow")
    val glowOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "glowOffset"
    )

    val glowingBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x33FFFFFF),
            Color(0xFF00E5FF).copy(alpha = 0.9f),
            Color(0xFFFFD700).copy(alpha = 0.9f),
            Color(0x33FFFFFF)
        ),
        start = Offset(glowOffset, 0f),
        end = Offset(glowOffset + 220f, 320f)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalSidePadding),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
        ) { page ->
            val drama = dramas[page % actualCount]
            val isCurrentPage = pagerState.currentPage == page

            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val cardScale = lerp(0.82f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))
            val cardAlpha = lerp(0.45f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))

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
                        width = if (isCurrentPage) 1.5.dp else 0.8.dp,
                        brush = if (isCurrentPage) glowingBorderBrush else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF))),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onDramaClick(drama) }
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
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                )

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

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 12.dp, end = 52.dp)
                ) {
                    DubbingLanguageBadge(drama = drama)

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = drama.title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// 🔘 ফিল্টার বাটনসমূহ: [ Latest ]  [ Hottest ]  [ All ]
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
// 🔖 My List কার্ড
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
// 🎨 ৪-কলাম ফিল্টার পেজ
// =========================================================================
@Composable
fun ShortsFilterAllScreen(
    items: List<ContentItemDto>,
    onBackClick: () -> Unit,
    onItemClick: (ContentItemDto) -> Unit
) {
    val filterTabs = listOf("All", "Bangla", "Hindi", "English")
    var selectedTab by rememberSaveable { mutableStateOf("All") }

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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding()
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

            Surface(
                shape = RoundedCornerShape(topStart = 4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = if (drama.rating > 0) String.format(Locale.US, "%.1f", drama.rating) else "7.8",
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
// 📥 ২ নম্বর ছবির ব্যাচ ডাউনলোড শিট
// =========================================================================
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShortsEpisodeBatchDownloadModal(
    drama: ContentItemDto,
    isVip: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var loadedEpisodes by remember { mutableStateOf<List<EpisodeDto>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }

    LaunchedEffect(drama.slug) {
        withContext(Dispatchers.IO) {
            try {
                val res = ApiClient.apiService.getWatchDetails(drama.slug)
                if (res.isSuccessful && res.body()?.episodes?.isNotEmpty() == true) {
                    loadedEpisodes = res.body()!!.episodes
                } else {
                    val count = if (drama.totalEpisodes > 0) drama.totalEpisodes else 20
                    loadedEpisodes = (1..count).map { num ->
                        EpisodeDto(
                            rawEpisodeId = "ep_${drama.slug}_$num",
                            episodeNumber = num,
                            downloadUrl = "https://cdn.playdramaflix.com/streams/${drama.slug}/ep_$num/download.mp4"
                        )
                    }
                }
            } catch (_: Exception) {
                val count = if (drama.totalEpisodes > 0) drama.totalEpisodes else 20
                loadedEpisodes = (1..count).map { num ->
                    EpisodeDto(
                        rawEpisodeId = "ep_${drama.slug}_$num",
                        episodeNumber = num,
                        downloadUrl = "https://cdn.playdramaflix.com/streams/${drama.slug}/ep_$num/download.mp4"
                    )
                }
            }
            isLoadingEpisodes = false
        }
    }

    val episodes = loadedEpisodes
    val episodeChunks = remember(episodes) { episodes.chunked(CHUNK_SIZE_BATCH) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }
    val selectedEpisodes = remember { mutableStateListOf<EpisodeDto>() }

    val isAllSelected = remember(selectedEpisodes.size, episodes.size) {
        selectedEpisodes.size == episodes.size && episodes.isNotEmpty()
    }

    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    var isFetchingSizes by remember { mutableStateOf(false) }

    LaunchedEffect(selectedEpisodes.toList()) {
        val uncalculated = selectedEpisodes.filter { ep ->
            val key = "${ep.episodeId}_${ep.episodeNumber}"
            !realFileSizes.containsKey(key) || (realFileSizes[key] ?: 0L) <= 0L
        }

        if (uncalculated.isNotEmpty()) {
            isFetchingSizes = true
            uncalculated.forEach { ep ->
                coroutineScope.launch {
                    val dlUrl = ep.resolveDownloadUrl(drama.slug)
                    val size = fetchRealFileSize(dlUrl)
                    val key = "${ep.episodeId}_${ep.episodeNumber}"
                    if (size > 0) {
                        realFileSizes[key] = size
                    }
                }
            }
            isFetchingSizes = false
        }
    }

    val totalSelectedBytes = remember(selectedEpisodes.toList(), realFileSizes.toMap()) {
        selectedEpisodes.sumOf { ep ->
            val key = "${ep.episodeId}_${ep.episodeNumber}"
            realFileSizes[key] ?: 0L
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161A24),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = drama.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5), modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Color(0xFF262E3E), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 6.dp))

                Text("Download", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

                if (isLoadingEpisodes) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E676), strokeWidth = 2.5.dp)
                    }
                } else {
                    if (episodeChunks.size > 1) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(episodeChunks) { index, chunk ->
                                val start = index * CHUNK_SIZE_BATCH + 1
                                val end = start + chunk.size - 1
                                val isSelected = (index == selectedChunkIndex)

                                Text(
                                    text = "$start-$end",
                                    color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { selectedChunkIndex = index }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 90.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                            val isSelected = selectedEpisodes.contains(ep)

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF0F3B32) else Color(0xFF222634))
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, if (isSelected) Color(0xFF00E676) else Color(0xFF6B7280), CircleShape)
                                        .background(if (isSelected) Color(0xFF00E676) else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                color = Color(0xFF1A1F2C),
                tonalElevation = 10.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

                        val displaySize = formatBytesDisplay(totalSelectedBytes, isFetchingSizes && totalSelectedBytes == 0L)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.74f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(BlueGreenGradient)
                                .clickable {
                                    val targets = if (selectedEpisodes.isNotEmpty()) selectedEpisodes.toList() else episodes.take(1)
                                    
                                    val quotaCheck = DownloadQuotaManager.checkCanDownload(context, totalSelectedBytes, isVip)

                                    if (quotaCheck.canDownload) {
                                        if (!isVip) {
                                            DownloadQuotaManager.recordDownloadUsage(context, totalSelectedBytes)
                                        }

                                        targets.forEach { ep ->
                                            R2DownloadManager.startDownload(
                                                context = context,
                                                downloadUrl = ep.resolveDownloadUrl(drama.slug),
                                                title = drama.title,
                                                episodeNumber = ep.episodeNumber
                                            )
                                        }

                                        onDismiss()
                                        Toast.makeText(context, "📥 Download started for ${targets.size} episodes! Check notifications.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, quotaCheck.message, Toast.LENGTH_LONG).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                Text(
                                    text = if (selectedEpisodes.isNotEmpty()) "Download (${selectedEpisodes.size}) · $displaySize" else "Download",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isVip) {
                        Text(
                            text = "👑 VIP Member: Unlimited Downloads",
                            color = GoldVip,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        val usedFormatted = DownloadQuotaManager.formatBytes(DownloadQuotaManager.getTodayUsedBytes(context))
                        val remainingFormatted = DownloadQuotaManager.formatBytes(DownloadQuotaManager.getRemainingFreeBytes(context))
                        Text(
                            text = "Daily Free Limit: $usedFormatted / 2.0 GB used ($remainingFormatted left)",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 📱 ১ নম্বর ছবির লিস্টিং পেজ
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F15))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
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
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.85f),
                                        Color(0xFF0C0F15)
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            items(items, key = { it.slug }) { drama ->
                TopPicksItemRow(
                    drama = drama,
                    onClick = { onItemClick(drama) },
                    onDownload = { onDownloadClick(drama) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TopPicksItemRow(
    drama: ContentItemDto,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(94.dp)
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
                        text = if (drama.rating > 0) String.format(Locale.US, "%.1f", drama.rating) else "7.8",
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

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BlueGreenGradient)
                    .clickable { onDownload() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("Download", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

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

            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DubbingLanguageBadge(drama = drama)
            }

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

@Composable
fun DubbingLanguageBadge(drama: ContentItemDto) {
    val isBangla = drama.isBanglaDub || drama.dubBadge.contains("Bangla", true) || drama.dubBadge.contains("বাংলা", true)
    val isHindi = drama.isHindiDub || drama.dubBadge.contains("Hindi", true)
    val isEnglish = drama.dubBadge.contains("English", true) || drama.dubBadge.contains("Eng", true)

    val (badgeText, badgeBgColor, badgeTextColor) = when {
        isBangla -> Triple("বাংলা", Color(0xFFFFB300), Color.Black)
        isHindi -> Triple("Hindi", Color(0xFF00B0FF), Color.Black)
        isEnglish -> Triple("Eng", Color(0xFF10B981), Color.White)
        drama.dubBadge.isNotBlank() -> Triple(drama.dubBadge.take(6), Color(0xFF6366F1), Color.White)
        else -> Triple("HD", Color(0x99000000), Color.White)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 6.dp, topEnd = 8.dp))
            .background(badgeBgColor)
            .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = badgeText,
            color = badgeTextColor,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black
        )
    }
}
