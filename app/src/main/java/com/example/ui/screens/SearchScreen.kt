@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
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

// 🏷️ ১. শুধুমাত্র ডাটাবেজের ভ্যালিড ক্যাটাগরি তালিকা (অতিরিক্তগুলো রিমুভ করা হয়েছে)
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

    // 🎙️ ভয়েস সার্চ লঞ্চার
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
            // 🔍 Search Bar Pill
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
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (searchState.searchQuery.isNotEmpty()) TealAccent else TextMuted,
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

            // Results Counter & Clear Filter
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
            // 🎬 3-Column Drama Grid (Smooth Scroll)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
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
// 🖼️ 3-Column Poster Card (চিকন শাইনিং লাইন ও ১ লাইনে টাইটেল)
// -------------------------------------------------------------
@Composable
private fun SearchDramaGridCard(
    drama: ContentItemDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // 🌟 কার্ডের চারপাশে আলোর রেখা ঘোরার জন্য ইনফিনিট ট্রানজিশন
    val infiniteTransition = rememberInfiniteTransition(label = "searchCardShine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    // ✨ চিকন শাইনিং গ্রেডিয়েন্ট ব্রাশ (1.dp)
    val shineBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x33FFFFFF),            // হালকা বেসিক বর্ডার
            Color(0xFF00E5FF).copy(0.8f),  // গ্লোয়িং সায়ান শাইন
            Color(0xFFFFD700).copy(0.85f), // গোল্ডেন শাইন
            Color(0x33FFFFFF)             // হালকা বেসিক বর্ডার
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 250f, 350f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        // Poster Box with Aspect Ratio & 1dp Shining Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,              // 👈 চিকন ১ ডিপি লাইন
                    brush = shineBorderBrush,  // 👈 শাইনিং অ্যানিমেশন
                    shape = RoundedCornerShape(10.dp)
                )
                .background(CardBgDark)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl.ifBlank { drama.bannerUrl })
                    .crossfade(true)
                    .build(),
                contentDescription = drama.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // নিচের ডার্ক শ্যাডো
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // 🏷️ ডাব ব্যাজ (Bangla = গোল্ডেন, Hindi = ব্লু)
            val isBangla = drama.language.contains("Bangla", ignoreCase = true) || drama.customDubBadge.contains("Bangla", ignoreCase = true)
            val badgeColor = if (isBangla) Color(0xFFFFB300) else Color(0xFF00B0FF)

            Surface(
                shape = RoundedCornerShape(topEnd = 10.dp, bottomStart = 8.dp),
                color = badgeColor,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = if (isBangla) "Bangla" else "Hindi",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // 📺 এপিসোড সংখ্যা
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Episodes" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        // 📝 টাইটেল (কড়াভাবে ১ লাইনে সীমাবদ্ধ রাখা হয়েছে)
        Text(
            text = drama.title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,                     // 👈 ১ লাইনে সীমাবদ্ধ
            overflow = TextOverflow.Ellipsis, // 👈 বড় হলে বাকি অংশ ... দেখাবে
            lineHeight = 13.sp
        )
    }
}
