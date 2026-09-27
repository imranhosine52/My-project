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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPageDto
import com.example.data.model.UserReelDto
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val ActionGreen = Color(0xFF00E676)
private val BgDark = Color(0xFF090C13)
private val CardDarkBg = Color(0xFF121622)
private val BorderStrokeColor = Color(0xFF1E2638)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun CreatorPageProfileScreen(
    pageId: Int,
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onReelClick: (UserReelDto) -> Unit,
    onOpenDirectChat: (creatorUserId: String, creatorName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pageInfo by remember { mutableStateOf<CreatorPageDto?>(null) }
    var pageReels by remember { mutableStateOf<List<UserReelDto>>(emptyList()) }
    var isFollowingState by remember { mutableStateOf(false) }
    var followersCountState by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    // পেজের ডেটা ও রিলস লোড করা
    LaunchedEffect(pageId) {
        isLoading = true
        val reelsRes = viewModel.repository.getReelsFeed(tab = "for_you", page = 1)
        val allReels = reelsRes.getOrDefault(emptyList())
        val matching = allReels.filter { it.pageId == pageId }

        pageReels = matching

        // পেজ মেটাডাটা
        val first = matching.firstOrNull()
        if (first != null) {
            val dto = CreatorPageDto(
                id = pageId,
                userId = first.userId,
                pageName = first.pageName,
                handle = first.handle.removePrefix("@"),
                avatar = first.pageAvatar,
                status = "approved",
                rawFollowersCount = 120,
                rawTotalViews = matching.sumOf { it.viewsCount }
            )
            pageInfo = dto
            followersCountState = dto.followersCount
        } else {
            pageInfo = CreatorPageDto(
                id = pageId,
                pageName = "Creator Page",
                handle = "creator",
                status = "approved"
            )
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (isLoading && pageInfo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ActionGreen, strokeWidth = 3.dp)
            }
        } else {
            val page = pageInfo!!

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // =============================================================
                // 🔝 ১. কভার, লোগো ও পেজ ইনফো (Full Span)
                // =============================================================
                item(span = { GridItemSpan(3) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // কভার ব্যানার ও ব্যাক বাটন
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1E2838), Color(0xFF0F1520))
                                    )
                                )
                        ) {
                            if (!page.cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(page.cover).crossfade(true).build(),
                                    contentDescription = "Cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(0.6f), Color.Transparent, BgDark)
                                        )
                                    )
                            )

                            // ব্যাক ও শেয়ার বাটন
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBackClick) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                                IconButton(onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Check out ${page.pageName} (@${page.handle}) on PlayDramaFlix Reels!")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Page"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                                }
                            }
                        }

                        // পেজ লোগো অবতার (কভারের সাথে কিছুটা ওভারল্যাপ)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .offset(y = (-38).dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2838))
                                    .border(2.5.dp, BgDark, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = page.avatar ?: "https://ui-avatars.com/api/?name=${page.pageName}&background=00E676&color=000&bold=true",
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // পেজের নাম ও বিবরণ
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-30).dp)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = page.pageName,
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = ActionGreen, modifier = Modifier.size(17.dp))
                            }

                            Text(
                                text = page.displayHandle,
                                color = ActionGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (!page.bio.isNullOrBlank()) {
                                Text(
                                    text = page.bio!!,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 📊 লাইভ মেট্রিক্স বার (Followers, Views, Reels)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardDarkBg,
                                border = BorderStroke(0.8.dp, BorderStrokeColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = followersCountState.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("Followers", color = TextMuted, fontSize = 11.sp)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderStrokeColor))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = formatMetric(page.totalViews), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("Total Views", color = TextMuted, fontSize = 11.sp)
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderStrokeColor))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = pageReels.size.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("Reels", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 🔘 অ্যাকশন বাটনসমূহ: [ Follow / Following ] ও [ Message 💬 ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isFollowingState = !isFollowingState
                                        followersCountState += if (isFollowingState) 1 else -1
                                        coroutineScope.launch {
                                            viewModel.repository.toggleFollowPage(pageId)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowingState) Color(0xFF1E2838) else ActionGreen
                                    ),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(42.dp)
                                ) {
                                    Text(
                                        text = if (isFollowingState) "Following ✓" else "+ Follow",
                                        color = if (isFollowingState) Color.White else Color.Black,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        onOpenDirectChat(page.userId.toString(), page.pageName)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2432)),
                                    border = BorderStroke(1.dp, BorderStrokeColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                        Text("Message", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // রিলস সেকশন হেডার
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-16).dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(18.dp))
                            Text("Reels & Videos", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // =============================================================
                // 🎬 ২. ৩-কলাম রিলস গ্রিড
                // =============================================================
                if (pageReels.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No reels published on this page yet", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(pageReels, key = { it.id }) { reel ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.68f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CardDarkBg)
                                .clickable { onReelClick(reel) }
                        ) {
                            AsyncImage(
                                model = reel.thumbUrl.ifBlank { reel.videoUrl },
                                contentDescription = reel.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // শ্যাডো ও ভিউস কাউন্টার
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(0.75f))
                                        )
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Text(
                                    text = formatMetric(reel.viewsCount),
                                    color = Color.White,
                                    fontSize = 10.sp,
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

private fun formatMetric(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
