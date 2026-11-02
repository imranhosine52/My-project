package com.example.ui.screens.player.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EpisodeDto
import java.util.Locale

/**
 * 📥 সাইড ড্রয়ার কোয়ালিটি আইটেম ডেটা মডেল
 */
data class SideDrawerQualityItem(
    val key: String,
    val label: String,
    val isVipOnly: Boolean
)

private fun formatBytesDisplay(bytes: Long, isCalculating: Boolean): String {
    if (bytes <= 0L) {
        return if (isCalculating) "Calculating..." else "0 MB"
    }
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

/**
 * 📑 ছবি ১-এর হুবহু ৫-কলাম বিশিষ্ট ল্যান্ডস্কেপ প্লেলিস্ট ও ডাউনলোড সাইড ড্রয়ার
 */
@Composable
fun PlayerPlaylistDrawer(
    episodes: List<EpisodeDto>,
    currentEpNumber: Int,
    sideDrawerType: String, // "playlist" অথবা "download"
    isVip: Boolean,
    shouldLockEpisodes: Boolean,
    downloadQualityOptions: List<SideDrawerQualityItem>,
    selectedDownloadQualityKey: String,
    onSelectQualityKey: (String) -> Unit,
    selectedDownloadEpisodes: SnapshotStateList<EpisodeDto>,
    totalSelectedBytes: Long,
    isFetchingSizes: Boolean,
    onSelectEpisode: (EpisodeDto) -> Unit,
    onStartDownload: () -> Unit,
    onNavigateToVip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayEps = remember(episodes) {
        episodes.ifEmpty {
            (1..40).map { EpisodeDto(episodeNumber = it, isLocked = it > 5) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        // ১. হেডার ও ক্লোজ বাটন
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (sideDrawerType == "download") "Download (${selectedDownloadEpisodes.size})" else "Episodes (${displayEps.size})",
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF8E95A5),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ২. ডাউনলোড মোড হলে কোয়ালিটি নির্বাচন রো (1080P, 720P, 480P)
        if (sideDrawerType == "download") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(downloadQualityOptions) { qItem ->
                    val isSelected = (selectedDownloadQualityKey == qItem.key)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Color(0xFF382B17) else Color(0xFF1E2433),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFFE5B567) else Color(0xFF2D3545)
                        ),
                        modifier = Modifier.clickable { onSelectQualityKey(qItem.key) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MiniCrownVector(tint = if (isSelected) Color(0xFFF6D38B) else Color(0xFFC49A52))
                            Text(
                                text = qItem.label,
                                color = if (isSelected) Color(0xFFF6D38B) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // =============================================================
        // 🔲 ৩. ছবি ১-এর হুবহু ৫-কলাম বিশিষ্ট এপিসোড গ্রিড
        // =============================================================
        LazyVerticalGrid(
            columns = GridCells.Fixed(5), // 👈 ছবি ১-এর মতো ৫ কলাম
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(displayEps, key = { it.episodeId }) { ep ->
                val isSelected = (ep.episodeNumber == currentEpNumber)
                val isEpLocked = shouldLockEpisodes && ep.isLocked
                val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                val isRange = ep.isMergedRange || ep.formattedEpisodeRange.length > 3
                val displayText = if (isRange) ep.formattedEpisodeRange else ep.episodeNumber.toString()

                // ছবি ১-এর স্টাইল: গাঢ় স্লেট/ডার্ক ব্যাকগ্রাউন্ড কার্ড
                Box(
                    modifier = Modifier
                        .aspectRatio(1f) // ছবি ১-এর মতো বর্গাকার কার্ড
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                sideDrawerType == "download" && isSelectedForDl -> Color(0xFF0F3B32)
                                sideDrawerType == "playlist" && isSelected -> Color(0xFF1A222D)
                                else -> Color(0xFF1E222B) // ছবি ১-এর ব্যাকগ্রাউন্ড
                            }
                        )
                        .border(
                            width = 0.8.dp,
                            color = when {
                                sideDrawerType == "download" && isSelectedForDl -> Color(0xFF00E676)
                                sideDrawerType == "playlist" && isSelected -> Color(0xFF00E676).copy(alpha = 0.6f)
                                else -> Color(0xFF282E3A)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (sideDrawerType == "download") {
                                if (isSelectedForDl) selectedDownloadEpisodes.remove(ep)
                                else selectedDownloadEpisodes.add(ep)
                            } else {
                                onSelectEpisode(ep)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ১. উপরে এপিসোড নম্বর (ছবি ১ অনুযায়ী অ্যাক্টিভ হলে উজ্জ্বল সবুজ)
                        Text(
                            text = displayText,
                            color = when {
                                sideDrawerType == "playlist" && isSelected -> Color(0xFF00E676) // 👈 ছবি ১-এ '2' সবুজ
                                sideDrawerType == "download" && isSelectedForDl -> Color(0xFF00E676)
                                else -> Color.White
                            },
                            fontSize = if (isRange) 11.5.sp else 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        // ২. ছবি ১-এর হুবহু নিচের "VIP" ট্যাগ (শুধুমাত্র লক করা পর্বগুলোতে)
                        if (isEpLocked && sideDrawerType == "playlist") {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "VIP",
                                color = Color(0xFFC99757), // ছবি ১-এর গোল্ডেন ব্রাউন টেক্সট
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        } else if (isRange) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "MULTI",
                                color = Color(0xFF00E5FF),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // ডাউনলোড মোডে সিলেক্টেড চেকমার্ক
                    if (sideDrawerType == "download" && isSelectedForDl) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }
            }
        }

        // ৪. ডাউনলোড বাটন
        if (sideDrawerType == "download") {
            Spacer(modifier = Modifier.height(8.dp))
            val displaySize = formatBytesDisplay(totalSelectedBytes, isFetchingSizes && totalSelectedBytes == 0L)

            Button(
                onClick = {
                    if (!isVip) {
                        onNavigateToVip()
                    } else {
                        onStartDownload()
                    }
                },
                enabled = selectedDownloadEpisodes.isNotEmpty() || !isVip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isVip) Color(0xFFE5B567) else Color(0xFF00D26A)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Text(
                    text = if (!isVip) "VIP 👑 Upgrade to Download" else "Download · $displaySize",
                    color = if (!isVip) Color(0xFF2B210E) else Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
