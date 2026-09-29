package com.example.ui.screens.reels.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

/**
 * 🔝 টপ নেভিগেশন বার:
 * (বামে হোমস্ক্রিনে ফেরার [← Back] আইকন, মাঝে ৩টি ট্যাব এবং ডানে Search ও ⋮ মেনু)
 */
@Composable
fun ReelsTopNavigationBar(
    currentTabIndex: Int,
    tabTitles: List<String>,
    isVisible: Boolean, // 🎯 কমেন্ট ওপেন হলে false হয়ে স্বয়ংক্রিয়ভাবে হাইড হবে
    onBackClick: () -> Unit,
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
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.80f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ১. বামে হোমে ফিরে যাওয়ার ব্যাক অ্যারো বাটন
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // ২. মাঝখানে ৩টি ট্যাব: Follow, Trend, Popular
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    tabTitles.forEachIndexed { index, tabName ->
                        val isSelected = (currentTabIndex == index)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onTabSelected(index) }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = tabName,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                                fontSize = if (isSelected) 16.sp else 14.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.5.dp))
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 22.dp else 0.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
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
