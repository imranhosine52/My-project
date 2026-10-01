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
import kotlinx.coroutines.delay

private val PureBlack = Color(0xFF000000)
private val DarkBg = Color(0xFF0C0F15)
private val BorderColor = Color(0xFF222838)
private val TikTokRed = Color(0xFFFE2C55)
private val GoldAccent = Color(0xFFFFB300)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

// 🌟 ১ নম্বর ছবির হুবহু হট সার্চ ট্রেন্ডিং র্যাংকিং মডেল
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

@Composable
fun ReelsSearchScreen(
    initialQuery: String = "",
    onBackClick: () -> Unit,
    onNavigateToResults: (query: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf(initialQuery) }

    // 🎯 সার্চ বক্সে রোটেটিং টেক্সট অ্যানিমেশনের প্লেসহোল্ডার তালিকা
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
        // 🔝 ১. টপ সার্চ বার: [ ← Back ] [ 🔍 Search Box (Animated) ] [ Search ]
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
                        // ১ নম্বর ছবির মতো অ্যানিমেটেড টেক্সট রোটেটর
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
                    val target = if (searchQuery.isNotBlank()) searchQuery else placeholderKeywords[placeholderIndex]
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
        // 📄 ১ নম্বর ছবির হুবহু সার্চ ল্যান্ডিং কন্টেন্ট (হিস্টোরি ও ট্রেন্ডিং র্যাংকিং)
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
                            Text(
                                text = "TRENDING",
                                color = TikTokRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                    PredefinedHotSearches.forEach { hotItem ->
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
    }
}
