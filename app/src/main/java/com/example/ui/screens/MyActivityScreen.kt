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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.UserCommentActivityDto
import com.example.data.model.UserLikedActivityDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val ActionGreen = Color(0xFF00D166)
private val ActivityCardBg = Color(0xFF121620)
private val TagBadgeBg = Color(0xFF1E2433)

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
    val activityState by viewModel.activityUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadUserActivity(isRefresh = false)
    }

    val activityData = activityState.activityData
    val likesList = activityData?.likesList ?: emptyList()
    val commentsList = activityData?.commentsList ?: emptyList()

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
            // Top Bar
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

            // Tabs Header
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
                            text = "My Likes (${likesList.size})",
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
                            text = "My Comments (${commentsList.size})",
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

            // Pull to Refresh Box
            val pullRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = activityState.isRefreshing,
                onRefresh = { viewModel.loadUserActivity(isRefresh = true) },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (activityState.isLoading && !activityState.isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.5.dp)
                    }
                } else {
                    if (selectedTab == 0) {
                        // ----------------- TAB 1: REAL LIKES -----------------
                        if (likesList.isEmpty()) {
                            EmptyStateNotice(message = "You haven't liked any posts yet.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(likesList, key = { it.idString }) { item ->
                                    RealLikedPostCard(
                                        item = item,
                                        userName = activityData?.userName ?: authState.userProfile?.displayName ?: "PlayDramaFlix User",
                                        userAvatar = activityData?.userAvatar ?: authState.userProfile?.avatar ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&q=80",
                                        onPlayClick = { onDramaClick(item.slug) },
                                        onShareClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Watch ${item.title} on PlayDramaFlix: https://playdramaflix.com/watch/${item.slug}")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // ----------------- TAB 2: REAL COMMENTS -----------------
                        if (commentsList.isEmpty()) {
                            EmptyStateNotice(message = "You haven't commented on any posts yet.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(commentsList, key = { it.commentIdString }) { item ->
                                    RealCommentPostCard(
                                        item = item,
                                        fallbackName = activityData?.userName ?: authState.userProfile?.displayName ?: "PlayDramaFlix User",
                                        fallbackAvatar = activityData?.userAvatar ?: authState.userProfile?.avatar ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&q=80",
                                        onPlayClick = {
                                            val slug = item.post?.slug ?: ""
                                            if (slug.isNotBlank()) onDramaClick(slug)
                                        },
                                        onShareClick = {
                                            val post = item.post
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "${item.commentText}\n\nWatch ${post?.title ?: "Drama"} on PlayDramaFlix: https://playdramaflix.com/watch/${post?.slug ?: ""}")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share comment"))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Real Liked Item Card (Tab 1)
// -------------------------------------------------------------
@Composable
private fun RealLikedPostCard(
    item: UserLikedActivityDto,
    userName: String,
    userAvatar: String,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(true) }
    var likesCount by remember { mutableIntStateOf(item.totalLikes ?: 0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ActivityCardBg)
            .padding(14.dp)
    ) {
        // User Header
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
                        model = userAvatar,
                        contentDescription = userName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column {
                    Text(
                        text = userName,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.likedDate ?: "Recent",
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

        // Title Caption
        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 16:9 Banner Video Preview with Play Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .clickable { onPlayClick() }
        ) {
            AsyncImage(
                model = item.bannerUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
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

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = item.effectiveDuration,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Badge Tag Label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TagBadgeBg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.effectiveBadge,
                color = TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Engagement Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable {
                        isLiked = !isLiked
                        likesCount = if (isLiked) likesCount + 1 else maxOf(0, likesCount - 1)
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
                        text = (item.totalComments ?: 0).toString(),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

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
                        text = (item.totalShares ?: 0).toString(),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(
                onClick = { Toast.makeText(context, "Downloading ${item.title}...", Toast.LENGTH_SHORT).show() },
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

// -------------------------------------------------------------
// Real Commented Item Card (Tab 2)
// -------------------------------------------------------------
@Composable
private fun RealCommentPostCard(
    item: UserCommentActivityDto,
    fallbackName: String,
    fallbackAvatar: String,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val post = item.post

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ActivityCardBg)
            .padding(14.dp)
    ) {
        // User Info Header
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
                        model = item.userAvatar ?: fallbackAvatar,
                        contentDescription = item.userName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column {
                    Text(
                        text = item.userName ?: fallbackName,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.commentDate ?: "Recent",
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

        // Actual User Comment Text
        Text(
            text = item.commentText,
            color = Color.White,
            fontSize = 13.5.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Attached Drama Card with Play Button
        if (post != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .clickable { onPlayClick() }
            ) {
                AsyncImage(
                    model = post.bannerUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
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

                Text(
                    text = post.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp)
                        .fillMaxWidth(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badge Tag Label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TagBadgeBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = post.effectiveBadge,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Engagement Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Like",
                        tint = ActionGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = (item.commentLikes ?: post?.totalLikes ?: 0).toString(),
                        color = ActionGreen,
                        fontSize = 12.sp
                    )
                }

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
                        text = (post?.totalComments ?: 0).toString(),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

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
                        text = (item.commentShares ?: post?.totalShares ?: 0).toString(),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 14.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
