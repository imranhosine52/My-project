@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrendingHashtagDto
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val DarkBg = Color(0xFF0C0F15)
private val BorderColor = Color(0xFF222838)
private val TikTokRed = Color(0xFFFE2C55)
private val GoldAccent = Color(0xFFFFB300)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

// 🌟 সার্ভারের লাইভ ডাটা ধারণকারী র্যাংকিং আইটেম
data class RealHotRankingItem(
    val rank: Int,
    val title: String,
    val realScore: String,
    val tagBadge: String? = null
)

@Composable
fun ReelsSearchScreen(
    initialQuery: String = "",
    onBackClick: () -> Unit,
    onNavigateToResults: (query: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    var searchQuery by remember { mutableStateOf(initialQuery) }

    // 🎯 সার্ভার থেকে আসা রিয়েল ট্রেন্ডিং ডেটা স্টেট (ডামি ডাটা ০%)
    var serverTrendingHashtags by remember { mutableStateOf<List<TrendingHashtagDto>>(emptyList()) }
    var serverTrendingReels by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isLoadingServerData by remember { mutableStateOf(true) }

    // ১. ব্যাকএন্ড থেকে লাইভ ট্রেন্ড ও রিয়েল ভিউজ ফেচ করা
    LaunchedEffect(Unit) {
        isLoadingServerData = true
        withContext(Dispatchers.IO) {
            val hashtagRes = repository.getTrendingHashtags()
            serverTrendingHashtags = hashtagRes.getOrDefault(emptyList())

            val trendRes = repository.getReelsFeed(tab = "trend", page = 1)
            serverTrendingReels = trendRes.getOrDefault(emptyList())
        }
        isLoadingServerData = false
    }

    // ২. সার্ভারের রিয়েল রিলস ও হ্যাশট্যাগ দিয়ে ডায়নামিক র্যাংকিং লিস্ট তৈরি
    val realHotRankings = remember(serverTrendingReels, serverTrendingHashtags) {
        val list = mutableListOf<RealHotRankingItem>()
        var currentRank = 1

        // ক) ট্রেন্ডিং রিলস থেকে র্যাংকিং
        serverTrendingReels.take(10).forEach { reel ->
            val title = reel.title?.takeIf { it.isNotBlank() } ?: reel.description?.take(40) ?: "Drama Reel"
            val badge = if (currentRank == 1) "Hot" else if (currentRank == 2 || currentRank == 3) "Trending" else null
            list.add(
                RealHotRankingItem(
                    rank = currentRank++,
                    title = title,
                    realScore = formatScore(reel.viewsCount),
                    tagBadge = badge
                )
            )
        }

        // খ) লাইভ ট্রেন্ডিং হ্যাশট্যাগ থেকে র্যাংকিং
        serverTrendingHashtags.take(5).forEach { tag ->
            list.add(
                RealHotRankingItem(
                    rank = currentRank++,
                    title = tag.displayTag,
                    realScore = tag.displayViews.replace("views", "").trim(),
                    tagBadge = if (currentRank <= 4) "Hot" else null
                )
            )
        }

        list
    }

    // ৩. সার্চ বক্সে একের পর এক অ্যানিমেশনের জন্য সার্ভার থেকে আসা রিয়েল টাইটেল তালিকা
    val dynamicPlaceholders = remember(serverTrendingReels, serverTrendingHashtags) {
        val keywords = mutableListOf<String>()
        serverTrendingReels.forEach { r ->
            r.title?.takeIf { it.isNotBlank() }?.let { keywords.add(it) }
        }
        serverTrendingHashtags.forEach { t ->
            keywords.add(t.displayTag)
        }
        if (keywords.isEmpty()) listOf("Search drama reels, hashtags, creators...") else keywords
    }

    var placeholderIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(dynamicPlaceholders) {
        if (dynamicPlaceholders.size > 1) {
            while (true) {
                delay(3000L)
                placeholderIndex = (placeholderIndex + 1) % dynamicPlaceholders.size
            }
        }
    }

    // রিসেন্ট সার্চ হিস্টোরি স্টোরেজ
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

    fun submitSearch(query: String) {
        val clean = query.trim()
        if (clean.isNotBlank()) {
            saveQueryToHistory(clean)
            focusManager.clearFocus()
            onNavigateToResults(clean)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
    ) {
        // =========================================================================
        // 🔝 ১. টপ সার্চ বার: [ ← Back ] [ 🔍 Search Box (Live Animated) ] [ Search ]
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

                    Box(modifier = Modifier.weight(1f)) {
                        // 🎯 লাইভ সার্ভার ডাটা নির্ভর টেক্সট রোটেটর
                        if (searchQuery.isEmpty() && dynamicPlaceholders.isNotEmpty()) {
                            val activeHint = dynamicPlaceholders.getOrElse(placeholderIndex) { "Search drama..." }
                            AnimatedContent(
                                targetState = activeHint,
                                transitionSpec = {
                                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                                },
                                label = "real_placeholder_anim"
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
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(CyanAccent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submitSearch(searchQuery) }),
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
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            // সার্চ বাটন
            Button(
                onClick = {
                    val target = if (searchQuery.isNotBlank()) {
                        searchQuery
                    } else {
                        dynamicPlaceholders.getOrElse(placeholderIndex) { "Drama" }
                    }
                    submitSearch(target)
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
        // 📄 ১ নম্বর ছবির সার্চ ল্যান্ডিং কন্টেন্ট (হিস্টোরি ও লাইভ সার্ভার র্যাংকিং)
        // =========================================================================
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
                                                .clickable { submitSearch(keyword) }
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

            // ২. সার্ভার থেকে প্রাপ্ত রিয়েল Hot Search র্যাংকিং লিস্ট (১ থেকে ১৫)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                Text(
                                    text = "LIVE",
                                    color = TikTokRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (isLoadingServerData) {
                            CircularProgressIndicator(color = CyanAccent, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
                        }
                    }

                    HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                    if (isLoadingServerData && realHotRankings.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.dp)
                        }
                    } else if (realHotRankings.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Search trends are loading...", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        realHotRankings.forEach { hotItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { submitSearch(hotItem.title) }
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

                                // 🎯 সার্ভার থেকে আসা আসল ভিউজ স্কোর
                                Text(
                                    text = hotItem.realScore,
                                    color = TextMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ভিউজ সংখ্যা সংক্ষেপক
private fun formatScore(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> count.toString()
        else -> "New"
    }
}
