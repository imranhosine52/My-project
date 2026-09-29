package com.example.ui.screens.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/**
 * 🔝 ২ নম্বর ছবির নির্দেশনা অনুযায়ী একদম ওপরে স্ক্রিনের ফাঁকা অংশে সেট করা টপ বার:
 * (বামে [+] বাটন, মাঝখানে Follow/Trend/Popular ট্যাব, ডানে Search ও [⋮] অপশন)
 */
@Composable
fun ReelsTopNavigationBar(
    currentTabIndex: Int,
    tabTitles: List<String>,
    hasApprovedCreatorPage: Boolean,
    onTabSelected: (index: Int) -> Unit,
    onOpenCreateReel: () -> Unit,
    onSearchClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
            .statusBarsPadding() // 🎯 ওপরে স্ট্যাটাস বারের সাথে নিখুঁতভাবে এলাইন করা
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ১. বামে (+) ক্রিয়েট বাটন
            if (hasApprovedCreatorPage) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }

            // ২. মাঝখানে ৩টি ট্যাব: Follow, Trend, Popular
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // ৩-ডট শুধুমাত্র Popular ট্যাবে (ইন্ডেক্স ২) দৃশ্যমান হবে
                if (currentTabIndex == 2) {
                    IconButton(
                        onClick = onOptionsClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
