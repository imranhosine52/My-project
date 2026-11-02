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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EpisodeDto
import com.example.ui.screens.EqualizerBarsIcon
import com.example.ui.theme.GoldVip
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
 * 📑 ল্যান্ডস্কেপ প্লেয়ারের প্লেলিস্ট ও ডাউনলোড সাইড ড্রয়ার
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
    // 🎯 মার্জড বা রেঞ্জ এপিসোড শনাক্তকরণ
    val hasRangeEpisodes = remember(episodes) {
        episodes.any { it.isMergedRange || it.formattedEpisodeRange.length > 3 }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // ১. হেডার ও ক্লোজ বাটন
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (sideDrawerType == "download") "Download (${selectedDownloadEpisodes.size})" else "Episodes (${episodes.size})",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose, modifier = Modifier.size(22.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF8E95A5),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ২. ডাউনলোড মোড হলে কোয়ালিটি নির্বাচন রো (1080P, 720P, 480P)
        if (sideDrawerType == "download") {
            Spacer(modifier = Modifier.height(3.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(downloadQualityOptions) { qItem ->
                    val isSelected = (selectedDownloadQualityKey == qItem.key)
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isSelected) Color(0xFF382B17) else Color(0xFF1E2433).copy(alpha = 0.8f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFFE5B567) else Color(0xFF2D3545)
                        ),
                        modifier = Modifier.clickable { onSelectQualityKey(qItem.key) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            MiniCrownVector(tint = if (isSelected) Color(0xFFF6D38B) else Color(0xFFC49A52))
                            Text(
                                text = qItem.label,
                                color = if (isSelected) Color(0xFFF6D38B) else Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // ৩. কমপ্যাক্ট পর্ব গ্রিড (রেঞ্জ থাকলে ৩ কলাম, সিঙ্গেল হলে ৪ কলাম)
        val displayEps = episodes.ifEmpty {
            (1..30).map { EpisodeDto(episodeNumber = it, isLocked = it > 1) }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (hasRangeEpisodes) 3 else 4),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(displayEps, key = { it.episodeId }) { ep ->
                val isSelected = (ep.episodeNumber == currentEpNumber)
                val isEpLocked = shouldLockEpisodes && ep.isLocked
                val isSelectedForDl = selectedDownloadEpisodes.contains(ep)
                val isRange = ep.isMergedRange || ep.formattedEpisodeRange.length > 3
                val displayText = ep.formattedEpisodeRange

                Box(
                    modifier = Modifier
                        .aspectRatio(if (isRange) 1.25f else 1.1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (sideDrawerType == "download") {
                                if (isSelectedForDl) Color(0xFF0F3B32) else Color(0xFF1E2433).copy(alpha = 0.70f)
                            } else {
                                if (isSelected) Color(0xFF0F3B32) else Color(0xFF1E2433).copy(alpha = 0.70f)
                            }
                        )
                        .border(
                            width = 0.8.dp,
                            color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) {
                                Color(0xFF00E676).copy(alpha = 0.5f)
                            } else {
                                Color(0xFF2C3545)
                            },
                            shape = RoundedCornerShape(6.dp)
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
                    // 🏷️ মার্জড পর্বের জন্য ব্যাজ (টপ-লেফটে)
                    if (isRange) {
                        val badgeLabel = if (ep.mergedEpisodeCount > 1) "${ep.mergedEpisodeCount} IN 1" else "MULTI"
                        Surface(
                            shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 4.dp),
                            color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) {
                                Color(0xFF00E676)
                            } else {
                                Color(0xFF3A4456)
                            },
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) Color.Black else Color(0xFFDCE0E8),
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.8.dp)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = if (isRange) 2.dp else 0.dp)
                    ) {
                        Text(
                            text = displayText,
                            color = if ((sideDrawerType == "download" && isSelectedForDl) || (sideDrawerType == "playlist" && isSelected)) Color(0xFF00E676) else Color.White,
                            fontSize = if (isRange) 10.sp else 12.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                        )

                        if (sideDrawerType == "playlist" && isSelected) {
                            Spacer(modifier = Modifier.height(2.dp))
                            EqualizerBarsIcon(modifier = Modifier.size(9.dp, 6.dp), tint = Color(0xFF00E676))
                        }

                        if (isEpLocked && !isSelected && sideDrawerType == "playlist") {
                            Text(text = "VIP", color = GoldVip, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ৪. ডাউনলোড বাটন
        if (sideDrawerType == "download") {
            Spacer(modifier = Modifier.height(5.dp))
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
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                Text(
                    text = if (!isVip) "VIP 👑 Upgrade to Download" else "Download · $displaySize",
                    color = if (!isVip) Color(0xFF2B210E) else Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
