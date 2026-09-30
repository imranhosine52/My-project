package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.SuggestedPageDto
import com.example.data.model.UserReelDto

private val TikTokRed = Color(0xFFFE2C55)
private val CardBg = Color(0xFF161B26)
private val BorderColor = Color(0xFF242E40)
private val TextMuted = Color(0xFF8E95A5)
private val CyanAccent = Color(0xFF00E5FF)

/**
 * 👥 Follow Tab Screen:
 * - শীর্ষে অনুভূমিক সাজেস্টেড ক্রিয়েটর রো (Horizontal Suggested Creators Row)
 * - নিচে উল্লম্ব সাজেস্টেড অ্যাকাউন্টস তালিকা (Vertical Accounts List)
 */
@Composable
fun FollowTabContent(
    suggestedPages: List<SuggestedPageDto>,
    allReels: List<UserReelDto> = emptyList(),
    safeTopPadding: Dp,
    onProfileClick: (pageId: Int) -> Unit,
    onFollowToggle: (pageId: Int, userId: Int) -> Unit,
    onDismissPage: (pageId: Int) -> Unit,
    onCloseHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // সার্ভারের সাজেস্টেড পেজ খালি থাকলে রিলসের ক্রিয়েটরদের দিয়ে লিস্ট তৈরি
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
                        rawFollowersCount = first.likesCount * 2,
                        rawTotalReels = reelsOfCreator.size,
                        rawIsFollowing = first.isFollowing
                    )
                }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = safeTopPadding,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // =========================================================================
        // 🌟 ১. শীর্ষে অনুভূমিক সাজেস্টেড ক্রিয়েটর রো (Horizontal Creator Carousel)
        // =========================================================================
        if (effectiveList.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🌟 Featured Creators",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(effectiveList.take(10), key = { "top_c_${it.pageId}_${it.userId}" }) { creator ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBg,
                                border = BorderStroke(0.8.dp, BorderColor),
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable { onProfileClick(creator.pageId) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    // ক্রিয়েটরের সার্কুলার অবতার
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E2838))
                                            .border(1.2.dp, CyanAccent.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(creator.avatar ?: "https://ui-avatars.com/api/?name=${creator.pageName}&background=1E2838&color=fff")
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = creator.pageName,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    // নাম
                                    Text(
                                        text = creator.pageName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )

                                    // ক্যাটাগরি / ফলোয়ার্স
                                    Text(
                                        text = "${creator.formattedFollowers} fans",
                                        color = TextMuted,
                                        fontSize = 10.5.sp
                                    )

                                    // ১-ক্লিক ফলো বাটন
                                    Button(
                                        onClick = { onFollowToggle(creator.pageId, creator.userId) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (creator.isFollowing) Color(0xFF263248) else TikTokRed
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(26.dp)
                                    ) {
                                        Text(
                                            text = if (creator.isFollowing) "Following" else "+ Follow",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.6.dp)
            }
        }

        // =========================================================================
        // 📋 ২. হেডার রো: "Suggested accounts" এবং "Close"
        // =========================================================================
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
                    fontSize = 14.5.sp,
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

        // =========================================================================
        // 👥 ৩. উল্লম্ব সাজেস্টেড অ্যাকাউন্টস রো তালিকা
        // =========================================================================
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
