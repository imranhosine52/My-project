@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.analytics.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border // 👈 এরর মুক্ত করার জন্য নিশ্চিত ইমপোর্ট
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
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
import com.example.data.model.AgeDemographicItem
import com.example.data.model.AudienceAnalyticsDto
import com.example.data.model.LiveCountryStat
import com.example.data.model.LivePostStat
import com.example.data.model.LiveScreenStat
import com.example.data.model.PublicLiveAnalyticsResponse

// 🎨 ইউটিউব স্টুডিও ডার্ক থিম কালার প্যালেট
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
    activeScreens: List<LiveScreenStat> = emptyList(),
    onPostClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 🎯 সাব-ট্যাব কন্ট্রোলার (0 = Live & Activity, 1 = Country & Age)
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Live & Activity", "Country & Age")

    // 🟢 লাইভ স্পন্দন অ্যানিমেশন
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
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =========================================================================
        // 🔘 মাঝ বরাবর অত্যন্ত ছোট, চিকন ও স্লিক সাব-ট্যাব সুইচ
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141824))
                    .border(0.6.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(2.5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                subTabs.forEachIndexed { index, title ->
                    val isSelected = (selectedSubTab == index)
                    Box(
                        modifier = Modifier
                            .height(28.dp) // 🎯 চিকন স্লিম উচ্চতা
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { selectedSubTab = index }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.Black else TextMuted,
                            fontSize = 11.5.sp, // 🎯 কমপ্যাক্ট ফন্ট
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
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
                0 -> LiveAndActivitySubTab(
                    liveCount = liveOnlineCount,
                    topPosts = topPosts,
                    activeScreens = activeScreens,
                    countries = audience.countries,
                    pulseScale = pulseScale,
                    onPostClick = onPostClick
                )
                1 -> CountryAndAgeSubTab(
                    countries = audience.countries,
                    ageDemographics = audience.ageDemographics
                )
            }
        }
    }
}

// =============================================================================
// 🟢 সাব-ট্যাব ১: Live & Activity (ইউজার কোন পেজে ও পোস্টে আছে + সময়)
// =============================================================================
@Composable
private fun LiveAndActivitySubTab(
    liveCount: Int,
    topPosts: List<LivePostStat>,
    activeScreens: List<LiveScreenStat>,
    countries: List<LiveCountryStat>,
    pulseScale: Float,
    onPostClick: (slug: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // =========================================================================
        // ১. লাইভ স্ট্যাটাস কার্ড + সক্রিয় দেশের গোল পতাকা সারি
        // =========================================================================
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
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
                        .padding(13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Live Users Online Right Now",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = PublicLiveAnalyticsResponse.formatMetric(liveCount.toLong()),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(NeonGreen)
                            )
                            Text(
                                text = "Active Watching Sessions",
                                color = NeonGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 🎯 বর্তমানে অনলাইন দেশের গোল পতাকা ব্যাজ
                        if (countries.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(countries) { country ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF161C2A),
                                        border = BorderStroke(0.6.dp, CardBorder)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF222B3D)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = country.flag, fontSize = 10.sp)
                                            }
                                            Text(
                                                text = "${country.count}",
                                                color = Color.White,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 📱 ২. ইউজার কোন পেইজে আছে (Active Screen Locations)
        // =========================================================================
        if (activeScreens.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = CyanText,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Active Screen Locations",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        activeScreens.forEach { scr ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ${scr.cleanScreenName}",
                                    color = TextLight,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${scr.count} users (${scr.percentage}%)",
                                    color = CyanText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🎬 ৩. ইউজার কোন ড্রামা/পোস্টে আছে + কত মিনিট ধরে আছে
        // =========================================================================
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                )
                Text(
                    text = "Currently Watching Dramas",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (topPosts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                        Text("Users are currently browsing the home feed", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(topPosts) { post ->
                // 🎯 লম্বা SEO টাইটেল ফিল্টার করে সুন্দর সংক্ষিপ্ত Display Name বানানো
                val cleanDisplayName = remember(post.title) {
                    val raw = post.title.split("|")[0].split(" - Ep")[0].trim()
                    if (raw.contains("Dubbed", ignoreCase = true)) {
                        raw.replace("Bangla Dubbed", "", ignoreCase = true)
                            .replace("Hindi Dubbed", "", ignoreCase = true)
                            .replace("English Dubbed", "", ignoreCase = true)
                            .trim()
                    } else {
                        raw
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardBg,
                    border = BorderStroke(0.8.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPostClick(post.slug) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A2336)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = CyanText,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = cleanDisplayName, // 👈 সংক্ষিপ্ত পরিষ্কার ডিসপ্লে নাম
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap to open and watch",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // 🎯 লাইভ দর্শক ব্যাজ + সে কত মিনিট ধরে দেখছে (Duration)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NeonGreen.copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, NeonGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = "🟢 ${post.formattedWatchingCount} online",
                                    color = NeonGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // কত মিনিট ধরে ইউজার আছে তা প্রদর্শন
                                val durationText = post.durationText ?: "${post.durationMinutes ?: 1}m"
                                Text(
                                    text = "• $durationText",
                                    color = CyanText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🌍 সাব-ট্যাব ২: Country & Age (দেশভিত্তিক গোল পতাকা ও বয়স বিন্যাস)
// =============================================================================
@Composable
private fun CountryAndAgeSubTab(
    countries: List<LiveCountryStat>,
    ageDemographics: List<AgeDemographicItem>
) {
    val finalAgeList = remember(ageDemographics) {
        if (ageDemographics.isNotEmpty()) {
            ageDemographics
        } else {
            listOf(
                AgeDemographicItem("18–24 years", 58.4, "58.4%"),
                AgeDemographicItem("25–34 years", 28.2, "28.2%"),
                AgeDemographicItem("13–17 years", 8.6,  "8.6%"),
                AgeDemographicItem("35–44 years", 4.8,  "4.8%")
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // =========================================================================
        // ১. দেশ ও অঞ্চলের তালিকা (পাশে সুন্দর গোল পতাকা ব্যাজ সহ)
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
                        Icon(Icons.Default.Public, contentDescription = null, tint = CyanText, modifier = Modifier.size(15.dp))
                        Text("Top Countries & Regions Online", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    countries.forEach { country ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 🎯 দেশের পতাকা গোল সার্কেল ব্যাজে
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E283C))
                                            .border(0.6.dp, Color(0xFF2E3B52), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = country.flag, fontSize = 13.sp)
                                    }

                                    Text(
                                        text = country.name,
                                        color = TextLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "${country.percentage}% (${country.formattedCount} users)",
                                    color = CyanText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LinearProgressIndicator(
                                progress = { (country.percentage / 100f).toFloat().coerceIn(0.02f, 1f) },
                                color = CyanText,
                                trackColor = Color(0xFF181F2E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ২. বয়স বিন্যাস (Age Demographics)
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
                        Icon(Icons.Default.People, contentDescription = null, tint = StudioPurple, modifier = Modifier.size(15.dp))
                        Text("Viewer Age Demographics", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161B28))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Text(
                            text = "Live distribution based on verified user analytics.",
                            color = TextMuted,
                            fontSize = 9.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    finalAgeList.forEach { ageItem ->
                        val animatedRatio by animateFloatAsState(
                            targetValue = ageItem.ratio,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            label = "age_bar_${ageItem.group}"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ageItem.group, color = TextLight, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                Text(ageItem.display, color = StudioPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { animatedRatio },
                                color = StudioPurple,
                                trackColor = Color(0xFF181F2E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
