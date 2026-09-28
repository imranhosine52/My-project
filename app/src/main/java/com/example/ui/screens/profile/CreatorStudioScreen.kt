@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwitchAccount
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
import com.example.ui.viewmodel.ReelsViewModel
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val AlertRed = Color(0xFFFF2A4B)
private val LinkCyan = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8692A6)

@Composable
fun CreatorStudioScreen(
    page: CreatorPageDto,
    reelsViewModel: ReelsViewModel,
    onSwitchToPersonalProfile: () -> Unit,
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val feedState by reelsViewModel.feedState.collectAsState()
    val pageReels = remember(feedState.reels, page.id) {
        feedState.reels.filter { it.pageId == page.id }
    }

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
            // 🔝 ৩ নম্বর ছবির হুবহু ক্রিয়েটর হেডার সেকশন (Full Width Span)
            // =========================================================================
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // ১. টপ অ্যাকশন বার: [ Back ] ----------------- [ 🔄 Switch to Personal ] [ ↗ Share ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 🔄 ফেসবুক স্টাইল প্রোফাইল সুইচিং বাটন
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E2638),
                                border = BorderStroke(0.8.dp, Color(0xFF00E676)),
                                modifier = Modifier.clickable { onSwitchToPersonalProfile() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwitchAccount,
                                        contentDescription = "Switch",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Personal",
                                        color = Color(0xFF00E676),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 🔗 পেজ লিংক শেয়ার বাটন
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Check out ${page.pageName} (${page.displayHandle}) on DramaFlix:\n${page.pageShareUrl}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Page Link"))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ২. ৩ নম্বর ছবির মূল হেডার: নাম, নোটিফিকেশন ৯+, হ্যান্ডেল ও ভাসমান স্টোরি অবতার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // পেজের নাম + লাল 9+ ব্যাজ
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = page.pageName.ifBlank { "Scene Flix" },
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AlertRed
                                ) {
                                    Text(
                                        text = "9+",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            // @handle
                            Text(
                                text = page.displayHandle,
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // ৩ নম্বর ছবির ৩টি মেট্রিক্স: Following, Follower, Likes
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = page.followingCount.toString(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Following", color = TextMuted, fontSize = 11.5.sp)
                                }

                                Column {
                                    Text(
                                        text = page.followersCount.toString(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Follower", color = TextMuted, fontSize = 11.5.sp)
                                }

                                Column {
                                    Text(
                                        text = page.totalLikes.toString(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Likes", color = TextMuted, fontSize = 11.5.sp)
                                }
                            }
                        }

                        // ৩ নম্বর ছবির ডানপাশের অবতার (What's good? বাবল + '+' বাটন সহ)
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF262C38)
                            ) {
                                Text(
                                    text = "What's good?",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Box(
                                modifier = Modifier.size(68.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color(0xFF1E2838), CircleShape)
                                        .background(Color(0xFF1A2230)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(page.avatar ?: "https://ui-avatars.com/api/?name=${page.pageName}&background=00E676&color=000&bold=true")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(LinkCyan)
                                        .border(1.5.dp, PureBlack, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Story",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ৩ নম্বর ছবির বায়ো ও ড্রামা লিংক: Full Drama Link 👉 https://...
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Page Link", page.pageShareUrl))
                                Toast.makeText(context, "Page link copied!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Full Drama Link 👉",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = page.pageShareUrl,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // =========================================================================
                    // 📑 ৩ নম্বর ছবির হুবহু ৫টি ট্যাব আইকন
                    // [ 📊 Grid | 🔒 Locked | 🔁 Reposts | 🔖 Bookmarks | ❤️ Liked ]
                    // =========================================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ট্যাব ০: রিলস গ্রিড (অ্যাক্টিভ লাইন সহ)
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(2.dp)
                                    .background(if (selectedTabIndex == 0) Color.White else Color.Transparent)
                            )
                        }

                        // ট্যাব ১: লকড / প্রাইভেট
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = if (selectedTabIndex == 1) Color.White else TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { selectedTabIndex = 1 }
                        )

                        // ট্যাব ২: রিপোস্ট
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Reposts",
                            tint = if (selectedTabIndex == 2) Color.White else TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { selectedTabIndex = 2 }
                        )

                        // ট্যাব ৩: সেভ / বুকমার্ক
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmarks",
                            tint = if (selectedTabIndex == 3) Color.White else TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { selectedTabIndex = 3 }
                        )

                        // ট্যাব ৪: লাইকড ভিডিও
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Liked",
                            tint = if (selectedTabIndex == 4) Color.White else TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { selectedTabIndex = 4 }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222634), thickness = 0.6.dp)
                }
            }

            // =========================================================================
            // 🎬 ৩ নম্বর ছবির মতো ৩-কলাম রিলস গ্রিড (ভিউ কাউন্টার ▷ 0, ▷ 2 সহ)
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
                            text = "No reels published on this page yet",
                            color = TextMuted,
                            fontSize = 13.sp
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

                        // ৩ নম্বর ছবির মতো নিচে বাম কোণায় সাদা রঙের ভিউ কাউন্টার (যেমন: ▷ 0, ▷ 2)
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = reel.viewsCount.toString(),
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
