@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.Context
import androidx.compose.animation.*
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
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
import com.example.data.model.UserReelDto
import com.example.ui.viewmodel.ReelsViewModel
import java.util.Locale

private val SearchRed = Color(0xFFFF2A4B)
private val DarkBg = Color(0xFF0C0F15)
private val InputBg = Color(0xFF161922)
private val CardBorderColor = Color(0xFF222838)
private val TextMuted = Color(0xFF8692A6)

private val SearchCategories = listOf(
    "All", "Trending 🔥", "Drama", "Entertainment", "Comedy", "Short Film", "Bangla Dub", "Hindi Dub", "Vlog"
)

@Composable
fun ReelsSearchScreen(
    viewModel: ReelsViewModel,
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

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

    fun removeSingleHistory(query: String) {
        searchHistoryList.remove(query)
        searchHistoryPrefs.edit().putStringSet("recent_queries", searchHistoryList.toSet()).apply()
    }

    fun clearAllHistory() {
        searchHistoryList.clear()
        searchHistoryPrefs.edit().remove("recent_queries").apply()
    }

    // 🎯 টাইপ-সেফ ফিল্টার ও ভিউজ অনুযায়ী সর্টিং
    val filteredReels: List<UserReelDto> = remember(searchQuery, selectedCategory, feedState.reels) {
        val baseList = feedState.reels.filter { reel ->
            val matchesQuery = searchQuery.isBlank() ||
                    reel.title?.contains(searchQuery, ignoreCase = true) == true ||
                    reel.description?.contains(searchQuery, ignoreCase = true) == true ||
                    reel.pageName.contains(searchQuery, ignoreCase = true) ||
                    reel.handle.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategory == "All" ||
                    selectedCategory == "Trending 🔥" ||
                    reel.title?.contains(selectedCategory, ignoreCase = true) == true ||
                    reel.description?.contains(selectedCategory, ignoreCase = true) == true

            matchesQuery && matchesCategory
        }

        baseList.sortedByDescending { it.viewsCount }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
    ) {
        // ১. সার্চ ইনপুট হেডার বার
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(InputBg)
                    .border(0.8.dp, CardBorderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search reels, creators, hashtags...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(SearchRed),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    saveQueryToHistory(searchQuery)
                                    focusManager.clearFocus()
                                }
                            }
                        ),
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

            Text(
                text = "Search",
                color = SearchRed,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        if (searchQuery.isNotBlank()) {
                            saveQueryToHistory(searchQuery)
                            focusManager.clearFocus()
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        // ২. ক্যাটাগরি চিপস
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(SearchCategories) { category ->
                val isSelected = (selectedCategory == category)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) SearchRed else InputBg,
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = if (isSelected) SearchRed else CardBorderColor
                    ),
                    modifier = Modifier.clickable {
                        selectedCategory = category
                        focusManager.clearFocus()
                    }
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) Color.White else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)

        // ৩. সার্চ হিস্ট্রি অথবা রিলস গ্রিড রেজাল্ট
        if (searchQuery.isEmpty() && selectedCategory == "All" && searchHistoryList.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(searchHistoryList, key = { it }) { queryItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                searchQuery = queryItem
                                saveQueryToHistory(queryItem)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = queryItem,
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { removeSingleHistory(queryItem) }
                        )
                    }
                    HorizontalDivider(color = Color(0xFF181D28), thickness = 0.5.dp)
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Clear all",
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { clearAllHistory() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            // ৪. রিলস সার্চ রেজাল্ট গ্রিড
            if (filteredReels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                        Text("No matching reels found", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Try searching with different keywords or hashtags.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredReels, key = { it.id }) { reel ->
                        ReelSearchResultCard(
                            reel = reel,
                            onClick = { onReelClick(reel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReelSearchResultCard(
    reel: UserReelDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141722))
            .clickable { onClick() }
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = formatViews(reel.viewsCount),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatViews(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
