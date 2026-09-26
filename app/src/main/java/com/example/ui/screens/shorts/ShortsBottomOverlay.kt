package com.example.ui.screens.shorts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
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
import java.util.Locale

@Composable
fun ShortsBottomOverlay(
    content: ContentItemDto,
    currentEpNum: Int,
    totalEpCount: Int,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isUserSeeking: Boolean,
    seekPosition: Long,
    currentSpeedText: String = "1x",
    currentQualityText: String = "360P",
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onOpenIntroductionTab: () -> Unit,
    onOpenEpisodesTab: () -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val padCurrent = String.format(Locale.US, "%02d", currentEpNum)
    val padTotal = String.format(Locale.US, "%02d", totalEpCount)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // =========================================================================
        // 🎬 ১. ওপরের হালকা শ্যাডো অংশ (টাইটেল, ডেসক্রিপশন ও এপিসোড বার)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.60f),
                            Color.Black.copy(alpha = 0.90f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ড্রামার টাইটেল ও থাম্বনেইল
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
                    text = content.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.70f)
                )
            }

            // ডেসক্রিপশন ও More বাটন
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
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "More ⌵",
                    color = Color(0xFFE5E7EB),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 🎯 ২ নম্বর ছবির হুবহু ফুল-উইডথ [ EP03 / EP46  ⌄ ] বার
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clickable { onOpenEpisodesTab() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = "Episodes",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = "EP$padCurrent / EP$padTotal",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Open Drawer",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // =========================================================================
        // ⏳ ২. চিকন টাইমলাইন বার (এপিসোড বারের ঠিক নিচে)
        // =========================================================================
        SleekOnlineTimeline(
            currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSeekStarted = onSeekStarted,
            onSeeking = onSeeking,
            onSeekFinished = onSeekFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color.Black)
        )

        // =========================================================================
        // ⬛ ৩. আপনার ছবির মতো নিচের "সলিড কালো ব্যাকগ্রাউন্ড" অংশ [ ⏱ 1x ] [ HD 360P ]
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black) // 👈 ২ নম্বর ছবির হুবহু সলিড কালো
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ⏱️ স্পিড বাটন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSpeedClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = "Playback Speed",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = currentSpeedText,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 📺 [HD] কোয়ালিটি বাটন (যেমন: HD 360P)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onQualityClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.2.dp,
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

                    Text(
                        text = currentQualityText.replace("p", "P", ignoreCase = true),
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
