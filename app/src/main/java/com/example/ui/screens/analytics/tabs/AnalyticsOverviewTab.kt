package com.example.ui.screens.analytics.tabs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OverviewAnalyticsDto
import com.example.data.model.TrafficSummaryItem

// 🎨 ইউটিউব স্টুডিও ডার্ক থিম কালার প্যালেট
private val CardBg = Color(0xFF10141F)
private val CardBorder = Color(0xFF1C2436)
private val CyanText = Color(0xFF00E5FF)
private val CyanLine = Color(0xFF00B0FF)
private val GoldTotal = Color(0xFFFFB300)
private val GreenTotal = Color(0xFF00E676)
private val TextMuted = Color(0xFF8E95A5)
private val TextLight = Color(0xFFCBD5E1)

@Composable
fun AnalyticsOverviewTab(
    overview: OverviewAnalyticsDto,
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

    val todayVisitors = overview.trafficSummary.firstOrNull { it.timeframe.equals("Today", true) }?.visitors ?: 0L
    val last7Visitors = overview.trafficSummary.firstOrNull { it.timeframe.contains("7", true) }?.visitors 
        ?: overview.last7DaysVisitors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 📈 ১. রিয়েল-টাইম অ্যানিমেটেড ট্র্যাফিক গ্রাফ কার্ড (Today vs 7 Days)
        // =========================================================================
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(CyanText)
                            )
                            Text(
                                text = "Traffic Trend",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0C243B),
                            border = BorderStroke(0.6.dp, CyanText.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = CyanText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Realtime Active",
                                    color = CyanText,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // মেট্রিক্স রো (Today vs 7 Days)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Today's Visitors", color = TextMuted, fontSize = 11.5.sp)
                            Text(
                                text = String.format("%,d", todayVisitors),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Last 7 Days Visitors", color = TextMuted, fontSize = 11.5.sp)
                            Text(
                                text = String.format("%,d", last7Visitors),
                                color = CyanText,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 🌊 স্মুথ ক্যানভাস অ্যানিমেটেড ট্র্যাফিক গ্রাফ
                    TrafficCurveCanvas(
                        sparkline = overview.viewsSparkline.ifEmpty { listOf(120f, 240f, 360f, 280f, 190f, 310f, 350f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            }
        }

        // =========================================================================
        // 📋 ২. ছবি ২-এর হুবহু "Traffic summary" টেবিল কার্ড
        // =========================================================================
        item {
            val summaryItems = if (overview.trafficSummary.isNotEmpty()) {
                overview.trafficSummary
            } else {
                // ফলব্যাক ডামি ডেটা (যদি ডাটাবেজে এখনো কোনো রেকর্ড না থাকে)
                listOf(
                    TrafficSummaryItem("Today", 63, 120),
                    TrafficSummaryItem("Yesterday", 1351, 3074),
                    TrafficSummaryItem("Last 7 Days", 9531, 25473),
                    TrafficSummaryItem("Last 30 Days", 33996, 77065),
                    TrafficSummaryItem("Total", 80508, 180418)
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // হেডার রো: 📅 Traffic summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = CyanText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Traffic summary",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // কলাম হেডার্স: TIMEFRAME | VISITORS | VIEWS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TIMEFRAME",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "VISITORS",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "VIEWS",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // টেবিলের রোগুলো
                    summaryItems.forEach { item ->
                        val isTotalRow = item.timeframe.equals("Total", ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // কলাম ১: Timeframe
                            Text(
                                text = item.timeframe,
                                color = if (isTotalRow) GoldTotal else TextLight,
                                fontSize = 13.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.SemiBold,
                                modifier = Modifier.weight(1.3f)
                            )

                            // কলাম ২: Visitors
                            Text(
                                text = item.formattedVisitors,
                                color = if (isTotalRow) Color.White else Color(0xFFE2E8F0),
                                fontSize = 13.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )

                            // কলাম ৩: Views (ছবি ২ অনুযায়ী সায়ান ও টোটাল রো নিয়ন গ্রিন)
                            Text(
                                text = item.formattedViews,
                                color = if (isTotalRow) GreenTotal else CyanText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!isTotalRow) {
                            HorizontalDivider(color = CardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🌊 স্মুথ ক্যানভাস অ্যানিমেটেড ট্র্যাফিক গ্রাফ
// -------------------------------------------------------------
@Composable
private fun TrafficCurveCanvas(
    sparkline: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (sparkline.size < 2) return@Canvas

        val maxVal = sparkline.maxOrNull() ?: 1f
        val minVal = sparkline.minOrNull() ?: 0f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val stepX = size.width / (sparkline.size - 1)
        val strokePath = Path()
        val fillPath = Path()

        sparkline.forEachIndexed { i, value ->
            val x = i * stepX
            val y = size.height - ((value - minVal) / range * (size.height * 0.82f))

            if (i == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (i - 1) * stepX
                val prevY = size.height - ((sparkline[i - 1] - minVal) / range * (size.height * 0.82f))
                val cx = (prevX + x) / 2f
                strokePath.cubicTo(cx, prevY, cx, y, x, y)
                fillPath.cubicTo(cx, prevY, cx, y, x, y)
            }
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        // গ্রেডিয়েন্ট ফিল
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    CyanLine.copy(alpha = 0.28f),
                    CyanLine.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = size.height
            )
        )

        // মূল লাইন
        drawPath(
            path = strokePath,
            color = CyanLine,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // সর্বশেষ বিন্দুতে গ্লো ডট
        val lastX = size.width
        val lastY = size.height - ((sparkline.last() - minVal) / range * (size.height * 0.82f))
        drawCircle(
            color = CyanText.copy(alpha = 0.35f),
            radius = 6.dp.toPx(),
            center = Offset(lastX, lastY)
        )
        drawCircle(
            color = CyanText,
            radius = 3.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}
