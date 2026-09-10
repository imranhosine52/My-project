package com.example.ui.screens.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // ড্রামা টাইটেল ও পোস্টার
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onOpenDrawer() }
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
                overflow = TextOverflow.Ellipsis
            )
        }

        // ডেসক্রিপশন সারাংশ (More সহ)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clickable { onOpenDrawer() }
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
            Text("More", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }

        // ⏳ স্লিম টাইমলাইন বার
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

        // 🔲 ৪ নম্বর ছবির বটম ড্রয়ার ট্রিগার বার ([Episodes 1/59 ^ ⛶])
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E222B).copy(alpha = 0.9f))
                .clickable { onOpenDrawer() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Episodes  $currentEpNum/$totalEpCount",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Open Drawer", tint = Color(0xFF9AA4B5), modifier = Modifier.size(20.dp))
                
                // ⛶ ৪ নম্বর ছবির ফুলস্ক্রিন আইকন (ক্লিক করলে সব হাইড হয়ে ফুলস্ক্রিন হবে)
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Fullscreen",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onToggleFullscreen() }
                )
            }
        }
    }
}
