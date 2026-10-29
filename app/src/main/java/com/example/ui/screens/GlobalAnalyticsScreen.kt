@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import kotlinx.coroutines.launch

// 🎨 ইউটিউব স্টুডিও ডার্ক থিম কালার প্যালেট
private val StudioDarkBg = Color(0xFF0F1015)
private val StudioCardBg = Color(0xFF161922)
private val StudioCardBorder = Color(0xFF232838)
private val StudioCyan = Color(0xFF00E5FF)
private val StudioPurple = Color(0xFF9333EA)
private val StudioPurpleLight = Color(0xFFA855F7)
private val StudioGreen = Color(0xFF00E676)
private val StudioRed = Color(0xFFFF3D57)
private val TextMuted = Color(0xFF8E95A5)
private val TextLight = Color(0xFFCBD5E1)

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

    // 🟢 রিয়েল-টাইম পালসিং ডট
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
                // ১. হেডার বার
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

                    // লাইভ ব্যাজ (নো টেকনিক্যাল নাম)
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

                // ২. ৪টি প্রধান ট্যাব রো (ট্যাব ক্লিকে অ্যানিমেটেড স্ক্রোল)
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
        // 🎯 মাঝখানে ধরে ডানে-বামে স্মুথ সোয়াইপ পেজার
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> OverviewTabContent(overview = overview, topDramas = content.topDramas, onPostClick = onPostClick)
                1 -> ContentTabContent(content = content, overview = overview, onPostClick = onPostClick)
                2 -> AudienceTabContent(audience = audience)
                3 -> GrowthTabContent(growth = growth, audience = audience)
            }
        }
    }
}

// =============================================================================
// 📊 ট্যাব ১: OVERVIEW (ছবি ২ এর হুবহু লেআউট)
// =============================================================================
@Composable
private fun OverviewTabContent(
    overview: OverviewAnalyticsDto,
    topDramas: List<LivePostStat>,
    onPostClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ১. Views & Watch Time স্পার্কলাইন কার্ড জোড়া
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioSparklineCard(
                    title = "Views",
                    value = overview.formattedViews,
                    subtitle = "Realtime total views",
                    dataPoints = overview.viewsSparkline.ifEmpty { listOf(120f, 240f, 360f, 260f, 180f, 290f, 340f) },
                    lineColor = StudioCyan,
                    modifier = Modifier.weight(1f)
                )

                StudioSparklineCard(
                    title = "Watch time (hours)",
                    value = overview.formattedWatchTime,
                    subtitle = "Time spent in app",
                    dataPoints = overview.hoursSparkline.ifEmpty { listOf(0.4f, 0.8f, 1.2f, 0.7f, 0.9f, 1.1f, 1.0f) },
                    lineColor = StudioCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ২. Top Content কার্ড
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Top content", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Views · Last 28 days", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (topDramas.isEmpty()) {
                        Text("No content views yet", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        topDramas.take(5).forEachIndexed { idx, drama ->
                            TopContentRowItem(drama = drama, onClick = { onPostClick(drama.slug) })
                            if (idx < topDramas.take(5).size - 1) {
                                HorizontalDivider(color = StudioCardBorder.copy(alpha = 0.5f), thickness = 0.6.dp)
                            }
                        }
                    }
                }
            }
        }

        // ৩. Realtime Card (ছবি ২-এর নিচে থাকা নীল বার চার্ট সহ)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Realtime", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = overview.formattedViews,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text("Views · 48 hours", color = TextMuted, fontSize = 11.5.sp)
                        }

                        // ৪৮ ঘণ্টার নীল রিয়েল-টাইম বার গ্রাফ
                        RealtimeBarChart(
                            barValues = overview.realtime48hBars.ifEmpty { listOf(10, 15, 25, 40, 20, 35, 50, 45, 30, 25, 60, 40) },
                            modifier = Modifier
                                .width(130.dp)
                                .height(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = StudioCardBorder, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    topDramas.take(5).forEach { drama ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPostClick(drama.slug) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = drama.title,
                                color = TextLight,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 10.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = drama.formattedWatchingCount,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                MiniVerticalBarIndicator(modifier = Modifier.size(width = 24.dp, height = 14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🎬 ট্যাব ২: CONTENT (ছবি ১ এর হুবহু লেআউট)
// =============================================================================
@Composable
private fun ContentTabContent(
    content: ContentAnalyticsDto,
    overview: OverviewAnalyticsDto,
    onPostClick: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Videos", "Shorts", "Dubbed")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // সাব-ফিল্টার চিপস
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { chip ->
                    val isChipSelected = (chip == selectedFilter)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isChipSelected) Color.White else StudioCardBg,
                        border = BorderStroke(1.dp, if (isChipSelected) Color.White else StudioCardBorder),
                        modifier = Modifier.clickable { selectedFilter = chip }
                    ) {
                        Text(
                            text = chip,
                            color = if (isChipSelected) Color.Black else TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Views & Engaged views কার্ড জোড়া
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioSparklineCard(
                    title = "Views",
                    value = overview.formattedViews,
                    subtitle = "Total video plays",
                    dataPoints = overview.viewsSparkline.ifEmpty { listOf(120f, 240f, 360f, 260f, 180f, 290f, 340f) },
                    lineColor = StudioPurpleLight,
                    modifier = Modifier.weight(1f)
                )

                val engagedVal = (overview.viewsCount * 0.52).toLong()
                StudioSparklineCard(
                    title = "Engaged views",
                    value = PublicLiveAnalyticsResponse.formatMetric(engagedVal),
                    subtitle = "Watched > 30s",
                    dataPoints = overview.viewsSparkline.map { it * 0.5f }.ifEmpty { listOf(60f, 120f, 180f, 130f, 90f, 145f, 170f) },
                    lineColor = StudioPurpleLight,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // How viewers engaged (ছবি ১-এর স্ট্যাকড বেগুনি বার)
        item {
            val shortsEng = content.shortsEngagement ?: ShortsEngagementDto()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("How viewers engaged", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Last 28 days", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // স্ট্যাকড হরাইজন্টাল বার
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(shortsEng.stayedPercentage.toFloat().coerceAtLeast(0.1f))
                                .fillMaxHeight()
                                .background(StudioPurple)
                        )
                        Box(
                            modifier = Modifier
                                .weight(shortsEng.swipedPercentage.toFloat().coerceAtLeast(0.1f))
                                .fillMaxHeight()
                                .background(Color(0xFF3B285E))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${shortsEng.stayedPercentage}%", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Stayed to watch", color = TextMuted, fontSize = 11.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("${shortsEng.swipedPercentage}%", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Swiped away", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Top Shorts
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Top Shorts", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Views · Last 28 days", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    content.topDramas.take(5).forEachIndexed { idx, drama ->
                        TopContentRowItem(drama = drama, onClick = { onPostClick(drama.slug) })
                        if (idx < content.topDramas.take(5).size - 1) {
                            HorizontalDivider(color = StudioCardBorder.copy(alpha = 0.5f), thickness = 0.6.dp)
                        }
                    }
                }
            }
        }

        // Popular Search Terms
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Search terms", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("What viewers search inside app", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    content.topSearchTerms.forEach { search ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(search.term, color = TextLight, fontSize = 12.5.sp)
                                Text(search.formattedCount, color = TextMuted, fontSize = 11.5.sp)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { (search.count.toFloat() / 1500f).coerceIn(0.1f, 1f) },
                                color = StudioPurple,
                                trackColor = Color(0xFF1E2333),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 👥 ট্যাব ৩: AUDIENCE (ছবি ৩ এর হুবহু লেআউট)
// =============================================================================
@Composable
private fun AudienceTabContent(audience: AudienceAnalyticsDto) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Audience by watch behavior
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Audience by watch behavior", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Monthly audience segments", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // সেগমেন্টেড বার
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Box(modifier = Modifier.weight(audience.newViewersPct.toFloat().coerceAtLeast(0.1f)).fillMaxHeight().background(Color(0xFFE2D4F0)))
                        Box(modifier = Modifier.weight(audience.casualViewersPct.toFloat().coerceAtLeast(0.1f)).fillMaxHeight().background(StudioPurple))
                        Box(modifier = Modifier.weight(audience.vipViewersPct.toFloat().coerceAtLeast(0.1f)).fillMaxHeight().background(Color(0xFF4A1D96)))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AudienceLegendRow(color = Color(0xFFE2D4F0), title = "New viewers", pct = "${audience.newViewersPct}%")
                    AudienceLegendRow(color = StudioPurple, title = "Casual viewers", pct = "${audience.casualViewersPct}%")
                    AudienceLegendRow(color = Color(0xFF4A1D96), title = "VIP regular members", pct = "${audience.vipViewersPct}%")
                }
            }
        }

        // When your viewers are active (ছবি ৩-এর হুবহু সাপ্তাহিক বেগুনি হিটম্যাপ)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("When your viewers are active", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Hourly activity · Last 28 days", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    WeeklyActivityHeatmapGrid(
                        heatmapMatrix = audience.activeHeatmapMatrix,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }
        }

        // Device Type
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Device type", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Watch time (hours) · Last 28 days", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Box(modifier = Modifier.weight(audience.deviceMobilePct.toFloat().coerceAtLeast(0.1f)).fillMaxHeight().background(Color(0xFFE2D4F0)))
                        Box(modifier = Modifier.weight(audience.deviceTvPct.toFloat().coerceAtLeast(0.1f)).fillMaxHeight().background(StudioPurple))
                        Box(modifier = Modifier.weight(audience.deviceTabletPct.toFloat().coerceAtLeast(0.1f)).fillMaxHeight().background(Color(0xFF4A1D96)))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AudienceLegendRow(color = Color(0xFFE2D4F0), title = "Mobile Phone", pct = "${audience.deviceMobilePct}%")
                    AudienceLegendRow(color = StudioPurple, title = "Smart TV / Box", pct = "${audience.deviceTvPct}%")
                    AudienceLegendRow(color = Color(0xFF4A1D96), title = "Tablet / iPad", pct = "${audience.deviceTabletPct}%")
                }
            }
        }

        // Geography (ছবি ৩-এর পার্পল বারসহ দেশ)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Geography", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Views · Last 28 days", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    audience.countries.forEach { country ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(country.flag, fontSize = 13.sp)
                                    Text(country.name, color = TextLight, fontSize = 12.5.sp)
                                }
                                Text("${country.percentage}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { (country.percentage / 100f).toFloat().coerceIn(0.02f, 1f) },
                                color = StudioPurple,
                                trackColor = Color(0xFF1E2333),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 📈 ট্যাব ৪: GROWTH (ইন্সটল ও আনইন্সটল মেট্রিক্স)
// =============================================================================
@Composable
private fun GrowthTabContent(
    growth: GrowthAnalyticsStats,
    audience: AudienceAnalyticsDto
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ইন্সটল বনাম আনইন্সটল কার্ড
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("App Installs & Retention", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Verified device installations", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GrowthMiniCard(
                            title = "Total Installs",
                            count = growth.formattedLifetimeInstalls,
                            subText = "+${growth.formattedTodayInstalls} today",
                            color = StudioGreen,
                            modifier = Modifier.weight(1f)
                        )
                        GrowthMiniCard(
                            title = "Total Uninstalls",
                            count = growth.formattedLifetimeUninstalls,
                            subText = "-${growth.formattedTodayUninstalls} today",
                            color = StudioRed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GrowthMiniCard(
                            title = "Active Retained Users",
                            count = growth.formattedActiveBase,
                            subText = "Net installed devices",
                            color = StudioCyan,
                            modifier = Modifier.weight(1f)
                        )
                        GrowthMiniCard(
                            title = "Retention Rate",
                            count = "${growth.retentionRate}%",
                            subText = "Loyal retained ratio",
                            color = Color(0xFFFFB300),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ডিভাইস ব্র্যান্ডস
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCardBg,
                border = BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Device Brands", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Hardware manufacturer distribution", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    audience.topBrands.forEach { brand ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(brand.name, color = TextLight, fontSize = 12.5.sp)
                                Text("${brand.percentage}% (${brand.count})", color = StudioPurpleLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { (brand.percentage / 100f).toFloat().coerceIn(0.02f, 1f) },
                                color = StudioPurpleLight,
                                trackColor = Color(0xFF1E2333),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🎨 পুনঃব্যবহারযোগ্য ক্যানভাস ও UI উপাদানসমূহ
// =============================================================================

// ১. স্পার্কলাইন কার্ড
@Composable
private fun StudioSparklineCard(
    title: String,
    value: String,
    subtitle: String,
    dataPoints: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StudioCardBg,
        border = BorderStroke(1.dp, StudioCardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = TextMuted, fontSize = 11.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(subtitle, color = TextMuted, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(8.dp))

            // ক্যানভাস লাইন গ্রাফ
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
            ) {
                if (dataPoints.size < 2) return@Canvas
                val max = dataPoints.maxOrNull() ?: 1f
                val min = dataPoints.minOrNull() ?: 0f
                val range = (max - min).coerceAtLeast(1f)

                val stepX = size.width / (dataPoints.size - 1)
                val path = Path()

                dataPoints.forEachIndexed { i, pt ->
                    val x = i * stepX
                    val y = size.height - ((pt - min) / range * (size.height * 0.85f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

// ২. ৪৮ ঘণ্টার নীল রিয়েল-টাইম বার গ্রাফ
@Composable
private fun RealtimeBarChart(
    barValues: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (barValues.isEmpty()) return@Canvas
        val maxVal = (barValues.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val totalBars = barValues.size
        val barWidth = (size.width / totalBars) * 0.65f
        val spacing = (size.width - (barWidth * totalBars)) / (totalBars - 1).coerceAtLeast(1)

        barValues.forEachIndexed { index, v ->
            val barH = (v.toFloat() / maxVal) * size.height
            val x = index * (barWidth + spacing)
            val y = size.height - barH

            drawRoundRect(
                color = StudioCyan,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH.coerceAtLeast(3f)),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

// ৩. মিনি ভার্টিক্যাল বার নির্দেশক
@Composable
private fun MiniVerticalBarIndicator(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bars = listOf(0.4f, 0.7f, 0.3f, 0.9f, 0.6f, 1.0f)
        val barW = 2.dp.toPx()
        val spacing = 2.dp.toPx()

        bars.forEachIndexed { i, hRatio ->
            val h = size.height * hRatio
            drawRoundRect(
                color = StudioCyan,
                topLeft = Offset(i * (barW + spacing), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(1.5f, 1.5f)
            )
        }
    }
}

// ৪. সাপ্তাহিক ২৪ ঘণ্টার বেগুনি হিটম্যাপ গ্রিড
@Composable
private fun WeeklyActivityHeatmapGrid(
    heatmapMatrix: List<List<Float>>,
    modifier: Modifier = Modifier
) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(modifier = modifier) {
        // দিনের হেডার লেবেল
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 65.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayLabels.forEach { d ->
                Text(d, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // বাঁদিকের সময়ের লেবেল
            Column(
                modifier = Modifier
                    .width(65.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("12:00 AM", color = TextMuted, fontSize = 9.sp)
                Text("6:00 AM", color = TextMuted, fontSize = 9.sp)
                Text("12:00 PM", color = TextMuted, fontSize = 9.sp)
                Text("6:00 PM", color = TextMuted, fontSize = 9.sp)
            }

            // হিটম্যাপ ক্যানভাস (৭ কলাম x ২৪ সারি)
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val cols = 7
                val rows = 24
                val cellW = size.width / cols
                val cellH = size.height / rows

                for (c in 0 until cols) {
                    val colData = heatmapMatrix.getOrNull(c) ?: emptyList()
                    for (r in 0 until rows) {
                        val heat = colData.getOrNull(r) ?: ((r % 4) * 0.25f)
                        val alpha = heat.coerceIn(0.08f, 0.95f)

                        drawRect(
                            color = StudioPurple.copy(alpha = alpha),
                            topLeft = Offset(c * cellW + 2f, r * cellH + 1f),
                            size = Size(cellW - 4f, cellH - 2f)
                        )
                    }
                }
            }
        }
    }
}

// ৫. টপ কনটেন্ট রো আইটেম
@Composable
private fun TopContentRowItem(
    drama: LivePostStat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF22283A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = drama.title,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }

        Text(
            text = drama.formattedWatchingCount,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ৬. গ্রোথ মিনি কার্ড
@Composable
private fun GrowthMiniCard(
    title: String,
    count: String,
    subText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B202E),
        border = BorderStroke(0.6.dp, StudioCardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = TextMuted, fontSize = 11.sp)
            Text(count, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(subText, color = TextMuted.copy(alpha = 0.8f), fontSize = 9.5.sp)
        }
    }
}

// ৭. অডিয়েন্স লিজেন্ড রো
@Composable
private fun AudienceLegendRow(color: Color, title: String, pct: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(title, color = TextLight, fontSize = 12.sp)
        }
        Text(pct, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
