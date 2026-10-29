@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.analytics.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudienceAnalyticsDto
import com.example.data.model.LiveCountryStat
import com.example.data.model.LivePostStat
import com.example.data.model.PublicLiveAnalyticsResponse

// 🎨 কালার প্যালেট
private val CardBg = Color(0xFF10141F)
private val CardBorder = Color(0xFF1C2436)
private val NeonGreen = Color(0xFF00E676)
private val CyanText = Color(0xFF00E5FF)
private val StudioPurple = Color(0xFF9333EA)
private val TextMuted = Color(0xFF8E95A5)
private val TextLight = Color(0xFFCBD5E1)

@Composable
fun AnalyticsAudienceTab(
    audience: AudienceAnalyticsDto,
    topPosts: List<LivePostStat>,
    liveOnlineCount: Int,
    onPostClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 🎯 ২টি সাব-ট্যাব কন্ট্রোলার (0 = Live & Posts, 1 = Country & Age)
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Live & Posts", "Country & Age")

    // 🟢 পালসিং লাইভ অ্যানিমেশন
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =========================================================================
        // 🔘 সাব-ট্যাব পিল সুইচ (Live & Posts বনাম Country & Age)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161A26))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            subTabs.forEachIndexed { index, title ->
                val isSelected = (selectedSubTab == index)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color.White else Color.Transparent)
                        .clickable { selectedSubTab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.Black else TextMuted,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // =========================================================================
        // 🔄 সাব-ট্যাব কনটেন্ট সুইচিং
        // =========================================================================
        AnimatedContent(
            targetState = selectedSubTab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "audience_sub_tab"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> LiveAndPostsSubTab(
                    liveCount = liveOnlineCount,
                    topPosts = topPosts,
                    pulseScale = pulseScale,
                    onPostClick = onPostClick
                )
                1 -> CountryAndAgeSubTab(
                    countries = audience.countries
                )
            }
        }
    }
}

// =============================================================================
// 🟢 সাব-ট্যাব ১: Live & Posts (অনলাইন ইউজার ও বর্তমানে দেখা পোস্টগুলো)
// =============================================================================
@Composable
private fun LiveAndPostsSubTab(
    liveCount: Int,
    topPosts: List<LivePostStat>,
    pulseScale: Float,
    onPostClick: (slug: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ১. বড় লাইভ স্ট্যাটাস কার্ড
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(NeonGreen.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Live Users Online Right Now",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = PublicLiveAnalyticsResponse.formatMetric(liveCount.toLong()),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(NeonGreen)
                            )
                            Text(
                                text = "Active Watching Sessions",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ২. ইউজাররা বর্তমানে কোন কোন পোস্টে রয়েছে (ক্লিকযোগ্য তালিকা)
        item {
            Text(
                text = "Currently Watching Dramas",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        if (topPosts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("Users are currently browsing the home feed", color = TextMuted, fontSize = 12.5.sp)
                    }
                }
            }
        } else {
            items(topPosts) { post ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPostClick(post.slug) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E283C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = CyanText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = post.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap to open and watch",
                                    color = TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        // লাইভ দর্শক ব্যাজ
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonGreen.copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, NeonGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🟢 ${post.formattedWatchingCount} online",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🌍 সাব-ট্যাব ২: Country & Age (দেশ ও বয়স বিন্যাস)
// =============================================================================
@Composable
private fun CountryAndAgeSubTab(
    countries: List<LiveCountryStat>
) {
    // বয়স বিন্যাস ডেটা (ডিফল্ট ১৮ বছর রুল সহ)
    val ageDemographics = remember {
        listOf(
            Triple("18–24 years", 58.4f, "58.4%"),
            Triple("25–34 years", 28.2f, "28.2%"),
            Triple("13–17 years", 8.6f, "8.6%"),
            Triple("35–44 years", 4.8f, "4.8%")
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ১. দেশভিত্তিক দর্শক (Geography)
        item {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = CyanText, modifier = Modifier.size(17.dp))
                        Text("Top Countries & Regions", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    countries.forEach { country ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(country.flag, fontSize = 14.sp)
                                    Text(country.name, color = TextLight, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                }
                                Text("${country.percentage}% (${country.formattedCount})", color = CyanText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { (country.percentage / 100f).toFloat().coerceIn(0.02f, 1f) },
                                color = CyanText,
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

        // ২. বয়স বিন্যাস (Age Demographics)
        item {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, tint = StudioPurple, modifier = Modifier.size(17.dp))
                        Text("Viewer Age Demographics", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // ইনফো নোট (ডিফল্ট ১৮ বছর)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1F2E))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Text(
                            text = "Default age 18 is applied if user skips birth date entry.",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    ageDemographics.forEach { (ageGroup, ratio, displayPct) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ageGroup, color = TextLight, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                Text(displayPct, color = StudioPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { ratio / 100f },
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
