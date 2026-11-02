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

        // [ 01 ], [ 02 ] বা [ 01-10 ] এপিসোড বাটনসমূহ
        items(episodes.size) { index ->
            val ep = episodes[index]
            val isSelected = currentEpNumber == ep.episodeNumber
            val isEpLocked = shouldLockEpisodes && ep.isLocked

            // 🎯 সার্ভার থেকে আসা ০১-১০ বা ১-১০ ডায়নামিক রেঞ্জ টেক্সট
            val displayText = ep.formattedEpisodeRange
            val isRange = displayText.length > 3
            val buttonWidth = if (isRange) 76.dp else 58.dp

            Box(
                modifier = Modifier
                    .size(width = buttonWidth, height = 46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            // সিলেক্টেড হলে শুধু সফট ডার্ক-গ্রিন ব্যাকগ্রাউন্ড শেড থাকবে
                            Brush.verticalGradient(listOf(Color(0xFF0F3B32), Color(0xFF0B2C25)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF222630), Color(0xFF222630)))
                        }
                    )
                    // 🎯 সবুজ বর্ডার লাইন সম্পূর্ণ রিমুভ করে সাধারণ ক্লিন ডার্ক বর্ডার রাখা হয়েছে
                    .border(
                        width = 0.8.dp,
                        color = Color(0xFF333A4A),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onEpisodeSelect(ep) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayText,
                        color = if (isSelected) Color(0xFF00E676) else Color(0xFFDCE0E8),
                        fontSize = if (isRange) 12.sp else 13.5.sp, // রেঞ্জ থাকলে লেখা যেন সুন্দর ফিট হয়
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
