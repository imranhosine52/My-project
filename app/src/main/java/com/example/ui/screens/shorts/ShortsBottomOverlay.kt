package com.example.ui.screens.shorts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onTitleClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDescExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // =========================================================================
        // 📝 টাইটেল (নীল দাগ পর্যন্ত সংক্ষেপিত ও মোর-এ সম্পূর্ণ এক্সপ্যান্ড)
        // =========================================================================
        if (isDescExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${content.title} >",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onTitleClick() }
                    )
                    Text(
                        text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        text = "Collapse",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { isDescExpanded = false }
                            .padding(top = 4.dp)
                    )
                }
            }
        } else {
            // ড্রামা টাইটেল ও পোস্টার (নির্দিষ্ট প্রস্থে সীমাবদ্ধ)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { onTitleClick() }
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

                // 🎯 আপনার নীল দাগের স্থান পর্যন্ত সীমাবদ্ধ (~৫৮% প্রস্থ)
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

            // ডেসক্রিপশন সারাংশ (More বাটন সহ)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(0.85f)
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { isDescExpanded = true }
                )
            }
        }

        // ⏳ স্লিম টাইমলাইন
        SleekOnlineTimeline(
            currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSeekStarted = onSeekStarted,
            onSeeking = onSeeking,
            onSeekFinished = onSeekFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        )

        // =========================================================================
        // 🔲 ফুল-উইথ এপিসোড বার: [ Episodes · 1/8                   ^ ]
        // (কোনো Ep লেখা থাকবে না এবং ফুলস্ক্রিন আইকন সম্পূর্ণ রিমুভ করা হয়েছে)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E222B).copy(alpha = 0.95f),
            border = BorderStroke(0.6.dp, Color(0xFF333B4A)),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable { onOpenDrawer() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 🎯 Ep শব্দ বাদ দিয়ে পরিষ্কার "Episodes · 1/8"
                Text(
                    text = "Episodes · $currentEpNum/$totalEpCount",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Open Drawer",
                    tint = Color(0xFF9AA4B5),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
