package com.example.ui.screens.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.EpisodeDto

private const val CHUNK_SIZE_DOWNLOAD = 50

@Composable
fun ShortsBatchDownloadSheet(
    title: String,
    episodes: List<EpisodeDto>,
    onDismiss: () -> Unit,
    onDownloadSelected: (List<EpisodeDto>) -> Unit
) {
    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val episodeChunks = remember(episodes) { episodes.chunked(CHUNK_SIZE_DOWNLOAD) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }

    val isAllSelected = remember(selectedDownloadEpisodes.size, episodes.size) {
        selectedDownloadEpisodes.size == episodes.size && episodes.isNotEmpty()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f) // 🎯 ৩ নম্বর ছবির নীল দাগ পর্যন্ত বড় হবে
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141822))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 🔝 হেডার: টাইটেল ও Close বাটন
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5))
                        }
                    }

                    Text(
                        text = "Download",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // রেঞ্জ ফিল্টার (যদি ৫০টির বেশি এপিসোড থাকে)
                    if (episodeChunks.size > 1) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(episodeChunks) { index, chunk ->
                                val start = index * CHUNK_SIZE_DOWNLOAD + 1
                                val end = start + chunk.size - 1
                                val isSelected = (index == selectedChunkIndex)

                                Text(
                                    text = "$start-$end",
                                    color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { selectedChunkIndex = index }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                    // 🔲 এক লাইনে ৬টি করে পর্ব (GridCells.Fixed(6)) এবং নিচে স্মুথ স্ক্রোল
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // 🎯 যত পর্বই থাকুক নিচে গ্রুপ হবে না, স্মুথ স্ক্রোল হবে
                    ) {
                        items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                            val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelectedForDl) Color(0xFF222B38) else Color(0xFF1E232E))
                                    .border(
                                        width = if (isSelectedForDl) 1.5.dp else 0.8.dp,
                                        color = if (isSelectedForDl) Color(0xFF00E676) else Color(0xFF2B3242),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isSelectedForDl) selectedDownloadEpisodes.remove(ep)
                                        else selectedDownloadEpisodes.add(ep)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ep.episodeNumber.toString(),
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // নিচে ডানের গোল বা সবুজ চেকমার্ক
                                if (isSelectedForDl) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .border(1.2.dp, Color(0xFF4A5568), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF222836), thickness = 0.8.dp)

                    // 🔘 [ ◯ Select All ] এবং 🟢+🔵 [ Download · Size ] বাটন
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable {
                                    if (isAllSelected) {
                                        selectedDownloadEpisodes.clear()
                                    } else {
                                        selectedDownloadEpisodes.clear()
                                        selectedDownloadEpisodes.addAll(episodes)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, if (isAllSelected) Color(0xFF00E676) else Color(0xFF8E95A5), CircleShape)
                                        .background(if (isAllSelected) Color(0xFF00E676) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAllSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                    }
                                }

                                Text("Select All", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            }

                            // 🎯 নীল ও সবুজ কম্বিনেশনের গ্রেডিয়েন্ট বাটন (Green + Blue Gradient)
                            Button(
                                onClick = {
                                    val targets = if (selectedDownloadEpisodes.isNotEmpty()) selectedDownloadEpisodes.toList()
                                    else episodes.take(1)
                                    onDownloadSelected(targets)
                                },
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.74f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF007AFF), // Vibrant Blue
                                                Color(0xFF00D166)  // Vibrant Green
                                            )
                                        )
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                                    Text(
                                        text = "Download · ${selectedDownloadEpisodes.size * 12} MB",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // নির্বাচিত পর্বের সংখ্যা
                        Text(
                            text = "${selectedDownloadEpisodes.size} episodes selected",
                            color = Color(0xFF8E95A5),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}
