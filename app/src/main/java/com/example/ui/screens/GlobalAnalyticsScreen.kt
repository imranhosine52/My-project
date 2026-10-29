@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.screens.analytics.tabs.AnalyticsAudienceTab
import com.example.ui.screens.analytics.tabs.AnalyticsContentTab
import com.example.ui.screens.analytics.tabs.AnalyticsGrowthTab
import com.example.ui.screens.analytics.tabs.AnalyticsOverviewTab
import kotlinx.coroutines.launch

// 🎨 ইউটিউব স্টুডিও ডার্ক থিম কালার প্যালেট
private val StudioDarkBg = Color(0xFF0F1015)
private val StudioCardBorder = Color(0xFF232838)
private val StudioGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun GlobalAnalyticsScreen(
    analyticsData: PublicLiveAnalyticsResponse?,
    isLoading: Boolean,
    onClose: () -> Unit,
    onPostClick: (slug: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val tabs = remember { listOf("Overview", "Content", "Audience", "Growth") }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    val data = analyticsData ?: PublicLiveAnalyticsResponse(liveOnlineCount = 1)
    val overview = data.overview ?: OverviewAnalyticsDto()
    val content = data.contentAnalytics ?: ContentAnalyticsDto()
    val audience = data.audienceAnalytics ?: AudienceAnalyticsDto()
    val growth = data.growth ?: GrowthAnalyticsStats()

    // 🟢 রিয়েল-টাইম লাইভ স্পন্দন অ্যানিমেশন
    val infiniteTransition = rememberInfiniteTransition(label = "studio_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        containerColor = StudioDarkBg,
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkBg)
                    .statusBarsPadding()
            ) {
                // ১. হেডার বার (টাইটেল ও লাইভ অডিয়েন্স ব্যাজ)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Analytics",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // লাইভ ব্যাজ (প্রফেশনাল লুক)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0D2418))
                            .border(0.8.dp, StudioGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(StudioGreen)
                        )
                        Text(
                            text = "${data.formattedLiveCount} Live",
                            color = StudioGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ২. ৪টি প্রধান ট্যাব রো (Overview, Content, Audience, Growth)
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = StudioDarkBg,
                    contentColor = Color.White,
                    edgePadding = 14.dp,
                    divider = { HorizontalDivider(color = StudioCardBorder, thickness = 0.6.dp) },
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                height = 2.5.dp,
                                color = Color.White
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = (pagerState.currentPage == index)
                        Tab(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = tabTitle,
                                    color = if (isSelected) Color.White else TextMuted,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // 🎯 ডানে-বামে স্মুথ সোয়াইপ পেজার
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                // ১. Overview ট্যাব: ট্র্যাফিক গ্রাফ এবং Traffic Summary টেবিল (Today, Yesterday, 7D, 30D, Total)
                0 -> AnalyticsOverviewTab(
                    overview = overview
                )

                // ২. Content ট্যাব: রিয়েল-টাইম পোস্ট তালিকা ও পজিশন অ্যানিমেশন
                1 -> AnalyticsContentTab(
                    content = content,
                    onPostClick = onPostClick
                )

                // ৩. Audience ট্যাব: লাইভ স্ক্রিন ট্র্যাকার, দেশভিত্তিক গোল পতাকা ও বয়স বিন্যাস
                2 -> AnalyticsAudienceTab(
                    audience = audience,
                    topPosts = content.topDramas,
                    liveOnlineCount = data.liveOnlineCount,
                    activeScreens = data.activeScreens, // 👈 রিয়েল-টাইম স্ক্রিন লোকেশন পাস করা হলো
                    onPostClick = onPostClick
                )

                // ৪. Growth ট্যাব: লাইফটাইম ইন্সটল, আনইন্সটল ও অ্যানিমেটেড সামারি
                3 -> AnalyticsGrowthTab(
                    growth = growth
                )
            }
        }
    }
}
