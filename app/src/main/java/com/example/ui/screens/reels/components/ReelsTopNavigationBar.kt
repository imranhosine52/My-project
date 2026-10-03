package com.example.ui.screens.reels.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * 🔝 আল্ট্রা-স্লিম টপ নেভিগেশন বার
 * - স্ট্যাটাস বারের একদম সাথে লাগানো (উপরে কোনো অতিরিক্ত ফাঁকা জায়গা নেই)।
 * - ডানে-বামে সোয়াইপ করলে ফ্লুইড ফিজিক্স অ্যানিমেশনে স্মুথলি ট্যাব ও ইন্ডিকেটর ট্র্যাকিং হবে।
 * - থ্রি-ডটে বর্তমান কোয়ালিটি ব্যাজ প্রদর্শিত হবে।
 */
@Composable
fun ReelsTopNavigationBar(
    currentTabIndex: Int,
    pagerOffsetFraction: Float = 0f,
    tabTitles: List<String>,
    isVisible: Boolean = true,
    hasApprovedCreatorPage: Boolean = false,
    activeQualityBadge: String = "HD", // 👈 বর্তমান অ্যাক্টিভ কোয়ালিটি ব্যাজ (720p/HD/SD)
    onBackClick: () -> Unit,
    onOpenCreateReel: () -> Unit = {},
    onTabSelected: (index: Int) -> Unit,
    onSearchClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(140)) + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.80f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(top = 2.dp, bottom = 2.dp) // 🎯 উপরে একদম মার্জিন কমিয়ে তুলে দেওয়া হলো
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp) // 🎯 স্লিম উচ্চতা
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ১. বামে ব্যাক বাটন
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // =========================================================================
                // 🎯 ২. মাঝের ৩টি ট্যাব (Follow | Trend | Popular) - স্মুথ ফ্লুইড অ্যানিমেশন
                // =========================================================================
                val currentPosition = currentTabIndex + pagerOffsetFraction

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    tabTitles.forEachIndexed { index, tabName ->
                        val distance = abs(currentPosition - index).coerceIn(0f, 1f)
                        val textAlpha = 1.0f - (distance * 0.45f)
                        val indicatorWidth = (22 * (1f - distance * 1.4f)).coerceAtLeast(0f).dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTabSelected(index) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tabName,
                                color = Color.White.copy(alpha = textAlpha),
                                fontSize = if (distance < 0.25f) 15.5.sp else 14.sp,
                                fontWeight = if (distance < 0.25f) FontWeight.Black else FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // ফ্লুইড আন্ডারলাইন ইন্ডিকেটর
                            Box(
                                modifier = Modifier
                                    .width(indicatorWidth)
                                    .height(2.2.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(Color.White.copy(alpha = (1f - distance).coerceIn(0f, 1f)))
                            )
                        }
                    }
                }

                // =========================================================================
                // 🔍 ৩. ডানে সার্চ এবং কোয়ালিটি ব্যাজ সহ থ্রি-ডট অপশন
                // =========================================================================
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
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    if (currentTabIndex == 2) {
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = onOptionsClick,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 🎯 থ্রি-ডটের উপর ছোট কোয়ালিটি ডট (যেমন: HD/SD)
                            if (activeQualityBadge.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF00E5FF),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(6.dp)
                                ) {}
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
    }
}
