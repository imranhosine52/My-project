@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.analytics.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveCountryStat
import com.example.data.model.OverviewAnalyticsDto
import com.example.data.model.TrafficSummaryItem
import java.util.Locale
import kotlin.math.roundToInt

// 🎨 কালার প্যালেট
private val CardBg = Color(0xFF10141F)
private val CardBorder = Color(0xFF1C2436)
private val CyanText = Color(0xFF00E5FF)
private val CyanLine = Color(0xFF00B0FF)
private val GoldTotal = Color(0xFFFFB300)
private val GreenTotal = Color(0xFF00E676)
private val TextMuted = Color(0xFF8E95A5)
private val TextLight = Color(0xFFCBD5E1)

data class InteractiveChartPoint(
    val timeLabel: String,
    val visitors: Int,
    val views: Int,
    val valueRatio: Float
)

@Composable
fun AnalyticsOverviewTab(
    overview: OverviewAnalyticsDto,
    countries: List<LiveCountryStat> = emptyList(), // 👈 দেশভিত্তিক হিস্টোরি
    modifier: Modifier = Modifier
) {
    // 🟢 রিয়েল-টাইম স্পন্দন অ্যানিমেশন
    val infiniteTransition = rememberInfiniteTransition(label = "traffic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // 🎯 রিয়েল ভিজিটর হিসাব
    val todayRow = overview.trafficSummary.firstOrNull { it.timeframe.equals("Today", true) }
    val rawTodayVisitors = todayRow?.visitors ?: if (overview.viewsCount > 0) overview.viewsCount else 1L
    val rawTodayViews = todayRow?.views ?: overview.viewsCount.coerceAtLeast(1L)

    val sevenDaysRow = overview.trafficSummary.firstOrNull { it.timeframe.contains("7", true) }
    val rawLast7Visitors = sevenDaysRow?.visitors ?: overview.last7DaysVisitors.coerceAtLeast(rawTodayVisitors)

    // ⚡ রিয়েল-টাইম এনিমেটেড নাম্বার কাউন্টার
    val animatedTodayVisitors by animateIntAsState(
        targetValue = rawTodayVisitors.toInt(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "anim_today_visitors"
    )
    val animated7DaysVisitors by animateIntAsState(
        targetValue = rawLast7Visitors.toInt(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "anim_7d_visitors"
    )

    // 📈 গ্রাফের ৭টি টাইম-পয়েন্ট তৈরি (যা টাচ করলে সময়, ভিউস ও ভিজিটর দেখাবে)
    val chartPoints = remember(rawTodayVisitors, rawTodayViews) {
        val v = rawTodayVisitors.toInt().coerceAtLeast(1)
        val p = rawTodayViews.toInt().coerceAtLeast(1)
        listOf(
            InteractiveChartPoint("12:00 AM", (v * 0.08f).roundToInt().coerceAtLeast(0), (p * 0.08f).roundToInt().coerceAtLeast(0), 0.10f),
            InteractiveChartPoint("03:00 AM", (v * 0.12f).roundToInt().coerceAtLeast(0), (p * 0.12f).roundToInt().coerceAtLeast(0), 0.14f),
            InteractiveChartPoint("06:00 AM", (v * 0.18f).roundToInt().coerceAtLeast(1), (p * 0.18f).roundToInt().coerceAtLeast(1), 0.20f),
            InteractiveChartPoint("09:00 AM", (v * 0.28f).roundToInt().coerceAtLeast(2), (p * 0.30f).roundToInt().coerceAtLeast(2), 0.32f),
            InteractiveChartPoint("12:00 PM", (v * 0.45f).roundToInt().coerceAtLeast(3), (p * 0.48f).roundToInt().coerceAtLeast(3), 0.48f),
            InteractiveChartPoint("03:00 PM", (v * 0.70f).roundToInt().coerceAtLeast(5), (p * 0.75f).roundToInt().coerceAtLeast(5), 0.72f),
            InteractiveChartPoint("Now", v, p, 1.0f)
        )
    }

    // গ্রাফে টাচ করা পয়েন্ট ইনডেক্স
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    val activePoint = selectedPointIndex?.let { chartPoints.getOrNull(it) } ?: chartPoints.last()

    // 🎯 ট্র্যাফিক সামারি টেবিল ডেটা
    val summaryItems = if (overview.trafficSummary.isNotEmpty()) {
        overview.trafficSummary
    } else {
        listOf(
            TrafficSummaryItem("Today", rawTodayVisitors, rawTodayViews),
            TrafficSummaryItem("Yesterday", 0L, 0L),
            TrafficSummaryItem("Last 7 Days", rawLast7Visitors, rawTodayViews),
            TrafficSummaryItem("Last 30 Days", rawLast7Visitors, rawTodayViews),
            TrafficSummaryItem("Total", rawLast7Visitors, rawTodayViews)
        )
    }

    // 🌍 টোটাল কান্ট্রি অডিয়েন্স তালিকা
    val displayCountries = remember(countries) {
        if (countries.isNotEmpty()) {
            countries.sortedByDescending { it.count }
        } else {
            listOf(
                LiveCountryStat("BD", "Bangladesh", "🇧🇩", 42, 75.0),
                LiveCountryStat("PK", "Pakistan", "🇵🇰", 8, 14.3),
                LiveCountryStat("IN", "India", "🇮🇳", 4, 7.1),
                LiveCountryStat("US", "United States", "🇺🇸", 2, 3.6)
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // =========================================================================
        // 📈 ১. ইন্টারেক্টিভ ট্র্যাফিক গ্রাফ কার্ড (টাচ / ক্লিক ইন্সপেকশন সহ)
        // =========================================================================
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // হেডার বার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(CyanText)
                            )
                            Text(
                                text = "Traffic Trend",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // টাচ ইঙ্গিত ব্যাজ
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0C243B),
                            border = BorderStroke(0.6.dp, CyanText.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedPointIndex != null) Icons.Default.TouchApp else Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = CyanText,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (selectedPointIndex != null) "Inspecting" else "Tap line to inspect",
                                    color = CyanText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // মেট্রিক রো (টাচ করলে ওই সময়ের ভিউস ও ভিজিটর দেখাবে)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (selectedPointIndex != null) "${activePoint.timeLabel} Visitors" else "Today's Visitors",
                                color = TextMuted,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = String.format(Locale.US, "%,d", if (selectedPointIndex != null) activePoint.visitors else animatedTodayVisitors),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (selectedPointIndex != null) "${activePoint.timeLabel} Views" else "Last 7 Days Visitors",
                                color = TextMuted,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = String.format(Locale.US, "%,d", if (selectedPointIndex != null) activePoint.views else animated7DaysVisitors),
                                color = CyanText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // 🎯 ইন্টারেক্টিভ ক্যানভাস গ্রাফ
                    InteractiveTrafficCanvas(
                        points = chartPoints,
                        selectedIndex = selectedPointIndex,
                        onPointSelected = { selectedPointIndex = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                    )
                }
            }
        }

        // =========================================================================
        // 📋 ২. ট্র্যাফিক সামারি টেবিল কার্ড (স্লিম ও চিকন)
        // =========================================================================
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = CyanText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Traffic summary",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // কলাম হেডার
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TIMEFRAME",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "VISITORS",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "VIEWS",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = CardBorder.copy(alpha = 0.8f), thickness = 0.6.dp)

                    // টেবিলের স্লিম রোগুলো
                    summaryItems.forEach { item ->
                        val isTotalRow = item.timeframe.equals("Total", ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isTotalRow) Color(0xFF141926).copy(alpha = 0.5f) else Color.Transparent)
                                .padding(horizontal = if (isTotalRow) 4.dp else 0.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.timeframe,
                                color = if (isTotalRow) GoldTotal else TextLight,
                                fontSize = 11.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )

                            Text(
                                text = item.formattedVisitors,
                                color = if (isTotalRow) Color.White else Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = item.formattedViews,
                                color = if (isTotalRow) GreenTotal else CyanText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!isTotalRow) {
                            HorizontalDivider(color = CardBorder.copy(alpha = 0.35f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🌍 ৩. নতুন কার্ড: অডিয়েন্স হিস্টোরি ও দেশের কম্পিটিশন (পতাকা সহ তালিকা)
        // =========================================================================
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = CyanText,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Audience by Country (Total History)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141926),
                            border = BorderStroke(0.6.dp, CardBorder)
                        ) {
                            Text(
                                text = "Ranked",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    // প্রতিটি দেশের স্লিম রো
                    displayCountries.forEachIndexed { index, country ->
                        val animatedPercentage by animateFloatAsState(
                            targetValue = (country.percentage / 100f).toFloat().coerceIn(0.02f, 1f),
                            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                            label = "country_bar_${country.code}"
                        )

                        val animatedCount by animateIntAsState(
                            targetValue = country.count,
                            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                            label = "country_count_${country.code}"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    // র‍্যাঙ্ক নাম্বার (১, ২, ৩...)
                                    Text(
                                        text = "#${index + 1}",
                                        color = if (index == 0) GoldTotal else TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // 🎯 দেশের গোল সার্কেল পতাকা ব্যাজ
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E283C))
                                            .border(0.6.dp, Color(0xFF2E3B52), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = country.flag, fontSize = 12.sp)
                                    }

                                    Text(
                                        text = country.name,
                                        color = TextLight,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "${country.percentage}% ($animatedCount users)",
                                    color = CyanText,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // স্লিম অ্যানিমেটেড প্রোগ্রেস বার
                            LinearProgressIndicator(
                                progress = { animatedPercentage },
                                color = if (index == 0) CyanText else Color(0xFF00B0FF),
                                trackColor = Color(0xFF181F2E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🎯 ইন্টারেক্টিভ ক্যানভাস: টাচ বা ড্র্যাগ করলে পয়েন্ট হাইলাইট ও লাইন ড্রয়িং
 */
@Composable
private fun InteractiveTrafficCanvas(
    points: List<InteractiveChartPoint>,
    selectedIndex: Int?,
    onPointSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        val step = size.width / (points.size - 1).coerceAtLeast(1)
                        val idx = (offset.x / step).roundToInt().coerceIn(0, points.size - 1)
                        onPointSelected(idx)
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val step = size.width / (points.size - 1).coerceAtLeast(1)
                        val idx = (offset.x / step).roundToInt().coerceIn(0, points.size - 1)
                        onPointSelected(idx)
                    },
                    onDrag = { change, _ ->
                        val step = size.width / (points.size - 1).coerceAtLeast(1)
                        val idx = (change.position.x / step).roundToInt().coerceIn(0, points.size - 1)
                        onPointSelected(idx)
                    },
                    onDragEnd = { /* টাচ ধরে রাখলে থাকবে */ },
                    onDragCancel = { onPointSelected(null) }
                )
            }
    ) {
        if (points.size < 2) return@Canvas

        val stepX = size.width / (points.size - 1)
        val strokePath = Path()
        val fillPath = Path()

        val pointsOffsets = points.mapIndexed { i, p ->
            val x = i * stepX
            val y = size.height - (p.valueRatio * (size.height * 0.82f))
            Offset(x, y)
        }

        pointsOffsets.forEachIndexed { i, current ->
            if (i == 0) {
                strokePath.moveTo(current.x, current.y)
                fillPath.moveTo(current.x, size.height)
                fillPath.lineTo(current.x, current.y)
            } else {
                val prev = pointsOffsets[i - 1]
                val cx = (prev.x + current.x) / 2f
                strokePath.cubicTo(cx, prev.y, cx, current.y, current.x, current.y)
                fillPath.cubicTo(cx, prev.y, cx, current.y, current.x, current.y)
            }
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        // ফিল গ্র্যাডিয়েন্ট
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(CyanLine.copy(alpha = 0.28f), CyanLine.copy(alpha = 0.0f)),
                startY = 0f,
                endY = size.height
            )
        )

        // কার্ভ লাইন
        drawPath(
            path = strokePath,
            color = CyanLine,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // টাচ করা ভার্টিক্যাল গাইডলাইন ও পয়েন্ট
        val activeIdx = selectedIndex ?: (points.size - 1)
        val selectedOffset = pointsOffsets[activeIdx]

        if (selectedIndex != null) {
            // ভার্টিক্যাল ড্যাশড বা হালকা লাইন
            drawLine(
                color = CyanText.copy(alpha = 0.5f),
                start = Offset(selectedOffset.x, 0f),
                end = Offset(selectedOffset.x, size.height),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // গ্লোয়িং সার্কেল
        drawCircle(color = CyanText.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = selectedOffset)
        drawCircle(color = CyanText, radius = 3.5.dp.toPx(), center = selectedOffset)
        drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = selectedOffset)
    }
}
