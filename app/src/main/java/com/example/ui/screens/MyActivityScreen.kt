@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val ActionGreen = Color(0xFF00D166)
private val ActivityCardBg = Color(0xFF121620)
private val TagBadgeBg = Color(0xFF1E2433)

private data class ActivityFeedItem(
    val id: String,
    val userName: String,
    val userAvatar: String,
    val date: String,
    val caption: String,
    val dramaTitle: String,
    val dramaSlug: String,
    val bannerUrl: String,
    val duration: String,
    val tagLabel: String,
    val initialLikes: Int,
    val commentsCount: Int,
    val sharesCount: Int
)

@Composable
fun MyActivityScreen(
    viewModel: DramaFlixViewModel,
    initialTab: Int = 0, // 0: My Likes, 1: My Comments
    onBackClick: () -> Unit,
    onDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    val currentUser = authState.userProfile?.displayName ?: "DramaFlix User"
    val currentAvatar = authState.userProfile?.avatar ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&q=80"

    // Mock/Generated feed items based on loaded dramas
    val activityList = remember(homeState.popularDramas, selectedTab) {
        val dramas = homeState.popularDramas.ifEmpty { homeState.recentlyAdded }
        dramas.take(6).mapIndexed { index, drama ->
            ActivityFeedItem(
                id = "act_$index",
                userName = if (selectedTab == 1) currentUser else listOf("sparkfilm", "ScreenSpy", "XX Films", "DramaQueen", "CinePhile").getOrElse(index % 5) { "MovieFan" },
                userAvatar = if (selectedTab == 1) currentAvatar else "https://images.unsplash.com/photo-${1534528741775 + index * 100}?w=120&auto=format&fit=crop&q=80",
                date = "${index + 1}0/08/2025",
                caption = if (selectedTab == 1) "Watched full episode! Absolutely thrilling plot and acting 🎬🔥 #PlayDramaFlix" else "This one's a must-watch. Pure cinematic gold 🎬✨ #trending #kdrama",
                dramaTitle = drama.title,
                dramaSlug = drama.slug,
                bannerUrl = drama.bannerUrl ?: drama.posterUrl ?: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&q=80",
                duration = "01:2${index % 9}",
                tagLabel = if (drama.isBanglaDub) "🎬 Bangla Dubbed Series" else "🍿 What to Watch • ${drama.releaseYear}",
                initialLikes = (index + 2) * 1420,
                commentsCount = (index + 1) * 45,
                sharesCount = (index + 1) * 180
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // -------------------------------------------------------------
            // Top Bar
            // -------------------------------------------------------------
            Surface(
                color = SurfaceDark,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceVariantDark)
                            .clickable { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "My Activity",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // -------------------------------------------------------------
            // Tabs: My Likes & My Comments
            // -------------------------------------------------------------
            Surface(color = SurfaceDark) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Tab 0: My Likes
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "My Likes",
                            color = if (selectedTab == 0) ActionGreen else TextMuted,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(2.5.dp)
                                .background(if (selectedTab == 0) ActionGreen else Color.Transparent)
                        )
                    }

                    // Tab 1: My Comments
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "My Comments",
                            color = if (selectedTab == 1) ActionGreen else TextMuted,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(2.5.dp)
                                .background(if (selectedTab == 1) ActionGreen else Color.Transparent)
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // Feed List
            // -------------------------------------------------------------
            if (activityList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0) "No liked posts yet." else "No comments found.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(activityList, key = { it.id }) { item ->
                        ActivityFeedCard(
                            item = item,
                            onPlayClick = { onDramaClick(item.dramaSlug) },
                            onShareClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Watch ${item.dramaTitle} on PlayDramaFlix: https://playdramaflix.com/watch/${item.dramaSlug}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityFeedCard(
    item: ActivityFeedItem,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(true) }
    var likesCount by remember { mutableIntStateOf(item.initialLikes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ActivityCardBg)
            .padding(14.dp)
    ) {
        // User Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceVariantDark)
                ) {
                    AsyncImage(
                        model = item.userAvatar,
                        contentDescription = item.userName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column {
                    Text(
                        text = item.userName,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.date,
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Caption Text
        Text(
            text = item.caption,
            color = TextPrimary,
            fontSize = 13.5.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Video Thumbnail Container with Play Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .clickable { onPlayClick() }
        ) {
            AsyncImage(
                model = item.bannerUrl,
                contentDescription = item.dramaTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark subtle gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )

            // Center Play Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Drama Title at Bottom Left
            Text(
                text = item.dramaTitle,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 10.dp)
                    .fillMaxWidth(0.75f)
            )

            // Duration Badge at Bottom Right
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = item.duration,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Channel / Tag Label Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TagBadgeBg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(14.dp))
            Text(
                text = item.tagLabel,
                color = TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Interaction Footer (Like, Comment, Share, Download)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Like Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable {
                        isLiked = !isLiked
                        likesCount = if (isLiked) likesCount + 1 else likesCount - 1
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) ActionGreen else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (likesCount >= 1000) "${likesCount / 1000}.${(likesCount % 1000) / 100}K" else "$likesCount",
                        color = if (isLiked) ActionGreen else TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Comment Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onPlayClick() }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = item.commentsCount.toString(),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Share Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onShareClick() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = item.sharesCount.toString(),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Download Icon
            IconButton(
                onClick = {
                    Toast.makeText(context, "Downloading ${item.dramaTitle}...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Download",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
