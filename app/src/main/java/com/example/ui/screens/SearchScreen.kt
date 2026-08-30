@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

private val ActionGreen = Color(0xFF00D166)
private val CardBgDark = Color(0xFF131A26)

private val filterTagsList = listOf(
    "All", "Bangla Dub", "Hindi Dub", "K-Drama", "C-Drama", "Shorts Drama", "Drama Series", "Anime", "Romance", "Action", "Popular"
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

    // 🎙️ সর্বজনীন ভাষা সাপোর্ট করা ভয়েস সার্চ লঞ্চার (বাংলা, English, হিন্দি ইত্যাদি)
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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search drama in any language (বাংলা, English, हिंदी)...")
            }
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice recognition is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // -------------------------------------------------------------
            // 🔍 Search Bar Pill with Voice Search
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBgDark)
                    .border(1.2.dp, if (searchState.searchQuery.isNotEmpty()) TealAccent else BorderDark, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Icon
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (searchState.searchQuery.isNotEmpty()) TealAccent else TextMuted,
                    modifier = Modifier.size(20.dp)
                )

                // Input Text Field
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
                    cursorBrush = SolidColor(TealAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search drama, anime, dubbed series...",
                                    color = TextMuted,
                                    fontSize = 13.5.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Clear (X) Button if query is not empty
                if (searchState.searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.onSearchQueryChanged("") }
                    )
                }

                // 🎙️ Voice Search Microphone Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TealAccent.copy(alpha = 0.15f))
                        .clickable { startVoiceSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = TealAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // -------------------------------------------------------------
            // 🏷️ Category Filter Chips (LazyRow)
            // -------------------------------------------------------------
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterTagsList) { tag ->
                    val isSelected = (activeFilterTag == tag)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) ActionGreen else CardBgDark,
                        border = BorderStroke(1.dp, if (isSelected) ActionGreen else BorderDark),
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
                            color = if (isSelected) Color.Black else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Results Counter & Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Results (${searchState.searchResults.size})",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                if (activeFilterTag != "All" || searchState.searchQuery.isNotEmpty()) {
                    Text(
                        text = "Clear Filter",
                        color = ActionGreen,
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

            Spacer(modifier = Modifier.height(10.dp))

            // -------------------------------------------------------------
            // 🎬 3-Column Drama Grid / Empty State
            // -------------------------------------------------------------
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
                            tint = TextMuted,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No drama found",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching with another name or speak using the microphone icon above.",
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    items(searchState.searchResults, key = { it.id }) { drama ->
                        SearchDramaGridCard(
                            drama = drama,
                            onClick = { onNavigateToPlayer(drama.slug) }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🖼️ 3-Column Poster Card Component
// -------------------------------------------------------------
@Composable
private fun SearchDramaGridCard(
    drama: ContentItemDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        // Poster Box with Aspect Ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .background(CardBgDark)
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

            // Subtle dark shadow gradient on top & bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // Dubbing Badge at Top Right (e.g. Hindi, Bangla)
            val isHindi = drama.isHindiDub || drama.dubBadge.contains("Hindi", ignoreCase = true)
            val badgeColor = if (isHindi) Color(0xFFFFB300) else Color(0xFFFFA000)

            Surface(
                shape = RoundedCornerShape(topEnd = 10.dp, bottomStart = 8.dp),
                color = badgeColor,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = if (isHindi) "Hindi" else "Bangla",
                    color = Color.Black,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Episode Count at Bottom Left
            Text(
                text = "${drama.totalEpisodes} Episodes",
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Title Below Card
        Text(
            text = drama.title,
            color = TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
