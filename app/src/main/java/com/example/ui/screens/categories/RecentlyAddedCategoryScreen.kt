package com.example.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContentItemDto
import com.example.ui.screens.CategoryGridDramaCard
import com.example.ui.theme.BackgroundDark

/**
 * 🎬 ২. Recently Added ক্যাটাগরি স্ক্রিন
 * (নতুন যুক্ত হওয়া ড্রামা ও পর্বগুলোর প্রিমিয়াম ৩-কলাম গ্রিড পেজ)
 */
@Composable
fun RecentlyAddedCategoryScreen(
    dramas: List<ContentItemDto>,
    statusBarTop: Dp,
    onDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (dramas.isEmpty()) {
            // ফাঁকা থাকলে সুন্দর এম্পটি স্টেট
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop + 94.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(46.dp)
                    )
                    Text(
                        text = "No recent dramas found",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "New episodes will be listed here as soon as they release.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // ৩-কলামের পারফেক্ট গ্রিড
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = statusBarTop + 94.dp,
                    bottom = 72.dp,
                    start = 12.dp,
                    end = 12.dp
                )
            ) {
                items(dramas, key = { it.id }) { drama ->
                    CategoryGridDramaCard(
                        drama = drama,
                        onClick = { onDramaClick(drama.slug) }
                    )
                }
            }
        }
    }
}
