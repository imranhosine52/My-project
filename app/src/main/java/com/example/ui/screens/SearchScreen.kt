@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.ui.LanguageDubBadge
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.launch

// 🎨 ব্যাকগ্রাউন্ড থেকে সম্পূর্ণ আলাদা উজ্জ্বল কালার প্যালেট
private val PureBlackBg = Color(0xFF07090E)
private val SearchBarBg = Color(0xFF161E2E)        // 👈 ব্যাকগ্রাউন্ডের সাথে মিশবে না, আলাদা ফুটে উঠবে
private val SearchBarBorder = Color(0xFF2C3954)    // 👈 স্লিম প্রফেশনাল বর্ডার
private val CompactCardBg = Color(0xFF101522)      // 👈 ডেক্সটপ স্টাইল কমপ্যাক্ট কার্ড ব্যাকগ্রাউন্ড
private val CardBorderColor = Color(0xFF1D2638)
private val ActivePillBg = Color(0xFF00E676)
private val GoldRating = Color(0xFFFFB300)

// 🌟 ডেক্সটপ স্টাইল ব্লু-গ্রিন স্লিম প্লে বাটন গ্রেডিয়েন্ট
private val BlueGreenPlayBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF007AFF),
        Color(0xFF00D166)
    )
)

private val filterTagsList = listOf(
    "All",
    "Bangla Dub",
    "Hindi Dub",
    "Shorts Drama",
    "Drama Series",
    "Anime Series",
    "Movies"
)

@Composable
fun SearchScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val searchState by viewModel.searchUiState.collectAsStateWithLifecycle()

    // 🎯 ডানে-বামে স্মুথ স্লাইডিং করার জন্য Pager State
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { filterTagsList.size })
    val filterChipScrollState = rememberLazyListState()

    // সোয়াইপ করলে ওপরের ট্যাগগুলো স্বয়ংক্রিয়ভাবে সেন্টারে স্ক্রোল হবে
    LaunchedEffect(pagerState.currentPage) {
        filterChipScrollState.animateScrollToItem(pagerState.currentPage)
    }

    // 🎙️ ভয়েস সার্চ লাউঞ্চার
    val speechRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.onSearchQueryChanged(spokenText)
                focusManager.clearFocus()
            }
        }
    }

    fun startVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search drama, movie or anime...")
            }
            speechRecognitionLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlackBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =============================================================
            // 🔝 ১. ডেক্সটপ স্টাইলের স্লিম হেডার ও স্পষ্ট সার্চ বার
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF141A28),
                                Color(0xFF0D121C),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 🔍 স্লিম ও কন্ট্রাস্ট সার্চ বক্স (উচ্চতা মাত্র 38dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp) // 👈 ডেক্সটপ স্টাইল স্লিম উচ্চতা
                            .clip(RoundedCornerShape(20.dp))
                            .background(SearchBarBg) // 👈 ব্যাকগ্রাউন্ড থেকে আলাদা রং
                            .border(
                                width = 1.dp,
                                color = if (searchState.searchQuery.isNotEmpty()) Color(0xFF00E5FF) else SearchBarBorder,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (searchState.searchQuery.isNotEmpty()) Color(0xFF00E5FF) else Color(0xFF8692A6),
                            modifier = Modifier.size(17.dp)
                        )

                        BasicTextField(
                            value = searchState.searchQuery,
                            onValueChange = { query ->
                                viewModel.onSearchQueryChanged(query)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.5.sp, // 👈 স্লিম ফন্ট
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color(0xFF00E5FF)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchState.searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search drama, movie, anime or series...",
                                            color = Color(0xFF7E8A9E),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (searchState.searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.onSearchQueryChanged("") }
                            )
                        }

                        // মাইক বাটন
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222B3D))
                                .clickable { startVoiceSearch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // 🏷️ ছোট ও স্লিম ফিল্টার ট্যাগস (উচ্চতা মাত্র 26dp)
                    LazyRow(
                        state = filterChipScrollState,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(filterTagsList) { index, tag ->
                            val isSelected = (pagerState.currentPage == index)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) ActivePillBg else Color(0xFF151C2B),
                                border = BorderStroke(
                                    width = 0.8.dp,
                                    color = if (isSelected) ActivePillBg else Color(0xFF242F45)
                                ),
                                modifier = Modifier
                                    .height(26.dp) // 👈 স্লিম ট্যাগ উচ্চতা
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                page = index,
                                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag,
                                        color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                                        fontSize = 11.sp, // 👈 স্লিম ট্যাগ টেক্সট
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =============================================================
            // ↔️ ২. ডানে-বামে সোয়াইপযোগ্য পেজার (HorizontalPager)
            // =============================================================
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val currentTag = filterTagsList[pageIndex]

                // নির্বাচিত ট্যাগ ও সার্চ কোয়েরি অনুযায়ী ড্রামা ফিল্টারিং
                val displayList = remember(searchState.searchResults, searchState.searchQuery, currentTag) {
                    val baseList = if (searchState.searchQuery.isBlank()) searchState.allDramas else searchState.searchResults
                    when (currentTag) {
                        "Bangla Dub" -> baseList.filter { it.isBanglaDub }
                        "Hindi Dub" -> baseList.filter { it.isHindiDub }
                        "Shorts Drama" -> baseList.filter { it.isShorts }
                        "Drama Series" -> baseList.filter { it.isDramaSeries }
                        "Anime Series" -> baseList.filter { it.isAnime }
                        "Movies" -> baseList.filter { it.isMovie }
                        else -> baseList
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // রেজাল্ট কাউন্টার রো
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Results (${displayList.size})",
                            color = Color(0xFFD5DAE5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (searchState.searchQuery.isNotEmpty()) {
                            Text(
                                text = "Clear Search",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    viewModel.onSearchQueryChanged("")
                                }
                            )
                        }
                    }

                    // 🎬 রেজাল্ট লিস্ট বা নো-রেজাল্ট স্টেট
                    if (displayList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    text = "No drama found in $currentTag",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Try searching with another keyword or swipe to another tab.",
                                    color = Color(0xFF8E95A5),
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp), // 👈 কমপ্যাক্ট গ্যাপ
                            contentPadding = PaddingValues(top = 2.dp, bottom = 80.dp)
                        ) {
                            itemsIndexed(
                                items = displayList,
                                key = { _, item -> "${item.slug}_${currentTag}" }
                            ) { index, drama ->
                                // =============================================================
                                // 🚀 ৩. সার্চ করলে নিচ থেকে উপরে ওঠার মসৃণ অ্যানিমেশন
                                // =============================================================
                                AnimatedVisibility(
                                    visible = true,
                                    enter = slideInVertically(
                                        initialOffsetY = { 60 + (index * 15).coerceAtMost(120) }, // 👈 নিচ থেকে উপরে ওঠার ইফেক্ট
                                        animationSpec = tween(
                                            durationMillis = 280,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) + fadeIn(animationSpec = tween(280))
                                ) {
                                    CompactDesktopSearchCard(
                                        drama = drama,
                                        onClick = { onNavigateToPlayer(drama.slug) }
                                    )
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
// 🖼️ ডেক্সটপ ভিউয়ের মতো স্লিম ও কমপ্যাক্ট কার্ড (উচ্চতা মাত্র ~58dp)
// =============================================================================
@Composable
private fun CompactDesktopSearchCard(
    drama: ContentItemDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp) // 👈 ডেক্সটপ স্টাইল কমপ্যাক্ট স্লিম উচ্চতা
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CompactCardBg),
        border = BorderStroke(0.6.dp, CardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 🖼️ স্লিম পোস্টার
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF1E2433))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(drama.posterUrl ?: drama.bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = drama.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // ডাবিং ব্যাজ (সুপার স্লিম)
                LanguageDubBadge(
                    dubText = drama.dubBadge,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // 📝 টাইটেল, মেটাডাটা ও রেটিং (একদম গোছানো কমপ্যাক্ট)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // ড্রামার ছোট ও পরিচ্ছন্ন নাম
                Text(
                    text = drama.displayName,
                    color = Color.White,
                    fontSize = 12.5.sp, // 👈 স্লিম টেক্সট
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // মেটাডাটা লাইন (সাল • ক্যাটাগরি • রেটিং)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val metaCategory = drama.categories.firstOrNull() ?: drama.type.replaceFirstChar { it.uppercase() }
                    Text(
                        text = "📺 ${drama.releaseYear} • $metaCategory • ${drama.country}",
                        color = Color(0xFF8E95A5),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // গোল্ডেন স্টার রেটিং
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        Text("★", color = GoldRating, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val displayRating = if (drama.rating > 0) String.format("%.1f", drama.rating) else "8.5"
                        Text(displayRating, color = GoldRating, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 🌟 স্লিম [ ▶ Play ] বাটন (উচ্চতা মাত্র 26dp)
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(BlueGreenPlayBrush)
                    .clickable { onClick() }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Play",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
