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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveCountryStat
import com.example.data.model.LivePostStat
import com.example.data.model.LiveScreenStat
import com.example.data.model.PublicLiveAnalyticsResponse

private val DeepSheetBg = Color(0xFF0C101A)
private val CardSurfaceBg = Color(0xFF141A28)
private val BorderStrokeColor = Color(0xFF222B3D)
private val NeonGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val GoldAccent = Color(0xFFFFB300)

@Composable
fun PublicLiveAnalyticsSheet(
    analyticsData: PublicLiveAnalyticsResponse?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPostClick: (slug: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🟢 লাইভ পালসিং অ্যানিমেশন (নিয়ন সবুজ স্পন্দন)
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepSheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            // ড্র্যাগ হ্যান্ডেল
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF2A364C))
                )
            }

            // হেডার রো
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // পালসিং লাইভ ডট
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(NeonGreen)
                    )

                    Text(
                        text = "LIVE AUDIENCE MONITOR",
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2638))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF8E95A5),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

            if (isLoading && analyticsData == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonGreen, strokeWidth = 2.5.dp)
                }
            } else {
                val data = analyticsData ?: PublicLiveAnalyticsResponse(liveOnlineCount = 1)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // =============================================================
                    // 👥 ১. মোট লাইভ অডিয়েন্স বিগ কার্ড
                    // =============================================================
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CardSurfaceBg,
                            border = BorderStroke(1.dp, BorderStrokeColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                NeonGreen.copy(alpha = 0.12f),
                                                Color.Transparent
                                            )
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
                                        text = "Active Viewers Right Now",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Text(
                                        text = data.formattedLiveCount,
                                        color = Color.White,
                                        fontSize = 38.sp,
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
                                            text = "Synchronized via High-Speed Redis Engine",
                                            color = NeonGreen,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 🌍 ২. দেশভিত্তিক লাইভ ডিস্ট্রিবিউশন
                    // =============================================================
                    if (data.countries.isNotEmpty()) {
                        item {
                            SectionHeaderBadge(
                                icon = Icons.Default.Public,
                                title = "Top Countries Online",
                                iconColor = CyanAccent
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardSurfaceBg,
                                border = BorderStroke(0.8.dp, BorderStrokeColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    data.countries.forEach { country ->
                                        CountryLiveStatRow(country = country)
                                    }
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 🔥 ৩. এই মুহূর্তে সর্বাধিক দেখা শোসমূহ (Top Watched Shows)
                    // =============================================================
                    if (data.topPosts.isNotEmpty()) {
                        item {
                            SectionHeaderBadge(
                                icon = Icons.Default.LocalFireDepartment,
                                title = "Top Watched Shows Right Now",
                                iconColor = GoldAccent
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardSurfaceBg,
                                border = BorderStroke(0.8.dp, BorderStrokeColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    data.topPosts.forEachIndexed { index, post ->
                                        TopWatchedPostRow(
                                            rank = index + 1,
                                            post = post,
                                            onClick = {
                                                if (post.slug.isNotBlank()) {
                                                    onDismiss()
                                                    onPostClick(post.slug)
                                                }
                                            }
                                        )
                                        if (index < data.topPosts.size - 1) {
                                            HorizontalDivider(
                                                color = BorderStrokeColor.copy(alpha = 0.5f),
                                                thickness = 0.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =============================================================
                    // 📍 ৪. স্ক্রিনভিত্তিক সক্রিয়তা
                    // =============================================================
                    if (data.activeScreens.isNotEmpty()) {
                        item {
                            SectionHeaderBadge(
                                icon = Icons.Default.ScreenShare,
                                title = "Audience by Screen Location",
                                iconColor = Color(0xFFA855F7)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardSurfaceBg,
                                border = BorderStroke(0.8.dp, BorderStrokeColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    data.activeScreens.forEach { screenStat ->
                                        ScreenLiveStatRow(screenStat = screenStat)
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
// সেকশন হেডার ব্যাজ
// -------------------------------------------------------------
@Composable
private fun SectionHeaderBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// -------------------------------------------------------------
// দেশভিত্তিক লাইভ রো
// -------------------------------------------------------------
@Composable
private fun CountryLiveStatRow(country: LiveCountryStat) {
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
                Text(text = country.flag, fontSize = 16.sp)
                Text(
                    text = country.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${country.percentage}% (${country.formattedCount})",
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // প্রোগ্রেস বার
        LinearProgressIndicator(
            progress = { (country.percentage / 100f).toFloat().coerceIn(0f, 1f) },
            color = CyanAccent,
            trackColor = Color(0xFF1B2436),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

// -------------------------------------------------------------
// টপ ড্রামা রো
// -------------------------------------------------------------
@Composable
private fun TopWatchedPostRow(
    rank: Int,
    post: LivePostStat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (rank == 1) GoldAccent else Color(0xFF222B3D),
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rank.toString(),
                        color = if (rank == 1) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Text(
                text = post.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NeonGreen.copy(alpha = 0.12f),
            border = BorderStroke(0.6.dp, NeonGreen.copy(alpha = 0.4f))
        ) {
            Text(
                text = "${post.formattedWatchingCount} watching",
                color = NeonGreen,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// স্ক্রিনভিত্তিক লাইভ রো
// -------------------------------------------------------------
@Composable
private fun ScreenLiveStatRow(screenStat: LiveScreenStat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "• ${screenStat.cleanScreenName}",
            color = Color(0xFFCBD5E1),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "${screenStat.percentage}%",
            color = Color(0xFFA855F7),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
