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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 🎯 উপরে বেশি কালো দেখা যাবে না, পেছনের ভিডিও স্ক্রিন হালকা ট্রান্সপারেন্ট দেখা যাবে
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // 🎯 ২ নম্বর ছবির মতো পারফেক্ট উচ্চতা (৮০% স্ক্রিন)
                    .fillMaxHeight(0.80f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF20232A)) // ২ নম্বর ছবির ডার্ক কালার
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // 🎯 নিচের বাটন যেন স্ক্রিনের বাইরে বা নেভিগেশন বারের নিচে না যায়
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 🔝 হেডার: ড্রামা টাইটেল ও Close (X) বাটন
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF9E9EA7)
                            )
                        }
                    }

                    // পাতলা ডিভাইডার লাইন
                    HorizontalDivider(color = Color(0xFF2E323C), thickness = 0.8.dp)

                    Text(
                        text = "Download",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // রেঞ্জ ফিল্টার (৫০টির বেশি পর্ব থাকলে)
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

                    // 🔲 ২ নম্বর ছবির মতো ৫-কলাম গ্রিড (GridCells.Fixed(5))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                            val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelectedForDl) Color(0xFF2C323D) else Color(0xFF353942)
                                    )
                                    .border(
                                        width = if (isSelectedForDl) 1.2.dp else 0.dp,
                                        color = if (isSelectedForDl) Color(0xFF00E676) else Color.Transparent,
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
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // নিচে ডানের সিলেক্টেড গোল আইকন (২ নম্বর ছবির হুবহু ডিজাইন)
                                if (isSelectedForDl) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .border(1.2.dp, Color(0xFF5A6272), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // =========================================================================
                    // 🔘 বটম বার: [Select All] এবং [ Download · Size ] বাটন
                    // =========================================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Select All চেকবক্স
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clickable {
                                        if (isAllSelected) {
                                            selectedDownloadEpisodes.clear()
                                        } else {
                                            selectedDownloadEpisodes.clear()
                                            selectedDownloadEpisodes.addAll(episodes)
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(
                                            1.5.dp,
                                            if (isAllSelected) Color(0xFF00E676) else Color(0xFF717886),
                                            CircleShape
                                        )
                                        .background(if (isAllSelected) Color(0xFF00E676) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAllSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Select All",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // 🎯 ২ নম্বর ছবির হুবহু ব্লু + গ্রিন গ্রেডিয়েন্ট বাটন
                            Button(
                                onClick = {
                                    val targets = if (selectedDownloadEpisodes.isNotEmpty()) {
                                        selectedDownloadEpisodes.toList()
                                    } else {
                                        episodes.take(1)
                                    }
                                    onDownloadSelected(targets)
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.76f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF0088FF), // Vibrant Blue
                                                Color(0xFF00D26A)  // Vivid Green
                                            )
                                        )
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    val totalMb = String.format(java.util.Locale.US, "%.1f", selectedDownloadEpisodes.size * 10.8)
                                    Text(
                                        text = "Download · ${totalMb}MB",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // সিলেক্টেড পর্বের সংখ্যা (নিচে সেন্টারে থাকবে এবং কখনোই স্ক্রিনের নিচে কাটবে না)
                        Text(
                            text = "${selectedDownloadEpisodes.size} episodes selected",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
