package com.example.ui.screens.reels.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlin.math.abs

/**
 * 🔝 টপ নেভিগেশন বার:
 * (ট্যাবের পেছনে ডিপ ডার্ক ব্যাকগ্রাউন্ড নিশ্চিত করা হয়েছে যাতে যেকোনো ভিডিওর ওপরে পরিষ্কার দেখা যায়)
 */
@Composable
fun ReelsTopNavigationBar(
    currentTabIndex: Int,
    pagerOffsetFraction: Float = 0f,
    tabTitles: List<String>,
    isVisible: Boolean = true,
    hasApprovedCreatorPage: Boolean = false,
    onBackClick: () -> Unit,
    onOpenCreateReel: () -> Unit = {},
    onTabSelected: (index: Int) -> Unit,
    onSearchClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 🎯 ট্যাবের নিচে ডিপ ডার্ক গ্রেডিয়েন্ট ব্যাকগ্রাউন্ড
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.96f),
                            Color.Black.copy(alpha = 0.80f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(top = 4.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ১. বামে হোমে ফেরার [← Back] বাটন ও [+] বাটন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (hasApprovedCreatorPage) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { onOpenCreateReel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .border(1.4.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create Reel",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // ২. মাঝখানে ৩টি ট্যাব: Follow, Trend, Popular (স্মুথ লাইটিং ট্রানজিশন)
                val currentDragPosition = currentTabIndex + pagerOffsetFraction

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    tabTitles.forEachIndexed { index, tabName ->
                        val distance = abs(currentDragPosition - index).coerceIn(0f, 1f)
                        val textAlpha = 1.0f - (distance * 0.45f)
                        val indicatorWidth = (22 * (1f - distance * 1.5f)).coerceAtLeast(0f).dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onTabSelected(index) }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = tabName,
                                color = Color.White.copy(alpha = textAlpha),
                                fontSize = if (distance < 0.3f) 16.sp else 14.5.sp,
                                fontWeight = if (distance < 0.3f) FontWeight.Black else FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.5.dp))
                            Box(
                                modifier = Modifier
                                    .width(indicatorWidth)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = (1f - distance).coerceIn(0f, 1f)))
                            )
                        }
                    }
                }

                // ৩. ডানে সার্চ ও ৩-ডট মেনু
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (currentTabIndex == 2) {
                        IconButton(
                            onClick = onOptionsClick,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
    }
}
