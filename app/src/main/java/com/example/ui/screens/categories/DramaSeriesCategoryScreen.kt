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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
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
import java.util.Locale
import java.util.Random

// 🎨 হোমপেজের হুবহু ব্যাকগ্রাউন্ড ও কালার প্যালেট
private val HomeBackgroundDark = Color(0xFF090A0F)
private val FilterBoxBackground = Color(0xFF10141F)
private val CardBorderColor = Color(0xFF1E2638)
private val ActivePillBg = Color(0xFF232B3E)
private val ActivePillText = Color(0xFFFFFFFF)
private val InactivePillText = Color(0xFF8E95A5)
private val GoldRating = Color(0xFFFFB300)

@Composable
fun DramaSeriesCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 🎯 রোটেশন / শাফেল সিড ট্র্যাকার (প্রতিবার পরিবর্তনের সাথে সাথে অবস্থান চেঞ্জ করার জন্য)
    var shuffleSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    // ফিল্টার স্টেটসমূহ
    var selectedCountry by rememberSaveable { mutableStateOf("All") }
    var selectedYear by rememberSaveable { mutableStateOf("All") }
    var selectedLanguage by rememberSaveable { mutableStateOf("All") }
    var selectedSort by rememberSaveable { mutableStateOf("ForYou") }

    val countryOptions = remember {
        listOf("All", "China", "Korea", "Japan", "Thailand", "Turkey", "Bangladesh", "India", "Asia")
    }

    // 🎯 ২০১০ থেকে ২০২৭ সাল পর্যন্ত
    val yearOptions = remember {
        listOf(
            "All", "2027", "2026", "2025", "2024", "2023", "2022", "2021",
            "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2013",
            "2012", "2011", "2010"
        )
    }

    val languageOptions = remember {
        listOf("All", "Bengali dub", "Hindi dub", "English dub", "Tamil dub", "Original")
    }

    val sortOptions = remember {
        listOf("ForYou", "Hottest", "Latest", "Rating")
    }

    // =========================================================================
    // ⚡ ডাইনামিক ফিল্টারিং ও কার্ড পজিশন রোটেটিং ইঞ্জিন
    // =========================================================================
    val filteredAndSortedDramas = remember(
        items,
        selectedCountry,
        selectedYear,
        selectedLanguage,
        selectedSort,
        shuffleSeed
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

        // ২. সাল ফিল্টার (২০১০-২০২৭)
        if (selectedYear != "All") {
            result = result.filter { drama ->
                drama.releaseYear.contains(selectedYear)
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

        // ৪. সর্টিং এবং ডাইনামিক পজিশন শিফটিং (ForYou মোডে কার্ড নিচ থেকে উপরে শাফেল হবে)
        when (selectedSort) {
            "Hottest" -> result.sortedByDescending { it.numericViews }
            "Latest" -> result.sortedByDescending { it.releaseYear.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            "Rating" -> result.sortedByDescending { it.rating }
            else -> {
                // 🎯 ForYou মোডে প্রতিবার কার্ডের অবস্থান সুন্দরভাবে রোটেট/শাফেল হবে
                if (result.size > 2) {
                    result.shuffled(Random(shuffleSeed))
                } else {
                    result
                }
            }
        }
    }

    // ৪টি করে কার্ড প্রতি সারিতে
    val gridChunks = remember(filteredAndSortedDramas) {
        filteredAndSortedDramas.chunked(4)
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
        // 🎛️ ১. ৪-স্তরের ফিল্টার বার
        // =========================================================================
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
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
                    // দেশ ফিল্টার
                    CompactFilterScrollRow(
                        options = countryOptions,
                        selectedOption = selectedCountry,
                        onOptionSelected = {
                            selectedCountry = it
                            shuffleSeed = System.currentTimeMillis() // 👈 অবস্থান রোটেশন
                        }
                    )

                    // সাল ফিল্টার
                    CompactFilterScrollRow(
                        options = yearOptions,
                        selectedOption = selectedYear,
                        onOptionSelected = {
                            selectedYear = it
                            shuffleSeed = System.currentTimeMillis() // 👈 অবস্থান রোটেশন
                        }
                    )

                    // ডাবিং ভাষা ফিল্টার
                    CompactFilterScrollRow(
                        options = languageOptions,
                        selectedOption = selectedLanguage,
                        onOptionSelected = {
                            selectedLanguage = it
                            shuffleSeed = System.currentTimeMillis() // 👈 অবস্থান রোটেশন
                        }
                    )

                    // সর্টিং ফিল্টার
                    CompactFilterScrollRow(
                        options = sortOptions,
                        selectedOption = selectedSort,
                        onOptionSelected = {
                            selectedSort = it
                            shuffleSeed = System.currentTimeMillis() // 👈 অবস্থান রোটেশন
                        }
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
                    .padding(horizontal = 10.dp, vertical = 2.dp),
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${filteredAndSortedDramas.size} Titles",
                    color = Color(0xFF8E95A5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // =========================================================================
        // 🔲 ৩. ৪-কলাম কম্প্যাক্ট গ্রিড
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
                        fontSize = 12.5.sp,
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
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowDramas.forEach { drama ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDesktopDramaCard(
                                drama = drama,
                                onClick = { onNavigateToPlayer(drama.slug) }
                            )
                        }
                    }
                    repeat(4 - rowDramas.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🔘 স্লিম অনুভূমিক ফিল্টার চিপস
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
// 🖼️ ৪-কলাম কম্প্যাক্ট কার্ড
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
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(6.dp))
                .border(0.6.dp, CardBorderColor, RoundedCornerShape(6.dp))
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
                                Color.Black.copy(alpha = 0.70f)
                            )
                        )
                    )
            )

            // ডাবিং ব্যাজ
            LanguageDubBadge(
                dubText = drama.dubBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // এপিসোড সংখ্যা
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Ep" else "HD"
            Text(
                text = epCount,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            )

            // রেটিং (হালকা ইফেক্ট)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldRating,
                    modifier = Modifier.size(8.5.dp)
                )
                Text(
                    text = if (drama.rating > 0) String.format(Locale.US, "%.1f", drama.rating) else "8.5",
                    color = GoldRating,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(2.5.dp))

        Text(
            text = drama.displayName,
            color = Color(0xFFE2E8F0),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
