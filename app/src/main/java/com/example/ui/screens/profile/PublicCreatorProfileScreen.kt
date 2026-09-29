@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserReelDto
import com.example.ui.VipCrown3DIcon
import com.example.ui.viewmodel.ReelsViewModel
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val TikTokRed = Color(0xFFFE2C55)
private val DarkButtonBg = Color(0xFF1F222A)
private val TextMuted = Color(0xFF8692A6)
private val ActionGreen = Color(0xFF00E676)

@Composable
fun PublicCreatorProfileScreen(
    pageId: Int,
    reelsViewModel: ReelsViewModel,
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenDirectMessage: (creatorId: String, creatorName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 🎯 সার্ভার স্পেসিফিকেশন ১: স্ক্রিন ওপেন হতেই লাইভ মেট্রিক্স ফেচ হবে
    LaunchedEffect(pageId) {
        reelsViewModel.loadUserProfileMetrics(targetUserId = pageId)
    }

    val profileUiState by reelsViewModel.profileState.collectAsStateWithLifecycle()
    val creatorProfile = profileUiState.profile

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Reels, 1: Reposts

    // এই ক্রিয়েটরের আপলোড করা রিলস তালিকা
    val feedState by reelsViewModel.feedState.collectAsStateWithLifecycle()
    val pageReels = remember(feedState.reels, pageId) {
        feedState.reels.filter { it.pageId == pageId || it.userId == pageId }
    }

    // সার্ভারের আসল ডাটা বাইন্ডিং (কোনো ডামি সংখ্যা নেই)
    val pageTitle = creatorProfile?.displayName ?: "Creator"
    val pageHandle = creatorProfile?.displayHandle ?: "@creator"
    val pageAvatar = creatorProfile?.avatar
    val isFollowing = creatorProfile?.isFollowing ?: false

    val followersText = creatorProfile?.formattedFollowers ?: "0"
    val followingText = creatorProfile?.formattedFollowing ?: "0"
    val likesText = creatorProfile?.formattedLikes ?: "0"

    val publicPageUrl = "https://playdramaflix.com/page/${pageHandle.removePrefix("@")}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        if (profileUiState.isLoading && creatorProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ActionGreen, strokeWidth = 3.dp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // =========================================================================
                // 🔝 ১. ক্রিয়েটর হেডার সেকশন (সম্পূর্ণ আসল ডাটা বাইন্ডেড)
                // =========================================================================
                item(span = { GridItemSpan(3) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // টপ বার: [ ← Back ] ----------------- [ 🔔 Notification ] [ ↗ Share ]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        Toast.makeText(context, "Notifications enabled for $pageTitle", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notification", tint = Color.White)
                                }

                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Check out $pageTitle ($pageHandle) on DramaFlix:\n$publicPageUrl"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Creator Profile"))
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                                }
                            }
                        }

                        // ২. নাম, হ্যান্ডেল ও অবতার
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = pageTitle,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (creatorProfile?.hasPage == true) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified Page",
                                            tint = ActionGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (creatorProfile?.isVip == true) {
                                        VipCrown3DIcon(modifier = Modifier.size(20.dp, 16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = pageHandle,
                                    color = TextMuted,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 🔥 ৩টি রিয়েল মেট্রিক্স কাউন্টার (কোনো ডামি হিসাব নেই)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = followingText,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text("Following", color = TextMuted, fontSize = 11.5.sp)
                                    }

                                    Column {
                                        Text(
                                            text = followersText,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text("Followers", color = TextMuted, fontSize = 11.5.sp)
                                    }

                                    Column {
                                        Text(
                                            text = likesText,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text("Likes", color = TextMuted, fontSize = 11.5.sp)
                                    }
                                }
                            }

                            // ডানপাশের প্রোফাইল অবতার
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color(0xFF222838), CircleShape)
                                    .background(Color(0xFF1E2432)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!pageAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(pageAvatar)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = pageTitle,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = pageTitle.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // বায়ো (যদি সার্ভার পাঠায়)
                        if (!creatorProfile?.bio.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = creatorProfile!!.bio!!,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // =========================================================================
                        // 🔘 ৩. বাটন রো: [ Follow / Following ] [ Message ] [ 👤+ ]
                        // =========================================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ফলো/আনফলো বাটন (সার্ভারের লাইভ স্টেট ও অপটিমিস্টিক আপডেট সহ)
                            Button(
                                onClick = {
                                    reelsViewModel.toggleFollowUser(pageId)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) DarkButtonBg else TikTokRed
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Message বাটন
                            Button(
                                onClick = {
                                    onOpenDirectMessage(creatorProfile?.userId?.toString() ?: pageId.toString(), pageTitle)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkButtonBg),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "Message",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // সাজেস্ট অপশন বাটন
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkButtonBg,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        Toast.makeText(context, "More creator suggestions", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Add",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // =========================================================================
                        // 📑 ৪. ট্যাব বার: [ 📊 Grid ]  [ 🔁 Reposts ]
                        // =========================================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { selectedTabIndex = 0 }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewStream,
                                    contentDescription = "Reels",
                                    tint = if (selectedTabIndex == 0) Color.White else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(2.dp)
                                        .background(if (selectedTabIndex == 0) Color.White else Color.Transparent)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { selectedTabIndex = 1 }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Reposts",
                                    tint = if (selectedTabIndex == 1) Color.White else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(2.dp)
                                        .background(if (selectedTabIndex == 1) Color.White else Color.Transparent)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF222634), thickness = 0.6.dp)
                    }
                }

                // =========================================================================
                // 🎬 ৫. ৩-কলাম রিলস গ্রিড (আসল ভিউ কাউন্ট সহ)
                // =========================================================================
                if (pageReels.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No public reels posted yet",
                                color = TextMuted,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                } else {
                    items(pageReels, key = { it.id }) { reel ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.68f)
                                .background(Color(0xFF141722))
                                .clickable { onReelClick(reel) }
                        ) {
                            AsyncImage(
                                model = reel.thumbUrl?.takeIf { it.isNotBlank() } ?: reel.videoUrl,
                                contentDescription = reel.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // ভিউ কাউন্টার (▷ 12.5K)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "▷",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = formatNumber(reel.viewsCount),
                                    color = Color.White,
                                    fontSize = 11.5.sp,
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

private fun formatNumber(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
