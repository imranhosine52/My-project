@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels.components

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPlaylistDto
import com.example.data.model.UserReelDto
import java.util.Locale

private val SheetBg = Color(0xFF141720)
private val CardDark = Color(0xFF262C38)
private val ActiveEpisodeRed = Color(0xFFE11D48)
private val BorderColor = Color(0xFF2B3446)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 📺 ২ নম্বর ছবির হুবহু শর্ট-ড্রামা সিরিজ ও পর্ব সিলেকশন বটম শিট
 */
@Composable
fun PlaylistEpisodesBottomSheet(
    seriesTitle: String,
    currentReelId: Int,
    episodes: List<UserReelDto>,
    creatorPlaylists: List<CreatorPlaylistDto> = emptyList(),
    isLoading: Boolean = false,
    isFavorite: Boolean = true,
    onToggleFavorite: () -> Unit = {},
    onEpisodeClick: (UserReelDto) -> Unit,
    onSelectOtherPlaylist: (CreatorPlaylistDto) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // পর্বগুলোকে ২৪টি করে ট্যাবে ভাগ করা (যেমন: 1-24, 25-48)
    val chunkSize = 24
    val episodeChunks = remember(episodes) {
        if (episodes.isEmpty()) emptyList()
        else episodes.chunked(chunkSize)
    }

    // কোন ট্যাবে কারেন্ট পর্বটি আছে তা নির্ধারণ
    val initialTabIndex = remember(episodes, currentReelId) {
        val curIdx = episodes.indexOfFirst { it.id == currentReelId }
        if (curIdx != -1) (curIdx / chunkSize).coerceIn(0, (episodeChunks.size - 1).coerceAtLeast(0)) else 0
    }

    var selectedChunkIndex by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
        ) {
            // =========================================================================
            // 🔝 ১. হেডার বার: [ ✕ ] ---- ড্রামার নাম ---- [ ↗ Share ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Text(
                    text = seriesTitle.ifBlank { "Mini-Drama Series" },
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Watch drama $seriesTitle on PlayDramaFlix!")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Series"))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

            // =========================================================================
            // 📜 ২. মূল স্ক্রোলযোগ্য অংশ (পর্ব গ্রিড + সাজেস্টেড সিরিজ)
            // =========================================================================
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ক) পর্ব রেঞ্জ ট্যাব সিলেক্টর (যেমন: [ 1-24 ]  [ 25-39 ])
                if (episodeChunks.size > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF090C13))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            episodeChunks.forEachIndexed { idx, chunk ->
                                val startEp = idx * chunkSize + 1
                                val endEp = startEp + chunk.size - 1
                                val isTabSelected = (selectedChunkIndex == idx)

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isTabSelected) CardDark else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedChunkIndex = idx }
                                ) {
                                    Text(
                                        text = "$startEp-$endEp",
                                        color = if (isTabSelected) Color.White else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // খ) ২ নম্বর ছবির মতো ৬-কলামের স্কয়ার পর্ব বোতাম গ্রিড
                item {
                    val currentVisibleEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { episodes }

                    if (isLoading && episodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ActiveEpisodeRed, strokeWidth = 2.dp)
                        }
                    } else if (currentVisibleEpisodes.isEmpty()) {
                        Text(
                            text = "No episodes uploaded yet.",
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6), // 👈 ২ নম্বর ছবির হুবহু ৬-কলাম
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(currentVisibleEpisodes, key = { it.id }) { ep ->
                                val isCurrentPlaying = (ep.id == currentReelId)
                                val epNum = ep.episodeNum

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrentPlaying) ActiveEpisodeRed else CardDark,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable {
                                            onEpisodeClick(ep)
                                            onDismiss()
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isCurrentPlaying) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text("ılı", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text("$epNum", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                                            }
                                        } else {
                                            Text(
                                                text = "$epNum",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // গ) ২ নম্বর ছবির নিচের অংশ: "More Series by Creator / Suggested"
                if (creatorPlaylists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "More Series by Creator",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(creatorPlaylists) { pl ->
                                Column(
                                    modifier = Modifier
                                        .width(115.dp)
                                        .clickable {
                                            onSelectOtherPlaylist(pl)
                                            onDismiss()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.70f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CardDark)
                                            .border(0.6.dp, BorderColor, RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(pl.effectivePoster ?: pl.effectiveBanner)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = pl.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                        ) {
                                            Text(
                                                text = "🔥 ${formatViewsCompact(pl.totalViews)}",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = pl.title,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Text(
                                        text = "${pl.totalEpisodes} Episodes",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 🔖 ৩. নিচে ২ নম্বর ছবির মতো স্টিকি "Added to Favorites" বাটন
            // =========================================================================
            Surface(
                color = SheetBg,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Button(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFavorite) CardDark else ActiveEpisodeRed
                        ),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = if (isFavorite) Color.White else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = if (isFavorite) "Added to Favorites" else "Add to Favorites",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatViewsCompact(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
