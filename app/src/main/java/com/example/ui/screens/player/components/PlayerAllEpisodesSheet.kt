package com.example.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EpisodeDto
import com.example.ui.screens.EqualizerBarsIcon
import com.example.ui.theme.GoldVip

@Composable
fun PlayerAllEpisodesSheet(
    episodes: List<EpisodeDto>,
    currentEpNumber: Int,
    shouldLockEpisodes: Boolean,
    onClose: () -> Unit,
    onSelectEpisode: (EpisodeDto) -> Unit,
    modifier: Modifier = Modifier
) {
    // 🎯 মার্জড বা রেঞ্জ এপিসোড আছে কিনা চেক করা
    val hasRangeEpisodes = remember(episodes) {
        episodes.any { it.isMergedRange || it.formattedEpisodeRange.length > 3 }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141720))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ১. হেডার
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All episodes (${episodes.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5))
                }
            }

            HorizontalDivider(color = Color(0xFF222836), thickness = 0.8.dp)

            // ২. ডাইনামিক এপিসোড গ্রিড (রেঞ্জ থাকলে ৪ কলাম, সিঙ্গেল হলে ৬ কলাম)
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (hasRangeEpisodes) 4 else 6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(episodes, key = { it.episodeId }) { ep ->
                    val isSelected = currentEpNumber == ep.episodeNumber
                    val isEpLocked = shouldLockEpisodes && ep.isLocked
                    val isRange = ep.isMergedRange || ep.formattedEpisodeRange.length > 3
                    val displayText = ep.formattedEpisodeRange

                    Box(
                        modifier = Modifier
                            .aspectRatio(if (isRange) 1.25f else 1.05f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(listOf(Color(0xFF0F3B32), Color(0xFF0B2C25)))
                                } else {
                                    Brush.linearGradient(listOf(Color(0xFF222630), Color(0xFF222630)))
                                }
                            )
                            .border(
                                width = if (isSelected) 1.2.dp else 0.8.dp,
                                color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFF333A4A),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectEpisode(ep) },
                        contentAlignment = Alignment.Center
                    ) {
                        // 🏷️ মার্জড পর্বের জন্য ছোট ব্যাজ
                        if (isRange) {
                            val badgeLabel = if (ep.mergedEpisodeCount > 1) "${ep.mergedEpisodeCount} IN 1" else "MULTI"
                            Surface(
                                shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 4.dp),
                                color = if (isSelected) Color(0xFF00E676) else Color(0xFF3A4456),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    color = if (isSelected) Color.Black else Color(0xFFDCE0E8),
                                    fontSize = 6.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // এপিসোড সংখ্যা বা রেঞ্জ (যেমন 01-10)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = if (isRange) 3.dp else 0.dp)
                        ) {
                            Text(
                                text = displayText,
                                color = if (isSelected) Color(0xFF00E676) else Color(0xFFDCE0E8),
                                fontSize = if (isRange) 11.5.sp else 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                            )

                            if (isSelected) {
                                Spacer(modifier = Modifier.height(2.dp))
                                EqualizerBarsIcon(
                                    modifier = Modifier.size(10.dp, 7.dp),
                                    tint = Color(0xFF00E676)
                                )
                            }
                        }

                        // লক আইকন
                        if (isEpLocked && !isSelected) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = GoldVip,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(9.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
