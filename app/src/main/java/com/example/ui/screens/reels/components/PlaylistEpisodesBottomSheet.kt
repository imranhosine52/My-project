@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
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
import com.example.ui.screens.EqualizerBarsIcon
import java.util.Locale

private val SheetBg = Color(0xFF0F131D)
private val CardDark = Color(0xFF1E2432)
private val BorderColor = Color(0xFF283144)
private val ActionGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 📺 শর্ট-ড্রামা সিরিজ ও পর্ব সিলেকশন বটম শিট
 */
@Composable
fun PlaylistEpisodesBottomSheet(
    seriesTitle: String,
    currentReelId: Int,
    episodes: List<UserReelDto>,
    creatorPlaylists: List<CreatorPlaylistDto> = emptyList(),
    isLoading: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onEpisodeClick: (UserReelDto) -> Unit,
    onSelectOtherPlaylist: (CreatorPlaylistDto) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // পর্বগুলোকে ২৪টি করে রেঞ্জে ভাগ করা (যেমন: 1-24, 25-48)
    val chunkSize = 24
    val episodeChunks = remember(episodes) {
        if (episodes.isEmpty()) emptyList()
        else episodes.chunked(chunkSize)
    }

    val initialTabIndex = remember(episodes, currentReelId) {
        val curIdx = episodes.indexOfFirst { it.id == currentReelId }
        if (curIdx != -1) (curIdx / chunkSize).coerceIn(0, (episodeChunks.size - 1).coerceAtLeast(0)) else 0
    }

    var selectedChunkIndex by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }

    // সাজেশন প্লেলিস্টগুলো প্রতি লাইনে ৪টি করে ভাগ করা
    val playlistChunksOfFour = remember(creatorPlaylists) {
        creatorPlaylists.chunked(4)
    }

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
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
            // =========================================================================
            // 🔝 ১. হেডার বার: [ ✕ ] ---- সিরিজের নাম ---- [ 🔖 Save/Bookmark ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = seriesTitle.ifBlank { "Mini-Drama" },
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    textAlign = TextAlign.Center
                )

                // 🎯 শেয়ার বাটনের বদলে এখানে ছোট সেভ/বুকমার্ক বাটন দেওয়া হলো
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save Playlist",
                        tint = if (isFavorite) ActionGreen else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

            // =========================================================================
            // 📜 ২. মূল স্ক্রোলযোগ্য অংশ
            // =========================================================================
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ক) রেঞ্জ ট্যাব [ 1-24 ]  [ 25-48 ]
                if (episodeChunks.size > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF090D14))
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
                                    border = if (isTabSelected) BorderStroke(0.8.dp, ActionGreen) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedChunkIndex = idx }
                                ) {
                                    Text(
                                        text = "$startEp-$endEp",
                                        color = if (isTabSelected) ActionGreen else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 5.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // 🔲 খ) ২ নম্বর ছবির হুবহু পর্ব গ্রিড (৬টি কলাম, 01, 02... স্টাইল)
                // =========================================================================
                item {
                    val currentVisibleEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { episodes }

                    if (isLoading && episodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.dp)
                        }
                    } else if (currentVisibleEpisodes.isEmpty()) {
                        Text(
                            text = "No episodes available.",
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6), // 👈 ৬টি কলাম
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        ) {
                            items(currentVisibleEpisodes, key = { it.id }) { ep ->
                                val isCurrentPlaying = (ep.id == currentReelId)
                                // 🎯 ২ নম্বর ছবির মতো সামনে শূন্য যোগ করা (যেমন: 01, 02, 09, 10...)
                                val formattedEpNum = String.format(Locale.US, "%02d", ep.episodeNum)

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isCurrentPlaying) Color(0xFF0F2D24) else CardDark
                                        )
                                        .border(
                                            width = if (isCurrentPlaying) 1.5.dp else 0.5.dp,
                                            color = if (isCurrentPlaying) ActionGreen else BorderColor,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            onEpisodeClick(ep)
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = formattedEpNum,
                                            color = if (isCurrentPlaying) ActionGreen else Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isCurrentPlaying) FontWeight.Black else FontWeight.Bold
                                        )

                                        // 🎯 ২ নম্বর ছবির মতো রানিং পর্বে নম্বরের নিচে ইকুয়ালাইজার বার
                                        if (isCurrentPlaying) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            EqualizerBarsIcon(
                                                modifier = Modifier.size(11.dp, 7.dp),
                                                tint = ActionGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // 🎬 গ) "More Series by Creator" (প্রতি সারিতে ৪টি করে কার্ড, আগুন ইমোজি ছাড়া)
                // =========================================================================
                if (creatorPlaylists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "More Series by Creator",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // প্রতি লাইনে ৪টি করে কার্ড
                    items(playlistChunksOfFour) { rowCards ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowCards.forEach { pl ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onSelectOtherPlaylist(pl)
                                            onDismiss()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.70f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CardDark)
                                            .border(0.6.dp, BorderColor, RoundedCornerShape(6.dp))
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
                                        // 🎯 আগুন ইমোজি ও 0 ভিউজের ব্যাজটি সম্পূর্ণ মুছে দেওয়া হয়েছে
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = pl.title,
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pl.totalEpisodes} Episodes",
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            // লাইনে ৪টির কম কার্ড থাকলে খালি জায়গা পূরণ
                            repeat(4 - rowCards.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
