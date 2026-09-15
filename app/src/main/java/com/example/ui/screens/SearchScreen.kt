@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

// 🎨 প্রিমিয়াম সিনেমাটিক কালার প্যালেট
private val PureBlackBg = Color(0xFF06080E)
private val DeepCardBg = Color(0xFF111520)
private val CardBorderColor = Color(0xFF1E2536)
private val ActionGreen = Color(0xFF00D166)
private val GoldRating = Color(0xFFFFB300)

// 🌟 গ্রিন ও ব্লু প্লে বাটন গ্রেডিয়েন্ট
private val BlueGreenPlayBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF007AFF), // Electric Blue
        Color(0xFF00D166)  // Emerald Green
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
    val searchState by viewModel.searchUiState.collectAsStateWithLifecycle()

    var activeFilterTag by remember { mutableStateOf("All") }

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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search drama in any language...")
            }
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice recognition is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlackBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =============================================================
            // 🔝 ১. নোটিফিকেশন পেজের মতো প্রিমিয়াম গ্রেডিয়েন্ট টপ হেডার
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF161B28),
                                Color(0xFF0E121B),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 🔍 প্রিমিয়াম সার্চ টাইপিং বক্স
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DeepCardBg)
                            .border(
                                width = 1.dp,
                                color = if (searchState.searchQuery.isNotEmpty()) Color(0xFF007AFF).copy(alpha = 0.7f) else CardBorderColor,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (searchState.searchQuery.isNotEmpty()) Color(0xFF007AFF) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )

                        BasicTextField(
                            value = searchState.searchQuery,
                            onValueChange = { query ->
                                viewModel.onSearchQueryChanged(query)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color(0xFF007AFF)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchState.searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search drama, movie, anime or series...",
                                            color = Color(0xFF64748B),
                                            fontSize = 13.sp
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
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.onSearchQueryChanged("") }
                            )
                        }

                        // স্টাইলিশ মাইক বাটন
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF162032))
                                .clickable { startVoiceSearch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // 🏷️ ক্যাটাগরি ফিল্টার চিপস
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filterTagsList) { tag ->
                            val isSelected = (activeFilterTag == tag)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) ActionGreen else DeepCardBg,
                                border = BorderStroke(0.8.dp, if (isSelected) ActionGreen else CardBorderColor),
                                modifier = Modifier.clickable {
                                    activeFilterTag = tag
                                    if (tag == "All") {
                                        viewModel.selectSearchTag("")
                                    } else {
                                        viewModel.selectSearchTag(tag)
                                    }
                                }
                            ) {
                                Text(
                                    text = tag,
                                    color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // রেজাল্ট কাউন্টার ও ক্লিয়ার ফিল্টার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Results (${searchState.searchResults.size})",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (activeFilterTag != "All" || searchState.searchQuery.isNotEmpty()) {
                    Text(
                        text = "Clear Filter",
                        color = Color(0xFF007AFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            activeFilterTag = "All"
                            viewModel.onSearchQueryChanged("")
                            viewModel.selectSearchTag("")
                        }
                    )
                }
            }

            // =============================================================
            // 🎬 ড্রামা লিস্ট (ব্যাজ মুক্ত পোস্টার ও ব্লু-গ্রিন Play বাটন)
            // =============================================================
            if (searchState.searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No drama found",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching with another title or use the voice search.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    items(
                        items = searchState.searchResults,
                        key = { it.slug.ifBlank { it.id } }
                    ) { drama ->
                        SearchDramaHorizontalRowCard(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 🖼️ হরিজন্টাল ড্রামা কার্ড (ক্লিন পোস্টার + ব্লু-গ্রিন Play বাটন)
// -----------------------------------------------------------------------------
@Composable
private fun SearchDramaHorizontalRowCard(
    drama: ContentItemDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCardBg),
        border = BorderStroke(0.8.dp, CardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🖼️ বামে পোস্টার থাম্বনেইল (Bangla/Hindi ব্যাজ সম্পূর্ণ রিমুভ করা হয়েছে)
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(94.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2433))
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

            // 📝 মাঝখানে টাইটেল, মেটাডাটা ও রেটিং
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // টাইটেল
                Text(
                    text = drama.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // মেটাডাটা রো (আইকন + সাল • ক্যাটাগরি • দেশ)
                val metaParts = mutableListOf<String>()
                if (drama.releaseYear.isNotBlank()) metaParts.add(drama.releaseYear)
                if (drama.categories.isNotEmpty()) metaParts.addAll(drama.categories.take(3))
                if (drama.country.isNotBlank()) metaParts.add(drama.country)
                val metaString = metaParts.joinToString(" • ")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = metaString.ifBlank { "Drama • HD" },
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // রেটিং (★ 7.5)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "★",
                        color = GoldRating,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val displayRating = if (drama.rating > 0) String.format("%.1f", drama.rating) else "7.1"
                    Text(
                        text = displayRating,
                        color = GoldRating,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 🌟 ব্লু ও গ্রিন প্রিমিয়াম গ্রেডিয়েন্ট [ ▶ Play ] বাটন
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(BlueGreenPlayBrush)
                    .clickable { onClick() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Play",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
