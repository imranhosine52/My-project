@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.ui.LanguageDubBadge

// 🎨 ফিক্সড কালার কনস্ট্যান্টসমূহ
private val HomeBackgroundDark = Color(0xFF090A0F)
private val FilterBoxBackground = Color(0xFF10141F)
private val CardBorderColor = Color(0xFF1E2638)
private val ActivePillBg = Color(0xFF232B3E)
private val ActivePillText = Color(0xFFFFFFFF)
private val InactivePillText = Color(0xFF8E95A5)

@Composable
fun DramaSeriesCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 🎯 ফিল্টার স্টেটসমূহ
    var selectedCountry by rememberSaveable { mutableStateOf("All") }
    var selectedYear by rememberSaveable { mutableStateOf("All") }
    var selectedLanguage by rememberSaveable { mutableStateOf("All") }
    var selectedSort by rememberSaveable { mutableStateOf("ForYou") }

    val countryOptions = remember {
        listOf("All", "China", "Korea", "Japan", "Thailand", "Turkey", "Bangladesh", "India", "Asia")
    }

    val yearOptions = remember {
        listOf("All", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2010s", "2000s", "1990s", "Other")
    }

    val languageOptions = remember {
        listOf("All", "Bengali dub", "Hindi dub", "English dub", "Tamil dub", "Original")
    }

    val sortOptions = remember {
        listOf("ForYou", "Hottest", "Latest", "Rating")
    }

    // ⚡ রিয়েল-টাইম সার্ভার ডাটা ফিল্টারিং লজিক
    val filteredAndSortedDramas = remember(
        items,
        selectedCountry,
        selectedYear,
        selectedLanguage,
        selectedSort
    ) {
        var result = items.filter { it.isDramaSeries || it.type.equals("series", true) }

        // ১. দেশ ফিল্টার
        if (selectedCountry != "All") {
            result = result.filter { drama ->
                drama.country.contains(selectedCountry, ignoreCase = true) ||
                drama.categories.any { it.contains(selectedCountry, ignoreCase = true) } ||
                drama.title.contains(selectedCountry, ignoreCase = true)
            }
        }

        // ২. সাল ফিল্টার
        if (selectedYear != "All") {
            result = result.filter { drama ->
                val y = drama.releaseYear.filter { it.isDigit() }.toIntOrNull() ?: 2026
                when (selectedYear) {
                    "2026" -> y == 2026
                    "2025" -> y == 2025
                    "2024" -> y == 2024
                    "2023" -> y == 2023
                    "2022" -> y == 2022
                    "2021" -> y == 2021
                    "2020" -> y == 2020
                    "2010s" -> y in 2010..2019
                    "2000s" -> y in 2000..2009
                    "1990s" -> y in 1990..1999
                    "Other" -> y < 1990
                    else -> drama.releaseYear.contains(selectedYear)
                }
            }
        }

        // ৩. ডাবিং ভাষা ফিল্টার
        if (selectedLanguage != "All") {
            result = result.filter { drama ->
                val badge = drama.dubBadge.lowercase()
                when (selectedLanguage) {
                    "Bengali dub" -> drama.isBanglaDub || badge.contains("bangla") || badge.contains("bengali")
                    "Hindi dub" -> drama.isHindiDub || badge.contains("hindi")
                    "English dub" -> badge.contains("eng") || drama.language.contains("eng", true)
                    "Tamil dub" -> badge.contains("tamil")
                    "Original" -> !drama.isBanglaDub && !drama.isHindiDub
                    else -> true
                }
            }
        }

        // ৪. সর্টিং
        when (selectedSort) {
            "Hottest" -> result.sortedByDescending { it.numericViews }
            "Latest" -> result.sortedByDescending { it.releaseYear.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            "Rating" -> result.sortedByDescending { it.rating }
            else -> result
        }
    }

    val gridChunks = remember(filteredAndSortedDramas) {
        filteredAndSortedDramas.chunked(3)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackgroundDark),
        contentPadding = PaddingValues(
            top = statusBarTop + 84.dp,
            bottom = 70.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =========================================================================
        // 🎛️ ১. কম্প্যাক্ট ৪-স্তরের ফিল্টার বক্স
        // =========================================================================
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = FilterBoxBackground),
                border = BorderStroke(0.6.dp, CardBorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactFilterScrollRow(
                        options = countryOptions,
                        selectedOption = selectedCountry,
                        onOptionSelected = { selectedCountry = it }
                    )

                    CompactFilterScrollRow(
                        options = yearOptions,
                        selectedOption = selectedYear,
                        onOptionSelected = { selectedYear = it }
                    )

                    CompactFilterScrollRow(
                        options = languageOptions,
                        selectedOption = selectedLanguage,
                        onOptionSelected = { selectedLanguage = it }
                    )

                    CompactFilterScrollRow(
                        options = sortOptions,
                        selectedOption = selectedSort,
                        onOptionSelected = { selectedSort = it }
                    )
                }
            }
        }

        // =========================================================================
        // 🏷️ ২. সেকশন হেডার
        // =========================================================================
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color(0xFFFF2A4B))
                    )
                    Text(
                        text = if (selectedCountry != "All") "$selectedCountry Series" else "Drama Series",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${filteredAndSortedDramas.size} Titles",
                    color = Color(0xFF8E95A5),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // =========================================================================
        // 🔲 ৩. ৩-কলাম কম্প্যাক্ট গ্রিড
        // =========================================================================
        if (filteredAndSortedDramas.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No drama found matching selected filters.",
                        color = Color(0xFF8E95A5),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(gridChunks.size) { rowIndex ->
                val rowDramas = gridChunks[rowIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDramas.forEach { drama ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDesktopDramaCard(
                                drama = drama,
                                onClick = { onNavigateToPlayer(drama.slug) }
                            )
                        }
                    }
                    repeat(3 - rowDramas.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🔘 ফিক্সড ফিল্টার চিপস রো (ActivePillText ভ্যারিয়েবল ফিক্সড)
// =========================================================================
@Composable
private fun CompactFilterScrollRow(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = (selectedOption == option)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) ActivePillBg else Color.Transparent)
                    .clickable { onOptionSelected(option) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    color = if (isSelected) ActivePillText else InactivePillText,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// =========================================================================
// 🖼️ ৩-কলাম কম্প্যাক্ট কার্ড
// =========================================================================
@Composable
fun CompactDesktopDramaCard(
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
                .border(0.6.dp, CardBorderColor, RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
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

            // ডাইনামিক ডাবিং ব্যাজ
            LanguageDubBadge(
                dubText = drama.dubBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Episodes" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = drama.displayName,
            color = Color(0xFFE2E8F0),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
