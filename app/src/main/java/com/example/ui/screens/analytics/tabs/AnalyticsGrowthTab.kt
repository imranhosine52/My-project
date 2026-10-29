package com.example.ui.screens.analytics.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GrowthAnalyticsStats
import com.example.data.model.GrowthSummaryItem

// 🎨 ইউটিউব স্টুডিও ডার্ক থিম কালার প্যালেট
private val CardBg = Color(0xFF10141F)
private val CardBorder = Color(0xFF1C2436)
private val MetricBoxBg = Color(0xFF151B2A)
private val GreenText = Color(0xFF00E676)
private val RedText = Color(0xFFFF3D57)
private val CyanText = Color(0xFF00E5FF)
private val GoldTotal = Color(0xFFFFB300)
private val TextMuted = Color(0xFF8E95A5)
private val TextLight = Color(0xFFCBD5E1)

@Composable
fun AnalyticsGrowthTab(
    growth: GrowthAnalyticsStats,
    modifier: Modifier = Modifier
) {
    // সামারি টেবিলের ফলব্যাক তালিকা (যদি ব্যাকএন্ড থেকে এখনো লোড না হয়ে থাকে)
    val summaryList = if (growth.growthSummary.isNotEmpty()) {
        growth.growthSummary
    } else {
        listOf(
            GrowthSummaryItem("Today", growth.todayInstalls.coerceAtLeast(1L), growth.todayUninstalls),
            GrowthSummaryItem("Yesterday", 0L, 0L),
            GrowthSummaryItem("1 Month (30 Days)", growth.todayInstalls.coerceAtLeast(1L), growth.todayUninstalls),
            GrowthSummaryItem("3 Months (90 Days)", growth.todayInstalls.coerceAtLeast(1L), growth.todayUninstalls),
            GrowthSummaryItem("1 Year (365 Days)", growth.lifetimeInstalls.coerceAtLeast(1L), growth.lifetimeUninstalls),
            GrowthSummaryItem("Total", growth.lifetimeInstalls.coerceAtLeast(1L), growth.lifetimeUninstalls)
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 📊 ১. উপরের বোর্ড: Total Lifetime Overview
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = GreenText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "App Installs & Retention Overview",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Realtime hardware-verified app lifecycle stats",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // রো ১: Total Installs & Total Uninstalls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GrowthMetricBox(
                            label = "Total Installs",
                            value = growth.formattedLifetimeInstalls,
                            subLabel = "+${growth.formattedTodayInstalls} today",
                            valueColor = GreenText,
                            modifier = Modifier.weight(1f)
                        )

                        GrowthMetricBox(
                            label = "Total Uninstalls",
                            value = growth.formattedLifetimeUninstalls,
                            subLabel = "-${growth.formattedTodayUninstalls} today",
                            valueColor = RedText,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // রো ২: Active Retained Users & Retention Rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GrowthMetricBox(
                            label = "Active Retained Users",
                            value = growth.formattedActiveBase,
                            subLabel = "Net active devices",
                            valueColor = CyanText,
                            modifier = Modifier.weight(1f)
                        )

                        GrowthMetricBox(
                            label = "Retention Rate",
                            value = "${growth.retentionRate}%",
                            subLabel = "Loyal retained ratio",
                            valueColor = GoldTotal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 📋 ২. নিচের বোর্ড: Installation & Churn Summary টেবিল
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // হেডার রো: 📥 Installation Summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = CyanText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Installation Summary",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // কলাম হেডার্স: TIMEFRAME | INSTALLS | UNINSTALLS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TIMEFRAME",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.4f)
                        )
                        Text(
                            text = "INSTALLS",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "UNINSTALLS",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // টেবিলের রোগুলো (Today, Yesterday, 1M, 3M, 1Y, Total)
                    summaryList.forEach { row ->
                        val isTotalRow = row.timeframe.equals("Total", ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // কলাম ১: Timeframe
                            Text(
                                text = row.timeframe,
                                color = if (isTotalRow) GoldTotal else TextLight,
                                fontSize = 13.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.SemiBold,
                                modifier = Modifier.weight(1.4f)
                            )

                            // কলাম ২: Installs (সবুজ ও নিয়ন গ্রিন)
                            Text(
                                text = row.formattedInstalls,
                                color = if (isTotalRow) GreenText else CyanText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )

                            // কলাম ৩: Uninstalls (লাল রঙে)
                            Text(
                                text = row.formattedUninstalls,
                                color = if (isTotalRow) RedText else TextMuted,
                                fontSize = 13.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.End,
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

/**
 * 📦 উপরের বোর্ডের জন্য মিনি মেট্রিক বক্স
 */
@Composable
private fun GrowthMetricBox(
    label: String,
    value: String,
    subLabel: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MetricBoxBg,
        border = BorderStroke(0.6.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = subLabel,
                color = TextMuted.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}
