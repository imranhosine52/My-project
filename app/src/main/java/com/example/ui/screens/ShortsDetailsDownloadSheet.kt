@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.data.model.EpisodeDto

private const val CHUNK_SIZE = 25

@Composable
fun ShortsDetailsDownloadSheet(
    content: ContentItemDto,
    episodes: List<EpisodeDto>,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onContinuePlay: () -> Unit,
    onToggleBookmark: () -> Unit,
    onStartBatchDownload: (List<EpisodeDto>) -> Unit
) {
    val context = LocalContext.current
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val effectiveEpisodes = remember(episodes) {
        if (episodes.isNotEmpty()) episodes else (1..46).map { num ->
            EpisodeDto(episodeNumber = num, isLocked = num > 1)
        }
    }

    val episodeChunks = remember(effectiveEpisodes) {
        effectiveEpisodes.chunked(CHUNK_SIZE)
    }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }

    // ডাউনলোড করার জন্য নির্বাচিত পর্বসমূহ
    val selectedEpisodesForDownload = remember { mutableStateListOf<EpisodeDto>() }

    val isAllSelected = remember(selectedEpisodesForDownload.size, effectiveEpisodes.size) {
        selectedEpisodesForDownload.size == effectiveEpisodes.size && effectiveEpisodes.isNotEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF24272E),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 🔝 হেডার (Title ও Close 'X')
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = content.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF9AA4B5),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // স্ক্রোলযোগ্য কনটেন্ট অংশ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =========================================================================
                // 🖼️ ৩য় ছবির পোস্টার ও মেটাডাটা ব্লক
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // পোস্টার থাম্বনেইল
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .height(118.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161A22))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(content.posterUrl ?: content.bannerUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = content.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // টাইটেল ও ক্যাটাগরি ট্যাগস
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = content.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // ৩য় ছবির ক্যাটাগরি ট্যাগস রো
                        val tags = remember(content) {
                            if (content.categories.isNotEmpty()) content.categories
                            else listOf("Contract Marriage", "Rivalry", "Artificial Intelligence", "Second Chance Rom...", "Fake Relationship")
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(tags) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF2C2216),
                                    border = BorderStroke(0.6.dp, Color(0xFF6B4E1B))
                                ) {
                                    Text(
                                        text = tag,
                                        color = Color(0xFFE5A84B),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${content.releaseYear.ifBlank { "2026" }} • ${content.type.replaceFirstChar { it.uppercase() }} • ${content.country}",
                            color = Color(0xFF8E95A5),
                            fontSize = 11.sp
                        )
                    }
                }

                // =========================================================================
                // 🔘 ৩য় ছবির অ্যাকশন বাটন রো: [ ▶ Continue ] [ 🔖 Bookmark ] [ 📤 Share ]
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ▶ Continue বাটন (সাদা ব্যাকগ্রাউন্ড)
                    Button(
                        onClick = onContinuePlay,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1.8f)
                            .height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Continue",
                                color = Color.Black,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 🔖 Bookmark বাটন
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2D323E),
                        border = BorderStroke(0.8.dp, Color(0xFF434A5C)),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { onToggleBookmark() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color(0xFF00E676) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 📤 Share বাটন
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2D323E),
                        border = BorderStroke(0.8.dp, Color(0xFF434A5C)),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable {
                                val shareUrl = "https://playdramaflix.com/watch/${content.slug}"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Watch ${content.title} on PlayDramaFlix: $shareUrl")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Drama"))
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // =========================================================================
                // 📝 Info / ডেসক্রিপশন সেকশন
                // =========================================================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Info",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                        color = Color(0xFFCCD0DB),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isDescriptionExpanded) "Less" else "More",
                            color = Color(0xFF8E95A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF8E95A5),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // =========================================================================
                // 📥 Download / ব্যাচ ডাউনলোড সেকশন (রেঞ্জ ট্যাব ও মাল্টি-সিলেক্ট গ্রিড)
                // =========================================================================
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Download",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // রেঞ্জ ট্যাব [ 1-25 ]  [ 26-46 ]
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(episodeChunks) { index, chunk ->
                            val start = index * CHUNK_SIZE + 1
                            val end = start + chunk.size - 1
                            val isSelected = (index == selectedChunkIndex)

                            Text(
                                text = "$start-$end",
                                color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { selectedChunkIndex = index }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    // ৫-কলামের পর্ব নির্বাচন গ্রিড
                    val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                            val isSelectedForDl = selectedEpisodesForDownload.contains(ep)

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelectedForDl) Color(0xFF1D382B) else Color(0xFF3A3F4D))
                                    .border(
                                        width = if (isSelectedForDl) 1.5.dp else 0.dp,
                                        color = if (isSelectedForDl) Color(0xFF00E676) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isSelectedForDl) {
                                            selectedEpisodesForDownload.remove(ep)
                                        } else {
                                            selectedEpisodesForDownload.add(ep)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ep.episodeNumber.toString(),
                                    color = if (isSelectedForDl) Color(0xFF00E676) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isSelectedForDl) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF333845), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 8.dp))

            // =========================================================================
            // 🟢 ৩য় ছবির বটম ডাউনলোড বাটন রো: [ ◯ Select All ] ও [ 📥 Download ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ◯ Select All টগল
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        if (isAllSelected) {
                            selectedEpisodesForDownload.clear()
                        } else {
                            selectedEpisodesForDownload.clear()
                            selectedEpisodesForDownload.addAll(effectiveEpisodes)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, if (isAllSelected) Color(0xFF00E676) else Color(0xFF8E95A5), CircleShape)
                            .background(if (isAllSelected) Color(0xFF00E676) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAllSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Text(
                        text = "Select All",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 📥 বড় সবুজ ডাউনলোড বাটন
                Button(
                    onClick = {
                        val targets = if (selectedEpisodesForDownload.isNotEmpty()) {
                            selectedEpisodesForDownload.toList()
                        } else {
                            effectiveEpisodes.take(1) // কিছুই সিলেক্ট না করলে বর্তমান/১ম পর্ব
                        }
                        onStartBatchDownload(targets)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D166)),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (selectedEpisodesForDownload.isNotEmpty()) "Download (${selectedEpisodesForDownload.size})" else "Download",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
