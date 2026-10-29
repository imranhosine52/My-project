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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
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

// 🎨 ইউটিউব স্টুডিও আল্ট্রা-ডার্ক কালার প্যালেট
private val StudioDarkBg = Color(0xFF0F1015)
private val StudioCardBorder = Color(0xFF1E2333)
private val StudioGreen = Color(0xFF00E676)
private val TextMuted = Color(0xFF8E95A5)
private val TabUnselected = Color(0xFF717A8C)

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

    // 🟢 রিয়েল-টাইম লাইভ পালসিং ডট অ্যানিমেশন
    val infiniteTransition = rememberInfiniteTransition(label = "studio_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
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
                // =========================================================================
                // 🔝 ১. প্রিমিয়াম হেডার বার (ব্যাক বাটন, টাইটেল ও লাইভ অনলাইন ব্যাজ)
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // স্লিম ব্যাক বাটন
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF161A26),
                            border = BorderStroke(0.6.dp, StudioCardBorder),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onClose() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Analytics",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                            Text(
                                text = "Realtime Studio Monitor",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 🟢 স্লিক লাইভ ব্যাজ
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF092015))
                            .border(0.8.dp, StudioGreen.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.5.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(StudioGreen)
                        )
                        Text(
                            text = "${data.formattedLiveCount} Live",
                            color = StudioGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // =========================================================================
                // 🧭 ২. বাটার-স্মুথ ন্যাভিগেশন ট্যাব বার (৪টি সমান ও স্লিম ট্যাব)
                // =========================================================================
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = StudioDarkBg,
                    contentColor = Color.White,
                    divider = {
                        HorizontalDivider(
                            color = StudioCardBorder.copy(alpha = 0.6f),
                            thickness = 0.6.dp
                        )
                    },
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            // 🎯 স্মুথ কাস্টম রাউন্ডেড বটম ইন্ডিকেটর
                            Box(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF00E5FF),
                                                Color.White,
                                                Color(0xFF00E5FF)
                                            )
                                        )
                                    )
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
                                    // 🎯 মসৃণ স্ক্রোল অ্যানিমেশন
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = tween(
                                            durationMillis = 350,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            },
                            text = {
                                Text(
                                    text = tabTitle,
                                    color = if (isSelected) Color.White else TabUnselected,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 0.2.sp
                                )
                            },
                            interactionSource = remember { MutableInteractionSource() }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // =========================================================================
        // 🔄 ৩. সোয়াইপ পেজার (প্রতিটি ট্যাবের সাথে পারফেক্ট ডাটা কানেকশন)
        // =========================================================================
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                // ১. Overview ট্যাব: টাচ গ্রাফ, Traffic Summary এবং নতুন দেশভিত্তিক অডিয়েন্স কার্ড
                0 -> AnalyticsOverviewTab(
                    overview = overview,
                    countries = audience.countries // 👈 দেশের ইতিহাস ওভারভিউতে পাস করা হলো
                )

                // ২. Content ট্যাব: আল্ট্রা-স্লিম লিডারবোর্ড র‍্যাঙ্কিং (#1, #2, #3) এবং সংক্ষিপ্ত Display Name
                1 -> AnalyticsContentTab(
                    content = content,
                    onPostClick = onPostClick
                )

                // ৩. Audience ট্যাব: স্লিম সাব-ট্যাব, লাইভ স্ক্রিন লোকেশন, গোল পতাকা ব্যাজ ও ওয়াচিং মিনিট
                2 -> AnalyticsAudienceTab(
                    audience = audience,
                    topPosts = content.topDramas,
                    liveOnlineCount = data.liveOnlineCount,
                    activeScreens = data.activeScreens, // 👈 লাইভ স্ক্রিন লোকেশন কানেক্টেড
                    onPostClick = onPostClick
                )

                // ৪. Growth ট্যাব: লাইফটাইম ইন্সটল, আনইন্সটল ও রিয়েল-টাইম রোলিং অ্যানিমেশন
                3 -> AnalyticsGrowthTab(
                    growth = growth
                )
            }
        }
    }
}
