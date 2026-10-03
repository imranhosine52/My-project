package com.example.ui.screens.reels.tabs

import androidx.compose.animation.*
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
private val CardBg = Color(0xFF141824)
private val BorderColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)
private val CyanAccent = Color(0xFF00E5FF)

/**
 * 👥 Follow Tab Screen:
 * - উপরে স্লাইডিং লাইনে শুধুমাত্র "Following" থাকা চ্যানেলগুলো থাকবে (fans লেখা ছাড়া)।
 * - নিচে "Suggested" অংশে শুধুমাত্র আন-ফলো থাকা নতুন চ্যানেলগুলো থাকবে।
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

    val effectiveList = remember(suggestedPages, allReels) {
        if (suggestedPages.isNotEmpty()) {
            suggestedPages
        } else {
            allReels
                .filter { it.pageName.isNotBlank() || it.handle.isNotBlank() || it.pageId > 0 || it.userId > 0 }
                .groupBy { reel ->
                    if (reel.pageId > 0) "page_${reel.pageId}"
                    else if (reel.userId > 0) "user_${reel.userId}"
                    else reel.displayHandle.ifBlank { reel.pageName }
                }
                .map { (_, reelsOfCreator) ->
                    val first = reelsOfCreator.first()
                    val resolvedPageId = if (first.pageId > 0) first.pageId else if (first.userId > 0) first.userId else first.id
                    val resolvedUserId = if (first.userId > 0) first.userId else resolvedPageId

                    SuggestedPageDto(
                        pageId = resolvedPageId,
                        userId = resolvedUserId,
                        pageName = first.pageName.ifBlank { first.displayHandle.removePrefix("@") }.ifBlank { "Drama Creator" },
                        handle = first.displayHandle,
                        avatar = first.pageAvatar ?: "https://ui-avatars.com/api/?name=${first.pageName}&background=00E676&color=000&bold=true",
                        category = "Entertainment",
                        rawFollowersCount = 0L,
                        rawTotalReels = reelsOfCreator.size,
                        rawIsFollowing = first.isFollowing
                    )
                }
        }
    }

    // =========================================================================
    // 🎯 ফিল্টারিং: ১. ফলো করা পেজগুলো উপরে | ২. আন-ফলো পেজগুলো নিচে
    // =========================================================================
    val followingList = remember(effectiveList) {
        effectiveList.filter { it.isFollowing }
    }

    val unfollowedSuggestedList = remember(effectiveList) {
        effectiveList.filter { !it.isFollowing }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = safeTopPadding + 14.dp,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // =========================================================================
        // 🌟 ১. উপরের স্লাইডিং লাইন: শুধুমাত্র "Following" থাকা চ্যানেলগুলো
        // =========================================================================
        if (followingList.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🌟 Following (${followingList.size})",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 🎯 অনুভূমিক স্লাইডিং সিস্টেম
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(followingList, key = { "following_${it.pageId}_${it.userId}" }) { creator ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBg,
                                border = BorderStroke(0.8.dp, BorderColor),
                                modifier = Modifier
                                    .width(125.dp)
                                    .clickable { onProfileClick(creator.pageId) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E2838))
                                            .border(1.2.dp, CyanAccent.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(creator.avatar)
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

                                    // 🎯 fans লেখাটি সম্পূর্ণ বাদ দেওয়া হয়েছে
                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Following বাটন (ক্লিক করলে আন-ফলো হয়ে নিচে চলে যাবে)
                                    Button(
                                        onClick = { onFollowToggle(creator.pageId, creator.userId) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF263248)
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(26.dp)
                                    ) {
                                        Text(
                                            text = "Following",
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

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color(0xFF1E2838), thickness = 0.6.dp)
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
        // 👥 ৩. নিচের লাইন: শুধুমাত্র আন-ফলো থাকা পেজগুলো (Suggested List)
        // =========================================================================
        if (unfollowedSuggestedList.isNotEmpty()) {
            itemsIndexed(
                items = unfollowedSuggestedList,
                key = { _, page -> "suggested_row_${page.pageId}_${page.userId}" }
            ) { _, page ->
                FollowUserItemRow(
                    page = page,
                    onProfileClick = {
                        val targetId = if (page.pageId > 0) page.pageId else page.userId
                        onProfileClick(targetId)
                    },
                    onFollowClick = {
                        // ক্লিক করলে এটি সাথে সাথে উপরে Following লাইনে চলে যাবে
                        onFollowToggle(page.pageId, page.userId)
                    },
                    onDismissClick = {
                        onDismissPage(page.pageId)
                    }
                )
            }
        } else {
            // যদি সব পেজ ফলো করা হয়ে যায়
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You are following all available creators! ✨",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
