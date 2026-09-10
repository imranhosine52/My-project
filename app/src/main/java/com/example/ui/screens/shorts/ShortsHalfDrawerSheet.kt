package com.example.ui.screens.shorts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import kotlinx.coroutines.launch

private const val CHUNK_SIZE_DRAWER = 50

@Composable
fun ShortsHalfDrawerSheet(
    content: ContentItemDto,
    episodes: List<EpisodeDto>,
    currentEpNum: Int,
    initialTab: Int = 1, // ০ = Introduction, ১ = Episodes
    isInWatchlist: Boolean,
    shortDramaRecommendations: List<ContentItemDto>,
    onSelectEpisode: (EpisodeDto) -> Unit,
    onSelectRecommendation: (String) -> Unit,
    onToggleWatchlist: () -> Unit,
    onDismiss: () -> Unit = {}, // 🎯 মিনিমাইজ বা বন্ধ করার অ্যাকশন
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDescExpanded by remember { mutableStateOf(false) }

    val episodeChunks = remember(episodes) { episodes.chunked(CHUNK_SIZE_DRAWER) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }

    // 🎯 ডানে-বামে সোয়াইপ করার জন্য ২ পেজের HorizontalPager
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, 1),
        pageCount = { 2 }
    )

    Surface(
        color = Color(0xFF181D29),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, Color(0xFF262E40)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // =============================================================
            // 🎯 উপর থেকে নিচে টান দিলে মিনিমাইজ হওয়ার ড্র্যাগ হ্যান্ডেল
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            // নিচের দিকে টান দিলে ড্রয়ার বন্ধ হবে
                            if (dragAmount > 10f) {
                                onDismiss()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFF5A667A))
                )
            }

            // হেডার: পোস্টার + টাইটেল + সবুজ [ Add list ] বাটন (নিচে টানলে বন্ধ হবে)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 15f) {
                                onDismiss()
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 50.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2B3346))
                    ) {
                        AsyncImage(
                            model = content.posterUrl ?: content.bannerUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column {
                        Text(
                            text = content.title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${episodes.size} Episodes",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Button(
                    onClick = onToggleWatchlist,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInWatchlist) Color(0xFF0F3B32) else Color(0xFF00D166)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isInWatchlist) "Added ✓" else "Add list",
                        color = if (isInWatchlist) Color(0xFF00E676) else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =============================================================
            // 📑 ট্যাব হেডার (ক্লিক করলেও পেজার স্ক্রোল হবে)
            // =============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Introduction ট্যাব
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    }
                ) {
                    Text(
                        text = "Introduction",
                        color = if (pagerState.currentPage == 0) Color.White else Color(0xFF94A3B8),
                        fontSize = 14.5.sp,
                        fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.5.dp)
                            .background(if (pagerState.currentPage == 0) Color(0xFF00E676) else Color.Transparent)
                    )
                }

                // Episodes ট্যাব
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    }
                ) {
                    Text(
                        text = "Episodes",
                        color = if (pagerState.currentPage == 1) Color.White else Color(0xFF94A3B8),
                        fontSize = 14.5.sp,
                        fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.5.dp)
                            .background(if (pagerState.currentPage == 1) Color(0xFF00E676) else Color.Transparent)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF262E40), thickness = 0.8.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // =============================================================
            // ↔️ ডানে-বামে সোয়াইপযোগ্য পেজার (HorizontalPager)
            // =============================================================
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page == 0) {
                    // =============================================================
                    // 📖 পেজ ০: Introduction ট্যাব
                    // =============================================================
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Column {
                                Text(
                                    text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    maxLines = if (isDescExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isDescExpanded) "Less" else "...more",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { isDescExpanded = !isDescExpanded }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF2E1C22),
                                    border = BorderStroke(0.6.dp, Color(0xFF702E3B))
                                ) {
                                    Text(
                                        text = "🔥 Trending No.3 >",
                                        color = Color(0xFFFF5252),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                content.categories.take(2).forEach { cat ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF222838),
                                        border = BorderStroke(0.6.dp, Color(0xFF334155))
                                    ) {
                                        Text(
                                            text = "$cat >",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            HorizontalDivider(color = Color(0xFF262E40), thickness = 0.8.dp)
                        }

                        item {
                            Text(
                                text = "Spin-off Program",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val shortDramasRows = shortDramaRecommendations.chunked(3)
                        items(shortDramasRows) { rowDramas ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowDramas.forEach { rec ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onSelectRecommendation(rec.slug)
                                                onDismiss() // রিকমেন্ডেশন সিলেক্ট করলেও বন্ধ হবে
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.72f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF222838))
                                        ) {
                                            AsyncImage(
                                                model = rec.posterUrl ?: rec.bannerUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(bottomStart = 4.dp),
                                                color = Color(0xFF00D166),
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Text(
                                                    text = "Short",
                                                    color = Color.Black,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }

                                            Text(
                                                text = "${rec.totalEpisodes} Episodes",
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = rec.title,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                repeat(3 - rowDramas.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    // =============================================================
                    // 📖 পেজ ১: Episodes ট্যাব
                    // =============================================================
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (episodeChunks.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                episodeChunks.forEachIndexed { index, chunk ->
                                    val start = index * CHUNK_SIZE_DRAWER + 1
                                    val end = start + chunk.size - 1
                                    val isSelected = (index == selectedChunkIndex)

                                    Text(
                                        text = "$start-$end",
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier
                                            .clickable { selectedChunkIndex = index }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Text("${episodes.size} Episodes", color = Color(0xFF94A3B8), fontSize = 11.5.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                        // 🔲 ৮-কলামের পর্ব গ্রিড
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                                val isCurrent = (ep.episodeNumber == currentEpNum)

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurrent) Color(0xFF0F3B32) else Color(0xFF222838))
                                        .border(
                                            width = if (isCurrent) 1.2.dp else 0.dp,
                                            color = if (isCurrent) Color(0xFF00E676) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        // 🎯 পর্বে ক্লিক করার সাথে সাথেই ড্রয়ার বন্ধ হয়ে যাবে
                                        .clickable {
                                            onSelectEpisode(ep)
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ep.episodeNumber.toString(),
                                        color = if (isCurrent) Color(0xFF00E676) else Color(0xFFE2E8F0),
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold
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
