@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui.screens.analytics.tabs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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

// 🎨 ৩য় স্ক্রিনশটের হুবহু কালার প্যালেট
private val CyanProgress = Color(0xFF00E5FF)
private val ProgressTrack = Color(0xFF1E2433)
private val DividerColor = Color(0xFF161A26)
private val TextLight = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF8E95A5)

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
                fontSize = 13.sp
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 🚀 key = { post.slug } এবং animateItemPlacement ব্যবহার করা হয়েছে,
            // যার ফলে ভিউজ বাড়লে পোস্টটি স্মুথ অ্যানিমেশনের সাথে স্বয়ংক্রিয়ভাবে উপরে উঠে যাবে!
            items(
                items = sortedPosts,
                key = { it.slug.ifBlank { it.id } }
            ) { post ->
                RealtimePostRowItem(
                    post = post,
                    onClick = { onPostClick(post.slug) },
                    modifier = Modifier.animateItemPlacement(
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = FastOutSlowInEasing
                        )
                    )
                )
                HorizontalDivider(color = DividerColor, thickness = 0.6.dp)
            }
        }
    }
}

/**
 * 🎬 ৩য় স্ক্রিনশটের হুবহু রো আইটেম
 */
@Composable
private fun RealtimePostRowItem(
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 14.dp)
        ) {
            // ১. বামে থাম্বনেইল পোস্টার
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 52.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E2536)),
                contentAlignment = Alignment.Center
            ) {
                if (!post.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.posterUrl,
                        contentDescription = post.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ২. মাঝখানে টাইটেল এবং নিচে সায়ান কালারের প্রোগ্রেস বার
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = post.title,
                    color = TextLight,
                    fontSize = 12.8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // সায়ান রঙের প্রোগ্রেস বার (ছবি ৩ অনুযায়ী)
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = CyanProgress,
                    trackColor = ProgressTrack,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }

        // ৩. ডানে ভিউজ সংখ্যা (যেমন: 1.7K, 1.0K, 748 ইত্যাদি)
        Text(
            text = post.formattedViews,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
