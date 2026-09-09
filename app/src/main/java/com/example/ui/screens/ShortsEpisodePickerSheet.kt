@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EpisodeDto

private const val CHUNK_SIZE = 25

@Composable
fun ShortsEpisodePickerSheet(
    title: String,
    episodes: List<EpisodeDto>,
    currentEpisodeNumber: Int,
    isVip: Boolean,
    shouldLockEpisodes: Boolean,
    onDismiss: () -> Unit,
    onSelectEpisode: (EpisodeDto) -> Unit
) {
    // পর্বগুলোকে ২৫টি করে ভাগে (Range Chunks: 1-25, 26-46) ভাগ করা
    val effectiveEpisodes = remember(episodes) {
        if (episodes.isNotEmpty()) episodes else (1..46).map { num ->
            EpisodeDto(episodeNumber = num, isLocked = num > 1)
        }
    }

    val episodeChunks = remember(effectiveEpisodes) {
        effectiveEpisodes.chunked(CHUNK_SIZE)
    }

    // ডিফল্টভাবে সক্রিয় পর্ব যে ভাগে আছে সেই ট্যাব সিলেক্ট হওয়া
    val initialChunkIndex = remember(currentEpisodeNumber, episodeChunks) {
        val idx = (currentEpisodeNumber - 1) / CHUNK_SIZE
        idx.coerceIn(0, (episodeChunks.size - 1).coerceAtLeast(0))
    }
    var selectedChunkIndex by remember { mutableIntStateOf(initialChunkIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF262933),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 🔝 ২য় ছবির হেডার (Title ও Close 'X' বাটন)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
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

            HorizontalDivider(color = Color(0xFF333845), thickness = 0.8.dp)

            // 📑 ২য় ছবির পর্বের রেঞ্জ ট্যাব: [ 1-25 ]  [ 26-46 ]
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(episodeChunks) { index, chunk ->
                    val start = index * CHUNK_SIZE + 1
                    val end = start + chunk.size - 1
                    val isSelected = (index == selectedChunkIndex)

                    Text(
                        text = "$start-$end",
                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clickable { selectedChunkIndex = index }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            // 🔲 ২য় ছবির হুবহু ৫-কলাম গ্রিড (5 Columns Square Grid)
            val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                    val isCurrent = (ep.episodeNumber == currentEpisodeNumber)
                    val isLocked = shouldLockEpisodes && ep.isLocked

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3A3F4D))
                            .clickable { onSelectEpisode(ep) },
                        contentAlignment = Alignment.Center
                    ) {
                        // এপিসোড নম্বর
                        Text(
                            text = ep.episodeNumber.toString(),
                            color = if (isCurrent) Color(0xFF00E676) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // ২য় ছবির মতো বাম কোণায় সবুজ প্লেয়িং ইকুয়ালাইজার
                        if (isCurrent) {
                            EqualizerBarsIcon(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .size(10.dp, 10.dp),
                                tint = Color(0xFF00E676)
                            )
                        } else if (isLocked) {
                            // লক করা থাকলে ডান কোণায় গোল্ডেন লক আইকন
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
