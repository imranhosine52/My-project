@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class
)

package com.example.ui.screens.categories

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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

private val BluePurpleGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF8A2387),
        Color(0xFFE94057),
        Color(0xFFF27121)
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

@Composable
fun AnimeCategoryScreen(
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

    // 🎯 অ্যানিমে পেজের সাব-ট্যাব ট্র্যাকিং স্টেট
    var activeListingViewType by rememberSaveable { mutableStateOf<String?>(null) }
    var targetDramaForBatchDownload by remember { mutableStateOf<ContentItemDto?>(null) }

    val watchlistDao = remember { AppDatabase.getInstance(context).watchlistDao() }
    val watchlistEntities by watchlistDao.getAllWatchlist().collectAsState(initial = emptyList())
    val mySavedAnime = remember(watchlistEntities, items) {
        val savedIds = watchlistEntities.map { it.id }.toSet()
        items.filter { it.slug in savedIds || it.id in savedIds }
    }

    val refreshSeed = rememberSaveable { System.currentTimeMillis() }
    val dynamicGridItems = remember(items, refreshSeed) {
        if (items.size <= 3) items
        else items.shuffled(java.util.Random(refreshSeed))
    }

    // 🎯 ব্যাক বাটন লজিক: সাব-ট্যাবে থাকলে সোজা অ্যানিমে হোমে আনবে
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
                    text = "No Anime found",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val topSliderItems = remember(items, refreshSeed) {
                items.shuffled(java.util.Random(refreshSeed + 5)).take(10)
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
                // ১. 🎬 সেন্টার-ল্যান্ডিং স্লাইডার
                if (topSliderItems.isNotEmpty()) {
                    item {
                        SingleFocusInfiniteTopCarousel(
                            dramas = topSliderItems,
                            onDramaClick = { anime -> onNavigateToPlayer(anime.slug) }
                        )
                    }
                }

                // ২. 🔘 ৩টি ফিল্টার বাটন: [ Latest ]  [ Hottest ]  [ All ]
                item {
                    ShortTvFilterPillsRow(
                        onSelectFilter = { filterName ->
                            activeListingViewType = filterName
                        }
                    )
                }

                // ৩. 🔖 My List রো
                if (mySavedAnime.isNotEmpty()) {
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
                                            activeListingViewType = "MyList"
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "View All (${mySavedAnime.size})",
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
                                items(mySavedAnime, key = { "saved_${it.slug}" }) { anime ->
                                    ShortTvMyListCard(
                                        drama = anime,
                                        onClick = { onNavigateToPlayer(anime.slug) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ৪. 🏷️ ৩-কলাম অ্যানিমে গ্রিড (Anime ব্যাজ ছাড়া)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Anime Series",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${items.size} Shows",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                items(gridChunks.size) { rowIndex ->
                    val rowItems = gridChunks[rowIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { anime ->
                            Box(modifier = Modifier.weight(1f)) {
                                AnimeCleanGridCard(
                                    drama = anime,
                                    onClick = { onNavigateToPlayer(anime.slug) }
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🚀 স্মুথ সাব-ট্যাব ওভারলে (ইন-প্লেস এনিমেশন, নো ফ্লিকার)
        // =========================================================================
        AnimatedVisibility(
            visible = activeListingViewType != null,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(250)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(200, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            when (activeListingViewType) {
                "All" -> {
                    ShortsFilterAllScreen(
                        items = items,
                        onBackClick = { activeListingViewType = null },
                        onItemClick = { anime ->
                            onNavigateToPlayer("${anime.slug}###subTab=All")
                        }
                    )
                }
                "Latest", "Hottest", "MyList" -> {
                    val currentType = activeListingViewType
                    val displayList = remember(currentType, items, mySavedAnime) {
                        when (currentType) {
                            "Latest" -> items.take(20)
                            "Hottest" -> items.sortedByDescending { it.numericViews }
                            "MyList" -> mySavedAnime
                            else -> items
                        }
                    }

                    ShortsListingTopPicksView(
                        title = when (currentType) {
                            "Latest" -> "Latest Anime Releases"
                            "Hottest" -> "Hottest Anime"
                            "MyList" -> "My Saved Anime"
                            else -> "Top Anime Picks"
                        },
                        items = displayList,
                        onBackClick = { activeListingViewType = null },
                        onItemClick = { anime ->
                            onNavigateToPlayer("${anime.slug}###subTab=$currentType")
                        },
                        onDownloadClick = { anime ->
                            targetDramaForBatchDownload = anime
                        }
                    )
                }
            }
        }

        // =========================================================================
        // 📥 ব্যাচ ডাউনলোড মডাল
        // =========================================================================
        targetDramaForBatchDownload?.let { anime ->
            ShortsEpisodeBatchDownloadModal(
                drama = anime,
                isVip = isUserVip,
                onDismiss = { targetDramaForBatchDownload = null }
            )
        }
    }
}

/**
 * 🌟 কার্ডের ওপরের বাম পাশের 'ANIME' ব্যাজটি বাদ দিয়ে শুধুমাত্র ডাবিং ব্যাজ রাখা হয়েছে
 */
@Composable
fun AnimeCleanGridCard(
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

            // 🎯 শুধুমাত্র ডানপাশের ভাষা ব্যাজটি থাকবে (বাম পাশের ANIME ব্যাজ পুরোপুরি সরানো হয়েছে)
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DubbingLanguageBadge(drama = drama)
            }

            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Episodes" else "Anime"
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
