package com.example.ui.screens.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
    currentQualityText: String = "720P",
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
    val episodeDisplayText = "$padCurrent/$padTotal"

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // =========================================================================
        // 🎬 ১. ওপরের অংশ: সরাসরি ভিডিওর ওপর থাকবে (কোনো কালো ব্লক থাকবে না)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ড্রামার টাইটেল ও পোস্টার
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
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.75f)
                )
            }

            // ডেসক্রিপশন ও More
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
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
        }

        // =========================================================================
        // ⏳ ২. আপনার নির্দেশিত টাইমলাইনের নীল দাগটি
        // =========================================================================
        SleekOnlineTimeline(
            currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSeekStarted = onSeekStarted,
            onSeeking = onSeeking,
            onSeekFinished = onSeekFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )

        // =========================================================================
        // ⬛ ৩. দাগের নিচ থেকে সম্পূর্ণ সলিড কালো ব্যাকগ্রাউন্ড (কোনো ফাঁকা থাকবে না)
        // =========================================================================
        Surface(
            color = Color.Black, // 👈 নিচ দিয়ে কোনো ভিডিও লিক হবে না
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding() // নেভিগেশন বার পর্যন্ত কালো থাকবে
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 👈 বাঁয়ে: "01/82 ⌄"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenEpisodesTab() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = episodeDisplayText,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Open Episodes Drawer",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 👉 ডানে: ⏱ 1x এবং [HD] 720P
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // স্পিড বাটন
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
                            contentDescription = "Speed",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentSpeedText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // কোয়ালিটি বাটন
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onQualityClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
