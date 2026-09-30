package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto

private val TextMuted = Color(0xFF8E95A5)

/**
 * 👥 ২ নম্বর ছবির হুবহু Follow Tab:
 * (কোনো ভিডিও থাকবে না, সম্পূর্ণ স্ক্রিন জুড়ে কেবল সাজেস্টেড পেজের লিস্ট থাকবে)
 */
@Composable
fun FollowTabContent(
    suggestedPages: List<SuggestedPageDto>,
    allReels: List<UserReelDto> = emptyList(), // 🎯 পেজ খালি থাকলে রিলস ক্রিয়েটরদের দেখানোর ফলব্যাক
    safeTopPadding: Dp,
    onProfileClick: (pageId: Int) -> Unit,
    onFollowToggle: (pageId: Int, userId: Int) -> Unit,
    onDismissPage: (pageId: Int) -> Unit,
    onCloseHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ২ নম্বর ছবির মতো নিশ্চিত লিস্ট: যদি সার্ভারের suggestedPages খালি থাকে, তবে রিলস ক্রিয়েটরদের দিয়ে পূর্ণ থাকবে
    val effectiveList = remember(suggestedPages, allReels) {
        if (suggestedPages.isNotEmpty()) {
            suggestedPages
        } else {
            allReels
                .filter { it.userId > 0 }
                .groupBy { it.userId }
                .map { (creatorId, reelsOfCreator) ->
                    val first = reelsOfCreator.first()
                    val pId = if (first.pageId > 0) first.pageId else creatorId
                    SuggestedPageDto(
                        pageId = pId,
                        userId = creatorId,
                        pageName = first.pageName.ifBlank { "Drama Creator" },
                        handle = first.displayHandle,
                        avatar = first.pageAvatar,
                        category = "Entertainment",
                        rawFollowersCount = first.likesCount * 3,
                        totalReels = reelsOfCreator.size,
                        rawIsFollowing = first.isFollowing
                    )
                }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = safeTopPadding,
            bottom = 50.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ২ নম্বর ছবির মতো হেডার
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suggested accounts",
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

        // ২ নম্বর ছবির মতো উল্লম্ব পেজ সাজেস্ট লিস্ট
        if (effectiveList.isNotEmpty()) {
            itemsIndexed(
                items = effectiveList,
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
    }
}
