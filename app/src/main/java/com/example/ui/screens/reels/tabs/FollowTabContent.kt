package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto

private val TextMuted = Color(0xFF8E95A5)
private val DarkCardBg = Color(0xFF141722)

/**
 * 👥 ১ নম্বর ছবির হুবহু Follow Tab লেআউট:
 * (সাজেস্টেড অ্যাকাউন্টস হেডার + FollowUserItemRow লিস্ট + ফলোয়িং ভিডিও ফিড)
 */
@Composable
fun FollowTabContent(
    suggestedPages: List<SuggestedPageDto>,
    followingFeedReels: List<UserReelDto>,
    safeTopPadding: Dp,
    onProfileClick: (pageId: Int) -> Unit,
    onFollowToggle: (pageId: Int, userId: Int) -> Unit,
    onDismissPage: (pageId: Int) -> Unit,
    onCloseHeaderClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = safeTopPadding, // 🎯 টপ বারের নিচে প্যাডিং নিশ্চিত করে ওভারল্যাপ রোধ
            bottom = 86.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ১ নম্বর ছবির মতো হেডার
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suggested Accounts",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Close",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onCloseHeaderClick() }
                )
            }
        }

        // ১ নম্বর ছবির মতো ইউজার রো-এর তালিকা
        if (suggestedPages.isNotEmpty()) {
            itemsIndexed(
                items = suggestedPages,
                key = { _, page -> "follow_row_${page.pageId}_${page.userId}" }
            ) { _, page ->
                FollowUserItemRow(
                    page = page,
                    onProfileClick = {
                        val targetId = if (page.pageId > 0) page.pageId else page.userId
                        onProfileClick(targetId)
                    },
                    onFollowClick = {
                        onFollowToggle(page.pageId, page.userId)
                    },
                    onDismissClick = {
                        onDismissPage(page.pageId)
                    }
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No more suggested creators",
                        color = TextMuted,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        // ফলো করা ক্রিয়েটরদের ফিড (যদি থাকে)
        if (followingFeedReels.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E2432), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "From creators you follow",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            itemsIndexed(
                items = followingFeedReels,
                key = { idx, reel -> "foll_feed_${reel.id}_$idx" }
            ) { _, reel ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCardBg)
                        .clickable { onReelClick(reel) }
                ) {
                    AsyncImage(
                        model = reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl,
                        contentDescription = reel.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222838))
                        ) {
                            AsyncImage(
                                model = reel.pageAvatar ?: "https://ui-avatars.com/api/?name=${reel.pageName}&background=00E676&color=000",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column {
                            Text(
                                text = reel.pageName,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = reel.title ?: reel.displayHandle,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
