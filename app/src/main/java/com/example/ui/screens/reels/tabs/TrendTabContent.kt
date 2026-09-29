package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserReelDto

/**
 * 🎬 ট্রেন্ড ট্যাবের সম্পূর্ণ ২-কলাম গ্রিড স্ক্রিন
 */
@Composable
fun TrendTabContent(
    trendReels: List<UserReelDto>,
    safeTopPadding: Dp,
    onReelClick: (UserReelDto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (trendReels.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No trending reels available",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
                start = 4.dp,
                end = 4.dp,
                top = safeTopPadding, // 🎯 টপ বারের নিচে মার্জিন নিশ্চিত করে ওভারল্যাপ রোধ
                bottom = 86.dp
            ),
            modifier = modifier.fillMaxSize()
        ) {
            itemsIndexed(trendReels, key = { idx, reel -> "trend_item_${reel.id}_$idx" }) { _, reel ->
                Trend2ColumnVideoCard(
                    reel = reel,
                    onClick = { onReelClick(reel) }
                )
            }
        }
    }
}
