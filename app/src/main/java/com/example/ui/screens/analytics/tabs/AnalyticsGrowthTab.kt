package com.example.ui.screens.analytics.tabs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GrowthAnalyticsStats
import com.example.data.model.GrowthSummaryItem
import java.util.Locale

// 🎨 ইউটিউব স্টুডিও আল্ট্রা-ডার্ক প্রিমিয়াম প্যালেট
private val CardBg = Color(0xFF10141F)
private val CardBorder = Color(0xFF1C2436)
private val MetricBoxBg = Color(0xFF141926)
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
    // 🎯 সামারি টেবিলের ডাটা প্রসেসিং
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

    val totalItem = summaryList.find { it.timeframe.equals("Total", ignoreCase = true) }
    val todayItem = summaryList.find { it.timeframe.equals("Today", ignoreCase = true) }

    val finalTotalInstalls = totalItem?.installs ?: 1L
    val finalTotalUninstalls = totalItem?.uninstalls ?: 0L
    val finalTodayInstalls = todayItem?.installs ?: 0L
    val finalTodayUninstalls = todayItem?.uninstalls ?: 0L

    val finalActiveBase = (finalTotalInstalls - finalTotalUninstalls).coerceAtLeast(1L)
    val finalRetention = if (finalTotalInstalls > 0) {
        (finalActiveBase.toDouble() / finalTotalInstalls.toDouble()) * 100.0
    } else 100.0

    // =========================================================================
    // ⚡ ১. রিয়েল-টাইম স্মুথ কাউন্টিং অ্যানিমেশন ইঞ্জিন (Animated Numbers)
    // =========================================================================
    val animDuration = 900
    val animEasing = FastOutSlowInEasing

    val animatedInstalls by animateIntAsState(
        targetValue = finalTotalInstalls.toInt(),
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "anim_installs"
    )

    val animatedUninstalls by animateIntAsState(
        targetValue = finalTotalUninstalls.toInt(),
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "anim_uninstalls"
    )

    val animatedActiveUsers by animateIntAsState(
        targetValue = finalActiveBase.toInt(),
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "anim_active_users"
    )

    val animatedRetention by animateFloatAsState(
        targetValue = finalRetention.toFloat(),
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "anim_retention"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp) // 🎯 কার্ডের মাঝের গ্যাপ কমানো হয়েছে
    ) {
        // =========================================================================
        // 📊 ১. উপরের বোর্ড: Total Lifetime Overview (স্মুথ এনিমেটেড কাউন্টার)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = GreenText,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "App Installs & Retention Overview",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Realtime hardware-verified app lifecycle stats",
                        color = TextMuted,
                        fontSize = 10.sp
                    )

                    // রো ১: টোটাল ইন্সটল ও আনইন্সটল
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GrowthMetricBox(
                            label = "Total Installs",
                            value = String.format(Locale.US, "%,d", animatedInstalls),
                            subLabel = "+${String.format(Locale.US, "%,d", finalTodayInstalls)} today",
                            valueColor = GreenText,
                            modifier = Modifier.weight(1f)
                        )

                        GrowthMetricBox(
                            label = "Total Uninstalls",
                            value = String.format(Locale.US, "%,d", animatedUninstalls),
                            subLabel = "-${String.format(Locale.US, "%,d", finalTodayUninstalls)} today",
                            valueColor = RedText,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // রো ২: এক্টিভ ইউজার ও রিটেনশন রেট
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GrowthMetricBox(
                            label = "Active Retained Users",
                            value = String.format(Locale.US, "%,d", animatedActiveUsers),
                            subLabel = "Net active devices",
                            valueColor = CyanText,
                            modifier = Modifier.weight(1f)
                        )

                        GrowthMetricBox(
                            label = "Retention Rate",
                            value = "${String.format(Locale.US, "%.1f", animatedRetention)}%",
                            subLabel = "Loyal retained ratio",
                            valueColor = GoldTotal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 📋 ২. নিচের বোর্ড: আল্ট্রা-স্লিম সামারি টেবিল (অতিরিক্ত ফাঁকা স্পেস সম্পূর্ণ মুক্ত)
        // =========================================================================
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp), // 🎯 একদম স্লিম প্যাডিং
                    verticalArrangement = Arrangement.spacedBy(5.dp) // 🎯 রো গুলোর ফাঁকা গ্যাপ কমানো হয়েছে
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = CyanText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Installation Summary",
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
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "INSTALLS",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "UNINSTALLS",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = CardBorder.copy(alpha = 0.8f), thickness = 0.6.dp)

                    // টেবিলের চিকন স্লিম রোগুলো
                    summaryList.forEach { row ->
                        val isTotalRow = row.timeframe.equals("Total", ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isTotalRow) Color(0xFF141926).copy(alpha = 0.5f) else Color.Transparent)
                                .padding(horizontal = if (isTotalRow) 4.dp else 0.dp, vertical = 2.5.dp), // 🎯 প্রতি লাইনে স্লিম হাইট
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.timeframe,
                                color = if (isTotalRow) GoldTotal else TextLight,
                                fontSize = 11.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )

                            Text(
                                text = row.formattedInstalls,
                                color = if (isTotalRow) GreenText else CyanText,
                                fontSize = 11.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = row.formattedUninstalls,
                                color = if (isTotalRow) RedText else TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (isTotalRow) FontWeight.Black else FontWeight.Medium,
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
    }
}

/**
 * 📦 মিনি মেট্রিক বক্স (স্লিমার ডিজাইন)
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
        shape = RoundedCornerShape(8.dp),
        color = MetricBoxBg,
        border = BorderStroke(0.6.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), // 🎯 কমপ্যাক্ট প্যাডিং
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 18.sp, // 🎯 স্লিক ফন্ট সাইজ
                fontWeight = FontWeight.Black
            )
            Text(
                text = subLabel,
                color = TextMuted.copy(alpha = 0.75f),
                fontSize = 9.sp
            )
        }
    }
}
