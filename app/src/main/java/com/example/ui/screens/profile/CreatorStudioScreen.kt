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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Edit
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
import com.example.data.repository.ReelsRepository
import com.example.ui.viewmodel.ReelsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val AlertRed = Color(0xFFFF2A4B)
private val LinkCyan = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8692A6)
private val ActionGreen = Color(0xFF00E676)
private val BookmarkGold = Color(0xFFFACC15)

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
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    var activePageData by remember { mutableStateOf(page) }
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Reels, 1: Locked, 2: Reposts, 3: Saved, 4: Liked
    var showEditProfileSheet by remember { mutableStateOf(false) }

    val feedState by reelsViewModel.feedState.collectAsState()
    val pageReels = remember(feedState.reels, activePageData.id) {
        feedState.reels.filter { it.pageId == activePageData.id }
    }

    // 🎯 ৩ নম্বর ট্যাবের জন্য সার্ভার থেকে সেভড রিলস স্টেট
    var savedReelsList by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isLoadingSavedReels by remember { mutableStateOf(false) }

    // যখন ইউজার সেভড ট্যাবে (ট্যাব ৩) ক্লিক করবে, সার্ভার থেকে get_saved_reels ফেচ হবে
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 3) {
            isLoadingSavedReels = true
            val result = repository.getSavedReels()
            savedReelsList = result.getOrDefault(emptyList())
            isLoadingSavedReels = false
        }
    }

    val organicLikesCount = remember(pageReels, activePageData.totalLikes) {
        if (activePageData.totalLikes > 0L) activePageData.totalLikes
        else pageReels.sumOf { it.likesCount }
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
            // 🔝 ১. ক্রিয়েটর হেডার সেকশন (Full Width Span)
            // =========================================================================
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // টপ অ্যাকশন বার
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF19202E),
                                border = BorderStroke(0.8.dp, Color(0xFF2E384D)),
                                modifier = Modifier.clickable { showEditProfileSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Edit",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E2638),
                                border = BorderStroke(0.8.dp, ActionGreen),
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
                                        tint = ActionGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Personal",
                                        color = ActionGreen,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Check out ${activePageData.pageName} (${activePageData.displayHandle}) on DramaFlix:\n${activePageData.pageShareUrl}"
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

                    // পেজ নাম, হ্যান্ডেল ও মেটাবক্স
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = activePageData.pageName.ifBlank { "Creator Page" },
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AlertRed
                                ) {
                                    Text(
                                        text = "9+",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = activePageData.displayHandle,
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // অর্গানিক লাইভ মেট্রিক্স
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = activePageData.followingCount.toString(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Following", color = TextMuted, fontSize = 11.5.sp)
                                }

                                Column {
                                    Text(
                                        text = activePageData.followersCount.toString(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Follower", color = TextMuted, fontSize = 11.5.sp)
                                }

                                Column {
                                    Text(
                                        text = organicLikesCount.toString(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("Likes", color = TextMuted, fontSize = 11.5.sp)
                                }
                            }
                        }

                        // পেজ লোগো অবতার
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
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable { showEditProfileSheet = true },
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
                                    if (!activePageData.avatar.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(activePageData.avatar)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Page Avatar",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = activePageData.pageName.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                                        contentDescription = "Edit photo",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // বায়ো ও ড্রামা লিংক
                    val effectiveBioText = remember(activePageData.bio, activePageData.handle) {
                        activePageData.bio?.takeIf { it.isNotBlank() } 
                            ?: "Full Drama Link 👉 https://playdramaflix.com/page/${activePageData.handle.removePrefix("@")}"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Page Bio Link", activePageData.pageShareUrl))
                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = effectiveBioText,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // =========================================================================
                    // 📑 ৫টি ট্যাব আইকন (৩ নম্বর ট্যাবে Saved Reels কানেকশন)
                    // =========================================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ট্যাব ০: রিলস গ্রিড
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

                        // ট্যাব ১: লকড
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = if (selectedTabIndex == 1) Color.White else TextMuted,
                            modifier = Modifier.size(20.dp).clickable { selectedTabIndex = 1 }
                        )

                        // ট্যাব ২: রিপোস্ট
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Reposts",
                            tint = if (selectedTabIndex == 2) Color.White else TextMuted,
                            modifier = Modifier.size(20.dp).clickable { selectedTabIndex = 2 }
                        )

                        // 🎯 ট্যাব ৩: সেভ / বুকমার্ক (GET get_saved_reels)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedTabIndex = 3 }
                        ) {
                            Icon(
                                imageVector = if (selectedTabIndex == 3) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Saved Reels",
                                tint = if (selectedTabIndex == 3) BookmarkGold else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(2.dp)
                                    .background(if (selectedTabIndex == 3) BookmarkGold else Color.Transparent)
                            )
                        }

                        // ট্যাব ৪: লাইকড ভিডিও
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Liked",
                            tint = if (selectedTabIndex == 4) Color.White else TextMuted,
                            modifier = Modifier.size(20.dp).clickable { selectedTabIndex = 4 }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222634), thickness = 0.6.dp)
                }
            }

            // =========================================================================
            // 🎬 রিলস গ্রিড (স্বাভাবিক রিলস অথবা সেভ করা রিলস)
            // =========================================================================
            when (selectedTabIndex) {
                // 🎯 ৩ নম্বর ট্যাব: সার্ভার থেকে পাওয়া Saved Reels
                3 -> {
                    if (isLoadingSavedReels) {
                        item(span = { GridItemSpan(3) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BookmarkGold, strokeWidth = 2.5.dp)
                            }
                        }
                    } else if (savedReelsList.isEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
                                Text("No saved reels found in your collection", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(savedReelsList, key = { "saved_${it.id}" }) { reel ->
                            ReelGridThumbnailItem(reel = reel, onReelClick = onReelClick)
                        }
                    }
                }

                // ০ নম্বর ট্যাব: নিজের পেজের রিলস
                0 -> {
                    if (pageReels.isEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
                                Text("No reels published on this page yet", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(pageReels, key = { it.id }) { reel ->
                            ReelGridThumbnailItem(reel = reel, onReelClick = onReelClick)
                        }
                    }
                }

                // অন্যান্য ট্যাব (লকড, রিপোস্ট, লাইকড)
                else -> {
                    item(span = { GridItemSpan(3) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
                            Text("No items found in this section", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Edit Profile Sheet
        if (showEditProfileSheet) {
            EditPageProfileSheet(
                page = activePageData,
                repository = repository,
                onBackClick = { showEditProfileSheet = false },
                onPageUpdated = { updated ->
                    activePageData = updated
                }
            )
        }
    }
}

@Composable
private fun ReelGridThumbnailItem(
    reel: UserReelDto,
    onReelClick: (UserReelDto) -> Unit
) {
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
