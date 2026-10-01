@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens.reels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto
import com.example.ui.viewmodel.ReelsViewModel

private val DarkBg = Color(0xFF0C0F15)
private val CardBg = Color(0xFF131722)
private val BorderColor = Color(0xFF222838)
private val TikTokRed = Color(0xFFFE2C55)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

// রেজাল্ট ট্যাবের তালিকা
enum class ReelsResultCategoryTab(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    CREATORS("Creators"),
    HASHTAGS("Hashtags"),
    SERIES("Series")
}

@Composable
fun ReelsSearchResultScreen(
    searchQuery: String,
    viewModel: ReelsViewModel,
    onBackClick: () -> Unit,
    onSearchSubmit: (newQuery: String) -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenCreatorProfile: (pageId: Int) -> Unit,
    onOpenHashtagExplorer: (tag: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()

    var inputQuery by remember { mutableStateOf(searchQuery) }
    var selectedTab by remember { mutableStateOf(ReelsResultCategoryTab.ALL) }

    // কি-ওয়ার্ড দিয়ে ভিডিও ফিল্টার
    val filteredVideos: List<UserReelDto> = remember(searchQuery, feedState.reels) {
        if (searchQuery.isBlank()) feedState.reels
        else feedState.reels.filter {
            it.title?.contains(searchQuery, ignoreCase = true) == true ||
            it.description?.contains(searchQuery, ignoreCase = true) == true ||
            it.pageName.contains(searchQuery, ignoreCase = true) ||
            it.handle.contains(searchQuery, ignoreCase = true) ||
            it.hashtags?.contains(searchQuery.removePrefix("#"), ignoreCase = true) == true
        }
    }

    // ক্রিয়েটর ফিল্টার
    val matchedCreators: List<SuggestedPageDto> = remember(searchQuery, feedState.suggestedPages) {
        if (searchQuery.isBlank()) feedState.suggestedPages
        else feedState.suggestedPages.filter {
            it.pageName.contains(searchQuery, ignoreCase = true) ||
            it.handle.contains(searchQuery, ignoreCase = true)
        }
    }

    // সিরিজ ফিল্টার
    val matchedSeries: List<UserReelDto> = remember(searchQuery, feedState.reels) {
        feedState.reels.filter {
            it.playlistId != null && it.playlistId > 0 &&
            (searchQuery.isBlank() ||
             it.playlistTitle?.contains(searchQuery, ignoreCase = true) == true ||
             it.title?.contains(searchQuery, ignoreCase = true) == true)
        }.distinctBy { it.playlistId }
    }

    // সংশ্লিষ্ট হ্যাশট্যাগ তালিকা
    val matchingHashtags = remember(searchQuery) {
        val clean = searchQuery.trim().removePrefix("#")
        listOf(
            "#$clean",
            "#${clean}_viral",
            "#${clean}_drama",
            "#${clean}_bangladub",
            "#${clean}_official"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
    ) {
        // =========================================================================
        // 🔝 ১. টপ সার্চ বার: [ ← Back ] [ 🔍 Search Box ] [ Search ]
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

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

                    BasicTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(CyanAccent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (inputQuery.isNotBlank()) {
                                    focusManager.clearFocus()
                                    onSearchSubmit(inputQuery.trim())
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (inputQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { inputQuery = "" }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (inputQuery.isNotBlank()) {
                        focusManager.clearFocus()
                        onSearchSubmit(inputQuery.trim())
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TikTokRed),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("Search", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // =========================================================================
        // 📑 ২. স্ক্রোলযোগ্য রেজাল্ট ক্যাটাগরি ট্যাব বার (২ নম্বর ছবির মতো)
        // =========================================================================
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
            ReelsResultCategoryTab.values().forEach { tab ->
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

        // =========================================================================
        // 🎬 ৩. ট্যাব কন্টেন্ট (২ নম্বর ছবির হুবহু ২-কলাম ভিডিও গ্রিড)
        // =========================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                // ১ & ২: ALL এবং VIDEOS ট্যাব
                ReelsResultCategoryTab.ALL, ReelsResultCategoryTab.VIDEOS -> {
                    if (filteredVideos.isEmpty()) {
                        EmptyResultPlaceholder(query = searchQuery, message = "No videos found")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = filteredVideos.size,
                                key = { index -> filteredVideos[index].id }
                            ) { index ->
                                val video = filteredVideos[index]
                                DouyinStyleVideoCard(
                                    reel = video,
                                    onClick = { onReelClick(video) }
                                )
                            }
                        }
                    }
                }

                // ৩: CREATORS ট্যাব
                ReelsResultCategoryTab.CREATORS -> {
                    if (matchedCreators.isEmpty()) {
                        EmptyResultPlaceholder(query = searchQuery, message = "No creator pages found")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                count = matchedCreators.size,
                                key = { index -> matchedCreators[index].pageId }
                            ) { index ->
                                val creator = matchedCreators[index]
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

                // ৪: HASHTAGS ট্যাব
                ReelsResultCategoryTab.HASHTAGS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            count = matchingHashtags.size,
                            key = { index -> matchingHashtags[index] }
                        ) { index ->
                            val tag = matchingHashtags[index]
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
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

                // ৫: SERIES ট্যাব
                ReelsResultCategoryTab.SERIES -> {
                    if (matchedSeries.isEmpty()) {
                        EmptyResultPlaceholder(query = searchQuery, message = "No series playlists found")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = matchedSeries.size,
                                key = { index -> matchedSeries[index].playlistId ?: matchedSeries[index].id }
                            ) { index ->
                                val sReel = matchedSeries[index]
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

// =============================================================================
// 🔲 ২ নম্বর ছবির হুবহু ২-কলাম ভিডিও কার্ড
// =============================================================================
@Composable
private fun DouyinStyleVideoCard(
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

                // ওপরে ডান কোণায় ২ নম্বর ছবির মতো ডিউরেশন ব্যাজ (যেমন: "03:25")
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                )
            }

            // ২ নম্বর ছবির নিচের রো: [ অ্যাভাটার + নাম ] ---- [ ♡ লাইক সংখ্যা ]
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
private fun EmptyResultPlaceholder(query: String, message: String = "No matching results found") {
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
            Text("Try searching with different keywords or topics.", color = TextMuted, fontSize = 12.sp)
        }
    }
}
