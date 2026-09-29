@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPageDto
import com.example.data.model.UserReelDto
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val TikTokRed = Color(0xFFFE2C55) // 👈 স্ক্রিনশটের হুবহু TikTok রেড ফলো বাটন
private val DarkButtonBg = Color(0xFF1F222A)
private val TextMuted = Color(0xFF8692A6)

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
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Reels, 1: Reposts
    var isFollowingState by remember { mutableStateOf(false) }

    // ডাটাবেজ থেকে রিলস ও ক্রিয়েটর ইনফো লোড
    val feedState by reelsViewModel.feedState.collectAsState()
    val pageReels = remember(feedState.reels, pageId) {
        feedState.reels.filter { it.pageId == pageId }
    }

    // প্রথম রিলস থেকে অর্গানিক পেজ ডাটা ডিটেকশন
    val firstReel = pageReels.firstOrNull()
    val pageTitle = firstReel?.pageName ?: "Creator"
    val pageHandle = firstReel?.displayHandle ?: "@creator"
    val pageAvatar = firstReel?.pageAvatar

    val organicFollowersCount = remember(pageReels) {
        (pageReels.size * 150).coerceAtLeast(12)
    }

    val organicLikesCount = remember(pageReels) {
        pageReels.sumOf { it.likesCount }
    }

    val organicFollowingCount = 4

    val publicPageUrl = "https://playdramaflix.com/page/${pageHandle.removePrefix("@")}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // =========================================================================
            // 🔝 ১. স্ক্রিনশটের হুবহু পাবলিক ক্রিয়েটর হেডার সেকশন
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

                    // ২. স্ক্রিনশটের মূল হেডার: নাম, হ্যান্ডেল ও বড় গোল অবতার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pageTitle,
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = pageHandle,
                                color = TextMuted,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // স্ক্রিনশটের ৩টি মেট্রিক্স কাউন্টার: Following | Followers | Likes
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = organicFollowingCount.toString(),
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Following", color = TextMuted, fontSize = 11.5.sp)
                                }

                                Column {
                                    Text(
                                        text = formatNumber(organicFollowersCount.toLong()),
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Followers", color = TextMuted, fontSize = 11.5.sp)
                                }

                                Column {
                                    Text(
                                        text = formatNumber(organicLikesCount),
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Likes", color = TextMuted, fontSize = 11.5.sp)
                                }
                            }
                        }

                        // স্ক্রিনশটের ডানপাশের বড় গোল প্রোফাইল অবতার
                        Box(
                            modifier = Modifier
                                .size(88.dp)
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

                    Spacer(modifier = Modifier.height(18.dp))

                    // =========================================================================
                    // 🔘 ৩. স্ক্রিনশটের হুবহু বাটন রো: [ Follow ] [ Message ] [ 👤+ ]
                    // =========================================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // লাল Follow বাটন (ফলো করা থাকলে সফট ডার্ক "Following" হবে)
                        Button(
                            onClick = {
                                isFollowingState = !isFollowingState
                                coroutineScope.launch {
                                    repository.toggleFollowPage(pageId)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowingState) DarkButtonBg else TikTokRed
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = if (isFollowingState) "Following" else "Follow",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Message বাটন
                        Button(
                            onClick = {
                                onOpenDirectMessage(firstReel?.userId.toString(), pageTitle)
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

                        // ছোট 👤+ সাজেস্ট বাটন
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkButtonBg,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    Toast.makeText(context, "More creator options", Toast.LENGTH_SHORT).show()
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
                    // 📑 ৪. স্ক্রিনশটের হুবহু ট্যাব বার: [ 📊 Grid ]  [ 🔁 Reposts ]
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
            // 🎬 ৫. ৩ নম্বর ছবির মতো ৩-কলাম রিলস গ্রিড (ভিউ কাউন্টার ▷ 131.9K সহ)
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

                        // স্ক্রিনশটের হুবহু নিচে বাম কোণায় সাদা রঙের ভিউ কাউন্টার (▷ 131.9K)
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

private fun formatNumber(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
