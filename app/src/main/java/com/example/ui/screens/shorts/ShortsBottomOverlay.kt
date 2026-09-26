package com.example.ui.screens.shorts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentItemDto
import com.example.ui.screens.SleekOnlineTimeline

@Composable
fun ShortsBottomOverlay(
    content: ContentItemDto,
    currentEpNum: Int,
    totalEpCount: Int,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isUserSeeking: Boolean,
    seekPosition: Long,
    currentSpeedText: String = "1x",       // 👈 যেমন: "1x", "1.25x", "1.5x", "2x"
    currentQualityText: String = "720P",   // 👈 যেমন: "720P", "480P", "360P", "Auto"
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onOpenIntroductionTab: () -> Unit,
    onOpenEpisodesTab: () -> Unit,
    onSpeedClick: () -> Unit,              // 👈 স্পিড চেঞ্জ করার বটম শীট ওপেন করবে
    onQualityClick: () -> Unit,            // 👈 কোয়ালিটি চেঞ্জ করার বটম শীট ওপেন করবে
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.50f),
                        Color.Black.copy(alpha = 0.95f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // ১. ড্রামার টাইটেল ও থাম্বনেইল
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onOpenIntroductionTab() }
        ) {
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = content.posterUrl ?: content.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = "${content.title} >",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.58f)
            )
        }

        // ২. ডেসক্রিপশন ও More বাটন
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable { onOpenIntroductionTab() }
        ) {
            Text(
                text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                color = Color(0xFFD1D5DB),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "More",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // =========================================================================
        // ⏳ ৩. স্লিম টাইমলাইন বার (আপনার ছবির উপরের চিকন দাগটি)
        // =========================================================================
        SleekOnlineTimeline(
            currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSeekStarted = onSeekStarted,
            onSeeking = onSeeking,
            onSeekFinished = onSeekFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        )

        // =========================================================================
        // 🌟 ৪. নিচের রো: বামে [ Episodes · 1/8 ^ ] এবং ডানে ছবির হুবহু [ ⏱ 1x ] [ HD 720P ]
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 👈 বামে: সেমি-ট্রান্সপারেন্ট Episodes বাটন
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.14f),
                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier
                    .height(32.dp)
                    .clickable { onOpenEpisodesTab() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Episodes · $currentEpNum/$totalEpCount",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Open Episodes Drawer",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 👉 ডানে: আপনার স্ক্রিনশটের হুবহু ডিজাইন [ (Icon) 1x ] এবং [ [HD] 720P ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                // ⏱️ স্পিডোমিটার আইকন + 1x বাটন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSpeedClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = "Playback Speed",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = currentSpeedText,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 📺 [HD] 720P বাটন (ছবির মতো রেক্টাঙ্গুলার বর্ডার বক্স সহ HD টেক্সট)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onQualityClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    // [HD] বক্স আইকন
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.3.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 0.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "HD",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 10.sp
                        )
                    }

                    // কোয়ালিটি টেক্সট (যেমন: 720P)
                    Text(
                        text = currentQualityText,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
