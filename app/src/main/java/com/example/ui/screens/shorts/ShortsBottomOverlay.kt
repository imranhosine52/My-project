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
    onOpenIntroductionTab: () -> Unit, // 👈 More বা টাইটেলে চাপ দিলে ডেসক্রিপশন ট্যাব ওপেন হবে
    onOpenEpisodesTab: () -> Unit,     // 👈 এপিসোড বারে চাপ দিলে এপিসোড লিস্ট ওপেন হবে
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // ড্রামা টাইটেল ও পোস্টার (নির্দিষ্ট প্রস্থে সীমাবদ্ধ, ক্লিক করলে ডেসক্রিপশন ট্যাবে নিয়ে যাবে)
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
                modifier = Modifier.fillMaxWidth(0.58f) // 🎯 নীল দাগের স্থান পর্যন্ত সীমাবদ্ধ
            )
        }

        // ডেসক্রিপশন সারাংশ (More বাটনে ক্লিক করলে সরাসরি পপ-আপের ডেসক্রিপশন ট্যাবে নিয়ে যাবে)
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

        // ⏳ স্লিম টাইমলাইন
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
        // 🔲 চিকন ও সেমি-ট্রান্সপারেন্ট গ্লাস বার: [ Episodes · 1/8               ^ ]
        // (উচ্চতা কমিয়ে ৩৪dp করা হয়েছে এবং কালো রঙের বদলে ট্রান্সপারেন্ট গ্লাস দেওয়া হয়েছে)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.14f), // 🎯 সেমি-ট্রান্সপারেন্ট যাতে পেছনের ভিডিও স্ক্রিন দেখা যায়
            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp) // 🎯 চিকন করা হয়েছে
                .clickable { onOpenEpisodesTab() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Episodes · $currentEpNum/$totalEpCount",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Open Drawer",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
