@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens.reels

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPageDto
import com.example.data.model.UserReelDto
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val DarkBg = Color(0xFF0C0F15)
private val CardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)
private val TikTokRed = Color(0xFFFE2C55)
private val GoldAccent = Color(0xFFFFB300)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

// 🌟 ১ নম্বর ছবির মতো হট সার্চ ট্রেন্ডিং র্যাংকিং মডেল
data class HotSearchItem(
    val rank: Int,
    val title: String,
    val hotScore: String,
    val tagBadge: String? = null // "Hot", "New", "Exclusive"
)

private val PredefinedHotSearches = listOf(
    HotSearchItem(1, "The Proud Dragon God Bangla Dubbed", "12.7M", "Hot"),
    HotSearchItem(2, "Hidden Love Hindi Dubbed Episode 1", "12.4M", "Hot"),
    HotSearchItem(3, "Solo Leveling Episode English Sub", "11.9M", "New"),
    HotSearchItem(4, "CEO Secret Bride Full Drama", "11.5M"),
    HotSearchItem(5, "Revenge of the Abandoned Daughter", "10.8M", "New"),
    HotSearchItem(6, "K-Drama Love Story Clips 2026", "10.4M"),
    HotSearchItem(7, "Bangla Dubbed Short Drama Episodes", "10.1M"),
    HotSearchItem(8, "My Demon Romantic Reel Moments", "9.8M", "Exclusive"),
    HotSearchItem(9, "Top Chinese Drama Shorts in Bangla", "9.4M"),
    HotSearchItem(10, "Billionaire in Disguise Episode 2", "8.9M"),
    HotSearchItem(11, "Super Power Awakening Full Story", "8.5M"),
    HotSearchItem(12, "Campus Romance Drama Flix Original", "8.1M"),
    HotSearchItem(13, "Destined to Meet You Mini Drama", "7.8M"),
    HotSearchItem(14, "Action Martial Arts Reel Highlights", "7.4M"),
    HotSearchItem(15, "Anime Viral Edits Solo Leveling", "7.1M")
)

// ২ নম্বর ছবির মতো রেজাল্ট ট্যাব
enum class SearchResultTab(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    CREATORS("Creators"),
    HASHTAGS("Hashtags"),
    SERIES("Series")
}

@Composable
fun ReelsSearchScreen(
    viewModel: ReelsViewModel,
    initialQuery: String = "",
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenCreatorProfile: (pageId: Int) -> Unit = {},
    onOpenHashtagExplorer: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var isSearchSubmitted by remember { mutableStateOf(initialQuery.isNotBlank()) }
    var selectedTab by remember { mutableStateOf(SearchResultTab.ALL) }

    // 🎯 সার্চ বক্সে একের পর এক টেক্সট অ্যানিমেশনের জন্য প্লেসহোল্ডার তালিকা
    val placeholderKeywords = remember {
        listOf("The Proud Dragon God", "Hidden Love", "Solo Leveling", "Bangla Dub", "CEO Secret Bride")
    }
    var placeholderIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2800L)
            placeholderIndex = (placeholderIndex + 1) % placeholderKeywords.size
        }
    }

    // রিসেন্ট সার্চ হিস্টোরি
    val searchHistoryPrefs = remember {
        context.getSharedPreferences("reels_search_history_prefs", Context.MODE_PRIVATE)
    }
    val searchHistoryList = remember {
        mutableStateListOf<String>().apply {
            val saved = searchHistoryPrefs.getStringSet("recent_queries", emptySet()) ?: emptySet()
            addAll(saved.toList().reversed())
        }
    }

    fun saveQueryToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            searchHistoryList.remove(trimmed)
            searchHistoryList.add(0, trimmed)
            searchHistoryPrefs.edit().putStringSet("recent_queries", searchHistoryList.toSet()).apply()
        }
    }

    fun clearAllHistory() {
        searchHistoryList.clear()
        searchHistoryPrefs.edit().remove("recent_queries").apply()
    }

    fun executeSearch(query: String) {
        val clean = query.trim()
        if (clean.isNotBlank()) {
            searchQuery = clean
            saveQueryToHistory(clean)
            isSearchSubmitted = true
            focusManager.clearFocus()
        }
    }

    // ব্যাক বাটন হ্যান্ডলার: রেজাল্ট পেজে থাকলে আগে সার্চ ল্যান্ডিং পেজে ফিরবে
    BackHandler {
        if (isSearchSubmitted) {
            isSearchSubmitted = false
        } else {
            onBackClick()
        }
    }

    // ফিল্টার্ড ডেটা ক্যালকুলেশন
    val filteredVideos = remember(searchQuery, feedState.reels) {
        if (searchQuery.isBlank()) feedState.reels
        else feedState.reels.filter {
            it.title?.contains(searchQuery, ignoreCase = true) == true ||
            it.description?.contains(searchQuery, ignoreCase = true) == true ||
            it.pageName.contains(searchQuery, ignoreCase = true) ||
            it.handle.contains(searchQuery, ignoreCase = true) ||
            it.hashtags?.contains(searchQuery.removePrefix("#"), ignoreCase = true) == true
        }
    }

    val matchedCreators = remember(searchQuery, feedState.suggestedPages) {
        if (searchQuery.isBlank()) feedState.suggestedPages
        else feedState.suggestedPages.filter {
            it.pageName.contains(searchQuery, ignoreCase = true) ||
            it.handle.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
    ) {
        // =========================================================================
        // 🔝 ১. টপ সার্চ বার: [ ← Back ] [ 🔍 Search Box (Animated) ] [ Search / Cancel ]
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (isSearchSubmitted) isSearchSubmitted = false else onBackClick()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // সার্চ বক্স
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF191D28),
                border = BorderStroke(0.8.dp, BorderColor),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        // ১ নম্বর ছবির মতো রোটেটিং টেক্সট অ্যানিমেশন
                        if (searchQuery.isEmpty()) {
                            AnimatedContent(
                                targetState = placeholderKeywords[placeholderIndex],
                                transitionSpec = {
                                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                                },
                                label = "placeholder_anim"
                            ) { hint ->
                                Text(
                                    text = hint,
                                    color = Color(0xFF64748B),
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (it.isBlank()) {
                                    isSearchSubmitted = false
                                }
                            },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(CyanAccent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { executeSearch(searchQuery) }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    searchQuery = ""
                                    isSearchSubmitted = false
                                }
                        )
                    }
                }
            }

            // সার্চ বাটন
            Button(
                onClick = { executeSearch(searchQuery) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TikTokRed),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("Search", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // =========================================================================
        // 🔄 স্টেট সুইচিং: ল্যান্ডিং পেজ (ছবি ১) বনাম রেজাল্ট পেজ (ছবি ২)
        // =========================================================================
        AnimatedContent(
            targetState = isSearchSubmitted,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "search_view_state"
        ) { inResultMode ->
            if (!inResultMode) {
                // =================================================================
                // 📄 ১ নম্বর ছবির হুবহু সার্চ ল্যান্ডিং পেজ
                // =================================================================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ১. Recent Search সেকশন (হিস্টোরি)
                    if (searchHistoryList.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Recent", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear History",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp).clickable { clearAllHistory() }
                                    )
                                }

                                // ২-কলাম হিস্টোরি গ্রিড
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    searchHistoryList.chunked(2).forEach { rowPair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowPair.forEach { keyword ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF161A24),
                                                    border = BorderStroke(0.6.dp, BorderColor),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { executeSearch(keyword) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = keyword,
                                                            color = Color(0xFFCBD5E1),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            }
                                            if (rowPair.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ২. ১ নম্বর ছবির হুবহু Hot Search র্যাংকিং লিস্ট (১ থেকে ১৫)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🔥", fontSize = 16.sp)
                                Text("DramaFlix Hot Search", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = TikTokRed.copy(alpha = 0.2f)
                                ) {
                                    Text("TRENDING", color = TikTokRed, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }

                            HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                            PredefinedHotSearches.forEach { hotItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { executeSearch(hotItem.title) }
                                        .padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        // র্যাংক নম্বর (১, ২, ৩ এর জন্য বিশেষ লাল ও গোল্ড কালার)
                                        Text(
                                            text = hotItem.rank.toString(),
                                            color = when (hotItem.rank) {
                                                1 -> TikTokRed
                                                2 -> GoldAccent
                                                3 -> Color(0xFFFF9100)
                                                else -> TextMuted
                                            },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.width(22.dp)
                                        )

                                        Text(
                                            text = hotItem.title,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (hotItem.rank <= 3) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Hot / New ব্যাজ
                                        if (hotItem.tagBadge != null) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = if (hotItem.tagBadge == "New") Color(0xFF00C853) else TikTokRed
                                            ) {
                                                Text(
                                                    text = hotItem.tagBadge,
                                                    color = Color.White,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    // ডানপাশে স্কোর যেমন 12.7M
                                    Text(
                                        text = hotItem.hotScore,
                                        color = TextMuted,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // =================================================================
                // 📄 ২ নম্বর ছবির হুবহু সার্চ রেজাল্ট পেজ
                // =================================================================
                Column(modifier = Modifier.fillMaxSize()) {
                    // রেজাল্ট ক্যাটাগরি ট্যাব বার: All | Videos | Creators | Hashtags | Series
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = DarkBg,
                        contentColor = Color.White,
                        edgePadding = 12.dp,
                        divider = { HorizontalDivider(color = BorderColor, thickness = 0.6.dp) },
                        indicator = { tabPositions ->
                            if (selectedTab.ordinal < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                    color = Color.White,
                                    height = 2.dp
                                )
                            }
                        }
                    ) {
                        SearchResultTab.values().forEach { tab ->
                            val isSelected = (selectedTab == tab)
                            Tab(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                text = {
                                    Text(
                                        text = tab.label,
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }

                    // ট্যাব কন্টেন্ট
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            SearchResultTab.ALL, SearchResultTab.VIDEOS -> {
                                if (filteredVideos.isEmpty()) {
                                    EmptySearchResultView(query = searchQuery)
                                } else {
                                    // ২ নম্বর ছবির হুবহু ২-কলাম ভিডিও গ্রিড
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        itemsIndexed(
                                            items = filteredVideos,
                                            key = { idx, v -> "search_vid_${v.id}_$idx" }
                                        ) { _, video ->
                                            DouyinSearchVideoCard(
                                                reel = video,
                                                onClick = { onReelClick(video) }
                                            )
                                        }
                                    }
                                }
                            }

                            SearchResultTab.CREATORS -> {
                                if (matchedCreators.isEmpty()) {
                                    EmptySearchResultView(query = searchQuery, message = "No creator pages found")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(matchedCreators, key = { it.pageId }) { creator ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(CardBg)
                                                    .clickable { onOpenCreatorProfile(creator.pageId) }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    AsyncImage(
                                                        model = creator.avatar ?: "https://ui-avatars.com/api/?name=${creator.pageName}&background=1E2434&color=fff",
                                                        contentDescription = creator.pageName,
                                                        modifier = Modifier.size(46.dp).clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Column {
                                                        Text(creator.pageName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                        Text("${creator.displayHandle} • ${creator.formattedFollowers} fans", color = TextMuted, fontSize = 11.5.sp)
                                                    }
                                                }

                                                Button(
                                                    onClick = { onOpenCreatorProfile(creator.pageId) },
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = TikTokRed),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("View", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SearchResultTab.HASHTAGS -> {
                                val tagList = remember(searchQuery) {
                                    listOf(
                                        "#${searchQuery.removePrefix("#")}",
                                        "#${searchQuery.removePrefix("#")}_viral",
                                        "#${searchQuery.removePrefix("#")}_drama",
                                        "#${searchQuery.removePrefix("#")}_bangla"
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(tagList) { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = CardBg,
                                            border = BorderStroke(0.6.dp, BorderColor),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onOpenHashtagExplorer(tag) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Box(
                                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(CyanAccent.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("#", color = CyanAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Column {
                                                        Text(tag, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                        Text("View all videos with this hashtag", color = TextMuted, fontSize = 11.sp)
                                                    }
                                                }
                                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            SearchResultTab.SERIES -> {
                                val matchedSeries = remember(searchQuery, feedState.reels) {
                                    feedState.reels.filter { it.playlistId != null && it.playlistId > 0 }
                                        .distinctBy { it.playlistId }
                                }

                                if (matchedSeries.isEmpty()) {
                                    EmptySearchResultView(query = searchQuery, message = "No series playlists found")
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(matchedSeries, key = { it.playlistId ?: it.id }) { sReel ->
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                                border = BorderStroke(0.8.dp, BorderColor),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(0.72f)
                                                    .clickable { onReelClick(sReel) }
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    AsyncImage(
                                                        model = sReel.thumbUrl ?: sReel.videoUrl,
                                                        contentDescription = sReel.playlistTitle,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))))
                                                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                                                        Surface(shape = RoundedCornerShape(4.dp), color = CyanAccent) {
                                                            Text("Series", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                        }
                                                        Text(sReel.playlistTitle ?: "Drama Series", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🔲 ২ নম্বর ছবির হুবহু ২-কলাম ভিডিও কার্ড
// =============================================================================
@Composable
private fun DouyinSearchVideoCard(
    reel: UserReelDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(0.6.dp, BorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ভিডিও পোস্টার ও ডিউরেশন ব্যাজ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = reel.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // ওপরে ডান কোণায় ২ নম্বর ছবির হুবহু ডিউরেশন ব্যাজ যেমন "03:25"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                ) {
                    Text(
                        text = reel.formattedDuration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // নিচের হালকা শ্যাডো
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                )
            }

            // ২ নম্বর ছবির নিচের রো: [ অ্যাভাটার + নাম ] ---- [ ♡ লাইক কাউন্ট ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    AsyncImage(
                        model = reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=222838&color=fff",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = reel.pageName.ifBlank { reel.displayHandle },
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (reel.isLiked) TikTokRed else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = reel.formattedLikes,
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchResultView(query: String, message: String = "No matching results found") {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.SearchOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(46.dp))
            Text(message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Try searching with different keywords or check hot trends.", color = TextMuted, fontSize = 12.sp)
        }
    }
}
