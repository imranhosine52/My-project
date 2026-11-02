package com.example.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun PlayerEpisodesRow(
    episodes: List<EpisodeDto>,
    currentEpNumber: Int,
    shouldLockEpisodes: Boolean,
    onAllClick: () -> Unit,
    onEpisodeSelect: (EpisodeDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [ All ] বাটন
        item {
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222630))
                    .border(0.8.dp, Color(0xFF333A4A), RoundedCornerShape(8.dp))
                    .clickable { onAllClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "All",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // [ 01 ], [ 02 ] অথবা মার্জড [ 01-10 ] এপিসোড বাটনসমূহ
        items(episodes.size) { index ->
            val ep = episodes[index]
            val isSelected = currentEpNumber == ep.episodeNumber
            val isEpLocked = shouldLockEpisodes && ep.isLocked

            // 🎯 সার্ভার থেকে আসা ০১-১০ বা ১-১০ রেঞ্জ
            val isRange = ep.isMergedRange || ep.formattedEpisodeRange.length > 3
            val displayText = ep.formattedEpisodeRange
            val buttonWidth = if (isRange) 80.dp else 58.dp

            Box(
                modifier = Modifier
                    .size(width = buttonWidth, height = 46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            // সিলেক্টেড হলে সফট ডার্ক-গ্রিন ব্যাকগ্রাউন্ড
                            Brush.verticalGradient(listOf(Color(0xFF0F3B32), Color(0xFF0B2C25)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF222630), Color(0xFF222630)))
                        }
                    )
                    .border(
                        width = 0.8.dp,
                        color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFF333A4A),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onEpisodeSelect(ep) },
                contentAlignment = Alignment.Center
            ) {
                // 🏷️ মার্জড এপিসোড বোঝানোর জন্য ছোট সূক্ষ্ম ব্যাজ (টপ-লেফটে)
                if (isRange) {
                    val countBadgeText = if (ep.mergedEpisodeCount > 1) "${ep.mergedEpisodeCount} IN 1" else "MULTI"
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 5.dp),
                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF3A4456),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = countBadgeText,
                            color = if (isSelected) Color.Black else Color(0xFFDCE0E8),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // মূল লেখা: 01-10 বা 01
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = if (isRange) 4.dp else 0.dp)
                ) {
                    Text(
                        text = displayText,
                        color = if (isSelected) Color(0xFF00E676) else Color(0xFFDCE0E8),
                        fontSize = if (isRange) 12.sp else 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.height(2.dp))
                        EqualizerBarsIcon(
                            modifier = Modifier.size(11.dp, 7.dp),
                            tint = Color(0xFF00E676)
                        )
                    }
                }

                // লক করা থাকলে ডান কোণায় লক আইকন
                if (isEpLocked && !isSelected) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = GoldVip,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(10.dp)
                    )
                }
            }
        }
    }
}
