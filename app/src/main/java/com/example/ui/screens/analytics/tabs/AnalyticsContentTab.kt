@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.analytics.tabs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentAnalyticsDto
import com.example.data.model.RealtimePostItem

// 🎨 প্রিমিয়াম লিডারবোর্ড কালার প্যালেট
private val CyanProgress = Color(0xFF00E5FF)
private val ProgressTrack = Color(0xFF161B28)
private val DividerColor = Color(0xFF141824)
private val GoldRank = Color(0xFFFFB300)
private val SilverRank = Color(0xFF00E5FF)
private val BronzeRank = Color(0xFFFF7A00)
private val TextLight = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 🎯 ড্রামার লম্বা SEO টাইটেল ফিল্টার করে সংক্ষিপ্ত Display Name তৈরি
 */
private fun extractCleanDisplayName(rawTitle: String): String {
    var clean = rawTitle.split("|")[0].split(" - Ep")[0].split("–")[0].trim()
    val removeSuffixes = listOf(
        "Bangla Dubbed All Episode",
        "Bangla Dubbed",
        "Hindi Dubbed Available",
        "Hindi Dubbed – Full Series",
        "Hindi Dubbed",
        "English Dubbed",
        "Available",
        "All Episode",
        "Full Series",
        "Season 1",
        "Season 2"
    )
    for (word in removeSuffixes) {
        clean = clean.replace(word, "", ignoreCase = true)
    }
    return clean.trim().ifBlank { rawTitle.split("|")[0].trim() }
}

@Composable
fun AnalyticsContentTab(
    content: ContentAnalyticsDto,
    onPostClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 🎯 ভিউজ সংখ্যা অনুযায়ী ডিসেন্ডিং অর্ডারে সাজানো (সর্বাধিক ভিউয়ের পোস্ট সবার উপরে থাকবে)
    val sortedPosts = remember(content.realtimePosts) {
        content.realtimePosts.sortedByDescending { it.views }
    }

    if (sortedPosts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No real-time post views recorded yet",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp) // 🎯 পোস্টগুলোর মাঝের গ্যাপ একদম স্লিম
        ) {
            itemsIndexed(
                items = sortedPosts,
                key = { _, post -> post.slug.ifBlank { post.id } }
            ) { index, post ->
                RealtimePostCompactRow(
                    rank = index + 1,
                    post = post,
                    onClick = { onPostClick(post.slug) },
                    modifier = Modifier.animateItemPlacement(
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = FastOutSlowInEasing
                        )
                    )
                )

                if (index < sortedPosts.size - 1) {
                    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

/**
 * 🎬 আল্ট্রা-কমপ্যাক্ট স্লিম রো আইটেম
 */
@Composable
private fun RealtimePostCompactRow(
    rank: Int,
    post: RealtimePostItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // প্রোগ্রেস বারের লাইভ অ্যানিমেশন
    val animatedProgress by animateFloatAsState(
        targetValue = post.progressRatio.coerceIn(0.04f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "post_progress"
    )

    // 🎯 সংক্ষিপ্ত পরিষ্কার নাম
    val cleanDisplayName = remember(post.title) {
        extractCleanDisplayName(post.title)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 3.5.dp, horizontal = 2.dp), // 🎯 অত্যন্ত স্লিম উচ্চতা
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            // ১. র‍্যাঙ্ক ব্যাজ (#1, #2, #3...)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (rank) {
                    1 -> GoldRank.copy(alpha = 0.15f)
                    2 -> SilverRank.copy(alpha = 0.12f)
                    3 -> BronzeRank.copy(alpha = 0.15f)
                    else -> Color(0xFF141926)
                },
                border = BorderStroke(
                    0.5.dp,
                    when (rank) {
                        1 -> GoldRank.copy(alpha = 0.6f)
                        2 -> SilverRank.copy(alpha = 0.5f)
                        3 -> BronzeRank.copy(alpha = 0.5f)
                        else -> Color(0xFF222B3D)
                    }
                ),
                modifier = Modifier.width(22.dp).height(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$rank",
                        color = when (rank) {
                            1 -> GoldRank
                            2 -> SilverRank
                            3 -> BronzeRank
                            else -> TextMuted
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // ২. ছোট কমপ্যাক্ট পোস্টার থাম্বনেইল
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 38.dp) // 🎯 সাইজ ছোট করা হয়েছে
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1A2234)),
                contentAlignment = Alignment.Center
            ) {
                if (!post.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.posterUrl,
                        contentDescription = cleanDisplayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ৩. সংক্ষিপ্ত Display Name এবং স্লিম প্রোগ্রেস বার
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = cleanDisplayName, // 👈 সংক্ষিপ্ত পরিষ্কার নাম
                    color = TextLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // চিকন স্লিম প্রোগ্রেস বার
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = if (rank == 1) GoldRank else CyanProgress,
                    trackColor = ProgressTrack,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(2.5.dp) // 🎯 আল্ট্রা-স্লিম উচ্চতা
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }

        // ৪. ডানপাশে ভিউজ সংখ্যা
        Text(
            text = post.formattedViews,
            color = if (rank == 1) GoldRank else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
