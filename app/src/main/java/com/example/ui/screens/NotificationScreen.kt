@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.data.model.NotificationItemDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardDarkBg = Color(0xFF131722)
private val UnreadBorderGold = Color(0xFFFFB300)
private val ActionRed = Color(0xFFFF3B30)
private val ActionGreen = Color(0xFF00D166)

@Composable
fun NotificationScreen(
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notificationState by viewModel.notificationUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
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
            // 🔝 Top App Bar
            Surface(
                color = SurfaceDark,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(22.dp)
                        )

                        Text(
                            text = "Notifications",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (notificationState.unreadCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ActionRed
                            ) {
                                Text(
                                    text = "${notificationState.unreadCount} New",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }

                    // 🗑️ Clear All Button
                    if (notificationState.notifications.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearAllNotifications()
                                Toast.makeText(context, "All notifications cleared", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, ActionRed.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = ActionRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear All",
                                color = ActionRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 📱 Notification List with Cinema Poster Card Design
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadNotifications()
                        delay(400)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (notificationState.isLoading && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TealAccent, strokeWidth = 2.5.dp)
                    }
                } else if (notificationState.notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVariantDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Text(
                                text = "No new notifications",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "You're all caught up! You'll be notified as soon as new episodes or movies are released.",
                                color = TextMuted,
                                fontSize = 12.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notificationState.notifications, key = { it.id }) { item ->
                            val isUnread = item.id !in notificationState.readNotificationIds && !item.isRead

                            // 🔍 স্মার্ট ড্রামা পোস্টার ও স্লাগ ডিটেক্টর
                            val matchedDrama = homeState.popularDramas.find { drama ->
                                drama.slug.equals(item.targetSlug, ignoreCase = true) ||
                                drama.title.contains(item.title.take(15), ignoreCase = true) ||
                                item.title.contains(drama.title.take(15), ignoreCase = true)
                            } ?: homeState.recentlyAdded.find { drama ->
                                item.title.contains(drama.title.take(12), ignoreCase = true)
                            }

                            val finalPosterUrl = item.effectivePoster
                                ?: matchedDrama?.posterUrl
                                ?: matchedDrama?.bannerUrl
                                ?: "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp"

                            val targetSlug = item.targetSlug.ifBlank { matchedDrama?.slug ?: "" }

                            CinemaPosterNotificationCard(
                                item = item,
                                posterUrl = finalPosterUrl,
                                isUnread = isUnread,
                                onClick = {
                                    viewModel.markNotificationAsRead(item.id)
                                    if (targetSlug.isNotBlank()) {
                                        onDramaClick(targetSlug)
                                    }
                                },
                                onDelete = { viewModel.deleteNotification(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🎬 সিনেমা পোস্টার স্টাইল নোটিফিকেশন কার্ড (আকর্ষণীয় ও আধুনিক ডিজাইন)
// -------------------------------------------------------------
@Composable
private fun CinemaPosterNotificationCard(
    item: NotificationItemDto,
    posterUrl: String,
    isUnread: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    // ভাষা/ডাবিং ট্যাগ ডিটেক্ট করা
    val isHindi = item.title.contains("Hindi", true) || item.message.contains("Hindi", true)
    val isBangla = item.title.contains("Bangla", true) || item.message.contains("Bangla", true) || (!isHindi)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        border = BorderStroke(
            width = if (isUnread) 1.2.dp else 0.8.dp,
            color = if (isUnread) UnreadBorderGold.copy(alpha = 0.8f) else Color(0xFF222838)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🖼️ ৩:৪ সিনেমা পোস্টার বক্স
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2433))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // হালকা ডার্ক গ্রেডিয়েন্ট
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // 🏷️ ডাবিং ব্যাজ (পোস্টারের উপরে ডানে)
                Surface(
                    shape = RoundedCornerShape(topEnd = 10.dp, bottomStart = 6.dp),
                    color = if (isHindi) Color(0xFF1E88E5) else Color(0xFFFFB300),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (isHindi) "Hindi" else "Bangla",
                        color = if (isHindi) Color.White else Color.Black,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // প্লে ওভারলে আইকন (পোস্টারের মাঝে)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 📝 নোটিফিকেশন শিরোনাম, মেসেজ ও টাইম
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isUnread) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ActionRed)
                            )
                        }
                    }

                    // ✕ ডিলিট বাটন (এক ক্লিকে স্থায়ী ডিলিট)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = Color(0xFF7E869E),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    text = item.message,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // নিচে সময় ও "Watch Now" বাটন
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.timeAgo,
                            color = Color(0xFF64748B),
                            fontSize = 10.5.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1A2638),
                        border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Watch Now",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
