@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*

// 🎨 প্রিমিয়াম ডার্ক থিম কালার প্যালেট
private val DarkBg = Color(0xFF080C14)
private val CardBg = Color(0xFF101624)
private val CardBorder = Color(0xFF1E283C)
private val NeonGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val GoldAccent = Color(0xFFFFB300)
private val RedAccent = Color(0xFFFF3D57)
private val PurpleAccent = Color(0xFFA855F7)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun GlobalAnalyticsScreen(
    analyticsData: PublicLiveAnalyticsResponse?,
    isLoading: Boolean,
    onClose: () -> Unit,
    onPostClick: (slug: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🟢 লাইভ স্পন্দন অ্যানিমেশন
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val data = analyticsData ?: PublicLiveAnalyticsResponse(liveOnlineCount = 1)
    val growth = data.growth ?: GrowthAnalyticsStats()
    val traffic = data.traffic ?: TrafficAnalyticsStats()
    val engagement = data.engagement ?: EngagementAnalyticsStats()

    Scaffold(
        containerColor = DarkBg,
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "GLOBAL ANALYTICS & INSIGHTS",
                            color = NeonGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF182030))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = CardBorder, thickness = 0.8.dp)
            }
        }
    ) { innerPadding ->
        if (isLoading && analyticsData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonGreen, strokeWidth = 3.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =============================================================
                // 🟢 ১. লাইভ অ্যাক্টিভ অনলাইন কার্ড
                // =============================================================
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.radialGradient(
                                        listOf(NeonGreen.copy(alpha = 0.14f), Color.Transparent)
                                    )
                                )
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Active Live Viewers",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = data.formattedLiveCount,
                                    color = Color.White,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(NeonGreen)
                                    )
                                    Text(
                                        text = "Synced Live with Cloudflare & Redis",
                                        color = NeonGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 📈 ২. অ্যাপ গ্রোথ ও রিটেনশন (ইন্সটল ও আনইন্সটল)
                // =============================================================
                item {
                    SectionTitle(icon = Icons.Default.TrendingUp, title = "App Growth & Retention", tint = NeonGreen)
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricMiniCard(
                                    label = "Total Installs",
                                    value = growth.formattedLifetimeInstalls,
                                    subLabel = "+${growth.formattedTodayInstalls} today",
                                    color = NeonGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricMiniCard(
                                    label = "Total Uninstalls",
                                    value = growth.formattedLifetimeUninstalls,
                                    subLabel = "-${growth.formattedTodayUninstalls} today",
                                    color = RedAccent,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricMiniCard(
                                    label = "Active Retained Users",
                                    value = growth.formattedActiveBase,
                                    subLabel = "Net installed base",
                                    color = CyanAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricMiniCard(
                                    label = "Retention Rate",
                                    value = "${growth.retentionRate}%",
                                    subLabel = "Loyal active ratio",
                                    color = GoldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // 📱 ৩. ডিভাইস ইন্টেলিজেন্স (Top Brands)
                // =============================================================
                if (data.topBrands.isNotEmpty()) {
                    item {
                        SectionTitle(icon = Icons.Default.Smartphone, title = "Top Device Brands", tint = PurpleAccent)
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                data.topBrands.forEach { brand ->
                                    BrandStatRow(brand = brand)
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 🚦 ৪. ট্র্যাফিক ও স্ট্রিমিং ওভারভিউ
                // =============================================================
                item {
                    SectionTitle(icon = Icons.Default.Visibility, title = "Traffic & Stream Overview", tint = CyanAccent)
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricMiniCard(
                                    label = "Today's Visitors",
                                    value = traffic.formattedTodayVisitors,
                                    subLabel = "Unique devices",
                                    color = CyanAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricMiniCard(
                                    label = "Today's Views",
                                    value = traffic.formattedTodayPageViews,
                                    subLabel = "Screen views",
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricMiniCard(
                                    label = "Video Streams",
                                    value = traffic.formattedTodayVideoViews,
                                    subLabel = "Dramas played today",
                                    color = GoldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricMiniCard(
                                    label = "Lifetime Visitors",
                                    value = traffic.formattedLifetimeVisitors,
                                    subLabel = "Total all time",
                                    color = NeonGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // 💬 ৫. কমিউনিটি এনগেজমেন্ট (Likes, Comments, Shares)
                // =============================================================
                item {
                    SectionTitle(icon = Icons.Default.Favorite, title = "Community Engagement", tint = RedAccent)
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EngagementMetric(
                                icon = Icons.Default.Favorite,
                                value = engagement.formattedLikes,
                                label = "Likes",
                                tint = RedAccent
                            )
                            VerticalDivider(color = CardBorder, modifier = Modifier.height(35.dp))
                            EngagementMetric(
                                icon = Icons.Default.ChatBubble,
                                value = engagement.formattedComments,
                                label = "Comments",
                                tint = CyanAccent
                            )
                            VerticalDivider(color = CardBorder, modifier = Modifier.height(35.dp))
                            EngagementMetric(
                                icon = Icons.Default.Share,
                                value = engagement.formattedShares,
                                label = "Shares",
                                tint = NeonGreen
                            )
                        }
                    }
                }

                // =============================================================
                // 🌍 ৬. টপ কান্ট্রিজ অনলাইন
                // =============================================================
                if (data.countries.isNotEmpty()) {
                    item {
                        SectionTitle(icon = Icons.Default.Public, title = "Live Audience by Country", tint = CyanAccent)
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                data.countries.forEach { country ->
                                    CountryLiveRow(country = country)
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 🔥 ৭. এই মুহূর্তে সর্বাধিক দেখা শোজ (Top Watched)
                // =============================================================
                if (data.topPosts.isNotEmpty()) {
                    item {
                        SectionTitle(icon = Icons.Default.LocalFireDepartment, title = "Top Watched Dramas Right Now", tint = GoldAccent)
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                data.topPosts.forEachIndexed { index, post ->
                                    TopWatchedDramaRow(
                                        rank = index + 1,
                                        post = post,
                                        onClick = {
                                            if (post.slug.isNotBlank()) {
                                                onClose()
                                                onPostClick(post.slug)
                                            }
                                        }
                                    )
                                    if (index < data.topPosts.size - 1) {
                                        HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 📍 ৮. স্ক্রিন অবস্থান বিন্যাস
                // =============================================================
                if (data.activeScreens.isNotEmpty()) {
                    item {
                        SectionTitle(icon = Icons.Default.ScreenShare, title = "Active Audience by Screen", tint = PurpleAccent)
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                data.activeScreens.forEach { screen ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• ${screen.cleanScreenName}", color = Color(0xFFCBD5E1), fontSize = 12.5.sp)
                                        Text("${screen.percentage}%", color = PurpleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// সেকশন টাইটেল
// -------------------------------------------------------------
@Composable
private fun SectionTitle(icon: ImageVector, title: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    }
}

// -------------------------------------------------------------
// মিনি মেট্রিক কার্ড
// -------------------------------------------------------------
@Composable
private fun MetricMiniCard(
    label: String,
    value: String,
    subLabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF141C2B),
        border = BorderStroke(0.6.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text(subLabel, color = TextMuted.copy(alpha = 0.8f), fontSize = 9.5.sp)
        }
    }
}

// -------------------------------------------------------------
// ডিভাইস ব্র্যান্ড রো
// -------------------------------------------------------------
@Composable
private fun BrandStatRow(brand: LiveBrandStat) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(brand.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("${brand.percentage}% (${brand.count})", color = PurpleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (brand.percentage / 100f).toFloat().coerceIn(0f, 1f) },
            color = PurpleAccent,
            trackColor = Color(0xFF1A2234),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

// -------------------------------------------------------------
// কান্ট্রি রো
// -------------------------------------------------------------
@Composable
private fun CountryLiveRow(country: LiveCountryStat) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(country.flag, fontSize = 16.sp)
                Text(country.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("${country.percentage}% (${country.formattedCount})", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (country.percentage / 100f).toFloat().coerceIn(0f, 1f) },
            color = CyanAccent,
            trackColor = Color(0xFF1A2234),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

// -------------------------------------------------------------
// এনগেজমেন্ট আইটেম
// -------------------------------------------------------------
@Composable
private fun EngagementMetric(icon: ImageVector, value: String, label: String, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextMuted, fontSize = 10.sp)
    }
}

// -------------------------------------------------------------
// টপ ড্রামা রো
// -------------------------------------------------------------
@Composable
private fun TopWatchedDramaRow(
    rank: Int,
    post: LivePostStat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (rank == 1) GoldAccent else Color(0xFF1E283C),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rank.toString(),
                        color = if (rank == 1) Color.Black else Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                text = post.title,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = NeonGreen.copy(alpha = 0.12f),
            border = BorderStroke(0.6.dp, NeonGreen.copy(alpha = 0.4f))
        ) {
            Text(
                text = "${post.formattedWatchingCount} watching",
                color = NeonGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}
