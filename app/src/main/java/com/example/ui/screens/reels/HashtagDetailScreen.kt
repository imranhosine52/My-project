@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.HashtagDetailResponse
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val DarkCardBg = Color(0xFF141722)
private val BorderColor = Color(0xFF222838)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun HashtagDetailScreen(
    hashtag: String,
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    val cleanTag = remember(hashtag) {
        val raw = hashtag.trim()
        if (raw.startsWith("#")) raw else "#$raw"
    }

    var hashtagDetail by remember { mutableStateOf<HashtagDetailResponse?>(null) }
    var reelsList by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    fun loadHashtagReels(force: Boolean = false) {
        if (!force) isLoading = true
        coroutineScope.launch {
            val result = repository.getHashtagReels(cleanTag, page = 1)
            isLoading = false
            isRefreshing = false

            if (result.isSuccess) {
                val detail = result.getOrNull()
                hashtagDetail = detail
                reelsList = detail?.reels ?: emptyList()
            } else {
                // অফলাইন বা নেটওয়ার্ক ফলব্যাক: ফিড থেকে ফিল্টার
                val feedResult = repository.getReelsFeed(tab = "for_you", page = 1)
                val allReels = feedResult.getOrDefault(emptyList())
                val matched = allReels.filter {
                    it.hashtags?.contains(cleanTag.removePrefix("#"), ignoreCase = true) == true ||
                    it.description?.contains(cleanTag, ignoreCase = true) == true ||
                    it.title?.contains(cleanTag, ignoreCase = true) == true
                }
                reelsList = matched
                hashtagDetail = HashtagDetailResponse(
                    success = true,
                    rawHashtag = cleanTag,
                    rawTotalViews = matched.sumOf { it.viewsCount },
                    rawTotalReels = matched.size,
                    reels = matched
                )
            }
        }
    }

    LaunchedEffect(cleanTag) {
        loadHashtagReels()
    }

    val shareUrl = "https://playdramaflix.com/hashtag/${cleanTag.removePrefix("#")}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // 🔝 ১. টপ বার: [ ← Back ] ------ #Hashtag ------ [ Share ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = cleanTag,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Watch trending reels for $cleanTag on DramaFlix:\n$shareUrl")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Hashtag"))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // =========================================================================
            // 🏷️ ২. হ্যাশট্যাগ হেডার ব্যানার (হেডার আইকন + নাম + ভিউজ কাউন্টার)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkCardBg,
                border = BorderStroke(0.8.dp, BorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // হ্যাশট্যাগ সার্কুলার আইকন
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#",
                            color = CyanAccent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = cleanTag,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = hashtagDetail?.displayStatsSubtitle
                                ?: "👁️ ${reelsList.sumOf { it.viewsCount }} Views • ${reelsList.size} Videos",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

            // =========================================================================
            // 🎬 ৩. ৩-কলাম ভিডিও থাম্বনেল গ্রিড (PullToRefresh সহ)
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        loadHashtagReels(force = true)
                        delay(500)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && reelsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.5.dp)
                    }
                } else if (reelsList.isEmpty()) {
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
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = "No reels for $cleanTag",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Be the first to post a reel with this hashtag!",
                                color = TextMuted,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 6.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(reelsList, key = { it.id }) { reel ->
                            HashtagReelThumbnailCard(
                                reel = reel,
                                onClick = { onReelClick(reel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🔲 একক রিলস থাম্বনেল কার্ড
 */
@Composable
private fun HashtagReelThumbnailCard(
    reel: UserReelDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(4.dp))
            .background(DarkCardBg)
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
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 4.dp),
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
                text = formatCompactViews(reel.viewsCount),
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatCompactViews(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
