package com.example.ui.screens.shorts

import androidx.compose.animation.core.*
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val CHUNK_RANGE_SIZE = 50

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
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDescExpanded by remember { mutableStateOf(false) }

    // 🎯 মাখনের মতো স্মুথ ড্র্যাগ-টু-মিনিমাইজ অ্যানিমেশন স্টেট
    val dragOffsetY = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(1200f) }

    // 🎯 সব পর্ব একসাথে স্ক্রোল হওয়ার জন্য গ্রিড স্টেট
    val episodeGridState = rememberLazyGridState()

    // পর্বের রেঞ্জ চিপস ক্যালকুলেশন (যেমন: 1-50, 51-76)
    val rangeChunks = remember(episodes) {
        episodes.chunked(CHUNK_RANGE_SIZE).mapIndexed { index, list ->
            val start = index * CHUNK_RANGE_SIZE + 1
            val end = start + list.size - 1
            Triple(start, end, index * CHUNK_RANGE_SIZE)
        }
    }
    var selectedRangeIndex by remember { mutableIntStateOf(0) }

    // ব্যবহারকারী স্ক্রোল করার সাথে সাথে ওপরের রেঞ্জ চিপ স্বয়ংক্রিয়ভাবে সিঙ্ক হবে
    LaunchedEffect(episodeGridState.firstVisibleItemIndex) {
        val visibleIndex = episodeGridState.firstVisibleItemIndex
        val matchingRange = rangeChunks.indexOfLast { it.third <= visibleIndex }
        if (matchingRange != -1) {
            selectedRangeIndex = matchingRange
        }
    }

    // ড্রয়ার ওপেন হলে বর্তমান পর্বের কাছে স্বয়ংক্রিয় স্ক্রোল
    LaunchedEffect(currentEpNum, episodes) {
        val targetIdx = episodes.indexOfFirst { it.episodeNumber == currentEpNum }
        if (targetIdx != -1) {
            episodeGridState.scrollToItem(targetIdx)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, 1),
        pageCount = { 2 }
    )

    Surface(
        color = Color(0xFF181D29),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, Color(0xFF262E40)),
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                sheetHeightPx = coordinates.size.height.toFloat()
            }
            .offset { IntOffset(0, dragOffsetY.value.coerceAtLeast(0f).roundToInt()) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // =============================================================
            // 🎯 ১. স্মুথ ড্র্যাগ হ্যান্ডেল বার (হাত দিয়ে টানলে ড্রয়ার স্মুথ নিচে নামবে)
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    // যদি ১৫০ পিক্সেলের বেশি নিচে নামানো হয়, তবে সম্পূর্ণ বন্ধ হবে
                                    if (dragOffsetY.value > 140f) {
                                        dragOffsetY.animateTo(
                                            targetValue = sheetHeightPx,
                                            animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                                        )
                                        onDismiss()
                                    } else {
                                        // কম নামালে সুন্দর স্প্রিং হয়ে আগের জায়গায় উঠে আসবে
                                        dragOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                                }
                            }
                        )
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

            // হেডার: পোস্টার + টাইটেল + সবুজ [ Add list ] বাটন (টানলেও স্মুথ হবে)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (dragOffsetY.value > 140f) {
                                        dragOffsetY.animateTo(sheetHeightPx, tween(220, easing = FastOutLinearInEasing))
                                        onDismiss()
                                    } else {
                                        dragOffsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                                }
                            }
                        )
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
            // 📑 ২. ট্যাব হেডার (Introduction & Episodes)
            // =============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            // ↔️ ৩. পেজার (পেজ ১-এ একটানা স্ক্রোলযোগ্য পর্ব গ্রিড)
            // =============================================================
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page == 0) {
                    // পেজ ০: Introduction ট্যাব
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
                                                onDismiss()
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
                    // 🌟 পেজ ১: সব পর্বের একটানা স্ক্রোল ডাউন গ্রিড (কোনো ফাঁকা থাকবে না)
                    // =============================================================
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ওপরে কুইক জাম্প রেঞ্জ চিপস (যেমন: 1-50, 51-76)
                        if (rangeChunks.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rangeChunks.forEachIndexed { index, triple ->
                                    val start = triple.first
                                    val end = triple.second
                                    val targetScrollItem = triple.third
                                    val isSelected = (index == selectedRangeIndex)

                                    Text(
                                        text = "$start-$end",
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier
                                            .clickable {
                                                selectedRangeIndex = index
                                                coroutineScope.launch {
                                                    // 🎯 স্মুথভাবে সেই রেঞ্জে স্ক্রোল করে যাবে
                                                    episodeGridState.animateScrollToItem(targetScrollItem)
                                                }
                                            }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Text("${episodes.size} Episodes", color = Color(0xFF94A3B8), fontSize = 11.5.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        // 🔲 ৮-কলামের পর্ব গ্রিড (১ থেকে শেষ পর্যন্ত সব পর্ব এক পেজে স্ক্রোল হবে)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            state = episodeGridState, // 👈 স্ক্রোল স্টেট যুক্ত
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // 🎯 পুরো episodes লিস্ট রেন্ডার হচ্ছে (৫০ এর পর ৫১ নিচে সুন্দরভাবে বসবে)
                            items(episodes, key = { it.episodeId }) { ep ->
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
